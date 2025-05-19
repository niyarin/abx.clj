(defproject abx "0.1.0-SNAPSHOT"
  :description "ABX (Android Binary XML) reader/writer library."
  :url "https://github.com/niyarin/abx.clj"
  :license {:name "MIT Licsense" }
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/data.xml "0.0.8"]]
  :main ^:skip-aot abx.cli
  :repl-options {:init-ns abx.core})
