(ns kafkus.kafka
  (:require [cyrus-config.core :as conf]
            [dvlopt.kafka :as K]
            [dvlopt.kafka.admin :as K.admin]
            [dvlopt.kafka.in :as K.in]
            [dvlopt.kafka.out :as K.out]
            [deercreeklabs.lancaster :as avro]
            [kafka-avro-confluent.v2.deserializer :as des]
            [kafka-avro-confluent.v2.serializer :as ser]
            [kafkus.avro :as kavro]
            [abracad.avro :as abracad-avro]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.core.async :as a]
            [taoensso.timbre :as log])
  (:import [java.util.concurrent TimeUnit]))

(conf/def default-schema-registry-url "URL of Confluent Schema Registry"
  {:spec string?
   :default "http://localhost:8081"})

(conf/def default-bootstrap-server "Default Kafka bootstrap server"
  {:spec string?
   :default "localhost:9092"})

(defn get-this-schema-deserializer [schema]
  (fn [ba _]
    (if (nil? ba)
      nil
      (avro/deserialize-same schema ba))))

(defn get-this-schema-serializer [schema]
  (fn [data _]
    (if (nil? data)
      nil (avro/serialize schema data))))

(defn get-nodes-from-bootstraps [bootstrap-servers]
  (let [nodes-raw (-> bootstrap-servers
                      (str/split #","))]
    (->> nodes-raw
         (map #(str/split % #":"))
         (map #(identity [(first %) (Integer. (second %))])))))

(defn get-schema-registry-deserializer [config]
  (let [deser (des/->avro-deserializer (->> config
                                            :schema-registry-url
                                            (assoc {} :schema-registry/base-url)))]
    (fn [data _]
      (if (nil? data)
        nil
        (.deserialize deser (:topic config) data)))))

(defn get-mode-deserializer [mode config]
  (case mode
    "raw" (get K/deserializers :string)
    "avro-raw" (get-this-schema-deserializer (avro/json->schema (get config :schema)))
    "avro-schema-registry" (get-schema-registry-deserializer config)))

(defn get-schema-registry-serializer [config]
  (let [serializer (ser/->avro-serializer
                    {:schema-registry/base-url
                     (:schema-registry-url config)})]
    (fn [data _]
      (if (nil? data)
        nil
        (let [res (.serialize serializer (:topic config)
                              {:value data
                               :schema (abracad-avro/parse-schema (:schema config))})]
          res)))))

(defn get-mode-serializer [mode config]
  (case mode
    "raw" (get K/serializers :string)
    "avro-raw" (get-this-schema-serializer (avro/json->schema (get config :schema)))
    "avro-schema-registry" (get-schema-registry-serializer config)))

(defn list-topics
  "Get list of non-internal topics whose name doesnt start with _
  For the ones that start with _ you probably know what you're doing,
  so there is no issue to patch this function/consume the data another way"
  [{:keys [bootstrap-servers] :as config}]
  (with-open [admin
              (K.admin/admin
               {::K/nodes (get-nodes-from-bootstraps bootstrap-servers)})]
    (->> (keys @(K.admin/topics admin {::K/internal? false}))
         (filter #(not (str/starts-with? % "_"))))))

(defn consume-from-topic
  "given configuration, start consuming data from the topic into a core-async channel"
  [{:keys [bootstrap-servers mode channel control rate] :as config}]
  (let [consumer
        (K.in/consumer {::K/nodes (get-nodes-from-bootstraps bootstrap-servers)
                        ::K/deserializer.key (get K/deserializers :string)
                        ::K/deserializer.value (get-mode-deserializer mode config)
                        ::K.in/configuration (walk/stringify-keys config)})
        control-channel (a/chan)]
    (K.in/register-for consumer [(get config :topic)])
    (a/go-loop []
      (doseq [record (K.in/poll consumer)]
        (when (not (nil? (::K/value record)))
          (a/timeout (/ (.toMillis TimeUnit/SECONDS 1) rate))
          (a/>! channel (::K/value record))))
      (if (nil? (a/poll! control-channel))
        (recur)
        (do
          (log/infof "terminating consumer %s" consumer)
          (.close consumer))))
    control-channel))

(defn stop! [control-channel]
  (a/>!! control-channel :stop))

(defn produce-to-topic
  [{:keys [bootstrap-servers topic payload mode schema]
    :as config}]
  (with-open [producer
              (K.out/producer
               {::K/nodes (get-nodes-from-bootstraps bootstrap-servers)
                ::K/serializer.key (K/serializers :string)
                ::K/serializer.value (get-mode-serializer mode config)
                ::K.out/configuration (walk/stringify-keys config)})]
    (K.out/send producer
                {::K/topic topic
                 ::K/key (str "kafkus-" (java.util.UUID/randomUUID))
                 ::K/value payload})))
