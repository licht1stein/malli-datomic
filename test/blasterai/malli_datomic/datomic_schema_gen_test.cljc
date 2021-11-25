(ns blasterai.malli-datomic.datomic-schema-gen-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [clojure.data]
            [clojure.pprint])
  (:require [blasterai.malli-datomic.datomic-schema-gen :as sg2]
            [blasterai.malli-datomic.malli-demo-schema :as cs]))


(def exp-dat-schema1
  {::sg2/defined-props-tracker
   {:enough-material?   true
    :user/first-name    true
    :user/id            true
    :e.order/status     true
    :ship-service       true
    :sent-from-llc?     true
    :e/role-set         true
    :order-info         true
    :status             true
    :order-id           true
    :due-date           true
    :user/gender        true
    :user/roles         true
    :comment            true
    :user/telegram-id   true
    :invoice-link       true
    :customer-address   true
    :user/email         true
    :user/middle-name   true
    :e/shipping-service true
    :user/last-name     true
    :material-info      true
    :user/birthday      true}


   ::sg2/datomic-schema-acc
   [{:db/ident :shipping-service/dhl,}
    {:db/ident :shipping-service/fedex,}
    {:db/ident :shipping-service/unknown,}
    {:db/ident :shipping-service/nova-poshta,}

    {:db/ident :order-status/new,}
    {:db/ident :order-status/in-progress,}
    {:db/ident :order-status/lacks-material,}
    {:db/ident :order-status/ready-for-shipping,}
    {:db/ident :order-status/completed,}

    {:db/ident :roles/admin}
    {:db/ident :roles/finance}
    {:db/ident :roles/c-level}
    {:db/ident :roles/manufacturing}
    {:db/ident :roles/shipping}

    {:db/ident :gender/male}
    {:db/ident :gender/female}

    ; user props
    {:db/ident       :user/id,
     :db/valueType   :db.type/long,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/gender,
     :db/valueType   :db.type/ref,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/first-name,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/middle-name,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/last-name,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/birthday,
     :db/valueType   :db.type/instant,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/email,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/telegram-id,
     :db/valueType   :db.type/long,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/roles,
     :db/valueType   :db.type/ref,
     :db/cardinality :db.cardinality/many}

    ; order props
    {:db/ident       :order-id,
     :db/valueType   :db.type/long,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :status,
     :db/doc         "Order status"
     :db/valueType   :db.type/ref,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :customer-address,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :order-info,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :sent-from-llc?,
     :db/valueType   :db.type/boolean,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :invoice-link,
     :db/valueType   :db.type/uri,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :ship-service,
     :db/valueType   :db.type/ref,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :comment,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :due-date,
     :db/valueType   :db.type/instant,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :material-info,
     :db/valueType   :db.type/string,
     :db/cardinality :db.cardinality/one}
    {:db/ident       :enough-material?,
     :db/valueType   :db.type/boolean,
     :db/cardinality :db.cardinality/one}]})

(deftest malli-schemas->datomic-schema-test
  (let [res (sg2/malli-schemas->datomic-schema
              [cs/schema:user
               cs/schema:shipping-order
               cs/schema:manufacturing-order])
        dat-schema (::sg2/datomic-schema-acc res)
        def-props (::sg2/defined-props-tracker res)
        exp-def-props (::sg2/defined-props-tracker exp-dat-schema1)
        exp-dat-schema (::sg2/datomic-schema-acc exp-dat-schema1)]
    (def r1 res)
    (is (= #{1} (set (vals (frequencies dat-schema)))))
    (is (= exp-def-props def-props)
        "actual defined props should equal the expected")
    (is (= exp-dat-schema1 res)
        "actual response should equal expected response")
    (is (= exp-dat-schema dat-schema)
        "datomic schemas should be equal")
    (when (not= exp-dat-schema1 res)
      (println "generated schema issues")
      (clojure.pprint/pprint
        (clojure.data/diff exp-dat-schema dat-schema)))))

(comment
  (t/run-tests 'blasterai.malli-datomic.datomic-schema-gen-test))


