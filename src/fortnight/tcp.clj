(ns fortnight.tcp
  (:import [java.net ServerSocket SocketException])
  (:require [clojure.tools.logging :as log]))

(defn server-socket
  "Creates a `java.net.ServerSocket` listening on the given port."
  [port max-conn]
  (ServerSocket. port max-conn))

(defn open?
  "Returns true if the socket is open."
  [socket]
  (not (.isClosed socket)))

(defn closed?
  "Returns true if the socket is closed."
  [socket]
  (.isClosed socket))

(defn close 
  "Closes a socket. May throw an exception if the socket is already closed."
  [socket]
  (.close socket))

(defn safe-close 
  "Closes a socket iff the socket is open."
  [socket]
  (when (open? socket) 
    (close socket)))

(defn conn->reader
  [conn]
  (-> conn
      (.getInputStream)
      (clojure.java.io/reader)))

(defn conn->writer
  [conn]
  (-> conn
      (.getOutputStream)
      (clojure.java.io/writer)))

(defn server-socket-listener
  "Given a socket connection and a handler function, will block on `java.net.ServerSocket#accept` while waiting for new incomming connections. 
   On connect, the handler is invoked in a new thread with the new connection socket."
  [socket handler]
  (while (open? socket)
    (try
      (let [conn (.accept socket)]
        (log/info "server-socket-listener" "New connection!")
        (-> (fn [] (handler conn))
            (Thread.)
            (.start)))
      (catch SocketException e
        (log/error "server-socket-listener" (.getMessage e)))))) ;; throw away bad incoming socket connections or be resilient to max-conn issues
