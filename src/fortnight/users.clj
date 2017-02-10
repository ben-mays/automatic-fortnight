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
    (log/debugf "[write] %s" msg)
    (catch SocketException e
      (log/error "[write]" e))))

(defn send-message
  "Synchronously sends the message given to the connections given."
  [msg conns]
  (let [valid-conns (->> conns
                         (filter some?) ;; filter nil connections
                         (filter #(tcp/open? %)))] ;; filter out closed sockets
    (dorun (pmap (fn [conn] (write msg conn)) valid-conns))))

(defn handle-new-clients
  "Reads the user id from the connection and stores the socket in the clients atom."
  [conn]
  (let [reader (tcp/conn->reader conn)
        inp    (.readLine reader)]
    (swap! clients assoc inp {:conn conn :reader reader})))

