(ns slacker.lookups
  (:require [clojure.core.memoize :as memo]
            [clojure.data.json :refer [read-str]]
            [org.httpkit.client :as http]
            [slacker
              [client :refer [emit! handle]]
              [converters :refer [string->keyword]]]))

(def token (atom nil))

(defn- refresh-cache
  [cache]
  (when-let [token @token]
    (future
      (memo/memo-clear! cache [token]
      (cache token)))))

;; +--------------------------------------------------------------------------+
;; | Channels                                                                 |
;; +--------------------------------------------------------------------------+

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

(def refresh-channels
  (refresh-cache channels))

;; +--------------------------------------------------------------------------+
;; | Users                                                                    |
;; +--------------------------------------------------------------------------+

(defn- lookup-users
  "Fetches and parses all the users visible from the given token and returns
  them as a sequence of keywordized Clojure maps. This operation performs an
  HTTPS request and is as such very slow. It should never be used directly,
  which is why it is private. Use the memoized `users` instead, which will
  be kept fresh by sensible cache evictions."
  [token]
  (-> (http/get "https://slack.com/api/users.list"
        {:query-params {:token token}})
    (deref)
    (:body)
    (read-str :key-fn string->keyword)
    (:members)))

(def users (memo/memo lookup-users))

(def refresh-users
  (refresh-cache users))
