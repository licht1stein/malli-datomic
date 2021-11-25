(ns blasterai.malli-datomic.entity-tools
  "Conform entities pulled out of a Datomic DB
  to their canonical form"
  (:require [malli.core :as m]
            [blasterai.malli-datomic.spec-utils :as u]
            [blasterai.malli-datomic.malli-demo-schema :as demo-schema]))


(defn- update-if [a-map simple-enum-key update-fn]
  (cond-> a-map
          (contains? a-map simple-enum-key)
          (update simple-enum-key update-fn)))

(defn- simplify-prop [m simple-enum-key]
  (update-if m simple-enum-key :db/ident))

(defn- simple-enum?->prop-name [[prop-name opts prop-schema-ref]]
  (if (u/enum-ref? prop-schema-ref)
    prop-name))



;; malli map prop anatomy
[:prop-name {:prop-opt 1} :e/prop-schema-ref]
:e/prop-schema-ref ;; -> references prop schema -> references schema definition

(defn- seq?->standardize-data
  [[prop-name opts prop-schema-ref :as prop-def]]
  (if (u/seq-schema? prop-schema-ref)
    (let [reffed-schema (u/ref-coll->reffed prop-schema-ref)
          enum? (u/enum-ref? reffed-schema)
          seq-type (u/coll-type-fn prop-schema-ref)]
      (cond-> {::prop         prop-name
               ::type-conform seq-type}
              enum? (assoc ::map-fn :db/ident)))))

(defn- standardize-coll
  [a-map {::keys [prop type-conform map-fn] :as coll-standard-spec}]
  (cond-> a-map
          map-fn (update-if prop #(map map-fn %))
          type-conform (update-if prop type-conform)))


;; API

(defn to-standard-form
  "convert datomic pull result"
  [malli-schema]
  (let [props-defs (m/children (m/deref-all malli-schema))
        simple-enums (not-empty (keep simple-enum?->prop-name props-defs))
        coll-schemas (not-empty (keep seq?->standardize-data props-defs))]
    (fn [datomic-entity]
      (let [v1 (if simple-enums
                 (reduce simplify-prop datomic-entity simple-enums)
                 datomic-entity)]
        (if coll-schemas
          (reduce standardize-coll v1 coll-schemas)
          v1)))))

(assert
  (= #:user{:roles #{:roles/admin :roles/finance}}
     ((to-standard-form demo-schema/schema:user)
      {:user/roles [{:db/ident :roles/admin} {:db/ident :roles/finance}]})))

(assert
  (= #:user{:gender :gender/female}
     ((to-standard-form demo-schema/schema:user)
      {:user/gender {:db/ident :gender/female}})))
