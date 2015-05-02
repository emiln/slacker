# slacker

> An enthusiastically asynchronous Slack bot library.

[ ![Codeship Status for emiln/slacker](https://codeship.com/projects/e060f4e0-a88d-0132-8121-06eff5d60a17/status?branch=master)](https://codeship.com/projects/67401)

## Introduction

Slacker is an enthusiastically asynchronous library for interacting with
[Slack](https://slack.com). It caters primarily to the creation of simple to
intermediate complexity bots, but beyond clinging to this rationale as an
excuse for maintaining a rather meager feature set, it does not limit your
ability to write a regular Slack client or interacting with Slack in other ways.

The library revolves around emitting and handling events. After connecting to a
Slack network, Slacker will emit topical events to any interested listener
functions. The topics include [those from Slack](https://api.slack.com/events),
but manhandled into a more Clojurian format of `:keywords-with-dashes`. Slacker
does not sit idly by and generates a number of events of its own. These are
surely documented and kept up to date in the appropriate part of this README.

## Emitting events

Emitting an event in Slacker is like shouting from your balcony in the the dark.
Is anyone listening? You don't know. If they shout back and you happen to be
listening, you might reason that they did. It seems prudent to be up-front about
this:

> Emitted events are fully asynchronous and anoymouse, emitted in a different
> thread pool, and without call-backs. You just don't know who emitted them or
> if anyone is receiving them.

Why? It's a really simple implementation and nothing more seems necessary or
indeed desirable for the bots currently built on top of Slacker. There is no
sensible design decision underpinning this decisive choice, and there are no
guarantees it will not change in the future. For now this has not felt awkward
and has not limited expressiveness. Disagreements are welcome and should be
raised as issues.

> An event can be emitted using `slacker.client/emit!`.

The first argument is a required topic, which is suggested to take the type of
a keyword, but no restrictions are enforced. Any additional arguments passed to
`emit!` are passed as arguments to functions registered as handlers for the
topic. A few examples of events similar to those emitted internally by Slacker:

```clojure
(emit! :bot-connected "the-token-used-for-the-bot")
(emit! :websocket-connected "https://sneaky.url" "wss://sneaky.socket")
```

There is nothing special or privileged about Slacker's internals; you could emit
precisely the same events to just the same effect.

## Handling events

It seems likely that you'll want to handle a subset of the emitted events. This
is your chance to respond to events on Slack networks as well as any internal
events produced by the Slacker library. Event handlers are functions of
arbitrary arity, and they will be called when events tagged with their topic of
choice are emitted and passed zero or more arguments. Event handlers are created
using `slacker.client/handle`. The following is a simple REPL session in which
a handler is created and then triggered:

```clojure
=> (handle :the-best-topic
     (fn [a b]
       (println a "and" b)))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@598458dd>

=> (emit! :the-best-topic 1 2)
nil
1 and 2
```

In the example above, `handle` is passed a topic, `:the-best-topic`, and a
function expecting 2 arguments. This subscribes the function as a handler for
events under the topic `:the-best-topic`, and in the future any emmitted events
with that topic will result in the function being called in its own go block.
The function will be passed any additional arguments beyond the topic passed to
`emit!`, which in the example above are 1 and 2. Essentially, this should be
considered equivalent to the following code being executed:

```clojure
(go ((fn [a b]
       (println a "and" b))
     1 2))
```

As you may suspect, this leaves plenty of room for simple mistakes like the
following:

```clojure
=> (emit! :the-best-topic 1)
nil
Exception in thread "async-dispatch-19" clojure.lang.ArityException: Wrong number of args (1) passed to: user/eval9158/fn--9159
```

Slacker makes no attempt to ensure your code actually *works*. It is merely a
handy way to broadcast and handle many asynchronous events. If your code throws
an exception or blocks indefinitely, you will break the go block corresponding
to that particular event handler. You will not, however, prevent other event
handlers from being executed.

## A simple example

In this section we'll write a fully functional bot that reprimands users when
they use profane language in channels. First up, we'll need a way to determine
if a word is profane. We'll take an RMS-inspired approach as the repository
hosting the README is intended to be profanity-free.

```clojure
(defn profane-word?
  "Returns a non-nil value if the word is at odds with our essential software
  freedoms."
  [word]
  (#{"microsoft" "windows" "proprietary" "visual studio" "non-free"}
   (clojure.string/lower-case word)))
```

This will return a non-nil value if presented with a word that'd raise the
blood pressure of true free software enthusiasts. Now we need a way to split
sentences into words to test each of them for profanities.

```clojure
(defn split-into-words
  "Splits any string into more palatable words."
  [sentence]
  (re-seq #"(?i)[a-z\-]+" sentence))
```

We're now ready to decide whether a full sentence triggers our essential
software freedoms-inpired outlook on life.

```clojure
(defn triggered?
  "Returns a non-nil value if the sentence triggers us, demanding a response."
  [sentence]
  (some profane-word? (split-into-words sentence)))
```

With this difficult groundwork laid, we're ready to connect to Slack and start
cleansing channels of profanity. We'll need to find the approprite
[Slack event](https://api.slack.com/events) to respond to, which will simply be
`:message` for this example. Note that all Slack events are converted to
Clojure keywords using `slacker.converters/string->keyword`. Our task is
two-fold:

### Connect to Slack

You should have a token for the bot you're creating. You can easily create a
new bot and retrieve its token inside the Slack web UI.

```clojure
(slacker.client/emit! :slacker.client/connect-bot "my-secret-bot-token")
```

This will produce a number of event emissions that we don't really care about.
If all goes well this will make Slacker connect to the network for which the
bot token is valid, and our event bus will be receiving Slack network events
for the channels visible to the bot. In particular we now have the ability to
handle `:message` events, corresponding to users or bots writing messages on a
channel.

### Respond to messages

To respond to a message we'll need to emit an event called
`slacker.client/send-message`, which will need a receiver (a channel for our
example) and message. The channels is a string in a specific format, and the
message is simply any string. We'll extract the special channel string from the
message we'll be responding to.

```clojure
(defn reprimand-profanity
  "Responds to profanity by calling out the offender in the channel."
  [{:keys [channel user text]}]
  (when (triggered? text)
    (slacker.client/emit!
      :slacker.client/send-message
      channel
      "Please don't use offensive words.")))
```

The last step is to register `reprimand-profanity` as a handler for general
`:message` events.

`(slacker.client/handle :message report-profanity)`

That's it. The bot will now respond to profane utterances with a simple, static
reprimand.
