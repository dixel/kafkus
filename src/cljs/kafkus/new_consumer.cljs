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
                                   connected?
                                   producer-enabled
                                   topic-schema
                                   schema-status
                                   payload
                                   send-status
                                   topic-key]]
            [kafkus.info :refer [info]]
            [cljs.core.async :as a]
            [cljs.pprint :as pprint]
            [sci.core :as sci]
            [reagent-forms.core :refer [bind-fields]]
            [taoensso.timbre :as log]
            [reagent.core :as reagent]
            [kafkus.utils :as u]
            [clojure.string :as str]))

(def smt
  (reagent/cursor state [:smt]))

(defn dropdown-menu [id items]
  [:div.row.p-2
   [:div.btn-group.d-flex.w-100
    [:span.text-left.text-truncate.border.w-25.rounded-left.p-2.bg-light id]
    (doall
     (for [i items]
       ^{:key (.random js/Math)}
       [:button.btn.btn-info.text-truncate
        {:class (when (= (@state id) i) "active")
         :aria-pressed (when (= (@state id) i) "true")
         :on-click (fn []
                     (swap! status #(conj % (str id " changed to " i)))
                     (swap! state #(assoc % id i)))}
        i]))]])

(defn input-row [id type]
  (let [append-id (str (name id) "-append")]
    [:div.row.p-2
     [:div.text-left.text-truncate.border.rounded-left.w-25.p-2.bg-light (name id)]
     [:div.border-right.border-top.border-bottom.rounded-right.w-75
      [:input.form-control.border-0.w-100
       {:type type
        :field type
        :aria-describedby append-id
        :id id
        :placeholder (name id)}]]]))

(defn dropdown-text [id & {:keys [type]}]
  [bind-fields (input-row id (or type :text)) state])

(defn kafka-settings []
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
         [:div
          (dropdown-menu :sasl.mechanism ["PLAIN" "SSL"])
          (dropdown-text :username)
          (dropdown-text :password :type :password)])]]]]])

(defn get-producer-payload-textarea []
  [:textarea.form-control.h-100 {:id :payload :field :textarea :defaultValue @payload}])

(defn get-producer-topic-key []
  [:input.form-control
       {:type :text
        :field :text
        :id :topic-key}])

(defn producer []
  [:div#producer.modal.fade
   {:tabIndex -1
    :role "dialog"
    :aria-labelledby "producer-label"
    :aria-hidden "true"}
   [:div.modal-dialog.modal-lg {:role "document"}
    [:div.modal-content
     [:div.modal-header
      [:h5.modal-title "Produce to " (@state :topic)]
      [:button.close
       {:type "button"
        :data-dismiss "modal"
        :aria-label "Close"}
       [:span
        {:aria-hidden "true"}
        [:i.fa.fa-times]]]]
     [:div.modal-body
      (if (= :ok (:status @schema-status))
        [:div.container
         [:div.row
          [:label.col-4 "Topic key (string)"]
          [:div.col-8
           [bind-fields (get-producer-topic-key) state]]]
         [:div.row.m-2
          [:div.col-6
           [bind-fields (get-producer-payload-textarea) state]]
          [:div.col-6
           [:pre (str "// schema from schema-registry\n" @topic-schema)]]]
         [:div.row.m-3
          [:button.btn.btn-success.btn-block
           {:on-click (fn []
                        (log/info "publishing to kafka...")
                        ((:send! @state) [:kafkus/send (get-config)]))}
           (str "Publish to " (@state :topic))]]
         (case (:status @send-status)
           :ok [:div.alert-success (str "published offset "
                                        (get-in @send-status [:metadata :dvlopt.kafka/offset])
                                        " partition "
                                        (get-in @send-status [:metadata :dvlopt.kafka/partition])
                                        " timestamp "
                                        (get-in @send-status [:metadata :dvlopt.kafka/timestamp]))]
           :error [:div.alert-danger (str "error publishing: " (:exception @send-status))]
           [:div])]
        [:div.container
         [:div.alert-danger.m-2 (str "couldn't fetch schema for topic " (@state :topic))]
         [:pre (:message @schema-status)]])]]]])

(defn modal-info []
  [:div#kafkus-info.modal.fade
   {:tabIndex -1
    :role "dialog"
    :aria-labelledby "kafka-settings-label"
    :aria-hidden "true"}
   [:div.modal-dialog.modal-lg {:role "document"}
    [:div.modal-content
     [:div.modal-header
      [:h5.modal-title "Kafkus"]
      [:button.close
       {:type "button"
        :data-dismiss "modal"
        :aria-label "Close"}
       [:span
        {:aria-hidden "true"}
        [:i.fa.fa-times]]]]
     [:div.modal-body
      info]]]])

