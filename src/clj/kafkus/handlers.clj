(ns kafkus.handlers
  (:require [ring.util.response :as r]
            [aleph.http :as http]
            [mount.core :as mount]
            [cheshire.core :as json]
            [aleph.http :as http]
            [cyrus-config.core :as conf]
            [taoensso.timbre :as log]
            [clojure.walk :refer [keywordize-keys]]))




(defn pong
  [request]
  {:status 200
   :body {:result :pong}
   :headers {"Content-Type" "application/json"}})
