(ns fortnight.users
  (:require [fortnight.tcp :as tcp]
            [fortnight.buffer :as buffer]
            [clojure.tools.logging :as log]
            )
  (:import [java.net SocketException]))

(def ^:private clients (atom {}))
(def ^:private messages (atom {}))

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
  [msgs conn]
  (try
    (let [writer (tcp/conn->writer conn)
          data   (->> msgs
                      (map #(str % "\r\n"))
                      (clojure.string/join))]
      (.write writer data)
      (.flush writer)
      (log/infof "[write] %s %s" data msgs))
    (catch SocketException e
      (log/error "[write]" e))))

(defn write-message
  "Synchronously sends the message given to the connections given."
  [msgs conns]
  (let [valid-conns (->> conns
                         (filter some?) ;; filter nil connections
                         (filter #(tcp/open? %)))] ;; filter out closed sockets
    (dorun (map (fn [conn] (write msgs conn)) valid-conns))))

(defn add-client
  [id]
  (swap! assoc id {:queue (buffer/fib-heap) :last-sent 0}))

(defn enqueue-message
  [user-id msg]

  (when (nil? (get @messages user-id))
    (add-client user-id))

  (let [queue (get-in @messages [user-id :queue])]
    (log/infof "[enqueue-message] Enqueing %s" msg)
    (.enqueue queue msg (:seq-num msg))))

(defn get-cursor
  []
  (inc (apply max (map :last-sent (vals @messages)))))

(defn set-last-sent
  [user-id seq-num]
  (if-let [user (get @messages user-id)]
    (let [swap-fn (fn [messages] (update-in messages [user-id :last-sent] seq-num))]
      (swap! messages swap-fn))))

(defn- dequeue-message
  [queue max-cursor]
  (if-let [head (.min queue)]
    (let [next-msg (.getValue head)]
      (log/infof "[dequeue-message] %s %s" next-msg max-cursor)
      (if (<= (:seq-num next-msg) max-cursor)
        (.getValue (.dequeueMin queue))))))

(defn handle-new-clients
  "Reads the user id from the connection and stores the socket in the clients atom."
  [conn]
  (let [reader (tcp/conn->reader conn)
        id     (.readLine reader)
        queue  (buffer/fib-heap)]
    (swap! clients assoc id {:conn          conn})
    (add-client id)
    
    (while true
      ;; attempts to process all sequential events, starting from cursor
      ;; will refresh the cursor when next-event returns nil
      (loop [cursor     (get-cursor)
             next-event (dequeue-message queue cursor)]
          (log/infof "[handle-new-clients] %s %s " cursor next-event)
          (if-not (nil? next-event)
            (do
              (when (not-any? #{"U"} (:type next-event))
                (write-message [(:raw next-event)] [conn]))
              (set-last-sent id (:seq-num next-event))
              (recur (inc cursor) (dequeue-message id (inc cursor)))))))
    (Thread/sleep 100)))
