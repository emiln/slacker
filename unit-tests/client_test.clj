(ns client-test
  (:require [expectations :refer [expect]]
            [slacker.client :refer [emit! handle))

;; Calling emit should trigger a handle for the given topic.
(let [result (promise)
      handler (handle :test #(deliver result true))]
  (emit! :test)
  (expect true @result))
