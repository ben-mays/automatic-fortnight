(ns fortnight.main
  (:gen-class)
	(:import [java.net InetAddress ServerSocket Socket SocketException]))

;; State
(def- sinks (atom []))
(def- clients (atom {}))
(def- cursor 0)
(def- event-buffer [])

;; Interface
(defn- server [port max-conn]
  (ServerSocket. port max-conn (InetAddress/getByName 127.0.0.1)))

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

(defn event-sourcer [conn]
	(future 
		(try ;; read stuff, append to min heap
			)))

(defn event-processor []
	(when-not (empty? event-buffer))
	;; event-buffer.peek == cursor + 1 ?
		;; events += event-buffer.pop
		;; cursor += 1
	;; process events
	)

(defn parse-line [conn])

(defn handle-events
	"Launches two threads, one to parse incoming events and append them into the event-buffer. 
	Another to pop off the event buffer and deliver messages to clients."
	[conn]
	;; handle connection setup
	;; start workers
	(let [source (event-sourcer conn)
		  proccessor (event-processor)]
	)
	;; block on worker futures
	)

(defn handle-clients [conn]
	;; parse client id
	;; assoc into client map
	;; done
	)

(defn- handle-incoming-connections
	"Listens for new incoming connections on the given socket and passes the connection along to a handler function in another thread. 
	Ensures the connection is closed properly."	
  [socket handler]
  (let [conn (.accept socket)]
    (future (handler conn))))

;; Return a meta map and future?
(defn start-event-server
  "Starts a SocketServer on the given port for the event source to connect to and continuously calls #accept on the socket, 
  processing new incoming connections. This function is not asyncronous and will block until the socket is closed."
  [config]
  (let [port (get config :port 9090)
  		max-conn 1 ;; event server only accepts a single event source
  		socket (server-socket port max-conn)])
    (while (not (closed? socket))
      (try
      	(handle-incoming-connections socket handle-events)
      	(catch SocketException _))))) ;; throw away bad incoming socket connections or be resilient to max-conn 


(defn start-client-server
  "Starts a SocketServer on the given port for the event source to connect to and continuously calls #accept on the socket, 
  processing new incoming connections. This function is not asyncronous and will block until the socket is closed."
  [config]
  (let [port (get config :port 9090)
  		max-conn (get config :max-conn 1000)
  		socket (server-socket port max-conn)])
    (while (not (closed? socket))
      (try
      	(handle-incoming-connections socket handle-clients)
      	(catch SocketException _))))) ;; throw away bad incoming socket connections or be resilient to max-conn issues

(defn -main
  [& args]
  (future start-client-server)
  (future start-event-server))
