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
(def user-server (atom nil))
(def event-processor (atom nil))

(defn start-event-source-server
  [config]
  (let [port        (:event-source-server-port config)
        max-conn    (:max-event-source-clients config)
        socket      (tcp/server-socket port max-conn)
        listener-fn #(tcp/server-socket-listener socket source/handle-new-event-source)
        listener    (Thread. listener-fn)]
    (.start listener)
    (log/infof "[start-event-source-server] Event source server listening on port %d!" port)
    {:port     port
     :max-conn max-conn
     :socket   socket
     :listener listener}))

(defn start-event-processor
  [config]
  (let [thread (Thread. #(processor/event-processor config))]
    (.start thread)
    (log/infof "[start-event-processor] Event processor thread started!")
    thread))

(defn start-user-server
  [config]
  (let [port        (:user-server-port config)
        max-conn    (:max-user-clients config)
        socket      (tcp/server-socket port max-conn)
        listener-fn #(tcp/server-socket-listener socket users/handle-new-clients)
        listener    (Thread. listener-fn)]
    (.start listener)
    (log/infof "[start-user-server] User server listening on port %d!" port)
    {:port     port
     :max-conn max-conn
     :socket   socket
     :listener listener}))

;; REPL
(defn stop
  []
  (when-not (nil? (:socket @event-server))
    (tcp/safe-close (:socket @event-server)))
  (when-not (nil? (:socket @user-server))
    (tcp/safe-close (:socket @user-server))))

(defn start
  [config]
  (log/infof "[start] Starting service with config %s" config)
  (stop) ;; prevent the sockets from being blocked by a previous repl run
  (users/reset-state!)
  (buffer/reset-state!)
  (reset! event-server (start-event-source-server config))
  (reset! user-server (start-user-server config))
  (reset! event-processor (start-event-processor config)))

(defn get-env-var
  ([key default] (get-env-var key default int))
  ([key default coerce-fn]
   (if-let [val (System/getenv key)]
    (coerce-fn val)
    default)))

(defn -main
  [& args]
  ;; load config
  (let [config {:event-timeout-ms         (get-env-var "eventTimeoutMs" 1000)
                :max-event-source-clients (get-env-var "maxEventSourceClients" 1)
                :event-source-server-port (get-env-var "eventSourceServerPort" 9090)
                :max-user-clients         (get-env-var "maxUserClients" 1000)
                :user-server-port         (get-env-var "userServerPort" 9099)}]
    ;; start servers
    (start config)))
