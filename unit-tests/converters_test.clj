(ns converters-test
  (:require
    [clojure.data.json :refer [read-str write-str]]
    [clojure.test :refer [deftest is testing]]
    [slacker.converters :refer [string->keyword string->slack-json]]))

(deftest about-string->keyword
  (testing "Facts about string->keyword"
    (testing "Converting underscores to dashes"
      (is (= :an-underscored-json-key
             (string->keyword "an_underscored_json_key"))))
    (testing "Converting any casing to lower-case"
      (is (= :uppercasestring
             (string->keyword "UPPERCASESTRING")))
      (is (= :mixedcasestring
             (string->keyword "MiXedCAsEstRINg"))))
    (testing "Not doing anything special about camelCase"
      (is (= :camelcasestring
             (string->keyword "CamelCaseString"))))))

(deftest about-string->slack-json
  (testing "Facts about string->slack-json"
    (testing "Using default values for type when it is not supplied"
      (is (= {"channel" "C03RGK7FC",
              "id" 1,
              "text" "test",
              "type" "message"}
             (read-str (string->slack-json "C03RGK7FC" "test" :id 1)))))
    (testing "Assigning unique ids for consecutive messages"
      (let [id1 (-> (string->slack-json "chan" "text") (read-str) (get "id"))
            id2 (-> (string->slack-json "chan" "text") (read-str) (get "id"))]
        (is (not= id1 id2))))))
