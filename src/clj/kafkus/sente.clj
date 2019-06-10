(ns kafkus.sente
  (:require [taoensso.sente :as s]
            [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]
            [taoensso.timbre :as log]
            [mount.core :as mount]))

(mount/defstate sente
  :start (let [{:keys [ch-recv send-fn connected-uids
                       ajax-post-fn ajax-get-or-ws-handshake-fn]}
               (s/make-channel-socket! (get-sch-adapter)
                                       {:user-id-fn #(:client-id %)})]
           (log/info "starting sente channels...")
           {:ring-ajax-post ajax-post-fn
            :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
            :ch-chsk ch-recv
            :chsk-send! send-fn
            :connected-uids connected-uids})
  :stop :pass ; sente doc suggests that it's stateless, so...
  )
