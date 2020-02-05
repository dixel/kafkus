(ns kafkus.utils
  (:require [taoensso.timbre :as log]))

(defn ->json [data]
  (.stringify js/JSON (clj->js data) nil 2))

(defn json->clj [data]
  (js->clj (js/JSON.parse data) :keywordize-keys true))

(defn pretty-json [raw-json]
  (try
    (.stringify js/JSON (.parse js/JSON raw-json) nil 2)
    (catch :default e
      (str "failed to parse json: <<" raw-json ">>"))))

(defn set-dom-element [id value]
  (try
    (set! (.-value (.getElementById js/document id)) value)
    (catch :default e
      (log/error "failed setting the default value for " id ": " e))))
