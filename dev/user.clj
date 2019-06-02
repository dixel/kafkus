(ns user
  (:require [mount.core :as mount]
            [cyrus-config.core :as conf]
            [kafkus.avro :as kavro]
            [kafkus.kafka :as kafka]
            [clojure.core.async :as a]
            [taoensso.timbre :as log]))

(log/merge-config! {:level :debug
                    :ns-whitelist ["kafkus.*"]})

(require '[kafkus.api :as api])

(conf/reload-with-override! (read-string (slurp ".config.edn")))

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

  (def plain-avro-schema-config
    {:bootstrap-servers "localhost:9092"
     :mode "avro-raw"
     :schema kavro/complex-schema
     :channel input
     :auto.offset.reset "earliest"
     :enable.auto.commit false
     :topic "kafkus-sample-raw-avro"
     :payload (kavro/sample-data kavro/complex-schema)
     :group.id (str "kafkus-consumer-" (str (java.util.UUID/randomUUID)))})

  (def schema-registry-config
    {:bootstrap-servers "localhost:9092"
     :mode "avro-schema-registry"
     :channel input
     :auto.offset.reset "earliest"
     :enable.auto.commit false
     :topic "kafkus-sample-schema-registry"
     :schema "{
        \"type\": \"record\",
        \"name\": \"value_schem\",
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
          \"name\": \"additionalField\",
          \"type\": \"string\",
          \"default\": \"\"
        }
        ]
     }"
     :schema-registry-url "http://localhost:8081"
     :payload {:id 777 :name "Amazing User" :additionalField "testcompat"}
     :group.id (str "kafkus-consumer-" (str (java.util.UUID/randomUUID)))})

  (def consumer
    (kafka/consume-from-topic debug-avro-schemas-config)))
