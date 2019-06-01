(ns kafkus.api
  (:require [kafkus.handlers :as handlers]
            [ring.util.response :as r]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer  [wrap-params]]
            [aleph.http :as http]
            [mount.core :as mount]
            [cheshire.core :as json]
            [aleph.http :as http]
            [cyrus-config.core :as conf]
            [taoensso.timbre :as log]))

(conf/def http-port "http port of the app"
  {:spec integer?
   :default 4040})

(conf/def http-host "http host of the app"
  {:spec string?
   :default "127.0.0.1"})

(defn app  [request]
  (log/debugf "request: %s"  (:uri request))
  (case  (:uri request)
    "/ping" (handlers/pong request)
    {:status 400 :body (str "bad request: " (:uri request))}))

(mount/defstate api
  :start (do
           (log/info "starting the API component...")
           (http/start-server (-> app
                                  wrap-json-body
                                  wrap-json-response
                                  wrap-params)
                              {:port http-port
                               :host http-host}))
  :stop (.close api))
