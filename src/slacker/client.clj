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
(def ^:private connection (atom nil))

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
  ([topic docstring handler-fn]
   (let [c (chan)]
     (sub publication topic c)
     (go-loop []
       (when-let [[topic & msg] (<! c)]
         (apply handler-fn msg)
         (recur)))))
  ([topic handler-fn]
   (handle topic "I'm too lazy to describe my handlers." handler-fn)))

;; +--------------------------------------------------------------------------+
;; | Websockets                                                               |
;; +--------------------------------------------------------------------------+

(handle ::connect-bot
  "Retrieves the websocket URL for a given bot token and emits this token in a
  command called ::connect-websocket commanding that a connection be made to
  the URL."
  (fn [token]
    (emit! ::connect-websocket
      token
      (-> (format "https://slack.com/api/rtm.start?token=%s" token)
        (http/get)
        (deref)
        (:body)
        (read-str :key-fn string->keyword)
        (:url)))))

(handle ::connect-websocket
  "Connects to the websocket temporarily available at `url` and emits the
  following two events:

  [::websocket-connected url socket] for handlers interested in the socket.
  [::bot-connected token] for handlers interested in the token."
  (fn [token url]
    (let [socket (connect url
                   :on-receive
                   (fn [raw]
                     (emit! ::receive-message
                       (read-str raw :key-fn string->keyword))))]
      (reset! connection socket)
      (emit! ::websocket-connected url socket)
      (emit! ::bot-connected token))))

;; +--------------------------------------------------------------------------+
;; | Message handling                                                         |
;; +--------------------------------------------------------------------------+

(handle ::receive-message
  "Handles the reception of a message by republishing it with a keywordized
  topic matching those described in the Slack API. This makes it easy to only
  subscribe to the kind of events you want, such as :message, :presence_change,
  etc."
  (fn [msg]
    (let [topic (cond (:type msg)     (:type msg)
                      (:reply_to msg) "reply"
                      :else           "unknown")]
      (go (>! publisher [(string->keyword topic) msg])))))


(handle ::send-message
  "Sends a message to the currently open websocket."
  (fn [msg]
    (send-msg @connection (string->slack-json msg))))
