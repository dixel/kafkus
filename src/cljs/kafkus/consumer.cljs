(ns kafkus.consumer
  (:require [kafkus.config :refer [state get-config default-limit count-rate topics play? schemas default-rate middle reverse-count-rate]]
            [cljs.core.async :as a]
            [cljs.pprint :as pprint]
            [sci.core :as sci]
            [reagent-forms.core :refer [bind-fields]]
            [taoensso.timbre :as log]
            [reagent.core :as reagent]
            [kafkus.utils :as u]))

(def transform-code
  (reagent/cursor state [:transformation]))

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

(defn transformation
  "transform output function"
  [id]
  [:textarea
   {:id id
    :field :textarea
    :placeholder "; single message\n; transformation\n; (clojure)\n; msg bound to `i`\n; EXAMPLES:\n(identity i)\n(get i :key)"}])

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
     [:div {:align "right"}
      [:pre {:align "center"}
       "consumer "
       [:a {:href "./producer"} "producer"]]]
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
     [:label.total "received total:" (:message-count @state)]
     [bind-fields
      (transformation :transformation)
      state]]
    [:div {:id "middle-panel"}
     [:button.clear
      {:on-click (fn [_]
                   (swap! state #(assoc % :middle '())))}
      "clear"]
     (for [item (:middle @state)]
       ^{:key (.random js/Math)}
       (if @transform-code
         [:div
          [:div.dark-grey [:pre " "]]
          [:pre (u/->json (try
                            (sci/eval-string @transform-code {:bindings {'i item}})
                            (catch :default e
                              e)))]]
         [:div
          [:div.dark-grey [:pre (str "partition " (:partition item) " | offset " (:offset item) " | key '" (:key item) "'")]]
          [:pre (:decoded item)]]))]]])
