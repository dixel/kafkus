(defproject kafkus "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.4.490"]
                 [cyrus/config "0.2.1"]
                 [mount "0.1.12"]
                 [com.taoensso/sente "1.14.0-RC2"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.8"]
                 [cheshire "5.8.1"]
                 [aleph "0.4.6"]
                 [com.taoensso/sente "1.14.0-RC2"]
                 [dvlopt/kafka "1.3.0-beta0"]
                 [ovotech/kafka-avro-confluent "2.1.0-1"]
                 [deercreeklabs/lancaster "0.6.6"]
                 [ring "1.6.3"]
                 [ring/ring-json "0.4.0"]]
  :main kafkus.core
  :uberjar-name "kafkus.jar"
  :profiles {:dev
             {:source-paths ["dev"]
              :repl-options {:init-ns user}
              :plugins
              [[lein-ancient "0.6.15"]
               [lein-kibit "0.1.5"]
               [jonase/eastwood "0.2.5"]]}})
