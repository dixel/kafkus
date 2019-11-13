(ns kafkus.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [kafkus.utils :as u]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent-forms.core :refer [bind-fields]]
            [mount.core :as mount]
            [reagent.core :as reagent :refer [atom]]
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

(def middle
  (reagent/cursor state [:middle]))

(def play?
  (reagent/cursor state [:play?]))

(def plaintext-jaas-template
  "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";")

(defn get-config []
  {:bootstrap-servers (get-in @state [:bootstrap :servers])
   :schema-registry-url (get @state :schema-registry-url)
   :auto.offset.reset (get @state :auto.offset.reset)
   :schema (get-in @state [:schemas (get @state :schema)])
   :mode (get @state :mode)
   :rate (count-rate (get @state :rate default-rate))
   :topic (get @state :topic)
   :security.protocol (get @state :security.protocol)
   :sasl.mechanism (get @state :sasl.mechanism)
   :sasl.jaas.config (when-let [jaas (get @state :sasl.jaas.config
                                          plaintext-jaas-template)]
                       (gstring/format jaas
                                       (get @state :username)
                                       (get @state :password)))})

(defn config-input
  "configuration text input"
  [field & {:keys [on-blur-fn hidden-fn password?]}]
  [:input.form-control
   {:id field
    :on-blur on-blur-fn
    :visible? (or hidden-fn (constantly true))
    :placeholder field
    :disabled @play?
    :field (if password? :password :text)}])

(defn playback [hidden-fn]
  (let [{:keys [send! receive]} @state]
    [:div
     [:button.playback
      {:hidden (hidden-fn)
       :on-click
       (fn []
         (reset! play? true)
         (swap! state #(assoc % :middle '()))
         (swap! state #(assoc % :message-count 0))
         (send! [:kafkus/stop :stop])
         (send! [:kafkus/start (get-config)]))}
      [:i {:class "fas fa-play"
           :style {"fontSize" "25px"}}]]
     [:button.playback
      {:hidden (not (hidden-fn))
       :on-click (fn []
                   (reset! play? false)
                   (send! [:kafkus/stop :stop])
                   (a/go
                     (a/<! (a/timeout 500))))}
      [:i {:class "fas fa-stop"
           :style {"fontSize" "25px"}}]]]))

(defn dyn-selector [field items & {:keys [hidden-fn disabled-fn on-click-fn]}]
  [:select
   {:style {:color (if (nil? (get @state field))
                     "grey"
                     "black")}
    :id field
    :on-change (fn [e]
                 (swap! state #(assoc % field
                                      (-> e .-target .-value))))
    :hidden (not (if hidden-fn
                   (hidden-fn)
                   true))
    :on-click (fn [e]
                (when on-click-fn
                  (on-click-fn)))
    :disabled (if disabled-fn
                (disabled-fn)
                @play?)}
   [:option.defaultOption {:selected "true"
                           :hidden "true"
                           :disabled "disabled"} field]
   (for [i items]
     ^{:key i}
     [:option i])])

(defn app []
  [:div
   [:div {:id "wrap"}
    [:div {:id "left-panel"}
     [:p {:id "logo"} [:b {:id "logo1"} "O_"] "kafkus"]
     (dyn-selector :security.protocol ["PLAINTEXT" "SASL_PLAINTEXT" "SASL_SSL" "SSL"])
     (dyn-selector :sasl.mechanism ["PLAIN" "SSL"]
                   :hidden-fn #(contains? #{"SASL_SSL" "SASL_PLAINTEXT"} (:security.protocol @state)))
     [bind-fields
      [:div
       (config-input :username
                     :hidden-fn #(contains? #{"SASL_SSL" "SASL_PLAINTEXT"} (:security.protocol @state)))]
      state]
     [bind-fields
      [:div
       (config-input :password
                     :password? true
                     :hidden-fn #(contains? #{"SASL_SSL" "SASL_PLAINTEXT"} (:security.protocol @state)))]
      state]
     [bind-fields
      [:div
       (config-input :bootstrap.servers
                     :on-blur-fn #((:send! @state)
                                   [:kafkus/list-topics (get-config)]))]
      state]
     (dyn-selector :mode ["raw" "json" "avro-raw" "avro-schema-registry"])
     (dyn-selector :auto.offset.reset ["earliest" "latest"])
     (dyn-selector :topic @topics :on-click-fn
                   #((:send! @state)
                     [:kafkus/list-topics (get-config)]))
     [bind-fields
      (config-input :schema-registry-url :hidden-fn #(= (:mode @state) "avro-schema-registry"))
      state]
     (dyn-selector :schema (sort (keys @schemas)) :hidden-fn #(= (:mode @state) "avro-raw"))
     [:div {:style {:padding "10px"}}]
     [:div {:align "left"} (playback #(identity @play?))]
     [:div {:style {:padding "10px"}}]
     [:div {:align "center"}
      [:label.to-range (str "rate: " (count-rate
                                      (or (:rate @state) default-rate)) " msg/s")]]
     [bind-fields
      [:input#range
       {:field :range
        :defaultValue default-rate
        :min 1
        :step 1
        :max 1000
        :id :rate}]
      state]
     [:div {:align "center"}
      [:label.to-range (str "output: " (:limit @state default-limit) " msg")]]
     [bind-fields
      [:input
       {:field :range
        :defaultValue default-limit
        :min 1
        :step 1
        :max 10000
        :id :limit}]
      state]
     [:div {:style {:padding "10px"}}]
     [:label.total "received total:" (:message-count @state)]]
    [:div {:id "middle-panel"}
     [:button.clear
      {:on-click (fn [_]
                   (swap! state #(assoc % :middle '())))}
      "clear"]
     (for [item (:middle @state)]
       ^{:key (.random js/Math)}
       [:div
        [:pre item]
        [:hr]])]]])

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
                             :mode mode)
                      (assoc-in [:bootstrap :servers]
                                (get defaults :bootstrap.servers))))
    (set! (.-value (.getElementById js/document "security.protocol"))
          (get defaults :security.protocol))
    (set! (.-value (.getElementById js/document "sasl.mechanism"))
          (get defaults :sasl.mechanism))
    (set! (.-value (.getElementById js/document "mode"))
          mode)
    (set! (.-value (.getElementById js/document "rate"))
          rate)
    (set! (.-value (.getElementById js/document "limit"))
          limit)
    (set! (.-value (.getElementById js/document "auto.offset.reset"))
          (get defaults :auto.offset.reset))))

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
        [:chsk/recv :kafkus/list-topics] (reset! topics (sort msg))
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
        (log/debug "[cljs] unknown event: " event)))
    (recur)))

(mount/defstate core
  :start (do
           (start-server)
           (reagent/render [app]
                           (js/document.getElementById "app")))
  :stop :pass)

(mount/start)
