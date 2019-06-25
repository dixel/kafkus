(ns kafkus.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [kafkus.utils :as u]
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
           :middle nil})))

(defn count-rate [rate]
  (cond
    (<= rate 550) (max 1 (quot rate 10))
    :else (- rate 500)))

(def topics
  (reagent/cursor state [:topics]))

(def schemas
  (reagent/cursor state [:schemas]))

(def middle
  (reagent/cursor state [:middle]))

(def play?
  (reagent/cursor state [:play?]))

(defn get-config []
  {:bootstrap-servers (get-in @state [:bootstrap :servers])
   :schema-registry-url (get @state :schema-registry-url)
   :auto.offset.reset (get @state :auto.offset.reset)
   :schema (get-in @state [:schemas (get @state :schema)])
   :mode (get @state :mode)
   :rate (count-rate (get @state :rate default-rate))
   :topic (get @state :topic)})

(defn config-input
  "configuration text input"
  [field & {:keys [on-blur-fn hidden-fn]}]
  [:input.form-control
   {:id field
    :on-blur on-blur-fn
    :visible? (or hidden-fn (constantly true))
    :placeholder field
    :disabled @play?
    :field :text}])

(def playback
  (let [{:keys [send! receive]} @state]
    [:div
     [:button
      {:on-click
       (fn []
         (reset! play? true)
         (swap! state #(assoc % :middle '()))
         (send! [:kafkus/stop :stop])
         (send! [:kafkus/start (get-config)]))}
      [:i {:class "fas fa-play"
           :style {"fontSize" "35px"}}]]
     [:button
      {:on-click (fn []
                   (send! [:kafkus/stop :stop]))}
      [:i {:class "fas fa-pause"
           :style {"fontSize" "35px"}}]]
     [:button
      {:on-click (fn []
                   (reset! play? false)
                   (send! [:kafkus/stop :stop])
                   (a/go
                     (a/<! (a/timeout 500))
                     (swap! state #(assoc % :middle '()))))}
      [:i {:class "fas fa-stop"
           :style {"fontSize" "35px"}}]]]))

(defn dyn-selector [field items & {:keys [hidden-fn disabled-fn]}]
  [:select
   {:style {:color (if (nil? (get @state field))
                     "grey"
                     "black")}
    :on-change (fn [e]
                 (swap! state #(assoc % field
                                      (-> e .-target .-value))))
    :hidden (not (if hidden-fn
                   (hidden-fn)
                   true))
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
     [bind-fields
      [:div
       (config-input :bootstrap.servers
                     :on-blur-fn #((:send! @state)
                                   [:kafkus/list-topics (get-config)]))]
      state]
     (dyn-selector :mode ["raw" "avro-raw" "avro-schema-registry"])
     (dyn-selector :auto.offset.reset ["earliest" "latest"])
     (dyn-selector :topic @topics)
     [bind-fields
      (config-input :schema-registry-url :hidden-fn #(= (:mode @state) "avro-schema-registry"))
      state]
     (dyn-selector :schema (keys @schemas) :hidden-fn #(= (:mode @state) "avro-raw"))
     [bind-fields
      [:input#range
       {:field :range
        :defaultValue default-rate
        :min 1
        :step 1
        :max 1000
        :id :rate}]
      state]
     [:label (str "rate: " (count-rate
                            (or (:rate @state) default-rate)) " msg/s")]
     [bind-fields
      [:input
       {:field :range
        :defaultValue default-limit
        :min 1
        :step 1
        :max 10000
        :id :limit}]
      state]
     [:label (str "limit: " (:limit @state default-limit))]
     playback
     [:pre "received total:" (count (:middle @state))]]
    [:div {:id "middle-panel"}
     (for [item (:middle @state)]
       ^{:key (.random js/Math)}
       [:div
        [:pre item]
        [:hr]])]]])

(defn start-server []
  (a/go-loop []
    (let [{:keys [event]} (a/<! (:receive @state))
          [msg-type message] event
          [msg-tag msg] message]
      (log/debug "[cljs] got message: " [msg-tag msg])
      (when (and (= msg-type :chsk/state)
                 (:first-open? msg))
        ((:send! @state) [:kafkus/list-schemas (get-config)]))
      (case [msg-type msg-tag]
        [:chsk/recv :kafkus/list-topics] (reset! topics (sort msg))
        [:chsk/recv :kafkus/list-schemas] (reset! schemas (sort msg))
        [:chsk/recv :kafkus/error] (reset! middle [msg])
        [:chsk/recv :kafkus/message] (swap!
                                      middle
                                      (fn [m]
                                        (take
                                         (get @state :limit default-limit)
                                         (conj m
                                               (if (= (:mode @state) "raw")
                                                 msg
                                                 (u/->json msg))))))
        (log/debug "[cljs] unknown event: " event)))
    (recur)))

(mount/defstate core
  :start (do
           (start-server)
           (reagent/render [app]
                           (js/document.getElementById "app")))
  :stop :pass)

(mount/start)
