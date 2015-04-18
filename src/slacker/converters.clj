(ns slacker.converters
  (:require
    [clojure.data.json :refer [write-str]]
    [clojure.string :as s]))

(def ^:private counter (atom 0))

(defn string->keyword
  "Takes a string like 'my_event' and returns a keyword like :my-event. Handles
  casing and underscore->dash conversion."
  [string]
  (-> (or string "unknown")
    (s/lower-case)
    (s/replace #"_" "-")
    (keyword)))

(defn string->slack-json
  "Takes a string and returns a JSON string readable as a message by the Slack
  API. Optionally takes a map containing any of

  :type    - One of the event types defined in the Slack API. Defaults to
             'message'.

  :channel - The channel ID to send to; not the channel name.

  :id      - A unique (for this session) integer. Default should be fine."
  [channel message & args]
  (->> args
    (apply hash-map)
    (merge {:channel channel
            :id (swap! counter inc)
            :text message
            :type "message"})
    write-str))
