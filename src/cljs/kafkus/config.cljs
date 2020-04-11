(ns kafkus.config
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [kafkus.utils :as u]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent-forms.core :refer [bind-fields]]
            [mount.core :as mount]
            [reagent.core :as reagent :refer [atom]]
            [reagent.cookies :as cookies]
            [taoensso.sente :as sente :refer (cb-success?)]
            [cljs.core.async :as a]
            [taoensso.timbre :as log]))

(def default-rate 500)

(def default-limit 100)

(def state
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk"
                                    nil
                                    {:type :auto})]
    (atom {:receive ch-recv
           :play? false
           :send! send-fn
           :left-panel nil
           :topics []
           :schemas []
           :message-count 0
           :middle nil})))

(defn count-rate [rate]
  (cond
    (<= rate 550) (max 1 (quot rate 10))
    :else (- rate 500)))

(defn reverse-count-rate [rate]
  (cond
    (<= rate 550) (max 1 (* rate 10))
    :else (+ rate 500)))

(def topics
  (reagent/cursor state [:topics]))

(def schemas
  (reagent/cursor state [:schemas]))

(def payload
  (reagent/cursor state [:payload]))

(def middle
  (reagent/cursor state [:middle]))

(def errors
  (reagent/cursor state [:errors]))

(def play?
  (reagent/cursor state [:play?]))

(def connected?
  (reagent/cursor state [:connected]))

(def plaintext-jaas-template
  "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";")

(defn get-config []
  {:bootstrap-servers (get-in @state [:bootstrap :servers])
   :schema-registry-url (get @state :schema-registry-url)
   :auto.offset.reset (get @state :auto.offset.reset)
   :schema (get-in @state [:schemas (get @state :schema)])
   :mode (or (get @state :value.deserializer) (get @state :mode))
   :payload (u/json->clj @payload)
   :rate (count-rate (get @state :rate default-rate))
   :topic (get @state :topic)
   :security.protocol (get @state :security.protocol)
   :sasl.mechanism (get @state :sasl.mechanism)
   :sasl.jaas.config (when-let [jaas (get @state :sasl.jaas.config
                                          plaintext-jaas-template)]
                       (gstring/format jaas
                                       (get @state :username)
                                       (get @state :password)))})
