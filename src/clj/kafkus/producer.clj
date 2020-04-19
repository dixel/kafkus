(ns kafkus.producer
  (:gen-class)
  (:require [clojure.string :as str]
            [cyrus-config.core :as conf]
            [kafkus.core :as core]
            [kafkus.kafka :as kafka]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(conf/def dummy-producer-sleep "time (milliseconds) between messages for dummy producer"
  {:spec int?
   :default 5000})

(conf/def dummy-producer-payload-size "number of messages to produce from dummy producer before stopping"
  {:spec int?
   :default 100000})

(def id (atom 0))

(defn get-dummy-producer-config []
  {:bootstrap-servers kafka/default-bootstrap-server
   :mode kafka/default-mode
   :enable.auto.commit true
   :topic "-kafkus-test-topic"
   :schema (json/encode
            {"type" "record",
             "name" "sampleSchema",
             "namespace" "com.sample.namespace",
             "fields"
             [{"name" "id",
               "type" "long"}
              {"name" "uuid",
               "type" "string"}
              {"name" "timestamp",
               "type" ["null"
                       {"type" "long",
                        "logical-type" "timestamp-millis"}],
               "default" "null"}]})
   :schema-registry-url kafka/default-schema-registry-url
   :callback (fn [payload] (log/info "producer callback: " payload))
   :sasl.mechanism kafka/default-sasl-mechanism
   :sasl.jaas.config "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"kafkabroker1\" password=\"kafkabroker1-secret\";"
   :security.protocol kafka/default-security-protocol
   :payload {:id (swap! id inc)
             :uuid (str (java.util.UUID/randomUUID))
             :timestamp (System/currentTimeMillis)}})

(defn -main [& args]
  (log/merge-config! {:level (keyword (str/lower-case core/log-level))
                      :ns-whitelist ["kafkus.*"]})
  (doseq [i (range dummy-producer-payload-size)]
    (Thread/sleep dummy-producer-sleep)
    (let [msg (get-dummy-producer-config)]
      (log/info "producing to kafka: " msg)
      (kafka/produce! msg))))
