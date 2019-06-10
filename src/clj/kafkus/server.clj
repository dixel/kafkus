(ns kafkus.server
  (:require [kafkus.api :refer [api]]
            [kafkus.sente :refer [sente]]
            [kafkus.kafka :as kafka]
            [mount.core :as mount]
            [taoensso.timbre :as log]
            [cyrus-config.core :as conf]
            [clojure.core.async :as a]))

(def connections (atom {}))

(defn start-kafkus-consumer [uid config]
  (when-let [maybe-connection (get @connection uid)]
    (kafka/stop! maybe-connection))
  (try
    (swap! connections
           (fn [conns]
             (assoc conns
                    uid (kafka/consume-from-topic config))))
    [:kafkus/start (format "starting consuming with config: %s" config)]
    (catch Exception e
      [:kafkus/error (format "error initializing the consumer with config %s: %s"
                             config
                             (.getMessage e))])))

(defn list-kafkus-topics [config]
  [:kafkus/list-topics (kafka/list-topics config)])

(defn list-kafkus-schemas [config]
  [:kafkus/list-schemas :not-implemented])

(defn start-kafkus-server []
  (a/go-loop []
    (let [{:keys ch-chsk chsk-send!} sente
          {:keys [event uid] :as full-message} (a/<! ch-chsk)
          [msg-id msg] event
          connection ]
      (log/debugf "got message in sente channel: %s" full-message)
      (case msg-id
        :kafkus/start (chsk-send! uid (start-kafkus-consumer uid msg))
        :kafkus/list-topics (chsk-send! uid (list-kafkus-topics msg))
        :kafkus/list-schemas (chsk-send! uid (list-kafkus-schemas msg))))))

(mount/defstate server
  :start (start-kafkus-server)
  :stop (a/close! server))
