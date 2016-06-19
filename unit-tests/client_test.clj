(ns client-test
  (:require [clojure.core.async :refer [<!!]]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :refer [*logger-factory*]]
            [clojure.tools.logging.impl :refer [disabled-logger-factory]]
            [slacker.client :refer [emit! emit-with-feedback! handle]]))

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

    (testing "Calling emit! should return nil"
      (is (= nil (emit! :nil "some arg"))))))

(deftest about-emit-with-feedback!
  (testing "Facts about emit-with-feedback!"

    (testing "Emit-with-feedback! should return a channel onto which the result
             of calling the handler with the args is put."
      (handle :add +)
      (is (= 6 (<!! (emit-with-feedback! :add 1 2 3)))))

    (testing "Multiple handlers should result in multiple values being put onto
             the return channel of a call to emit-with-feedback!"
      (handle :arith +)
      (handle :arith *)
      (handle :arith -)
      (let [return (emit-with-feedback! :arith 5 10)
            values (repeatedly 3 #(<!! return))]
        (is (= #{-5 15 50} (apply hash-set values)))))

    (testing "All return channels from multiple calls to emit-with-feedback!
             should receive a result from the handler."
      (handle :add +)
      (let [chan1 (emit-with-feedback! :add 1 2)
            chan2 (emit-with-feedback! :add 2 3)
            chan3 (emit-with-feedback! :add 3 4)
            return-1 (<!! chan1)
            return-2 (<!! chan2)
            return-3 (<!! chan3)]
        (is (= #{3 5 7}
               (hash-set return-1 return-2 return-3)))))))

(deftest about-error-emissions
  (testing "Facts about error emissions"

    (testing "Connect-bot should emit ::connect-bot-failed when it gets no HTTP
             response from Slack."
      (let [error (promise)
            mock (fn [_ f] (f {:error "Failed"}))
            handler (partial deliver error)]
        (with-redefs-fn {#'org.httpkit.client/get mock}
          (fn [& _]
            (handle :slacker.client/connect-bot-error handler)
            (emit! :slacker.client/connect-bot "test")
            (is @error)))))

    (testing "Connect-bot should emit ::connect-bot-failed when it gets a Slack
             error."
      (let [error (promise)
            body "{\"error\":\"failed\"}"
            mock (fn [_ f] (f {:status 200 :body body}))
            handler (partial deliver error)]
        (with-redefs-fn {#'org.httpkit.client/get mock}
          (fn [& _]
            (handle :slacker.client/connect-bot-error handler)
            (emit! :slacker.client/connect-bot "test")
            (is @error)))))))
