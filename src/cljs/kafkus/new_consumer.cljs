(ns kafkus.new-consumer
  (:require [kafkus.config :refer [state
                                   get-config
                                   default-limit
                                   count-rate
                                   topics
                                   play?
                                   schemas
                                   status
                                   errors
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

(defn dropdown-menu [id items]
  [:div.row.p-2
   [:div.input-group
    [:div.input-group-prepend
     [:div.btn-group
      (doall
       (for [i items]
         ^{:key (.random js/Math)}
         [:button.btn.btn-info
          {:class (when (= (@state id) i) "active")
           :aria-pressed (when (= (@state id) i) "true")
           :on-click (fn []
                       (swap! status #(conj % (str id " changed to " i)))
                       (swap! state #(assoc % id i)))}
          i]))]]
    [:input.form-control
     {:readonly "true"
      :placeholder (@state id)}]
    [:div.input-group-append
     [:span.input-group-text id]]]])

(defn input-row [id type]
  (let [append-id (str (name id) "-append")]
    [:div.input-group
     [:input.form-control
      {:type type
       :field type
       :aria-describedby append-id
       :id id
       :placeholder (name id)}]
     [:div.input-group-append
      {:id append-id}
      [:span.input-group-text (name id)]]]))

(defn dropdown-text [id & {:keys [type]}]
  [:div.row.p-2
   [bind-fields (input-row id (or type :text)) state]])

(defn modal []
  [:div#kafka-settings.modal.fade
   {:tabIndex -1
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
        [:i.fa.fa-check]]]]
     [:div.modal-body
      [:div.container
       (dropdown-text :bootstrap.servers)
       (dropdown-text :schema-registry-url)
       (dropdown-menu :auto.offset.reset ["earliest" "latest"])
       (dropdown-menu :value.deserializer ["raw" "json" "avro-schema-registry"])
       (dropdown-menu :security.protocol ["PLAINTEXT" "SASL_PLAINTEXT" "SASL_SSL"])
       (when (#{"SASL_PLAINTEXT" "SASL_SSL"} (@state :security.protocol))
         (dropdown-menu :sasl.mechanism ["PLAIN" "SSL"]))
       (when (#{"SASL_PLAINTEXT" "SASL_SSL"} (@state :security.protocol))
         (dropdown-text :username))
       (when (#{"SASL_PLAINTEXT" "SASL_SSL"} (@state :security.protocol))
         (dropdown-text :password :type :password))]]]]])

(defn output []
  [:div.row
   [:pre.pre-scrollable.bg-dark.text-white.p-3
    {:style {:max-height "75vh"
             :width "100%"
             :height "75vh"}}
    [:font.text-danger
     (str/join "\n" (reverse (take 3 @errors)))]
    [:font.text-success
     (str/join "\n" (reverse (take 3 @status)))]
    "\n"
    (when (> (:message-count @state) 0)
      [:font.text-success (str (:message-count @state) " messages fetched")])
    "\n"
    (for [i (:middle @state)]
      ^{:key (.random js/Math)}
      [:div [:font {:color "#5bc0de "}
             (apply str (repeat 5 "-"))
             (str  " partition " (:partition i) " - offset " (:offset i) " - key '"
                   (str/replace (:key i) #"\n|\r" "")
                   "' "
                   (apply str (repeat 20 "-")) "\n")]
       (:decoded i)])]])

(defn menu []
  [:div.row.justify-content-between
   [:div.col-2.p-0.m-0.align-self-center
    {:href "#"}
    [:img.img-fluid.p-2 {:src "./pic/kafkus.png"}]]
   [:div.col-10.btn-group.d-flex.m-0.p-0
    [:button.btn.dropdown-toggle.bg-light.btn-lg.text-truncate
     {:href "#"
      :id "topics-dropdown"
      :role "button"
      :data-toggle "dropdown"
      :aria-haspopup "true"
      :aria-expanded "false"}
     "topic"]
    [:div.dropdown-menu
     {:aria-labelledby "topics-dropdown"}
     (for [i @topics]
       ^{:key (.random js/Math)}
       [:a.dropdown-item
        {:on-click (fn []
                     (swap! status #(conj % (str "topic " i " selected")))
                     (swap! state #(assoc % :topic i)))} i])]
    [:button.btn.bg-light.btn-lg.border-left
     {:class (when-not @connected? "disabled")
      :on-click (fn []
                  (let [{:keys [send!]} @state]
                    (if @play?
                      (do
                        (swap! status #(conj % (str "stopped consuming from " (@state :topic))))
                        (reset! play? false)
                        (send! [:kafkus/stop :stop])
                        (a/go
                          (a/<! (a/timeout 500))))
                      (do
                        (swap! status #(conj % (str "consuming from " (@state :topic))))
                        (reset! play? true)
                        (swap! state #(assoc % :middle '()))
                        (swap! state #(assoc % :message-count 0))
                        (send! [:kafkus/stop :stop])
                        (send! [:kafkus/start (get-config)])))))}
     (if @play?
       [:i.fas.fa-stop]
       [:i.fas.fa-play])]
    [:button.btn.bg-light.btn-lg.border-left.text-truncate
     {:class (if @connected?
               "text-success"
               "text-danger")
      :on-click (fn []
                  ((:send! @state)
                   [:kafkus/list-topics (get-config)]))}
     (if @connected?
       [:i.fa.fa-link]
       [:i.fa.fa-unlink])
     (str " " (get-in @state [:bootstrap :servers]) " ")]
    [:button.btn.bg-light.btn-lg.border-left
     {:data-toggle "modal"
      :data-target "#kafka-settings"}
     [:i.fa.fa-cog]]]])

(defn app []
  [:div.container-fluid
   (menu)
   (modal)
   (output)])
