(ns slacker.client
  "A simple Slack bot following an emit/handle flow. Read more about it in the
  README."
  (:require
    [clojure.core.async :refer [<! >! chan go go-loop pub sub]]
    [clojure.data.json :refer [read-str]]
    [clojure.string :refer [lower-case]]
    [clojure.tools.logging :as log]
    [org.httpkit.client :as http]
    [gniazdo.core :refer [connect send-msg]]
    [slacker.converters :refer [string->keyword string->slack-json]]))

(def ^:private publisher (chan))
(def ^:private publication (pub publisher first))
(def ^:private connection (atom nil))

(defn- emit!-template
  [return-chan topic args]
  (log/debugf "Emit: topic=[%s], ns=[%s], msg=[%s]" topic *ns* args)
  (go (>! publisher (apply vector topic return-chan args))))

(defn emit!
  "Emits an event to handlers. It will find all handlers registered for the
  topic and call them with the additional arguments if any."
  [topic & args]
  (emit!-template nil topic args))

(defn emit-with-feedback!
  "Emits an event to handlers. It will find all handlers registered for the
  topic and call them with the additional arguments if any."
  [topic & args]
  (let [return-chan (chan)]
    (emit!-template return-chan topic args)
    return-chan))

(defn handle
  "Subscribes an event handler for the given topic. The handler will be called
  whenever an event is emitted for the given topic, and any optional args from
  emit! will be passed as arguments to the handler-fn."
  ([topic docstring handler-fn]
   (let [c (chan)]
     (sub publication topic c)
     (go-loop []
       (when-let [[topic return-chan & msg] (<! c)]
         (log/debugf "Handle: topic=[%s], ns=[%s], msg=[%s]" topic *ns* msg)
         (go (try
               (when-let [result (apply handler-fn msg)]
                 (when return-chan
                   (>! return-chan result)))
               (catch Throwable t
                 (->> t
                   clojure.stacktrace/print-stack-trace
                   with-out-str
                   (log/errorf "Error in ns=[%s], handler=[%s]:\n%s"
                               *ns* handler-fn)))))
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
                       (read-str raw :key-fn string->keyword)))
                   :on-error
                   (fn [& args]
                     (log/error "Error in websocket.")
                     (emit! ::websocket-errored args))
                   :on-close
                   (fn [& args]
                     (log/warn "Closed websocket.")
                     (emit! ::websocket-closed args)))]
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
  "Sends a message to the currently open websocket. Takes a channel ID and a
  message in the form of a string."
  (fn [receiver msg]
    (send-msg @connection (string->slack-json receiver msg))))
