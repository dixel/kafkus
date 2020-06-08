(ns kafkus.api
  (:require [kafkus.handlers :as handlers]
            [kafkus.sente :refer [sente]]
            [ring.util.response :as r]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer  [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.util.response :as response :refer [resource-response content-type header]]
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

(conf/def cookie-expiration-seconds "number of seconds to get the cookies expired"
  {:spec integer?
   :default 5259492
   })

(defn sample-resp-wrapper [resp]
  (log/info (keys resp))
  resp)

(defn wrap-selective-basic-auth [handler]
  (fn [request]
    (if (= (:uri request) "/auth")
      ((wrap-basic-authentication handler #(identity {:username %1
                                                      :password %2})) request)
      (handler request))))

(defn app  [request]
  (log/debugf "request: %s"  [(:request-method request)
                              (:uri request)])
  (case [(:request-method request) (:uri request)]
    [:get "/"] (some-> (resource-response "newconsumer.html" {:root "public"})
                       (content-type "text/html; charset=utf-8"))
    [:get "/new-consumer"] (some-> (resource-response "newconsumer.html" {:root "public"})
                                   (content-type "text/html; charset=utf-8"))
    [:get "/auth"] (some-> (resource-response "newconsumer.html" {:root "public"})
                           (response/set-cookie "kafkus-username"
                                                (get-in request
                                                        [:basic-authentication
                                                         :username])
                                                {:same-site :strict
                                                 :max-age cookie-expiration-seconds})
                           (response/set-cookie "kafkus-password"
                                                (get-in request
                                                        [:basic-authentication
                                                         :password])
                                                {:same-site :strict
                                                 :max-age cookie-expiration-seconds})
                           (content-type "text/html; charset=utf-8"))
    [:get "/ping"] (handlers/pong request)
    [:get "/chsk"] ((get sente :ring-ajax-get-or-ws-handshake) request)
    [:post "/chsk"] ((get sente :ring-ajax-post) request)
    {:status 400 :body (str "bad request: " (:uri request))}))

(mount/defstate api
  :start (do
           (log/info "starting the API component...")
           (http/start-server (-> app
                                  wrap-selective-basic-auth
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
