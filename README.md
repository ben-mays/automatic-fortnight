# automatic-fortnight

The Fortnight service is a simple event multiplexing system that exposes a two interfaces; one for `event source` clients and another for `user` clients. I was sent the project description days before I was on holiday and just recently had some bandwidth to tackle it. On my return flight in January, I began working on it. I've chosen to use no external libraries (even core.async) with the exception of `log4j` and `cprop` for logging and configuration.

## Usage

The system exposes the following options:

* Max Event Source Clients
* Event Source port
* Max User Clients
* User client port
* Max Delay (if -1 wait forever)

### Docker
## 

# Design

The design is rather simple. The initial thinking about ordering an infinite of sequence of events, with some bounded delay, lended itself to a few abstractions that could be extended with more time. I can see whole-sale service replacements for the in-process components below to implement as a more resilient 

There are 3 abstract components in the service:

* The event source handler - listens on the `port` and handles incoming event source socket connections. On a new connection, a thread is launched that parses incoming events into a shared event-buffer (implemented as a priority queue / min heap).
* The user handler - listens on the `port` and handles incoming user client socket connections. On a new connection, a thread is launched which will register the user client, listen for events from the event processor and sends them to the user.
* The event processor - Pulls events out of the event-buffer in-order and fans them out into user threads. The event buffer maintains a cursor that indicates the next event to send, allowing events to be placed into the event-buffer in an un-ordered and un-sequential fashion. The event processor will only send the next event if it's sequence number matches the cursor _or_ the maximum event delay has been exceeded.

There are some very obvious limitations and improvements that can be made given more investment:

* In this simple implementation, the user handlers do not need to be separate threads and could exist simply as socket connections with a managing thread pulling messages out of a shared buffer. 
* In another implementation, the ordering of events could occur on each user client thread, with global synchronization happening only on the cursor. This would be an improvement at a very large scale to reduce the insertion time into the min-heap (log k), by reducing k to be only the per user messages. Initially this was my approach but found the simpler version satisfies the requirements.
* Congestion control could be implemented on the Event Source interface to handle failure more elegantly.
* Persistence to allow durability of events
* New events are stored even if they are less than the cursor. I wasn't sure how to handle a new event source coming on that was duplicating seq-nums.
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
