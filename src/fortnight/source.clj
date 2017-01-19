(ns fortnight.source
  (:require [fortnight.tcp :as tcp]
            [fortnight.users :as users]
            [clojure.tools.logging :as log]))

(defn follow
  [event]
  (let [followed      (:to event)
        follower      (:from event)]
    (users/enqueue-message followed event)))

(defn unfollow
  [event]
  (let [followed (:to event)
        follower (:from event)]
    (users/enqueue-message followed event)))

(defn broadcast
  [event]
  (users/enqueue-message (keys (users/get-clients)) event))

(defn private-message
  [event]
  (users/enqueue-message (:to event) event))

(defn status-update
  [event]
  ;; the user handler will spawn the other events when it's processed.
  (users/enqueue-message (:from event) event))

(defn process-event
  [event]
  ;;(log/infof "[process-event] %s" event)
  (condp = (:type event)
    "F" (follow event) ;; update the client atom for the `to` user with the `from` user id and send a message to `to` user
    "U" (unfollow event)
    "B" (broadcast event)
    "P" (private-message event)
    "S" (status-update event))
  (users/conj-seen (:seq-num event))
  #_(users/update-cursor))

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
