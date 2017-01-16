(ns fortnight.users
  (:require [fortnight.tcp :as tcp]
            [clojure.tools.logging :as log])
  (:import [java.net SocketException]))

(def ^:private clients (atom {}))

(defn reset-state!
  "Helper function for managing local namespace state."
  []
  (reset! clients {}))

(defn get-clients []
  @clients)

(defn user->conn
  [user]
  (get-in @clients [user :conn]))

(defn- write
  [msg conn]
  (try
    (let [writer (tcp/conn->writer conn)]
      (.write writer (str msg "\r\n"))
      (.flush writer))
    (log/info "-write" msg)
    (catch SocketException e
      (log/error "-write" (.getMessage e)))))

(defn send-message
  "Synchronously sends the message given to the connections given."
  [msg conns]
  (let [valid-conns (->> conns
                         (filter some?) ;; filter nil connections
                         (filter #(tcp/open? %)))] ;; filter out closed sockets
    (dorun (map (fn [conn] (write msg conn)) valid-conns))))

;; TODO: impl event processing per user?
(defn handle-new-clients
  "Reads the user id from the connection and stores the socket in the clients atom."
  [conn]
  (with-open [reader (tcp/conn->reader conn)
              writer (tcp/conn->writer conn)]
    (let [inp (.readLine reader)]
      (swap! clients assoc inp {:conn conn :reader reader :writer writer})
      (while true
        (Thread/sleep 1000)))))

