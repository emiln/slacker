(ns client-test
  (:require [expectations :refer [expect]]
            [slacker.client :refer [emit! handle]]))

;;; Calling emit should trigger a handler for the given topic.

;; Arrange.
(let [result (promise)
      handler (handle :test #(deliver result true))]
  ;; Act.
  (emit! :test)
  ;; Assert.
  (expect true @result))

;;; Calling emit should trigger multiple handlers for the given topic.

;; Arrange
(let [results (repeatedly 3 promise)]
  (doseq [result results]
    (handle :topic #(deliver result true)))
  ;; Act.
  (emit! :topic)
  ;; Assert.
  (expect true (every? true? (map deref results))))

;;; Calling emit should correctly pass additional arguments to the handler.

;; Arrange.
(let [result (promise)
      object (Object.)]
  (handle :with-arguments
    (fn [a b c] (deliver result [a b c])))
  ;; Act.
  (emit! :with-arguments "string" 12345 object)
  ;; Assert.
  (expect ["string" 12345 object] @result))
