(ns slacker.lookups
  (:require [clojure.core.memoize :as memo]
            [clojure.data.json :refer [read-str]]
            [org.httpkit.client :as http]
            [slacker
              [client :refer [emit! handle]]
              [converters :refer [string->keyword]]]))

(defn- lookup-channels
  "Fetches and parses all the channels visible from the given token and returns
  them as a sequence of keywordized Clojure maps. This operation performs an
  HTTPS request and is as such very slow. It should never be used directly,
  which is why it is private. Use the memoized `channels` instead, which will
  be kept fresh by sensible cache evictions."
  [token]
  (-> (http/get "https://slack.com/api/channels.list"
        {:query-params {:token token}})
    (deref)
    (:body)
    (read-str :key-fn string->keyword)
    (:channels)))

(def channels (memo/memo lookup-channels))
(def tokens (atom #{}))

(defn refresh-cache
  [& _]
  (doseq [token @tokens]
    (future
      (memo/memo-clear! channels [token])
      (channels token))))

;; +--------------------------------------------------------------------------+
;; | Populate/refresh                                                         |
;; +--------------------------------------------------------------------------+

(handle :channel-created refresh-cache)
(handle :channel-deleted refresh-cache)
(handle :channel-rename refresh-cache)
(handle :slacker.client/bot-connected refresh-cache)

(handle :slacker.client/connect-bot
  (partial swap! tokens conj))
