(ns client-test
  (:require [expectations :refer [expect]]
            [slacker.client :refer [emit! handle]]))

;; Calling emit should trigger a handler for the given topic.
(let [result (promise)
      handler (handle :test #(deliver result true))]
  (emit! :test)
  (expect true @result))

;; Calling emit should trigger multiple handlers for the given topic.
(let [[result1 result2 result3] (repeatedly 3 promise)]
  (handle :topic #(deliver result1 true))
  (handle :topic #(deliver result2 true))
  (handle :topic #(deliver result3 true))
  (emit! :topic)
  (expect true (and @result1 @result2 @result3)))
