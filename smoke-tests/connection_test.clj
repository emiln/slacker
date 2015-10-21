(ns connection-test
  (:require [clojure.test :refer [deftest is testing]]
            [environ.core :refer [env]]
            [slacker.client :refer [await! emit! handle]]))

(deftest basic-connection
  (testing "About connecting:"

    (testing "A :hello event is received when connecting."
      (let [token (env :slack-bot-token)]
        (emit! :slacker.client/connect-bot token)
        (is (await! :hello 5000)))))) 
