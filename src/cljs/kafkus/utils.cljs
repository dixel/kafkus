(ns kafkus.utils)

(defn ->json [data]
  (.stringify js/JSON (clj->js data) nil 2))

(defn pretty-json [raw-json]
  (try
    (.stringify js/JSON (.parse js/JSON raw-json) nil 2)
    (catch :default e
      (str "failed to parse json: <<" raw-json ">>"))))
