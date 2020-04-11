(ns kafkus.new-consumer
  (:require [kafkus.config :refer [state
                                   get-config
                                   default-limit
                                   count-rate
                                   topics
                                   play?
                                   schemas
                                   default-rate
                                   middle
                                   reverse-count-rate
                                   connected?]]
            [cljs.core.async :as a]
            [cljs.pprint :as pprint]
            [sci.core :as sci]
            [reagent-forms.core :refer [bind-fields]]
            [taoensso.timbre :as log]
            [reagent.core :as reagent]
            [kafkus.utils :as u]
            [clojure.string :as str]))

(defn badge [id]
  (let [current-value (or (get @state id)
                          (get-in @state (map keyword (str/split (name id) #"\."))))
        max-length 15]
    [:div.row.mx-auto
     [:span.badge.invisible "+"] ; placeholder for aligment
     [:span.badge.badge-info (apply str (take max-length current-value))
      (if (> (count current-value) max-length)
        ".."
        "")]]))

(defn dropdown-menu [id items]
  [:li.nav-item.dropdown.p-2
   [:a.nav-link.dropdown-toggle
    {:href "#"
     :id (str id "-menu")
     :role "button"
     :data-toggle "dropdown"
     :aria-haspopup "true"
     :aria-expanded "false"}
    (name id)]
   [:div.dropdown-menu
    {:aria-labelledby (str id "-menu")}
    (for [i items]
      ^{:key (.random js/Math)}
      [:a.dropdown-item
       {:on-click (fn [] (swap! state #(assoc % id i)))} i])]
   (badge id)])

(defn input-row [id type]
  [:input.dropdown-item.form-control
   {:type type
    :field type
    :id id
    :placeholder (name id)}])

(defn dropdown-text [id]
  [:li.nav-item.dropdown.p-2
   [:a.nav-link.dropdown-toggle
    {:href "#"
     :id (str id "-dropdown")
     :role "button"
     :data-toggle "dropdown"
     :aria-haspopup "true"
     :aria-expanded "false"}
    (name id)]
   [:div.dropdown-menu
    {:aria-labelledby (str id "-dropdown")}
    [bind-fields (input-row id :text) state]]
   (badge id)])

(defn credentials []
  [:li.nav-item.dropdown
   {:visible? (constantly false)}
   [:a.nav-link.dropdown-toggle
    {:href "#"
     :id "credentials"
     :role "button"
     :data-toggle "dropdown"
     :aria-haspopup "true"
     :aria-expanded "false"}
    "credentials"]
   [:div.dropdown-menu
    {:aria-labelledby "credentials"}
    [:form
     [bind-fields (input-row :username :text) state]
     [bind-fields (input-row :password :password) state]]]
   (badge :username)])

(def dialog
  )

(defn app []
  [:div
   [:ul.nav.navbar.navbar-expand-lg.navbar-light.bg-light
    [:div.row.justify-content-between
     [:div.navbar-brand.col-4 {:href "#"}
      [:img.img-fluid {:src "./pic/kafkus.png"
                       :width "100px"}]]
     [:button.navbar-toggler
      {:type "button"
       :data-toggle "collapse"
       :data-target "#navbarSupportedContent"
       :aria-controls "navbarSupportedContent"
       :aria-expanded "false"
       :aria-label "toggle naviga"}
      [:span.navbar-toggler-icon]]
     [:div.col-5
      {:on-click (fn []
                   (log/info "sending...")
                   ((:send! @state)
                    [:kafkus/list-topics (get-config)]))}
      [:div.collapse.navbar-collapse
       {:id "navbarSupportedContent"}
       [:ul.navbar-nav.mr-auto
        [:div.dropdown.border-right.pr-5.p-3.mr-5
         [:a.nav-link.dropdown-toggle
          {:href "#"
           :id "topics-dropdown"
           :role "button"
           :data-toggle "dropdown"
           :aria-haspopup "true"
           :aria-expanded "false"}
          (or (get @state :topic) "topic")]

         [:div.dropdown-menu
          {:aria-labelledby "topics-dropdown"}
          (for [i @topics]
            ^{:key (.random js/Math)}
            [:a.dropdown-item
             {:on-click (fn [] (swap! state #(assoc % :topic i)))} i])]]
        [:li.nav-item.p-3.mr-3.pr-5.border-right
         {:class (when-not @connected? "disabled")}
         [:a.nav-link [:i.fas.fa-play]]]
        [:span.navbar-text.p-3.mr-3.pr-5.border-right
         [:span.navbar-text
          (if @connected?
            [:i.fa.fa-link]
            [:i.fa.fa-unlink])
          (str " " (get-in @state [:bootstrap :servers]) " ")]]
        [:li.nav-item.p-3.mr-2.pr-4.border-right
         [:a.nav-link
          {:data-toggle "modal"
           :data-target "#kafka-settings"}
          [:i.fa.fa-cog]]]]]]]]
   [:div#kafka-settings.modal.fade
    {:tabindex -1
     :role "dialog"
     :aria-labelledby "kafka-settings-label"
     :aria-hidden "true"}
    [:div.modal-dialog.modal-lg {:role "document"}
     [:div.modal-content
      [:div.modal-header
       [:h5.modal-title "Settings"]
       [:button.close
        {:type "button"
         :data-dismiss "modal"
         :aria-label "Close"}
        [:span
         {:aria-hidden "true"}
         [:i.fa.fa-times]]]]
      [:div.modal-body
       [:div.container
        [:div.row.border-bottom
         [:p.p-3 "Kafka"]
         (dropdown-text :bootstrap.servers)
         (dropdown-menu :auto.offset.reset ["earliest" "latest"])]
        [:div.row.border-bottom
         [:p.p-3 "Ser/de"]
         (dropdown-menu :value.deserializer ["raw" "json" "avro-raw" "avro-schema-registry"])
         (dropdown-text :schema-registry-url)]
        [:div.row
         [:p.p-3 "Security"]
         (dropdown-menu :security.protocol ["PLAINTEXT" "SASL_PLAINTEXT" "SASL_SSL" "SSL"])
         [:div.container.row
          {:class (when-not (#{"SASL_PLAINTEXT" "SASL_SSL"}
                             (@state :security.protocol))
                    "d-none")}
          (dropdown-menu :sasl.mechanism ["PLAIN" "SSL"])
          (credentials)]]]]
      [:div.modal-footer.close {:data-dismiss "modal"}
       [:button.btn-primary.close "OK"]]]]]
   [:pre.pre-scrollable.bg-dark.text-white.p-3
    {:style {:max-height "75vh"
             :height "75vh"}}
    (for [i (:middle @state)]
      ^{:key (.random js/Math)}
      [:div [:font {:color "#5bc0de "}
             (apply str (repeat 5 "█"))
             (str  " partition " (:partition i) " █ offset " (:offset i) " █ key '"
                   (str/replace (:key i) #"\n|\r" "")
                   "' "
                   (apply str (repeat 20 "█")) "\n")]
       (:decoded i)])]])

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
