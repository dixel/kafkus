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
            [clojure.string :as str]
            [clojure.walk :as walk]
            [taoensso.timbre :as log])
  (:import [org.apache.kafka.common.serialization Deserializer]))

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

(defn get-mode-deserializer [mode config]
  (case mode
    "raw" (get K/deserializers :string)
    "avro-raw" (get-this-schema-deserializer (avro/json->schema (get config :schema)))
    "avro-schema-registry" (des/->avro-deserializer config)))

(defn get-mode-serializer [mode config]
  (case mode
    "raw" (get K/serializers :string)
    "avro-raw" (get-this-schema-serializer (avro/json->schema (get config :schema)))
    "avro-schema-registry" (ser/->avro-serializer config)))

(defn consume-from-topic [{:keys [bootstrap-servers mode channel] :as config}]
  (with-open [consumer
              (K.in/consumer {::K/nodes (get-nodes-from-bootstraps bootstrap-servers)
                              ::K/deserializer.key (get K/deserializers :string)
                              ::K/deserializer.value (get-mode-deserializer mode config)
                              ::K.in/configuration (walk/stringify-keys config)})]
    (K.in/register-for consumer [(get config :topic)])
    (doseq [record (K.in/poll consumer)]
      (log/info record))
    (K.in/commit-offsets consumer)))

(defn produce-to-topic [{:keys [bootstrap-servers topic payload mode]
                         :as config}]
  (with-open [producer
              (K.out/producer
               {::K/nodes (get-nodes-from-bootstraps bootstrap-servers)
                ::K/serializer.key (K/serializers :string)
                ::K/serializer.value (get-mode-serializer mode config)
                ::K/out/configuration (walk/stringify-keys config)})]
    (K.out/send producer
                {::K/topic topic
                 ::K/key (str "kafkus-" (java.util.UUID/randomUUID))
                 ::K/value payload})))

(comment
  (def debug-config
    {:bootstrap-servers "localhost:9092"
     :mode "avro-raw"
     :schema sample-schema
     :auto.offset.reset "earliest"
     :enable.auto.commit false
     :topic "challenge"
     :payload (kavro/sample-data kavro/sample-schema)
     :group.id (str "kafkus-consumer-" (str (java.util.UUID/randomUUID)))})
  
  )
