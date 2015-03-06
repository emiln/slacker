(ns slacker.client
  "A simple Slack bot following an emit/handle flow. Read more about it in the
  README."
  (:require
    [clojure.core.async :refer [<! >! chan go go-loop pub sub]]
    [clojure.data.json :refer [read-str]]
    [clojure.string :refer [lower-case replace]]
    [org.httpkit.client :as http]
    [gniazdo.core :refer [connect send-msg]]
    [slacker.converters :refer [string->keyword string->slack-json]]))

(def ^:private publisher (chan))
(def ^:private publication (pub publisher first))

(defn emit!
  "Emits an event to handlers. It will find all handlers registered for the
  topic and call them with the additional arguments if any."
  [topic & args]
  (go (>! publisher (apply vector topic args)))
  nil)


(defn handle
  "Subscribes an event handler for the given topic. The handler will be called
  whenever an event is emitted for the given topic, and any optional args from
  emit! will be passed as arguments to the handler-fn."
  [topic handler-fn]
  (let [c (chan)]
    (sub publication topic c)
    (go-loop []
      (when-let [[topic & msg] (<! c)]
        (apply handler-fn msg)
        (recur)))))

(defn client!
  "Connects to the given websocket URL and returns the open socket. The client
  will registers a handler to make sending messages to the Slack server more
  convenient:

  (emit! :slacker.client/send-message some-string) will be picked up by the
  client (notice the namespaced keyword) and some-string will be sent with all
  of :channel, :id, :text, and :type set. Which channel? #srsbsns on Dongers
  Inc. This is awaiting a future expansion of the API."
  [url]
  (let [socket (connect
                 url
                 :on-receive
                 (fn [raw]
                   (emit! ::receive-message (read-str raw :key-fn keyword))))]
    ;; Handle ::send-message events by sending them to the Slack server.
    (handle ::send-message
      (fn [msg]
        (send-msg socket (string->slack-json msg))))
    ;; Handle ::receive-message events by publishing them with :type as topic.
    (handle ::receive-message
      (fn [msg]
        (let [topic (cond (:type msg)     (:type msg)
                          (:reply_to msg) "reply"
                          :else           "unknown")]
          (go (>! publisher [(string->keyword topic) msg])))))
    socket))

(defn login!
  "Connects to Slack given a token and returns the socket connection."
  [token]
  (-> (format "https://slack.com/api/rtm.start?token=%s" token)
        (http/get)
        (deref)
        (:body)
        (read-str :key-fn keyword)
        (:url)
        (client!)))
