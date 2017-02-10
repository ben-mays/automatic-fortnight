(defproject fortnight "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.16"]]

  :source-paths ["src"]

  :profiles {:uberjar {:aot :all}}

  :main fortnight.main)
