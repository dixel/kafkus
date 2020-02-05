(ns kafkus.producer
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [kafkus.utils :as u]
            [kafkus.config :refer [state get-config default-limit count-rate topics play? schemas default-rate middle reverse-count-rate connected?]]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent-forms.core :refer [bind-fields]]
            [mount.core :as mount]
            [reagent.core :as reagent :refer [atom]]
            [taoensso.sente :as sente :refer (cb-success?)]
            [cljs.core.async :as a]
            [taoensso.timbre :as log]))

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

(defn config-checkbox
  "configuration checkbox"
  [field & {:keys [on-blur-fn hidden-fn]}]
  [:div.row [:label.column "SASL/SSL"]
   [:input.column
    {:id field
     :name field
     :on-blur on-blur-fn
     :visible? (or hidden-fn (constantly true))
     :placeholder field
     :disabled @play?
     :type :checkbox}]])

(defn playback [hidden-fn]
  (let [{:keys [send! receive]} @state]
    [:div
     [:button.btn.btn-dark.container-fluid
      {:hidden (hidden-fn)
       :on-click
       (fn []
         (reset! play? true)
         (swap! state #(assoc % :middle '()))
         (swap! state #(assoc % :message-count 0))
         (send! [:kafkus/send (get-config)]))}
      [:i {:class "fas fa-play"
           :style {"fontSize" "25px"}}]]]))

(defn dyn-selector [field items & {:keys [hidden-fn disabled-fn on-click-fn]}]
  (let [label-id (str (name field) "-label")]
    [:div.dropdown.show
     {:hidden (when hidden-fn (not (hidden-fn)))}
     [:button.btn.dropdown-toggle
      {:type "button"
       :id field
       :on-click on-click-fn
       :data-toggle "dropdown"
       :aria-haspopup "true"
       :aria-expanded "false"}
      (or (get @state field) field)]
     [:div.dropdown-menu {:aria-labelledby field}
      [:a.dropdown-item.disabled.text-muted [:i field]]
      (for [i items]
        ^{:key i}
        [:a.dropdown-item
         {:href "#"
          :on-click (fn []
                      ((:send! @state)
                       [:kafkus/get-topic-sample-value (assoc (get-config) :topic i)])
                      (swap! state #(assoc % field i)))} i])]]))

(defn get-payload []
  [:textarea.form-control {:id :payload :field :textarea :rows 20}])

(defn app []
  [:div.container
   [:div.row {:id "wrap"
              :style {:height "100vh"}}
    [:div.col-3.bg-secondary {:id "left-panel"}
     [:p {:id "logo"} [:b {:id "logo1"} "O_"] "kafkus"]
     [:nav.vertical-navbar.navbar-expand-lg
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
      [:div.input-group
       [bind-fields
        (config-input :bootstrap.servers
                      :on-blur-fn #((:send! @state)
                                    [:kafkus/list-topics (get-config)]))
        state]
       [:div.input-group-append
        [:div.primary
         {:class (conj [:input-group-text]
                       (if @connected? :text-success :text-danger))}
         "●"]]]
      (dyn-selector :mode ["avro-schema-registry"])
      [bind-fields
       (config-input :schema-registry-url :hidden-fn #(= (:mode @state) "avro-schema-registry"))
       state]
      (dyn-selector :topic @topics :on-click-fn
                    #((:send! @state)
                      [:kafkus/list-topics (get-config)]))
      (dyn-selector :schema (sort (keys @schemas)) :hidden-fn #(= (:mode @state) "avro-raw"))
      (playback #(not connected?))
      [:div {:style {:padding "10px"}}]]]
    [:div.col-9.bg-light.mh-100 {:id "middle-panel"
                                 :style {:overflow-y "scroll"}}
     [:div
      [:button.rounded-lg.bg-transparent.border-dark.float-right
       {:on-click (fn [_]
                    (swap! state #(assoc % :middle '())))}
       "clear"]]
     [:br]
     [:br]
     [bind-fields (get-payload) state]]]])
