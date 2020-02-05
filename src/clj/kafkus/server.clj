(ns kafkus.server
  (:require [kafkus.api :refer [api]]
            [kafkus.sente :refer [sente]]
            [kafkus.kafka :as kafka]
            [mount.core :as mount]
            [taoensso.timbre :as log]
            [cyrus-config.core :as conf]
            [clojure.core.async :as a]
            [kafkus.avro :as avros]))

(conf/def load-default-config
  "Enable/disable loading of the default config"
  {:spec boolean?
   :default false})

(def connections (atom {}))

(defn get-new-group-id []
  (str "kafkus-consumer-" (str (java.util.UUID/randomUUID))))

(defn stop-kafkus-consumer [uid]
  (log/info "try stopping connection for uid " uid)
  (when-let [maybe-connection (get @connections uid)]
    (swap! connections #(dissoc % uid))
    (log/info "stopping connection for uid " uid)
    (kafka/stop! maybe-connection)))

(defn start-kafkus-consumer [uid config]
  (stop-kafkus-consumer uid)
  (log/info "starting consumer: " uid)
  (log/debug "consumer config: " config)
  (try
    (swap! connections
           (fn [conns]
             (assoc conns
                    uid (kafka/consume!
                         (assoc config
                                :group.id (get-new-group-id)
                                :callback #((:chsk-send! sente) uid
                                            [:kafkus/message %]))))))
    [:kafkus/start (format "starting consuming with config: %s" config)]
    (catch Exception e
      (log/error "failed starting the consumer: " (.getMessage e))
      [:kafkus/error (format "error initializing the consumer with config %s: %s"
                             config
                             (.getMessage e))])))

(defn list-kafkus-topics [config]
  (try
    [:kafkus/list-topics (kafka/list-topics config)]
    (catch Exception e
      (log/error "can't list topics: " (.getMessage e))
      [:kafkus/error (format "can't list topics: %s" (.getMessage e))])))

(defn produce-message [config]
  (kafka/produce! (assoc config :schema (kafka/get-schema-for-topic config))))

(defn list-kafkus-schemas [config]
  [:kafkus/list-schemas (avros/list-schemas)])

(defn get-defaults []
  (if load-default-config
    [:kafkus/defaults
     {:bootstrap.servers kafka/default-bootstrap-server
      :schema-registry-url kafka/default-schema-registry-url
      :rate kafka/default-rate
      :mode kafka/default-mode
      :limit kafka/default-limit
      :sasl.jaas.config kafka/default-sasl-jaas-config
      :sasl.mechanism kafka/default-sasl-mechanism
      :security.protocol kafka/default-security-protocol
      :auto.offset.reset kafka/default-auto-offset-reset}]
    [:kafkus/no-defaults nil]))

(defn start-kafkus-server []
  (a/go-loop []
    (let [{:keys [ch-chsk chsk-send!]} sente
          {:keys [event uid] :as full-message} (a/<! ch-chsk)
          [msg-id msg] event]
      (log/debugf "got message in sente channel: %s" [msg-id msg])
      (case msg-id
        :kafkus/start (chsk-send! uid (start-kafkus-consumer uid msg))
        :kafkus/stop (stop-kafkus-consumer uid)
        :kafkus/list-topics (a/go (chsk-send! uid (list-kafkus-topics msg)))
        :kafkus/list-schemas (chsk-send! uid (list-kafkus-schemas msg))
        :kafkus/get-defaults (chsk-send! uid (get-defaults))
        :kafkus/get-topic-sample-value (chsk-send! uid [:kafkus/get-topic-sample-value
                                                        (try (kafka/get-default-payload-for-topic msg)
                                                             (catch Exception e
                                                               (log/error "failed to generate default payload: " e)))])
        :kafkus/send (produce-message msg)
        :chsk/uidport-close (stop-kafkus-consumer uid)
        (log/debug "unknown message with id " msg-id))
      (recur))))

(mount/defstate server
  :start (start-kafkus-server)
  :stop (a/close! server))
