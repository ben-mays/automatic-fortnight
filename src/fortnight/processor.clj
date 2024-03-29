(ns fortnight.processor
  (:require [fortnight.users :as users]
            [fortnight.buffer :as buffer]
            [clojure.tools.logging :as log]))

(def ^:private maze (atom {})) ;; a map from user-id to a vector of user-ids

(defn- get-followers
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

(defn follow
  [event]
  (let [followed      (:to event)
        followed-conn (users/user->conn followed)
        follower      (:from event)]
    (swap! maze follow-swap-fn follower followed)
    (users/send-message (:raw event) [followed-conn])))

(defn unfollow
  [event]
  (let [followed (:to event)
        follower (:from event)]
    (swap! maze unfollow-swap-fn follower followed)))

(defn broadcast
  [event]
  (let [conns (map :conn (vals (users/get-clients)))]
    (users/send-message (:raw event) conns)))

(defn private-message
  [event]
  (let [to      (:to event)
        to-conn (users/user->conn to)]
    (if (not (nil? to-conn))
      (users/send-message (:raw event) [to-conn]))))

(defn status-update
  [event]
  (let [from           (:from event)
        follower-conns (->> (get-followers from)
                            (map users/user->conn))]
    (users/send-message (:raw event) follower-conns)))

(defn process-event
  [event]
  (log/debugf "[process-event] %s" event)
  (if (= (mod (:seq-num event) 10000) 0) 
    (log/infof "[process-event] %s" event))
  (condp = (:type event)
    "F" (follow event) ;; update the client atom for the `to` user with the `from` user id and send a message to `to` user
    "U" (unfollow event) ;; update the client atom for the `user`, removing the `from` user id
    "B" (broadcast event) ;; send the message to all clients
    "P" (private-message event) ;; send a message to the `to` user
    "S" (status-update event)) ;; send a message to all the followers of the `from` user id
  (buffer/inc-cursor))

(defn is-next?
  [seq-num]
  (= seq-num (:seq-num (buffer/peek-event))))

(defn jump-cursor
  "If the timed-out-event is not the next-event in the event buffer, jumps the cursor to the next-event (inc)."
  [timed-out-event]
  (if-not (is-next? timed-out-event)
    (do
      (buffer/inc-cursor)
      (log/debugf "[jump-cursor] New cursor position %d!" (buffer/get-cursor)))
    (log/debugf "[jump-cursor] Cursor caught up to %d!" timed-out-event)))

(defn event-processor
  "Pulls events off the event buffer continuously, ensures that the events are in order. Will block on a missing event for `event-timeout-ms` milliseconds before invoking jump-cursor to make progress."
  [config]
  (while true
    (if (buffer/event-buffer-ready?)
      (let [next-event (buffer/get-cursor)
            timeout    (:event-timeout-ms config)]
        (if (is-next? next-event)
          (process-event (buffer/pop-event))
          (if (< 0 timeout)
            (do
              (log/debugf "[event-processor] Cursor encounted a hole at %d waiting %d ms. %s" next-event timeout)
              (Thread/sleep timeout)
              (jump-cursor next-event))))))))
