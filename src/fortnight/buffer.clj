(ns fortnight.buffer
  (:require [clojure.tools.logging :as log]))

(def event-comparator
  (comparator (fn [a b]
                (cond
                  (and (nil? a) (nil? b)) 0
                  (nil? a)                -1
                  (nil? b)                1
                  :else                   (< (:seq-num a) (:seq-num b))))))

(defn min-heap
  [init-cap comparator]
  (java.util.PriorityQueue. init-cap comparator))

(def events (atom (min-heap 11 event-comparator)))
(def cursor (atom 1))

(defn reset-state!
  "Helper function for managing local namespace state."
  []
  (reset! events (min-heap 11 event-comparator))
  (reset! cursor 1))

(defn get-cursor
  []
  @cursor)

(defn inc-cursor
  []
  (swap! cursor inc))

(defn push-event
  [event]
  (log/infof "[push-event] %s" event)
  (if-not (nil? event)
    (.add @events event)))

(defn pop-event
  []
  (.poll @events))

(defn peek-event
  []
  (.peek @events))

(defn event-buffer-ready?
  []
  (not (= (.size @events) 0)))
