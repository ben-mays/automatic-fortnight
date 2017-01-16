(ns fortnight.main
  (:gen-class)
  (:require [fortnight.tcp :as tcp]
            [fortnight.source :as source]
            [fortnight.processor :as processor]
            [fortnight.buffer :as buffer]
            [fortnight.users :as users]
            [clojure.tools.logging :as log]))

;; State
(def event-server (atom nil))
(def client-server (atom nil))
(def event-processor (atom nil))

(defn start-event-source-server
  [config]
  (let [port     (get config :port 9090)
        max-conn (get config :max-event-source 1)
        socket   (tcp/server-socket port max-conn)
        listener-fn #(tcp/server-socket-listener socket source/handle-new-event-source)
        listener (Thread. listener-fn)]
    (.start listener)
    {:port     port
     :max-conn max-conn
     :socket   socket
     :listener listener}))

(defn start-event-processor
  [config]
  (let [thread (Thread. processor/event-processor)]
    (.start thread)
    thread))

(defn start-client-server
  [config]
  (let [port     (get config :port 9099)
        max-conn (get config :max-user-connections 1000)
        socket   (tcp/server-socket port max-conn)
        listener-fn #(tcp/server-socket-listener socket users/handle-new-clients)
        listener (Thread. listener-fn)]
    (.start listener)
    {:port     port
     :max-conn max-conn
     :socket   socket
     :listener listener}))

;; REPL
(defn stop
  []
  (when-not (nil? (:socket @event-server))
    (tcp/safe-close (:socket @event-server)))
  (when-not (nil? (:socket @client-server))
    (tcp/safe-close (:socket @client-server))))

(defn start
  []
  (stop) ;; prevent the sockets from being blocked by a previous repl run
  (users/reset-state!)
  (buffer/reset-state!)
  (reset! event-server (start-event-source-server {}))
  (reset! client-server (start-client-server {}))
  (reset! event-processor (start-event-processor {})))

(defn -main
  [& args]
  ;; load config
  ;; init logging
  ;; start servers
  (start))