(defn output []
  [:div.row
   {:style {:min-height "70vh" :max-height "70vh" :height "70vh"}}
   [:pre.pre-scrollable.bg-dark.text-white.p-3
    {:style {:min-height "100%" :max-height "100%" :width "100%" :height "100%"}}
    [:font.text-success
     (str/join "\n" (reverse (take 3 @status)))]
    "\n"
    (when (> (:message-count @state) 0)
      [:font.text-success (str (:message-count @state) " messages fetched")])
    "\n"
    [:font.text-danger
     (str/join "\n" (reverse (take 3 @errors)))]
    "\n"
    (for [i (:middle @state)]
      ^{:key (.random js/Math)}
      (let [{:keys [partition offset key value decoded]} i]
        [:div [:font {:color "#5bc0de "}
               (apply str (repeat 5 "-"))
               (str  " partition " partition " - offset " offset " - key '"
                     (str/replace (or key "") #"\n|\r" "") ; avoid corrupting output if non-string keys contain newline symbols
                     "' "
                     (apply str (repeat 20 "-")) "\n")]
         (if (empty? @smt)
           (:decoded i)
           (try
             (when-let [result (sci/eval-string @smt {:bindings {'i i
                                                                 'partition partition
                                                                 'offset offset
                                                                 'key key
                                                                 'value value
                                                                 'decoded decoded}})]
               (u/->json result))
             (catch :default e
               [:div
                [:font.text-danger (str "failed executing: " e "\noriginal message:\n")]
                (u/->json i)])))]))]])


(defn menu []
  [:div.row.justify-content-between
   {:style {:min-height "5vh" :max-height "5vh" :height "5vh"}}
   [:div.col-2.p-0.m-0.align-self-center
    {:href "#"}
    [:img.img-fluid {:src "./pic/kafkus.png"
                     :style {:max-height "5vh"}}]]
   [:div.col-10.btn-group.d-flex.m-0.p-0
    {:style {:min-height "5vh" :max-height "5vh" :height "5vh"}}
    [:button.btn.dropdown-toggle.btn-light.text-truncate.rounded-0
     {:href "#"
      :id "topics-dropdown"
      :role "button"
      :data-toggle (when-not @play? "dropdown")
      :disabled @play?
      :aria-haspopup "true"
      :aria-expanded "false"}
     "topic"]
    [:div.dropdown-menu.rounded-0
     {:aria-labelledby "topics-dropdown"}
     (for [i @topics]
       ^{:key (.random js/Math)}
       [:a.dropdown-item
        {:on-click (fn []
                     (swap! status #(conj % (str "topic " i " selected")))
                     (swap! state #(assoc % :topic i))
                     (reset! topic-key (str "kafkus-" (random-uuid)))
                     ((:send! @state)
                      [:kafkus/get-topic-sample-value (assoc (get-config) :topic i)])
                     ((:send! @state)
                      [:kafkus/get-schema (assoc (get-config) :topic i)])
                     )} i])]
    [:button.btn.rounded-0
     {:title "consume from topic"
      :class (if (and @connected?
                      (@state :topic))
               (if @play?
                 "btn-warning"
                 "btn-success")
               "btn-light")
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
    (when @producer-enabled
      [:button.btn.rounded-0
       (let [enabled (and @connected?
                          (@state :topic)
                          (not @play?))]
         {:title "produce to topic"
          :class (if enabled
                   "btn-success"
                   ["btn-light" "disabled"])
          :data-toggle (if enabled "modal" "")
          :data-target "#producer"})
       [:i.fa.fa-circle]])
    [:button.btn.bg-light.text-truncate
     {:class (if @connected?
               "text-success"
               "text-danger")
      :disabled @play?
      :on-click (fn []
                  (when-not @play?
                    ((:send! @state)
                     [:kafkus/list-topics (get-config)])))}
     (if @connected?
       [:i.fa.fa-link]
       [:i.fa.fa-unlink])
     (str " " (get-in @state [:bootstrap :servers]) " ")]
    [:button.btn.bg-light
     {:class (when @play? "disabled")
      :data-toggle (when-not @play? "modal")
      :data-target "#kafka-settings"}
     [:i.fa.fa-cog]]]])

(defn range-input [id min max step]
  [:input.form-control.custom-range.border-0
   {:field :range
    :type :range
    :step step
    :min min
    :max max
    :id id}])

(defn get-placeholder []
  "Single message transformation (Clojure).
EXAMPLES - (->> value :customers first (map :name))
         - (str \"[\" key \"]: [\" value)
BINDINGS
i      - whole message
value  - only deserialized value
key    - message key (as string)
")

(defn bottom []
  [:div.row
   {:style {:min-height "20vh"
            :height "20vh"}}
   [:div.col-9.text-right.p-1.w-100.bg-light.btn-group
    [:button.btn.btn-primary.h-100
     {:data-toggle "modal"
      :data-target "#kafkus-info"}
     [:i.fa.fa-info]]
    [bind-fields (#(identity [:textarea.form-control.custom-control.h-100.p-2.rounded-0.border-left-0
                              {:field :textarea
                               :id :smt
                               :style {:font-family "monospace"
                                       :font-size "8pt"
                                       :resize "none"}
                               :placeholder (get-placeholder)}]))
     state]]

   [:div.bg-light.col-3.p-1.pr-2
    [:div.text-right.m-0
     [:label.pr-3.text-faded
      {:style {:font-size "8pt"}} (str "msg/s: " (@state :rate))]
     [bind-fields (range-input :rate 1 501 50) state]
     [:label.pt-3.pr-3.text-faded
      {:style {:font-size "8pt"}} (str "limit: " (@state :limit))]
     [bind-fields (range-input :limit 1 10001 500) state]]]])

(defn app []
  [:div.container-fluid
   {:min-height "100%"
    :height "100%"}
   (menu)
   (kafka-settings)
   (producer)
   (output)
   (bottom)
   (modal-info)])
