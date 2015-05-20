(set-env!
  :source-paths #{"src"}
  :dependencies
  '[[adzerk/boot-test "1.0.4" :scope "test"]
    [adzerk/bootlaces "0.1.11" :scope "test"]
    [http-kit "2.1.16"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    [org.clojure/data.json "0.2.5"]
    [org.clojure/tools.logging "0.3.1"]
    [stylefruits/gniazdo "0.3.1"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "1.2.0")

(bootlaces! +version+)

(task-options!
  pom {:project 'emiln/slacker
       :version "1.2.0"
       :description "An enthusiastically asynchronous Slack bot library."
       :url "https://github.com/emiln/slacker"
       :scm {:url "https://github.com/emiln/slacker"}})

(deftask slacker-test
  "Run the unit tests for Slacker in a pod."
  []
  (merge-env! :source-paths #{"unit-tests"})
  (require 'adzerk.boot-test)
  ((resolve 'adzerk.boot-test/test)))
