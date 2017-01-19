(ns fortnight.users
  (:require [fortnight.tcp :as tcp]
            [fortnight.buffer :as buffer]
            [clojure.tools.logging :as log])
  (:import [java.net SocketException]))

(def ^:private clients (atom {}))
(def ^:private messages (atom {}))
(def seen (atom [0]))
(def cursor (atom 1)) ;; front of seen
(def ^:private maze (atom {})) ;; a map from user-id to a vector of user-ids

(defn get-followers
  [user]
  (get @maze user))

(defn- follow-swap-fn
  [maze follower followed]
  (let [existing-followers (get-followers followed)
        new-followers      (set (conj existing-followers follower))]
    (assoc maze followed new-followers)))

(defn- unfollow-swap-fn
  [maze follower followed]
  (let [existing-followers (get-followers followed)
        new-followers      (disj existing-followers follower)]
    (assoc maze followed new-followers)))

(defn swap-seen-fn
  "Sorts the seen list and removes the first N sequential items."
  [seen]
  (cond
    (empty? seen)       []
    (= 1 (count seen))  seen
    (<= 2 (count seen)) (loop [coll (sort seen)]
                          (if (= (inc (first coll)) (second coll))
                            (recur (rest coll))
                            coll))))

(defn swap-cursor-fn
  [cursor seen]
  (if (empty? seen)
    cursor
    (first seen)))

(defn update-cursor
  []
  ;;(log/info "UPDATE")
  (swap! seen swap-seen-fn)
  (swap! cursor swap-cursor-fn @seen))

(defn conj-seen
  [i]
  (swap! seen conj i))

(defn reset-state!
  "Helper function for managing local namespace state."
  []
  (reset! clients {}))

(defn get-clients
  []
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

(defn enqueue-message
  [user-id msg]
  (log/info "HERE" msg user-id (get @messages user-id))
  (when (nil? (get @messages user-id))
    (log/info "ADDING USER" user-id msg)
    (swap! messages assoc user-id {:queue (buffer/fib-heap)}))

  (let [queue (get-in @messages [user-id :queue])]
    ;;(log/infof "[enqueue-message] Enqueing %s %s %s" msg queue (.size queue))
    (.enqueue queue msg (:seq-num msg))))

(defn get-cursor
  []
  ;;(inc (apply max (map :last-sent (vals @messages))))
  @cursor)

(defn- dequeue-message
  [queue max-cursor]
  (if-let [head (.min queue)]
    (let [next-msg (.getValue head)]
      (log/infof "[dequeue-message] %s %s" next-msg max-cursor)
      (if (<= (:seq-num next-msg) max-cursor)
        (.getValue (.dequeueMin queue))))))

(defn follow
  [event]
  (let [followed      (:to event)
        follower      (:from event)]
    (swap! maze follow-swap-fn follower followed)))

(defn unfollow
  [event]
  (let [followed (:to event)
        follower (:from event)]
    (swap! maze unfollow-swap-fn follower followed)))

(defn generate-follower-messages
  [event]
  (log/info "GENERAATING FOLLOWERS" event (get-followers (:from event)) @maze)
  (let [followers (get-followers (:from event))]
    (for [f followers]
      (enqueue-message f event))))

(defn apply-side-effects
  [event]
  (condp = (:type event)
    "F" (follow event)
    "U" (unfollow event)
    nil))

(defn process-event
  [event conn]
  (log/info "PROCESSING" event)
  (apply-side-effects event)
  (when-not (contains? #{"U"} (:type event))
    (log/info "WRITING" event)
    (write-message [(:raw event)] [conn])))

(defn handle-new-clients
  "Reads the user id from the connection and stores the socket in the clients atom."
  [conn]
  (let [reader (tcp/conn->reader conn)
        id     (.readLine reader)]

    (swap! clients assoc id {:conn conn})
    (log/info @messages)
    (when (nil? (get @messages id))
      (swap! messages assoc id {:queue (buffer/fib-heap)}))

    (while true
      ;; attempts to process all sequential events, starting from cursor
      ;; will refresh the cursor when next-event returns nil
      (let [queue      (get-in @messages [id :queue])
            next-event (dequeue-message queue @cursor)]
        (log/infof "[handle-new-clients] c=%s n=%s h=%s" @cursor next-event (.min queue) (.size queue))
        (when-not (nil? next-event)
          ;; special handling for the S event
          (if (and (= (:type next-event) "S") (= (:from next-event) id))
            ;; the follower maze will be in the proper state to generate new events for the followers. 
            (generate-follower-messages next-event)
            ;; else proceed as normal
            (process-event next-event conn)))
        (update-cursor)
        (Thread/sleep 1000)))))
