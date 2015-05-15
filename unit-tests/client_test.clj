(ns client-test
  (:require [clojure.test :refer [deftest is testing]]
            [slacker.client :refer [emit! handle]]))

(deftest about-emit!
  (testing "Facts about emit!"
    (testing "Calling emit! should trigger a handler for the topic"
      (let [result (promise)
            handler (handle :test #(deliver result true))]
        (emit! :test)
        (is @result)))

    (testing "Calling emit! should trigger multiple handlers for the topic"
      (let [results (repeatedly 3 promise)]
        (doseq [result results]
          (handle :topic #(deliver result true)))
        (emit! :topic)
        (is (every? true? (map deref results)))))

    (testing "Calling emit should pass additional arguments to the handler"
      (let [result (promise)
            object (Object.)]
        (handle :with-arguments
          (fn [a b c] (deliver result [a b c])))
        (emit! :with-arguments "string" 12345 object)
        (is (= ["string" 12345 object] @result))))

    (testing "One failing handler should not kill other handlers"
      (let [promises (into [] (repeatedly 100 promise))]
        (handle :danger (fn [a])) ; clojure.lang.ArityException
        (dotimes [i 100]
          (handle :danger #(deliver (get promises i) true)))
        (emit! :danger)
        (is (every? true? (map deref promises)))))))
