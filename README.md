# slacker

> A basic Slack bot library revolving around an `emit!`/`handle` flow.

## Getting started

Here's a simple namespace that will launch a working bot (barring the token):
```clojure
(ns my-bot.core
  (:require [slacker.client :refer [emit! handle login!]]))

(def raw-socket (login! "my-super-secret-bot-token"))

(handle :message
  (fn [{:keys [text]}]
    (when (re-seq #"(?i)developing" text)
      (emit! :slacker.client/send-message
        (->> "DEVELOPERS"
          (repeat 16)
          (clojure.string/join " "))))))
```

There are really only three concepts you must familiarize yourself with in this
working example:

### `login!`

For the `login!` token you should simply create a bot on Slack and use the
token created in the process. Note that several running instances of your bot
can use the same token if you're trying to minimize the number of bots in your
channels.

### `emit!`

Calling `emit!` signals that an event happened, and that you expect something
else to deal with it. When emitting an event, you supply a topic and zero or
more additional arguments as needed. In our example we're emitting an event on
the topic `slacker.client/send-message`. As you might expect, this event
signals that someone wants to send a message through the slacker client to the
Slack server, and, indeed, there is an event handler registered inside the core
of slacker for doing just that. You are not limited to emitting events with
topics that slacker knows in advance, though. This is a general, fast, and
asynchronous way of communicating between the different parts of your
application, and you should use it whenever it simplifies your data flow.

Note that `emit!` is completely orthogonal to network activity. You don't even
need to have connected your bot to use it.

### `handle`

You've already seen a handler, namely the one dealing with the
`slacker.client/send-message` topic. Here's how it's defined:

```clojure
(handle ::send-message
  (fn [msg]
    (send-msg socket (string->slack-json msg))))
```

That is the actual code that sends your messages to the websocket. It takes two
arguments: a topic and a function of any arity. Consider the following flow:

```clojure
(emit! :my-topic "a" :b [3])
```

This will expect a handler for the topic `:my-topic` to be able to take 3
arguments.

Here an example of using `emit!` and `handle` that you can run in your
REPL:

```clojure
(handle :product
  (fn [& numbers]
    (println (clojure.string/join "*" numbers)
             "="
             (reduce * numbers))))
```

You are now ready to print some spankin' products. If you were to

```clojure
(emit! :product 12 6 8 3 32)
(apply emit! :product (range 15 0 -1))
```

your REPL should print something along the lines of

```text
12*6*8*3*32 = 55296
15*14*13*12*11*10*9*8*7*6*5*4*3*2*1 = 1307674368000
```

which is clearly widely useful and practical.
