(ns user
  (:require [mount.core :as mount]
            [cyrus-config.core :as conf]
            [taoensso.timbre :as log]))

(log/merge-config! {:level :debug
                    :ns-whitelist ["kafkus.*"]})

(require '[kafkus.api :as api])

(conf/reload-with-override! (read-string (slurp ".config.edn")))

(defn start []
  (mount/start))

(defn stop []
  (mount/stop))
