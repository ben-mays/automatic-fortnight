(ns fortnight.buffer 
  "The buffer is an abstraction around a sequential log that applies an ordering to new elements. 
  Elements can be pushed out of order and readers should update the cursor position on successful processing events.")

;; I originally started with a sorted list as a basis for the log to get off the ground. 
;; I was suprised to see it performed well on the default test conf, given we were performing `n * n log n` operations on insertion. 
;; I followed up with a min binary heap, expecting to see huge performance improvement, since we're moving to a log n insertion. Suprisingly, using the min binary heap wasn't much of an improvment on the default configuration. 
;; I chalked this up to dequeueing also being `log n` and investigated other data structures, eventually trying out a fibonacci heap; which provides constant time insert, but still takes `log n` to dequeue.

(declare log)
(declare bin-heap-log)
(declare naive-log)

(defn reset-state!
  "Helper function for managing local namespace state."
  []
  (reset! log (bin-heap-log)))

(defprotocol Log 
  (cursor [this])
  (inc-cursor [this])
  (push [this event])
  (peek [this])
  (pop  [this])
  (ready? [this]))

(deftype NaiveLog [events cursor]
  Log
  (cursor [this]
    @cursor)
  (inc-cursor [this]
    (swap! cursor inc))
  (push [this event]
    ;; hack to sort the event buffer on each push. 
    (swap! events (fn [buffer event] (sort-by :seq-num (conj buffer event))) event))
  (pop [this]
    (locking @events
      (let [event (first @events)]
        (swap! events rest)
        event)))
  (peek [this] 
    (first @events))
  (ready? [this]
    (not (empty? @events))))

;; depends on the java queue interface
(deftype BinHeapLog [queue cursor]
  Log
  (cursor [this]
    @cursor)
  (inc-cursor [this]
    (swap! cursor inc))
  (push [this event]
    (when-not (nil? event)
      (.offer queue event)))
  (pop [this]
    (.poll queue))
  (peek [this] 
    (.peek queue))
  (ready? [this]
    (not (= (.size queue) 0))))

;; min heap wrapper for events
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

(defn blocking-min-heap
  [init-cap comparator]
  (java.util.concurrent.PriorityBlockingQueue. init-cap comparator))



(defn- naive-log [] (NaiveLog. (atom []) (atom 1)))
(defn- bin-heap-log [] (BinHeapLog. (blocking-min-heap 1000 event-comparator) (atom 1)))

;; Public Interface
(def log (atom (bin-heap-log)))
(defn get-cursor
  []
  (.cursor @log))

(defn inc-cursor
  []
  (.inc-cursor @log))

(defn push-event 
  [event]
  (.push @log event))

(defn pop-event
  []
  (.pop @log))

(defn peek-event
  []
  (.peek @log))

(defn event-buffer-ready?
  []
  (.ready? @log))



