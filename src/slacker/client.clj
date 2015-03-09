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
  [topic handler-fn]
  (let [c (chan)]
    (sub publication topic c)
    (go-loop []
      (when-let [[topic & msg] (<! c)]
        (apply handler-fn msg)
        (recur)))))

;; Handle ::connection-open events by connecting.
(handle ::connection-open
  (fn [token]
    (emit! ::connection-bind
      (-> (format "https://slack.com/api/rtm.start?token=%s" token)
        (http/get)
        (deref)
        (:body)
        (read-str :key-fn string->keyword)
        (:url)))))

;; Handle ::connection-bind by actually binding the websocket.
(handle ::connection-bind
  (fn [url]
    (let [socket (connect url
                   :on-receive
                   (fn [raw]
                     (emit! ::receive-message
                       (read-str raw :key-fn string->keyword))))]
      (reset! connection socket)
      (emit! ::connection-bound socket))))

;; Handle ::send-message events by sending them to the Slack server.
(handle ::send-message
  (fn [msg]
    (send-msg @connection (string->slack-json msg))))

;; Handle ::receive-message events by publishing them with :type as topic.
(handle ::receive-message
  (fn [msg]
    (let [topic (cond (:type msg)     (:type msg)
                      (:reply_to msg) "reply"
                      :else           "unknown")]
      (go (>! publisher [(string->keyword topic) msg])))))
