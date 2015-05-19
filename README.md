# slacker

> An enthusiastically asynchronous Slack bot library.

[ ![Circle CI status for emiln/slacker](https://circleci.com/gh/emiln/slacker/tree/master.svg?style=badge)](https://circleci.com/gh/emiln/slacker)

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

> Emitted events are fully asynchronous and anonymous, emitted in a different
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
  (#{"microsoft" "windows" "proprietary" "non-free" "copyright"}
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
cleansing channels of profanity. We'll need to find the appropriate
[Slack event](https://api.slack.com/events) to respond to, which will simply be
`:message` for this example. Note that all Slack events are converted to
Clojure keywords using `slacker.converters/string->keyword`. Our task is
two-fold:https://github.com/emiln/slacker/blob/5-better-readme/README.md

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
example) and message. The channel is a string in a specific format, and the
message is simply any string. We'll extract the special channel string from the
message we'll be responding to along with the actual text of the message.

```clojure
(defn reprimand-profanity
  "Responds to profanity by calling out the offender in the channel."
  [{:keys [channel text]}]
  (when (triggered? text)
    (slacker.client/emit!
      :slacker.client/send-message
      channel
      "Don't use offensive words!")))
```

The last step is to register `reprimand-profanity` as a handler for general
`:message` events.

`(slacker.client/handle :message report-profanity)`

That's it. The bot will now respond to profane utterances with a simple, static
reprimand.

Here is the full code, written in a proper namespace, reordered slightly, and
with use of `require`, all ready to paste into a file and save.

```clojure
(ns mybot.core
  (:require [clojure.string :refer [lower-case]]
            [slacker.client :refer [emit! handle]]))

(defn profane-word?
  "Returns a non-nil value if the word is at odds with our essential software
  freedoms."
  [word]
  (#{"microsoft" "windows" "proprietary" "non-free" "copyright"}
   (lower-case word)))

(defn split-into-words
  "Splits any string into more palatable words."
  [sentence]
  (re-seq #"(?i)[a-z\-]+" sentence))

(defn triggered?
  "Returns a non-nil value if the sentence triggers us, demanding a response."
  [sentence]
  (some profane-word? (split-into-words sentence)))

(defn reprimand-profanity
  "Responds to profanity by calling out the offender in the channel."
  [{:keys [channel text]}]
  (when (triggered? text)
    (emit! :slacker.client/send-message channel "Don't use offensive words!")))

(handle :message reprimand-profanity)

(emit! :slacker.client/connect-bot "my-secret-bot-token")
```

## A slightly more complex example

Having successfully created a bot that responds with static responses to certain
words, it's time to address how you can handle state in a completely
asynchronous library like this. In this example we'll add moods to the bot,
making it grow angrier if people keep ignoring its reprimands, happier if people
use words from the free software movement, and slowly return to a neutral mood
if no triggers are seen for a while.

To get started we'll store the mood state in an atom as a simple integer. We'll
keep a mood for each user, as our advanced bot wants to respond justly and not
conflate its opinion about the different users in a channel. We'll use a map
from user id string to mood integer.

```clojure
(def moods
  "Our attitudes towards the various users on the network."
  (atom {}))
```

Zero will be our neutral bot mood, and we can `swap!` it with `inc` or `dec` to
alter it. It feels intuitive to let positive numbers correspond to positive
moods. As it seems unlikely we'll exert the effort required to author more than
a few specific phrases for the bot, we'll create a few helper functions to let
us increase and decrease the atom, but within specific bounds.

```clojure
(defn mood-dec
  "Decreases the mood, but does not let it drop below -3."
  [moods user]
  (update-in moods [user] #(max -3 (dec (or % 0)))))

(defn mood-inc
  "Increases the mood, but does not let it rise above 3."
  [moods user]
  (update-in moods [user] #(min 3 (inc (or % 0)))))
```

We should also create a mapping from mood to string. We'll make use of `format`
to insert the name of the offender for a more personal response, so the strings
returned here should make use of `%s` to refer to the offender if necessary.

```clojure
(defn mood-string
  "Takes an integer representing a mood and returns a string with our response
  to the mentioning of proprietary software, taking into consideration our mood.
  This string is intended to be used with `format` and have passed as argument
  a formatted user id."
  [mood]
  (get {-3 "SHUT THE FUCK UP ABOUT YOUR SHIT-WARE %s I'LL KILL YOU"
        -2 "Shut it about your proprietary malware already, %s."
        -1 "I told you not to talk about that, %s."
         0 "Don't talk about proprietary software, %s. It triggers me."
         1 "Please don't mention proprietary software, %s."
         2 "I'd like to change the topic from proprietary software, %s."
         3 "I'm sorry, %s, but I'm not keen on proprietary software."}
    mood "I don't know how to feel about that, %s."))
```

With this intricate mood system in place, we're ready to dive into some details
about Slack. When you receive a `:message`, you can extract its `:user` value,
which will be an id in the form of a string. If you wrap this id in "<@" and
">", slack will translate that into the user name and highlight it, which is
precisely what we want for this bot. We'll add a simple helper function for just
that task.

```clojure
(defn format-user-id
  "Formats a user id for proper rendering in Slack."
  [id]
  (str "<@" id ">"))
```

We've got most of the plumbing in place now, but there's one issue we haven't
really addressed. We probably don't want to respond to our own messages
including the word "proprietary" by writing another message including the word
"proprietary". You probably see the problem with this. How do we address this?
We'll simply use a black list of user ids who do not warrant a reprimand.
Slacker provides a very basic function to look up users on the network.

```clojure
(def trusted-user?
  "Returns true if we trust the user id to discuss proprietary software. This
  currently amounts to the user being specifically our bot."
  (let [me (->> (slacker.lookups/users "our-secret-bot-token")
             (filter #(= (:name %) "mybot"))
             (first)
             (:id))]
    (partial = me)))
```

We're now ready to write some handlers. The first one will reprimand and then
decrease our mood.

```clojure
(defn reprimand-profanity
  "Responds to profanity by calling out the offender in the channel."
  [{:keys [channel user text]}]
  (when (and (not (trusted-user? user))
             (triggered? text))
    (let [mood (get @moods user 0)
          mood-string (mood-string mood)
          user-string (format-user-id user)
          message (format mood-string user-string)]
      (emit! :slacker.client/send-message channel message)
      (swap! moods mood-dec user))))
```

The next one will increase our mood (but remain silent) when the GPL is
mentioned.

```clojure
(defn happy-message?
  "Returns truthy is we're pleased by this message."
  [message]
  (re-find #"GPL" message))

(defn make-happy
  "Ensure our opinion of a user is raised if we're pleased with their message."
  [{:keys [user text]}]
  (when (happy-message? text)
    (swap! moods mood-inc user)))
```

Let's register both of them.

```clojure
(handle :message reprimand-profanity)
(handle :message make-happy)
```

We're now keeping track of our disposition towards the various users in the
channels we monitor. Here is the code wrapped up in a namespace for convenience:

```clojure
(ns mybot.core
  (:require [clojure.string :refer [lower-case]]
            [slacker.client :refer [emit! handle]]
            [slacker.lookups :refer [users]]))

(def moods
  "Our attitudes towards the various users on the network."
  (atom {}))

(defn mood-dec
  "Decreases the mood, but does not let it drop below -3."
  [moods user]
  (update-in moods [user] #(max -3 (dec (or % 0)))))

(defn mood-inc
  "Increases the mood, but does not let it rise above 3."
  [moods user]
  (update-in moods [user] #(min 3 (inc (or % 0)))))

(defn profane-word?
  "Returns a non-nil value if the word is at odds with our essential software
  freedoms."
  [word]
  (#{"microsoft" "windows" "proprietary" "non-free" "copyright"}
   (lower-case word)))

(defn split-into-words
  "Splits any string into more palatable words."
  [sentence]
  (re-seq #"(?i)[a-z\-]+" sentence))

(defn triggered?
  "Returns a non-nil value if the sentence triggers us, demanding a response."
  [sentence]
  (some profane-word? (split-into-words sentence)))

(defn mood-string
  "Takes an integer representing a mood and returns a string with our response
  to the mentioning of proprietary software, taking into consideration our mood.
  This string is intended to be used with `format` and have passed as argument
  a formatted user id."
  [mood]
  (get {-3 "SHUT THE FUCK UP ABOUT YOUR SHIT-WARE %s I'LL KILL YOU"
        -2 "Shut it about your proprietary malware already, %s."
        -1 "I told you not to talk about that, %s."
         0 "Don't talk about proprietary software, %s. It triggers me."
         1 "Please don't mention proprietary software, %s."
         2 "I'd like to change the topic from proprietary software, %s."
         3 "I'm sorry, %s, but I'm not keen on proprietary software."}
    mood "I don't know how to feel about that, %s."))

(defn format-user-id
  "Formats a user id for proper rendering in Slack."
  [id]
  (str "<@" id ">"))

(def trusted-user?
  "Returns true if we trust the user id to discuss proprietary software. This
  currently amounts to the user being specifically our bot."
  (let [me (->> (users "our-secret-bot-token")
             (filter #(= (:name %) "mybot"))
             (first)
             (:id))]
    (partial = me)))

(defn reprimand-profanity
  "Responds to profanity by calling out the offender in the channel."
  [{:keys [channel user text]}]
  (when (and (not (trusted-user? user))
             (triggered? text))
    (let [mood (get @moods user 0)
          mood-string (mood-string mood)
          user-string (format-user-id user)
          message (format mood-string user-string)]
      (emit! :slacker.client/send-message channel message)
      (swap! moods mood-dec user))))

(defn happy-message?
  "Returns truthy is we're pleased by this message."
  [message]
  (re-find #"GPL" message))

(defn make-happy
  "Ensure our opinion of a user is raised if we're pleased with their message."
  [{:keys [user text]}]
  (when (happy-message? text)
    (swap! moods mood-inc user)))

(handle :message reprimand-profanity)
(handle :message make-happy)

(emit! :slacker.client/connect-bot "my-secret-bot-token")
```

I hope this has convinced you that it is possible to write bots of at least
intermediate complexity with Slacker. If you think you're missing essential
tools, be sure to raise an issue about it.

## List of Slacker events

Events emitted by Slacker itself are always Clojure keywords and follow a
simple naming convention.

### Calls to action

A call to action generally takes the form "imperative verb"-"noun" like in
`:connect-bot`. These signal an expectation that someone will respond to the
event, and that this is necessary for the emission to be successfully completed.

All current calls to action in Slacker:

Name | Arguments | Description
--- | --- | ---
`:slacker.client/connect-bot` | `token` | Connects the Slack bot to the network using the token.
`:slacker.client/connect-websocket` | `token`, `url` | Opens a websocket client to the given url with the Slack bot token.
`:slacker.client/receive-message` | `message` | Parses the raw Slack message, emitting a new event with under a topic fitting for the message type.
`:slacker.client/send-message` | `receiver`, `message` | Sends message, a string, to the receiver, which should be an ID for a channel, group or user.

### Notifications

A notification doesn't remand further action and instead signals that a
processing of some kind has completed. These generally take the form
"noun"-"past tense verb" like `:bot-connected`.

All current notifications in Slacker:

Name | Arguments | Description
--- | --- | ---
`:slacker.client/websocket-connected` | `url`, `socket` | Logs the URL that was successfully connected to and the open websocket.
`:slacker.client/bot-connected` | `token` | Logs the token that was successfully connected to.

## Logging

Slacker uses [tools.logging](https://github.com/clojure/tools.logging) for all
its internal logging. You should refer to this library about how to configure
it, but it will work out-of-the-box by displaying important logging (of level
`:info` or more severe) in `System.out`, and ignoring any logging of lesser
importance.

Slacker will:

*   Log any call to `emit!` and `handle` with level `:debug`.
*   Log any exception in handlers with level `:error`.

This means you will have to actively change configuration to see debugging
messages if you want this, but you will be notified of any exceptions happening
in your asynchronous code.

You probably want to set up proper logging using one of the loggers supported
by [tools.logging](https://github.com/clojure/tools.logging):

*   [slf4j](http://www.slf4j.org/)
*   [Apache commons-logging](https://commons.apache.org/proper/commons-logging/)
*   [log4j](http://logging.apache.org/log4j/2.x/)
*   [java.util.logging](http://docs.oracle.com/javase/8/docs/api/index.html?java/util/logging/package-summary.html)
