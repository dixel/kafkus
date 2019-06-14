(ns kafkus.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [kafkus.utils :as u]
            [mount.core :as mount]
            [reagent.core :as reagent :refer [atom]]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [cljs.core.async :as a]
            [taoensso.timbre :as log]))

(def state
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk"
                                    nil
                                    {:type :auto})]
    (atom {:receive ch-recv
           :send! send-fn
           :left-panel nil
           :topics []
           :middle-panel nil})))

(defn config-text-field
  "configuration text input"
  [name]
  [:div
   [:input
    {:id name
     :on-key-up #(swap!
                  state
                  (fn [state]
                    (let [component-value
                          (.-value (js/document.getElementById name))]
                      (-> state
                          (assoc-in
                           [:config (keyword name)]
                           component-value)))))
     :placeholder name
     :type "text"}]])

(defn selector-field [name items]
  [:select
   {:id name
    :on-change
    (fn []
      (let [val (.-value (.getElementById js/document name))]
        (swap! state #(assoc-in % [:config (keyword name)] val))))}
   (for [item items]
     ^{:key (.random js/Math)}
     [:option
      {:value item}
      item])])

(def playback-component
  (let [{:keys [send! receive]} @state]
    [:div
     [:button
      {:on-click
       (fn []
         (send! [:kafkus/stop-message {:cmd :stop}])
         (send! [:kafkus/start-message (assoc (:config @state)
                                              :cmd :start)])
         (a/go-loop []
           (let [{:keys [?data]} (a/<! receive)
                 [msg-id msg] ?data]
             (log/infof "got message: %s" msg)
             (cond
               (= msg-id :kafkus/payload)
               (swap! state
                      (fn [state]
                        (update state :middle
                                (fn [middle]
                                  (conj middle (u/->json msg))))))
               (= msg-id :kafkus/error)
               (swap! state
                      (fn [state]
                        (update state :middle
                                (fn [middle]
                                  (conj middle msg)))))))
           (recur)))}
      [:i {:class "fas fa-play"} ">"]]
     [:button
      {:on-click (fn []
                   (send! [:some/stop-message {:cmd :stop}]))}
      [:i {:class "fas fa-pause"} "||"]]
     [:button
      {:on-click (fn []
                   (swap! state #(assoc % :middle '()))
                   (send! [:some/stop-message {:cmd :stop}]))}
      [:i {:class "fas fa-stop"} "[]"]]]))

(defn app []
  [:div
   [:div {:id "wrap"}
    [:div {:id "left-panel"}
     [:p {:id "logo"} [:b {:id "logo1"} "O_"] "kafkus"]
     (config-text-field "bootstrap.servers")
     (config-text-field "group.id")
     (config-text-field "auto.offset.reset")
     (config-text-field "topic")
     playback-component
     [:pre "received total:" (count (:middle @state))]]
    [:div {:id "middle-panel"}
     (for [item (:middle-panel @state)]
       ^{:key (.random js/Math)}
       [:div
        [:pre item]
        [:hr]])]]])

(mount/defstate core
  :start (reagent/render [app] (js/document.getElementById "app"))
  :stop :pass)

(mount/start)
