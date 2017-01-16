(ns fortnight.buffer)

;; cursor is the next event id to process, the events should be an ordered collection
;;(def event-buffer (atom {:cursor 0 :events []}))
(def ^:private events (atom []))
(def ^:private cursor (atom 1))

(defn reset-state!
  "Helper function for managing local namespace state."
  []
  (reset! events [])
  (reset! cursor 1))

(defn get-cursor 
  []
  @cursor)

(defn inc-cursor 
  []
  (swap! cursor inc))

(defn push-event
  [event]
  ;; hack to sort the event buffer on each push
  (swap! events (fn [buffer event] (sort-by :seq-num (conj buffer event))) event))

(defn pop-event
  []
  (locking @events
    (let [event (first @events)]
      (swap! events rest)
      event)))

(defn peek-event
  []
  (first @events))

(defn event-buffer-ready?
  []
  (not (empty? @events)))

;; TODO: impl prio queue
