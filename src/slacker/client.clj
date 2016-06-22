(ns slacker.client
  "A simple Slack bot following an emit/handle flow. Read more about it in the
  README."
  (:require
    [clojure.core.async :refer [alts!! <! <!! >! chan go go-loop pub sub timeout]]
    [clojure.data.json :refer [read-str]]
    [clojure.stacktrace :refer [print-stack-trace]]
    [clojure.string :refer [lower-case]]
    [clojure.tools.logging :as log]
    [org.httpkit.client :as http]
    [gniazdo.core :refer [connect send-msg]]
    [slacker.converters :refer [string->keyword string->slack-json]]))

(def ^:private publisher (chan))
(def ^:private publication (pub publisher first))
(def ^:private connection (atom nil))

(defn await!
  "Blocks the thread, awaiting the occurrence of the given topic on the event
  channel. This is very handy for awaiting :slacker.client/bot-disconnected in
  the main function, which essentially blocks until the bot dies."
  [topic & [time-out]]
  (let [c (chan)]
    (sub publication topic c)
    (if time-out
      (first (alts!! [c (timeout time-out)]))
      (<!! c))))

(defn- emit!-template
  [return-chan topic args]
  (log/debugf "Emit: topic=[%s], ns=[%s], msg=[%s]" topic *ns* args)
  (go (>! publisher (apply vector topic return-chan args))))

(defn emit!
  "Emits an event to handlers. It will find all handlers registered for the
  topic and call them with the additional arguments if any."
  [topic & args]
  (emit!-template nil topic args)
  nil)

(defn emit-with-feedback!
  "Emits an event to handlers. It will find all handlers registered for the
  topic and call them with the additional arguments if any."
  [topic & args]
  (let [return-chan (chan)]
    (emit!-template return-chan topic args)
    return-chan))

(defmacro with-stacktrace-log
  "Attempts to evaluate body and logs the stacktrace of any thrown throwable
  as they would otherwise be difficult to notice given the asynchronous nature
  of everything.

  Wrap any expression for which error logging is desired in this macro."
  [& body]
  `(try
     ~@body
     (catch Throwable t#
       (->> t#
         print-stack-trace
         with-out-str
         (log/errorf "Error during 'handle' in ns=[%s]:\n%s" *ns*)))))

(defn handle
  "Subscribes an event handler for the given topic. The handler will be called
  whenever an event is emitted for the given topic, and any optional args from
  emit! will be passed as arguments to the handler-fn."
  [topic handler-fn]
  (let [c (chan)]
    (sub publication topic c)
    (go-loop []
      (when-let [[topic return-chan & msg] (<! c)]
        (go (with-stacktrace-log
              (when-let [result (apply handler-fn msg)]
                (when return-chan
                  (>! return-chan result)))))
          (recur)))
    nil))

;; +--------------------------------------------------------------------------+
;; | Websockets                                                               |
;; +--------------------------------------------------------------------------+

(defn connect-bot
  "Retrieves the websocket URL for a given bot token and emits this token in a
  command called ::connect-websocket commanding that a connection be made to
  the URL.

  Any error encountered in communicating with Slack is emitted with the topic
  ::connect-bot-error."
  [token]
  (http/get
    (format "https://slack.com/api/rtm.start?token=%s" token)
    (fn [{:keys [status body error]}]
      (if-let [payload (read-str (or body "{}") :key-fn string->keyword)]
        (if-let [url (:url payload)]
          (emit! ::connect-websocket token url)
          (emit! ::connect-bot-error (:error payload "unknown_error")))
        (emit! ::connect-bot-error
               {:status status
                :error error})))))

(handle ::connect-bot connect-bot)

(defn connect-websocket
  "Connects to the websocket temporarily available at `url` and emits the
  following two events:

  [::websocket-connected url socket] for handlers interested in the socket.
  [::bot-connected token] for handlers interested in the token."
  [token url]
  (let [socket (connect url
                 :on-receive
                 (fn [raw]
                   (emit! ::receive-message
                     (read-str raw :key-fn string->keyword)))
                 :on-error
                 (fn [& args]
                   (log/error "Error in websocket.")
                   (emit! ::websocket-erred args)
                   (emit! ::bot-disconnected))
                 :on-close
                 (fn [& args]
                   (log/warn "Closed websocket.")
                   (emit! ::websocket-closed args)
                   (emit! ::bot-disconnected)))]
    (reset! connection socket)
    (emit! ::websocket-connected url socket)
    (emit! ::bot-connected token)))

(handle ::connect-websocket connect-websocket)

;; +--------------------------------------------------------------------------+
;; | Message handling                                                         |
;; +--------------------------------------------------------------------------+

(defn receive-message
  "Handles the reception of a message by republishing it with a keywordized
  topic matching those described in the Slack API. This makes it easy to only
  subscribe to the kind of events you want, such as :message, :presence_change,
  etc."
  [msg]
  (let [topic (cond (:type msg)     (:type msg)
                    (:reply_to msg) "reply"
                    :else           "unknown")]
    (go (>! publisher [(string->keyword topic) nil msg]))))

(handle ::receive-message receive-message)

(defn send-message
  "Sends a message to the currently open websocket. Takes a channel ID and a
  message in the form of a string."
  [receiver msg]
  (send-msg @connection (string->slack-json receiver msg)))

(handle ::send-message send-message)
