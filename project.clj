(defproject kafkus "0.1.1"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [org.clojure/core.async "0.4.490"]
                 [reagent "0.8.1"]
                 [cyrus/config "0.2.1"]
                 [mount "0.1.12"]
                 [com.taoensso/sente "1.15.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.8"]
                 [cheshire "5.8.1"]
                 [aleph "0.4.6"]
                 [com.taoensso/sente "1.14.0-RC2"]
                 [dvlopt/kafka "1.3.0-beta0"]
                 [ovotech/kafka-avro-confluent "2.1.0-1"]
                 [deercreeklabs/lancaster "0.6.6"]
                 [org.apache.avro/trevni-avro "1.8.2"
                  :classifier "tests"
                  :exclusions [org.eclipse.jetty/jetty-util]]
                 [org.apache.avro/trevni-core "1.8.2"
                  :classifier "tests"
                  :exclusions [org.eclipse.jetty/jetty-util]]
                 [reagent-forms "0.5.43"]
                 [reagent-utils "0.3.3"]
                 [ring "1.6.3"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.4.0"]
                 [ring-basic-authentication "1.0.5"]]
  :main kafkus.core
  :source-paths ["src/clj", "src/cljs"]
  :uberjar-name "kafkus.jar"
  :cljsbuild {:builds [{:id "min"
                        :source-paths ["src/cljs"]
                        :compiler
                        {:output-to "resources/public/cljs-out/kafkus.js"
                         :optimizations :advanced}}]}
  :profiles {:dev
             {:source-paths ["dev"]
              :repl-options {:init-ns user
                             :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
              :dependencies [[com.bhauman/figwheel-main "0.2.0"]
                             [com.bhauman/rebel-readline-cljs "0.1.4"]
                             [cider/piggieback "0.4.1"]]
              :resource-paths ["target" "resources"]
              :clean-targets ^{:protect false} ["target"]
              :aliases {"fig" ["trampoline" "run" "-m" "figwheel.main"]
                        "build-dev" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]}
              :plugins [[lein-ancient "0.6.15"]
                        [lein-cljsbuild "1.1.7"]
                        [lein-kibit "0.1.5"]
                        [jonase/eastwood "0.2.5"]]}
             :uberjar {:aot :all
                       :prep-tasks ["compile"
                                    ["cljsbuild" "once" "min"]]}})
