(ns fortnight.source
  (:require [fortnight.tcp :as tcp]
            [fortnight.buffer :as buffer]
            [clojure.tools.logging :as log]))

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
  "Continually reads from the given connection and appends each new line to the event-buffer."
  [conn]
  (with-open [reader (tcp/conn->reader conn)]
    (while true
      (if-let [inp (.readLine reader)]
        (let  [event   (parse-event inp)
               seq-num (:seq-num event)]
        (if (<= (buffer/get-cursor) seq-num)
          (buffer/push-event (parse-event inp))
          (log/warnf "[handle-new-event-source] Discarding %d!" seq-num)))))))

