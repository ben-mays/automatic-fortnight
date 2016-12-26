(defproject fortnight "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [org.clojure/test.check "0.9.0"]] ;; for generative testing :D

  :source-paths ["src"]

  :profiles {:uberjar {:aot :all}}

  :main fortnight.main)
