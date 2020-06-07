(ns kafkus.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [kafkus.config :refer [connected?
                                   count-rate
                                   default-limit
                                   default-rate
                                   get-config
                                   middle
                                   errors
                                   payload
                                   play?
                                   reverse-count-rate
                                   schemas
                                   state
                                   status
                                   topics
                                   topic-schema
                                   schema-status
                                   send-status
                                   topic-key]]
            [kafkus.utils :as u]
            [kafkus.consumer :as consumer]
            [kafkus.new-consumer :as new-consumer]
            [kafkus.producer :as producer]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent-forms.core :refer [bind-fields]]
            [mount.core :as mount]
            [reagent.core :as reagent :refer [atom]]
            [reagent.cookies :as cookies]
            [taoensso.sente :as sente :refer (cb-success?)]
            [cljs.core.async :as a]
            [taoensso.timbre :as log]))

(defn set-defaults [defaults]
  (let [{:keys [mode rate limit]} defaults]
    (swap! state #(-> %
                      (assoc :rate (reverse-count-rate rate)
                             :auto.offset.reset (get defaults :auto.offset.reset)
                             :schema-registry-url (get defaults :schema-registry-url)
                             :security.protocol (get defaults :security.protocol)
                             :sasl.jaas.config (get defaults :sasl.jaas.config)
                             :sasl.mechanism (get defaults :sasl.mechanism)
                             :limit limit
                             :mode mode
                             :value.deserializer mode
                             :username (js/decodeURIComponent (cookies/get-raw "kafkus-username"))
                             :password (js/decodeURIComponent (cookies/get-raw "kafkus-password"))
                             :producer-enabled (get defaults :producer-enabled))
                      (assoc-in [:bootstrap :servers]
                                (get defaults :bootstrap.servers))))
    (u/set-dom-element "security.protocol" (get defaults :security.protocol))
    (u/set-dom-element "sasl.mechanism" (get defaults :sasl.mechanism))
    (u/set-dom-element "mode" mode)
    (u/set-dom-element "rate" rate)
    (u/set-dom-element "limit" limit)
    (u/set-dom-element "auto.offset.reset" (get defaults :auto.offset.reset)))
  (swap! status #(conj % (str "loaded default configuration for [" (get defaults :bootstrap.servers) "]")))
  ((:send! @state)
   [:kafkus/list-topics (get-config)]))

(defn start-server []
  (a/go-loop []
    (let [{:keys [event]} (a/<! (:receive @state))
          [msg-type message] event
          [msg-tag msg] message]
      (log/debug "[cljs] got message: " [msg-tag msg])
      (when (and (= msg-type :chsk/state)
                 (:first-open? msg))
        ((:send! @state) [:kafkus/list-schemas (get-config)])
        ((:send! @state) [:kafkus/get-defaults {}]))
      (case [msg-type msg-tag]
        [:chsk/recv :kafkus/list-topics] (do
                                           (swap! status #(conj % (str "connected to " (get-in @state [:bootstrap :servers]))))
                                           (reset! errors nil)
                                           (reset! connected? true)
                                           (reset! topics (sort msg)))
        [:chsk/recv :kafkus/list-schemas] (reset! schemas msg)
        [:chsk/recv :kafkus/error] (do (reset! connected? false)
                                       (swap! errors #(conj % msg)))
        [:chsk/recv :kafkus/defaults] (set-defaults msg)
        [:chsk/recv :kafkus/message] (swap!
                                      middle
                                      (fn [m]
                                        (swap! state #(update % :message-count inc))
                                        (take
                                         (get @state :limit default-limit)
                                         (conj
                                          m
                                          (case (:mode @state)
                                            "avro-schema-registry" (assoc msg :decoded (u/->json (:value msg)))
                                            "avro-raw" (assoc msg :decoded (u/->json (:value msg)))
                                            "raw" (assoc msg :decoded (:value msg))
                                            "json" (assoc msg :decoded (u/pretty-json (:value msg))))))))
        [:chsk/recv :kafkus/get-topic-sample-value] (->> msg
                                                         u/->json
                                                         u/pretty-json
                                                         (reset! payload)
                                                         (u/set-dom-element "payload"))
        [:chsk/recv :kafkus/get-schema] (do
                                          (reset! schema-status {:status :ok})
                                          (->> msg
                                                 u/->json
                                                 u/pretty-json
                                                 (reset! topic-schema)))
        [:chsk/recv :kafkus/get-schema-error] (reset! schema-status {:status :error :message msg})
        [:chsk/recv :kafkus/send-success] (do (reset! send-status (merge {:status :ok} msg))
                                              (reset! topic-key (str "kafkus-" (random-uuid))))
        [:chsk/recv :kafkus/send-error] (reset! send-status (merge {:status :error} msg))
        (log/debug "[cljs] unknown event: " event)))
    (recur)))

(defn try-register-settings
  ([] (try-register-settings 1000))
  ([timeout]
   (js/setTimeout (fn []
                    (log/info "registering modal close events...")
                    (try
                      (.call
                       (goog.object/get (js/jQuery "#kafka-settings") "on")
                       (js/jQuery "#kafka-settings")
                       "hide.bs.modal"
                       (fn []
                         ((:send! @state)
                          [:kafkus/list-topics (get-config)])))
                      (catch :default e
                        (log/error "failed to register modal close events: " e)
                        (try-register-settings (* 2 timeout))))) timeout)))

(mount/defstate core
  :start (do
           (start-server)
           (log/info js.window.location.pathname)
           (case js.window.location.pathname
             "/consumer" (reagent/render [consumer/app]
                                         (js/document.getElementById "app"))
             "/new-consumer" (do (reagent/render [new-consumer/app]
                                                 (js/document.getElementById "app"))
                                 (try-register-settings))
             "/producer" (reagent/render [producer/app]
                                         (js/document.getElementById "app"))
             (do (reagent/render [new-consumer/app]
                                 (js/document.getElementById "app"))
                 (try-register-settings))))
  :stop :pass)

(mount/start)
