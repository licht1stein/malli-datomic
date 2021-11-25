(ns blasterai.malli-datomic.spec-utils-test
  (:require [clojure.test :as t :refer [deftest is testing]])
  (:require [blasterai.malli-datomic.spec-utils :as su]
            [malli.core :as m]))

(def reg1
  {::-t      [:map [::-name string?]]
   ::num     [:and int? [:> 100]]
   ::num-vec [:vector ::num]
   ::map1    [:map ::num ::num-vec]
   ::-v      [:set ::-t]
   ::-vs     [:set string?]})

(def schema:v
  (m/schema [:schema {:registry reg1} ::-v]))

(def schema:vs
  (m/schema [:schema {:registry reg1} ::-vs]))

(def schema:num-vec
  (m/schema [:schema {:registry reg1} ::num-vec]))

(def schema:map1
  (m/schema [:schema {:registry reg1} ::map1]))


(deftest simple-seq-schema?-test
  (is (not (su/simple-seq-schema? schema:v)))
  (is (su/simple-seq-schema? schema:vs)))

(deftest seq-schema->contents-atomic-predicate-test
  (is (= 'int? (su/seq-schema->contents-atomic-predicate schema:num-vec))))

(comment
  (t/run-tests 'blasterai.malli-datomic.spec-utils-test))
