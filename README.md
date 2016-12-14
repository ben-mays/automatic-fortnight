# automatic-fortnight
Just some thoughts for now.

# Complex Approach
## Router Design

The router simply processes all the events and generates messages to the user client proccesses. The router handles all user registration/de-registration and keeps a map of user ids to channels. It also keeps track of it's events in a ordered buffer and processes the events when the cursor is either greater than the event or the event sequence number is `cursor + 1`. User clients consist of a user id, a open socket connection and chan used for processing events.

## Client Design

To handle a large stream of events with no ordering, we use a ordered buffer (implemented as a min heap) that is _processed_ up to the next `hole`. 

```
[0, 1, 3, 4, 5]
      ^^^ hole exists and will block processing for X seconds.
      if the hole is not filled within X seconds we move jump to the next event.
```

We can block indefinitely on the `hole` until we hit some capacity on our heap, then we need to either fail or make progress.

All clients publish their current cursor position periodically, where the main thread then takes the maximum of all the client threads and notifies all user threads. The user threads can then process up to the max cursor position I and then any preceding K events that are sequential in it's queue -- updating the new max cursor position to I + K.

How to handle a hole that was a no-op event? The main thread (or could be another worker thread) handles these events and update the max cursor position using the similar scheme above.

How does this handle the worst case [N, N-1, ... 1] -- it doesn't. It just provides a clean abstraction over sorting the events.

## Simpler approach:

Create min heap
Set cursor to 0
Read in events to min heap
if cursor = top of heap, process events
