(ns client-test
  (:require [clojure.core.async :refer [<!!]]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :refer [*logger-factory*]]
            [clojure.tools.logging.impl :refer [disabled-logger-factory]]
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
      ;; Disable logging as it clogs up System.out.
      (binding [*logger-factory* disabled-logger-factory]
        (let [promises (into [] (repeatedly 100 promise))]
          (handle :danger (fn [a])) ; clojure.lang.ArityException
          (dotimes [i 100]
            (handle :danger #(deliver (get promises i) true)))
          (emit! :danger)
          (is (every? true? (map deref promises))))))

    (testing "Emit! should return a channel onto which the result of calling
             the handler with the args is put."
      (handle :add +)
      (is (= 6 (<!! (emit! :add 1 2 3)))))

    (testing "Multiple handlers should result in multiple values being put onto
             the return channel of a call to emit!"
      (handle :arith +)
      (handle :arith *)
      (handle :arith -)
      (let [return (emit! :arith 5 10)
            values (repeatedly 3 #(<!! return))]
        (is (= #{-5 15 50} (apply hash-set values)))))

    (testing "All return channels from multiple calls to emit! should receive a
             result from the handler."
      (handle :add +)
      (let [[c1 c2 c3] [(emit! :add 1 2) (emit! :add 2 3) (emit! :add 3 4)]
            return-1 (<!! c1)
            return-2 (<!! c2)
            return-3 (<!! c3)]
        (is (= #{3 5 7}
               (hash-set return-1 return-2 return-3)))))))
