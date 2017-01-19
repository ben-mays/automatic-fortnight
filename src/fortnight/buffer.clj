(ns fortnight.buffer
  (:require [clojure.tools.logging :as log])
  (:import [FibonacciHeap]))

(def event-comparator
  (comparator (fn [a b]
                (cond
                  (nil? a)                1
                  (nil? b)                -1
                  :else                   (< (:seq-num a) (:seq-num b))))))

(defn min-heap
  [init-cap comparator]
  (java.util.PriorityQueue. init-cap comparator))

(defn fib-heap []
  (FibonacciHeap.))

(def events (fib-heap))
(def cursor (atom 1))

(defn reset-state!
  "Helper function for managing local namespace state."
  []
  (reset! cursor 1))

(defn get-cursor
  []
  @cursor)

(defn inc-cursor
  []
  (swap! cursor inc))

(defn peek-event
  []
  (locking events
    (.getValue (.min events))))

(defn push-event
  [event]
  (locking events
    (log/infof "[push-event] %s" event)
    (if-not (nil? event)
      (.enqueue events event (:seq-num event)))
    (log/infof "[push-event] [new event %s] %s %d" event (peek-event) (.size events))
    ))

(defn pop-event
  []
  (locking events
    (let [event (.getValue (.dequeueMin events))]
      (log/info "[pop-event]" event)
      event)))

(defn event-buffer-ready?
  []
  (not (= (.size events) 0)))
