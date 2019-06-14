(ns kafkus.utils)

(defn ->json [data]
  (.stringify js/JSON (clj->js data) nil 2))
