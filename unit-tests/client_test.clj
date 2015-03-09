(ns client-test
  (:require [expectations :refer [expect]]
            [slacker.client :refer [emit! handle]]))

;; Calling emit should trigger a handler for the given topic.
(let [result (promise)
      handler (handle :test #(deliver result true))]
  (emit! :test)
  (expect true @result))

;; Calling emit should trigger multiple handlers for the given topic.
(let [results (repeatedly 3 promise)]
  (doseq [result results]
    (handle :topic #(deliver result true)))
  (emit! :topic)
  (expect true (every? true? (map deref results))))

;; Calling emit should correctly pass additional arguments to the handler.
(let [result (promise)
      object (Object.)]
  (handle :with-arguments
    (fn [a b c] (deliver result [a b c])))
  (emit! :with-arguments "string" 12345 object)
  (expect ["string" 12345 object] @result))
