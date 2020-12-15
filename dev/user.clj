(ns user
  (:require [mount.core :as mount]
            [cyrus-config.core :as conf]
            [kafkus.avro :as kavro]
            [kafkus.kafka :as kafka]
            [kafkus.server :as server]
            [deercreeklabs.lancaster :as avro]
            [cheshire.core :as json]
            [clojure.core.async :as a]
            [taoensso.timbre :as log]))

(log/merge-config! {:level :debug
                    :ns-whitelist ["kafkus.*"]})

(require '[kafkus.api :as api])

(try
  (conf/reload-with-override! (read-string (slurp ".config.edn")))
  (catch Exception e
    :pass))

(defn start []
  (mount/start))

(defn stop []
  (mount/stop))

(comment
  (def input (a/chan))

  (a/go-loop []
    (let [payload (a/<! input)]
      (clojure.pprint/pprint payload)
      (when (not (nil? payload))
        (recur))))

  (def incer (atom 0))

  (defn get-schema-registry-config []
    {:bootstrap-servers "localhost:9092"
     :mode "avro-schema-registry"
     :channel input
     :auto.offset.reset "earliest"
     :enable.auto.commit false
     :topic "-kafkus-enum"
     :schema "{
        \"type\": \"record\",
        \"name\": \"sampleSchema\",
        \"fields\": [
        {
          \"name\": \"id\",
          \"type\": \"long\"
        },
        {
          \"name\": \"name\",
          \"type\": \"string\"
        },
        {
          \"name\": \"someEnum\",
          \"type\": {
            \"type\": \"enum\",
            \"name\": \"SomeEnum\",
            \"symbols\": [\"SINGLE\", \"DOUBLE_WORD\", \"DOUBLE_WORD_SNAKE\"]
          }
        },
        {
          \"name\": \"stringField\",
          \"type\": \"string\",
          \"default\": \"\"
        },
        {
          \"name\": \"dateField\",
          \"type\": [\"null\", {
                     \"type\": \"long\",
                     \"logical-type\": \"timestamp-millis\"
          }],
          \"default\": \"null\"
        }
        ]
     }"
     :schema-registry-url "http://localhost:8081"
     :callback (fn [payload] (clojure.pprint/pprint payload))
     :sasl.mechanism "PLAIN"
     :sasl.jaas.config "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"kafkabroker1\" password=\"kafkabroker1-secret\";"
     :security.protocol "PLAINTEXT"
     :rate 15
     :payload {:id (swap! incer inc)
               :name "Amazing User"
               :stringField "produced some test data"
               :someEnum "DOUBLE_WORD_SNAKE"
               :dateField 0}
     :group.id (str "kafkus-consumer-" (str (java.util.UUID/randomUUID)))})

  (def consumer
    (kafka/consume! (get-schema-registry-config))))
