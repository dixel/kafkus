(ns kafkus.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [kafkus.config :refer [connected?
                                   count-rate
                                   default-limit
                                   default-rate
                                   get-config
                                   middle
                                   payload
                                   play?
                                   reverse-count-rate
                                   schemas
                                   state
                                   topics]]
            [kafkus.utils :as u]
            [kafkus.consumer :as consumer]
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
                             :username (js/decodeURIComponent (cookies/get-raw "kafkus-username"))
                             :password (js/decodeURIComponent (cookies/get-raw "kafkus-password")))
                      (assoc-in [:bootstrap :servers]
                                (get defaults :bootstrap.servers))))
    (u/set-dom-element "security.protocol" (get defaults :security.protocol))
    (u/set-dom-element "sasl.mechanism" (get defaults :sasl.mechanism))
    (u/set-dom-element "mode" mode)
    (u/set-dom-element "rate" rate)
    (u/set-dom-element "limit" limit)
    (u/set-dom-element "auto.offset.reset" (get defaults :auto.offset.reset))))

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
        [:chsk/recv :kafkus/list-topics] (do (reset! connected? true) (reset! topics (sort msg)))
        [:chsk/recv :kafkus/list-schemas] (reset! schemas msg)
        [:chsk/recv :kafkus/error] (reset! middle [msg])
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
                                            "avro-schema-registry" (u/->json msg)
                                            "avro-raw" (u/->json msg)
                                            "raw" msg
                                            "json" (u/pretty-json msg))))))
        [:chsk/recv :kafkus/get-topic-sample-value] (->> msg
                                                         u/->json
                                                         u/pretty-json
                                                         (u/set-dom-element "payload")
                                                         (reset! payload))
        (log/debug "[cljs] unknown event: " event)))
    (recur)))

(mount/defstate core
  :start (do
           (start-server)
           (log/info js.window.location.pathname)
           (case js.window.location.pathname
             "/consumer" (reagent/render [consumer/app]
                                         (js/document.getElementById "app"))
             "/producer" (reagent/render [producer/app]
                                         (js/document.getElementById "app"))
             (reagent/render [consumer/app]
                             (js/document.getElementById "app"))))
  :stop :pass)

(mount/start)
