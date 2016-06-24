(set-env!
  :source-paths #{"src"}
  :dependencies
  '[[adzerk/boot-test "1.0.4" :scope "test"]
    [adzerk/bootlaces "0.1.11" :scope "test"]
    [http-kit "2.1.19"]
    [org.clojure/core.async "0.2.385"]
    [org.clojure/data.json "0.2.6"]
    [org.clojure/tools.logging "0.3.1"]
    [stylefruits/gniazdo "1.0.0"]
    [environ "1.0.3"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "1.6.0")

(bootlaces! +version+)

(task-options!
  pom {:project 'emiln/slacker
       :version +version+
       :description "An enthusiastically asynchronous Slack bot library."
       :url "https://github.com/emiln/slacker"
       :scm {:url "https://github.com/emiln/slacker"}})

(deftask unit-tests
  "Run the unit tests for Slacker in a pod."
  []
  (merge-env! :source-paths #{"unit-tests"})
  (require 'adzerk.boot-test)
  ((resolve 'adzerk.boot-test/test)))

(deftask smoke-tests
  "Run the smoke tests for Slacker in a pod."
  []
  (merge-env! :source-paths #{"smoke-tests"})
  (require 'adzerk.boot-test)
  ((resolve 'adzerk.boot-test/test)))

(deftask tests
  "Run the full test suite for Slacker in a pod."
  []
  (merge-env! :source-paths #{"unit-tests" "smoke-tests"})
  (require 'adzerk.boot-test)
  ((resolve 'adzerk.boot-test/test)))

