(ns kafkus.kafka
  (:require [cyrus-config.core :as conf]
            [dvlopt.kafka :as K]
            [dvlopt.kafka.admin :as K.admin]
            [dvlopt.kafka.in :as K.in]
            [dvlopt.kafka.out :as K.out]
            [deercreeklabs.lancaster :as avro]
            [kafka-avro-confluent.deserializers :as des]
            [kafka-avro-confluent.schema-registry-client :as schema-registry-client]
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

(conf/def default-rate "default number of messages per second shown"
  {:spec int?
   :default 1})

(conf/def default-limit "default number of messages displayed"
  {:spec int?
   :default 1000})

(conf/def default-schema-registry-url "default URL for confluent schema registry"
  {:spec string?
   :default "http://localhost:8081"})

(conf/def default-mode "default ser/de mode (avro-raw/raw/avro-schema-registry"
  {:spec string?
   :default "avro-schema-registry"})

(conf/def default-auto-offset-reset "default auto.offset.reset consumer option"
  {:spec string?
   :default "latest"})

(conf/def default-sasl-mechanism "default SASL auth mechanism for kafka"
  {:spec string?
   :default "PLAIN"})

(conf/def default-sasl-jaas-config "default kafka JAAS config format string (%s for username/password placeholder)"
  {:spec string?
   :default "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";"})

(conf/def default-security-protocol "default kafka auth security protocol"
  {:spec string?
   :default "PLAINTEXT"})

(defn get-this-schema-deserializer [schema]
  (fn [ba _]
    (if (nil? ba)
      nil
      (try
        (avro/deserialize-same schema ba)
        (catch Exception e
          {:error (format "failed to deserialize %s with provided schema: %s"
                          (String. ba) (.getMessage e))})))))

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
  (let [deser (des/->avro-deserializer
               (schema-registry-client/->schema-registry-client
                {:base-url
                 (or
                  (get config :schema-registry-url)
                  default-schema-registry-url)})
               :convert-logical-types? false)]
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
                     (or
                      (get config :schema-registry-url) default-schema-registry-url)})]
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
               {::K/nodes (get-nodes-from-bootstraps (or bootstrap-servers
                                                         default-bootstrap-server))
                ::K.admin/configuration (walk/stringify-keys config)})]
    (->> (keys @(K.admin/topics admin {::K/internal? false}))
         (filter #(not (str/starts-with? % "_"))))))

(defn consume!
  "given configuration, start consuming data from the topic into a core-async channel"
  [{:keys [bootstrap-servers mode callback rate] :as config}]
  (let [consumer-config {::K/nodes (get-nodes-from-bootstraps bootstrap-servers)
                         ::K/deserializer.key (get K/deserializers :string)
                         ::K/deserializer.value (get-mode-deserializer mode config)
                         ::K.in/configuration (walk/stringify-keys config)}
        consumer
        (K.in/consumer consumer-config)
        control-channel (a/chan)]
    (log/debugf "starting consumer with config: %s" consumer-config)
    (K.in/register-for consumer [(get config :topic)])
    (a/go-loop [[record & records] (K.in/poll consumer
                                              {::K/timeout [0 :milliseconds]})]
      (when-let [value (::K/value record)]
        (a/<! (a/timeout (/ (.toMillis TimeUnit/SECONDS 1)
                            (or rate default-rate))))
        (log/debugf "kafka message: %s" value)
        (callback value))
      (cond
        (= :stop (a/poll! control-channel))
        (do
          (log/infof "terminating consumer %s" consumer)
          (a/close! control-channel)
          (.close consumer))

        (nil? records)
        (recur (K.in/poll consumer {::K/timeout [0 :milliseconds]}))

        :else
        (recur records)))
    control-channel))

(defn stop! [control-channel]
  (a/go (a/>! control-channel :stop)))

(defn produce!
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
