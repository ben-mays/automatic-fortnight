(ns fortnight.source
  (:require [fortnight.tcp :as tcp]
            [fortnight.users :as users]
            [clojure.tools.logging :as log]))

(def ^:private maze (atom {})) ;; a map from user-id to a vector of user-ids
(def seen (atom [1]))
(def cursor (atom 1)) ;; front of seen

(defn swap-seen-fn
  "Sorts the seen list and removes the first N sequential items."
  [seen]
  (cond
    (empty? seen)       []
    (= 1 (count seen))  []
    (<= 2 (count seen)) (loop [coll (sort seen)]
                          (if (= (inc (first coll)) (second coll))
                            (recur (rest coll))
                            ;; if seen[0] != seen[1] then return seen[1:] -- we've already determined seen[-1] is sequential to seen[0]
                            coll)))

(defn swap-cursor-fn
  [cursor seen]
  (if (empty? seen)
    cursor
    (first seen)))

(defn update-cursor
  []
  (swap! seen swap-seen-fn)
  (swap! cursor swap-cursor-fn @seen))

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
        follower      (:from event)]
    (swap! maze follow-swap-fn follower followed)
    (users/enqueue-message followed event)))

(defn unfollow
  [event]
  (let [followed (:to event)
        follower (:from event)]
    (swap! maze unfollow-swap-fn follower followed)
    (users/enqueue-message followed event)))

(defn broadcast
  [event]
  (users/enqueue-message (keys (users/get-clients)) event))

(defn private-message
  [event]
  (users/enqueue-message (:to event) event))

(defn status-update
  [event]
  (let [followers (get-followers (:from event))]
    (for [f followers]
      (users/enqueue-message f event))))

(defn process-event
  [event]
  (log/infof "[process-event] %s" event)
  (condp = (:type event)
    "F" (follow event) ;; update the client atom for the `to` user with the `from` user id and send a message to `to` user
    "U" (unfollow event)
    "B" (broadcast event)
    "P" (private-message event)
    "S" (status-update event))
  (swap! seen conj (:seq-num event))
  (update-cursor))

(defn parse-event
  "Parses a raw event into a map."
  [line]
  (let [arr (clojure.string/split line #"\|")]
    {:seq-num (Long/parseLong (first arr))
     :type    (second arr)
     :from    (nth arr 2 nil)
     :to      (nth arr 3 nil)
     :raw     line}))

(defn handle-new-event-source
  "Continually reads from the given connection and processes events."
  [conn]
  (with-open [reader (tcp/conn->reader conn)]
    (while true
      (if-let [inp (.readLine reader)]
        (let  [event   (parse-event inp)]
          (process-event event))))))
