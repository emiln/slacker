(ns converters-test
  (:require
    [clojure.data.json :refer [read-str write-str]]
    [expectations :refer [expect]]
    [slacker.converters :refer [string->keyword string->slack-json]]))

;; +--------------------------------------------------------------------------+
;; | Facts about string->keyword:                                             |
;; +--------------------------------------------------------------------------+

;; It should convert underscores to dashes.
(expect :an-underscored-json-key
  (string->keyword "an_underscored_json_key"))

;; It should convert any casing to lower-case.
(expect :uppercasestring
  (string->keyword "UPPERCASESTRING"))
(expect :mixedcasestring
  (string->keyword "MiXedCAsEstRINg"))

;; It is not supposed to do anything special about camel case.
(expect :camelcasestring
  (string->keyword "CamelCaseString"))

;; +--------------------------------------------------------------------------+
;; | Facts about string->slack-json:                                          |
;; +--------------------------------------------------------------------------+

;; It should use its default values for channel and type when they are
;; not supplied as arguments. Note that "id" can't be expected to have a
;; default value as its value varies.
(expect {"channel" "C03RGK7FC",
         "id" 1,
         "text" "test",
         "type" "message"}
  (read-str (string->slack-json "test" :id 1)))

;; It should assign unique ids for two consecutive messages.
(let [id1 (-> "test" (string->slack-json) (read-str) (get "id"))
      id2 (-> "test" (string->slack-json) (read-str) (get "id"))]
  (expect (not= id1 id2)))
