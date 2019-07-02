(ns kafkus.api
  (:require [kafkus.handlers :as handlers]
            [kafkus.sente :refer [sente]]
            [ring.util.response :as r]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer  [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.response :refer [resource-response content-type]]
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
  (log/debugf "request: %s"  [(:request-method request) (:uri request)])
  (case [(:request-method request) (:uri request)]
    [:get "/"] (some-> (resource-response "index.html" {:root "public"})
                       (content-type "text/html; charset=utf-8"))
    [:get "/ping"] (handlers/pong request)
    [:get "/chsk"] ((get sente :ring-ajax-get-or-ws-handshake) request)
    [:post "/chsk"] ((get sente :ring-ajax-post) request)
    {:status 400 :body (str "bad request: " (:uri request))}))

(mount/defstate api
  :start (do
           (log/info "starting the API component...")
           (http/start-server (-> app
                                  wrap-params
                                  wrap-keyword-params
                                  (wrap-defaults
                                   (assoc-in
                                    site-defaults
                                    [:security :anti-forgery] false))
                                  wrap-json-body
                                  wrap-json-response)
                              {:port http-port
                               :host http-host}))
  :stop (.close api))
