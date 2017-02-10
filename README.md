# automatic-fortnight

The Fortnight service is a simple event multiplexing system that exposes a two interfaces; one for `event source` clients and another for `user` clients. I was sent the project description days before I was on holiday and just recently had some bandwidth to tackle it. On my return flight in January, I began working on it. I've chosen to use no external libraries (even core.async) with the exception of `clojure.tools.logging` for logging.

## Usage

The system exposes the following options as env vars:

* `maxEventSourceClients` - the maximum number of event source clients. Default: 1
* `eventSourceServerPort` - the port to listen on for the event source client connections. Default: 9090
* `maxUserClients` - the maximum number of concurrent user clients that can connect to the server. Default: 1000
* `userServerPort` - the port to listen on for new user connections. Default: 9099
* `eventTimeoutMs` - the time in milliseconds to wait before skipping an event. If set to a negative number, the system will block for every event. Default: 1000

### Docker

I decided to dockerize the service for ease of deployment - the docker container will abstract away the environment so testers don't need to install JDK8. Optionally, I've incuded a jar you can run under `bin`.

* Install [docker-compose](https://docs.docker.com/compose/install/) (and docker) on your system.
* Clone the repo
* Launch the server with `make dev-server`. 

For production deployments, we could use something like Docker Swarm or any host with Docker installed. The compose file would point to a pre-built docker image for the application. The docker-compose file provided is only for development.

### Jar 

* Ensure you have JDK8 nstalled
* Clone the repo
* Execute `java -jar fortnight.jar` from your terminal

# Design

The design is rather simple. The initial thinking about ordering an infinite of sequence of events, with some bounded delay, lended itself to a few abstractions that could be extended with more time. I can see whole-sale service replacements for the in-process components below to implement as a more resilient 

There are 3 abstract components in the service:

* The event source handler - listens on the `port` and handles incoming event source socket connections. On a new connection, a thread is launched that parses incoming events into a shared event-buffer.
* The user handler - listens on the `port` and handles incoming user client socket connections. On a new connection, a thread is launched which will register the user client. Sending messages to the user socket occurs via the event processor thread.
* The event processor - Pulls events out of the event-buffer in-order and fans them out into user threads. The event buffer maintains a cursor that indicates the next event to send, allowing events to be placed into the event-buffer in an un-ordered and un-sequential fashion. The event processor will only send the next event if it's sequence number matches the cursor _or_ the maximum event delay has been exceeded.

There are some very obvious limitations and improvements that can be made given more investment:

* A different event buffer implementation. See `buffer.clj` for more information -- I attempted to use both a min binary heap and min fibonacci heap, which actually resulted in a slow down due to additional dequeuing costs. Ultimately the binary heap implementation seemed to scale linearly with input and achieve the goals of the project.
* In another implementation, the ordering of events could occur on each user client thread, with global synchronization happening only on the cursor. This would be an improvement at a very large scale to reduce the insertion time into the min-heap (log k), by reducing k to be only the per user messages. Initially this was my approach but found the simpler version satisfies the requirements. As an experiment, I branched and tried this approach. It required all user threads to receive all follow/unfollow events, to build their time-relative follower-maps for calculating the recipients of status update messages. This implementation became a bit unweildy and I felt more comfortable submitting a simpler version. Another note about per-user-processing was that we duplicated messages across buffers, causing excess memory to be consumed. This became a concern as more users were added. It became clear there were two trade offs each approach was solving for -- throughput and concurrency.
* Congestion control could be implemented on the Event Source interface to handle failure more elegantly.
* Persistence to allow durability of events
* The event-buffer is unbounded and if the rate of inbound events is greater than the rate of processing events, the system with OOM eventually. This could be mitigated with congestion control or by simply dropping some % of events after determining the rate is unsustainable.


## Ordering Events

My mental model of the service looks like a buffer with many cursors -- one for each user and one for the processor. Each user cursor is waiting on the processor cursor to pass it before it can continue processing the buffer. The simplified implementation of this is sharing an cursor atom globally, but I imagine this could be implemented as a protocol in a further implementation without shared memory. 

e.g. 
```
event-buffer:

[0, _, _, 3, 4, 5, _, _, 6]
    ^     ^     ^
     \     \     \
      \     \     user 2 cursor - waiting on `p_cursor > user2_cursor` to be true
       \     user 1  - waiting on `p_cursor > user1_cursor` to be true
        \ 
         processor cursor waiting on seq-num / handling a hole

messages-buffer for user 1:

[3, 4, 5]

messages-buffer for user 2:

[5]

```

Because the cursor is monotonic and is only mutated by a single processor thread, this protocol works reasonably well to ensure that each user sends the correct messages in order.

## Testing

I wanted to use Clojure 1.9 Alpha for this project to do some generative testing using the new spec utilities, but due to a very restrictive schedule I didn't find the time. I manually tested with the client provided, varying the seed and concurrency but chose not to invest any more time in the project as I had gone over the alloted time limit and needed to move on. Total time on this project was around 8-10 hours, mostly on a flight and some clean up over MLK weekend.

I ran most of my tests against a Docker container with 2 CPUs and 2G memory; using the supplied test harness. To hit the default configuration timeouts, you might need to increase these. 

If I were given more time, I would build abstractions around the source, buffer and user processing that would have allowed more formal testing. 