(ns slacker.converters
  (:require
    [clojure.data.json :refer [write-str]]
    [clojure.string :refer [lower-case replace]]))

(def ^:private counter (atom 0))

(defn string->keyword
  "Takes a string like 'my_event' and returns a keyword like :my-event. Handles
  casing and underscore->dash conversion."
  [string]
  (-> (or string "unknown")
    (lower-case)
    (replace #"_" "-")
    (keyword)))

(defn string->slack-json
  "Takes a string and returns a JSON string readable as a message by the Slack
  API. Optionally takes a map containing any of

  :type    - One of the event types defined in the Slack API. Defaults to
             'message'.

  :channel - The channel ID to send to; not the channel name. Defaults to the
             '#srsbsns' channel ID on Dongers Inc.

  :id      - A unique (for this session) integer. Default should be fine."
  [message & args]
  (->> args
    (apply hash-map)
    (merge {:channel "C03RGK7FC"
            :id (swap! counter inc)
            :text message
            :type "message"})
    write-str))
