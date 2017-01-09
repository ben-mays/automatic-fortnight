(ns fortnight.main
  (:gen-class)
  (:import [java.net ServerSocket SocketException]))

;; State
(def clients (atom {}))
;; cursor is the next event id to process, the events should be an ordered collection
;;(def event-buffer (atom {:cursor 0 :events []}))
(def events (atom []))
(def cursor (atom 1))
(def maze (atom {})) ;; a map from user-id to a vector of user-ids
(def event-server (atom nil))
(def client-server (atom nil))

;; Server Util
(defn server-socket
  "Creates a #java.net.ServerSocket listening on the given port."
  [port max-conn]
  (ServerSocket. port max-conn))

(defn closed?
  "True if the server is running."
  [socket]
  (.isClosed socket))

(defn close 
  [socket]
  (.close socket))

(defn safe-close 
  [socket]
  (when-not (closed? socket) 
    (close socket)))

(defn conn->reader
  [conn]
  (-> conn
      (.getInputStream)
      (clojure.java.io/reader)))

(defn conn->writer
  [conn]
  (-> conn
      (.getOutputStream)
      (clojure.java.io/writer)))

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

(defn parse-event 
	[line]
 ;; (println "Parsing " line)
	(let [arr (clojure.string/split line #"\|")]
		{:seq-num (Long/parseLong (first arr))
     :type    (second arr)
     :from    (nth arr 2 nil)
     :to      (nth arr 3 nil)
     :raw     line}))

(defn event-sourcer
  "Returns a future that continually reads from the given connection and appends each new line to the event-buffer."
  [conn]
  (with-open [reader (conn->reader conn)]
      (while true
        (let [inp (.readLine reader)]
          (if (not (nil? inp))
            (push-event (parse-event inp)))))))

(defn write
  [msg conn]
  (try
    (let [writer (conn->writer conn)]
      (.write writer (str msg "\r\n"))
      (.flush writer))
    (println "Wrote " msg)
    (catch SocketException _)))


(defn send-message
  [msg conns]
  (let [valid-conns (->> conns
                         (filter some?)
                         (filter #(not (closed? %))))]
    (dorun (map (fn [conn] (write msg conn)) valid-conns))
    ;;(println "Sent to " msg valid-conns conns)
    ))

(defn user-connected?
  [user-id client-map]
  (not (nil? (get client-map user-id))))

(defn user->conn
  [user]
  (get-in @clients [user :conn]))

(defn get-followers
  [user]
  (get @maze user))

(defn follow-swap-fn
  [maze follower followed]
  (let [existing-followers (get-followers followed)
        new-followers      (set (conj existing-followers follower))]
      (assoc maze followed new-followers)))

(defn unfollow-swap-fn
  [maze follower followed]
  (let [existing-followers (get-followers followed)
        new-followers      (disj existing-followers follower)]
    (assoc maze followed new-followers)))

(defn follow
  [event]
  (let [followed      (:to event)
        followed-conn (get-in @clients [followed :conn])
        follower      (:from event)]
    (swap! maze follow-swap-fn follower followed)
    (send-message (:raw event) [followed-conn])))

(defn unfollow
  [event]
  (let [followed (:to event)
        follower (:from event)]
  (swap! maze unfollow-swap-fn follower followed)))

(defn broadcast
  [event]
  (let [conns (map :conn (vals @clients))]
    (send-message (:raw event) conns)))

(defn private-message
  [event]
  (let [to      (:to event)
        to-conn (get-in @clients [to :conn])]
    (if (not (nil? to-conn))
      (send-message (:raw event) [to-conn]))))

(defn status-update
  [event]
  (let [from           (:from event)
        follower-conns (->> (get-followers from)
                            (map user->conn))]
    (println "Sending status update from " from " to " (get-followers from))
    (send-message (:raw event) follower-conns)))

(defn process-event
  [event]
  (println "Processing " event)
  (condp = (:type event)
        "F" (follow event) ;; update the client atom for the `to` user with the `from` user id and send a message to `to` user
        "U" (unfollow event)
        "B" (broadcast event)
        "P" (private-message event)
        "S" (status-update event))
  (swap! cursor inc))

(defn is-next?
  [seq-num]
  (= seq-num (:seq-num (peek-event))))

(defn event-processor
  []
	(while true
    ;;    (Thread/sleep 1000)
    ;; (print (map closed? (map :conn (vals @clients))))
		(if (event-buffer-ready?)
      ;; if cursor is pointing to the next event, process and update cursor
      ;; else, sleep 2
      (do
        (if (is-next? @cursor)
          (process-event (pop-event))
          (do
            (println "cursor is behind" @cursor (peek-event))
            (Thread/sleep 100))))
      ;;(println "no events")
      )))
	;; event-buffer.peek == cursor + 1 ?
		;; events += event-buffer.pop
		;; cursor += 1
	;; process events

(defn handle-events
  "Launches two threads, one to parse incoming events and append them into the event-buffer. 
	Another to pop off the event buffer and deliver messages to clients."
  [conn]
	;; handle connection setup
	;; start workers
  (let [source-thread    (Thread. (fn [] (event-sourcer conn)))
        processor-thread (Thread. event-processor)]
    (print "Starting threads")
    (.start source-thread)
    (.start processor-thread)
    (.join source-thread)
    (.join processor-thread)
    (print "Finished")))

(defn handle-clients
  "Reads the user id from the connection and stores the socket in the clients atom."
  [conn]
  (with-open [reader (conn->reader conn)
              writer (conn->writer conn)]
    (let [inp (.readLine reader)]
      (swap! clients assoc inp {:conn conn :writer writer :followers []})
      (while true
        (Thread/sleep 1000)
        ;;(println inp (closed? conn))
        ))))

(defn server-socket-listener
  [socket handler]
  (future
    (while (not (closed? socket))
      (try
        (let [conn (.accept socket)]
          (print "New connection!")
          (-> (fn [] (handler conn))
              (Thread.)
              (.start)))
        (catch SocketException e
          (print (.getMessage e))))))) ;; throw away bad incoming socket connections or be resilient to max-conn issues

(defn start-event-server
  [config]
  (let [port     (get config :port 9090)
        max-conn (get config :max-event-source 1)
        socket   (server-socket port max-conn)
        listener (server-socket-listener socket handle-events)]
    {:port     port
     :max-conn max-conn
     :socket   socket
     :listener listener}))

;; Will probably need to switch to NIO SocketChannels to handle 1000s of clients.
(defn start-client-server
  [config]
  (let [port     (get config :port 9099)
        max-conn (get config :max-user-connections 1000)
        socket   (server-socket port max-conn)
        listener (server-socket-listener socket handle-clients)]
    {:port     port
     :max-conn max-conn
     :socket   socket
     :listener listener}))

(defn stop []
  (reset! event-server nil))

(defn start []
  (reset! event-server (start-event-server {}))
  (reset! client-server (start-client-server {}))
  (reset! cursor 1)
  (reset! events [])
  (reset! clients {}))

