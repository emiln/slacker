(set-env!
  :source-paths #{"src"}
  :dependencies
  '[[expectations "2.0.9"]
    [http-kit "2.1.16"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    [org.clojure/data.json "0.2.5"]
    [stylefruits/gniazdo "0.3.1"]])

;; Poor solution. Expectations are loaded for absolutely everything, which
;; isn't the intention.
;; TODO: figure how to properly load expectations only in the test task.
(require '[expectations :as exp])
(exp/disable-run-on-shutdown)

;; Much like above. This isn't really a good approach to testing. Help wanted!
(deftask test
  []
  (fn middleware [next-handler]
    (fn handler [fileset]
      (set-env! :source-paths #(conj % "unit-tests"))
      (use 'client-test 'converters-test)
      (exp/run-all-tests)
      identity)))
