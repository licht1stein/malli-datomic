(ns blasterai.malli-datomic.datomic-schema-gen
  "Generate Datomic schema from malli schema
  https://docs.datomic.com/cloud/schema/schema.html
  https://docs.datomic.com/cloud/schema/schema-reference.html
  "
  (:require [malli.core :as m]
            [blasterai.malli-datomic.malli-demo-schema :as cs]
            [blasterai.malli-datomic.spec-utils :as u])
  #?(:clj (:import (clojure.lang PersistentVector))))


(comment
  "Main members are:"
  blasterai.malli-datomic.datomic-schema-gen/malli-spec->datomic-schema
  blasterai.malli-datomic.datomic-schema-gen/malli-map->datomic-schema)


; Value Types  Description  Java Equivalent  Example
; numbers
:db.type/bigdec    ; arbitrary precision decimal  java.math.BigDecimal  1.0M
:db.type/bigint    ; arbitrary precision integer  java.math.BigInteger  7N
:db.type/double    ; 64-bit IEEE 754 floating point number  double  1.0
:db.type/float     ; 32-bit IEEE 754 floating point number  float  1.0
:db.type/long      ; 64 bit two's complement integer  long  42

; simple primitives
:db.type/boolean   ; boolean  boolean  true
:db.type/instant   ; instant in time  java.util.Date  #inst "2017-09-16T11:43:32.450-00:00"
:db.type/keyword   ; namespace + name  N/A  :yellow
:db.type/string    ; Unicode string  java.lang.String  "foo"
:db.type/symbol    ; symbol  N/A  foo
:db.type/uuid      ; 128-bit universally unique identifier  java.util.UUID  #uuid "f40e770e-9ad5-11e7-abc4-cec278b6b50a"
:db.type/uri       ; Uniform Resource Identifier (URI)  java.net.URI  https://www.datomic.com/details.html
:db.type/bytes     ; Value type for small binary data  byte[]  (byte-array (map byte [1 2 3]))

;
:db.type/ref       ; reference to another entity  N/A  42
:db.type/tuple     ; tuples of scalar values  N/A  [42 12 "foo"]


(defn derive-value-type
  "derive :db/valueType from spec-item-schema type"
  [schema]
  (def s1 schema)
  (let [schema-def  (-> schema m/deref m/deref)
        schema-type (-> schema-def m/type)]
    ; case didn't work here
    (cond

      ; numbers
      (#{'int?
         'pos-int?
         'nat-int?
         'number?
         'zero?} schema-type)       :db.type/long
      (#{:bigint} schema-type)      :db.type/bigint
      ; bigint isn't a built-in malli predicate, so it must be defined in your registry first

      (#{'decimal?} schema-type)    :db.type/bigdec
      (#{'double?} schema-type)     :db.type/double
      (#{'float?} schema-type)      :db.type/float

      ; simple primitives
      (#{'boolean?
         'false?
         'true?} schema-type)       :db.type/boolean
      (#{'inst?} schema-type)       :db.type/instant
      (#{'keyword?} schema-type)    :db.type/keyword
      (#{'string? :re} schema-type) :db.type/string
      (#{'symbol?} schema-type)     :db.type/symbol
      (#{'uuid?} schema-type)       :db.type/uuid
      (#{'uri?} schema-type)        :db.type/uri
      (#{'bytes?} schema-type)      :db.type/bytes

      ; things to think about
      (#{:ref
         :set 'set?
         :map 'map?
         :vector 'vector?
         :sequential} schema-type) :db.type/ref
      (#{:tuple} schema-type)      :db.type/tuple

      :else
      (throw (ex-info "Unsupported data type" {:type schema-type}))
      #_:db.type/ref)))


(def datomic-copied-props
  [:db/doc :db/unique :db/isComponent :db/noHistory

   ; tuples https://docs.datomic.com/cloud/schema/schema-reference.html#tuples
   :db/tupleType ; :db.type/string, ...
   :db/tupleTypes ; [:db.type/string, ...]
   :db/tupleAttrs

   ; entity specs https://docs.datomic.com/cloud/schema/schema-reference.html#entity-specs
   :db/ensure ;

   ; entity predicates: https://docs.datomic.com/cloud/schema/schema-reference.html#entity-predicates
   :db.entity/attrs ; required attrs vec [:first-name :last-name]
   :db.entity/preds])

(def schema:order
  [:map
   [:order/id int?]
   [:order/comments [:vector string?]]])


{:db/ident       :order/comments
 :db/valueType   :db.type/tuple
 :db/cardinality :db.cardinality/one
 :db/tupleType   :db.type/string}


(defn simple-seq-type [seq-schema]
  (derive-value-type (u/seq-schema->contents-atomic-predicate seq-schema)))

(comment
  (simple-seq-type (m/schema [:vector int?]))
  (-> (m/children (m/deref (m/deref cs/schema:user)))
      (nth 5) last m/deref u/ref-coll->reffed
      m/type type u/ref-coll->reffed))


(defn enum-kw->ident [kw]
  {:db/ident kw})

(defn prepend-enum-idents
  [{::keys [defined-props-tracker datomic-schema-acc] :as acc}
   enum-name enum-schema]
  (let [enum-members (m/children enum-schema)
        all-kw? (every? keyword? enum-members)
        enum-idents (and all-kw? (mapv enum-kw->ident enum-members))
        new-dat-schema-acc (if all-kw? (into enum-idents datomic-schema-acc))]
    (-> (cond-> acc new-dat-schema-acc (assoc ::datomic-schema-acc new-dat-schema-acc))
        (cond-> all-kw? (assoc-in [::defined-props-tracker enum-name] true))
        (cond-> (not all-kw?) (update-in [::warnings] (comp vec conj)
                                         {:warn/code ::didnt-translate-enum-to-idents
                                          :spec/enum-name enum-name})))))

(assert
  (= {::datomic-schema-acc    [#:db{:ident :e/one} #:db{:ident :e/two}],
      ::defined-props-tracker #:e{:status true}}
     (prepend-enum-idents {} :e/status (m/schema [:enum :e/one :e/two]))))

(assert
  (= {::warnings [{:warn/code ::didnt-translate-enum-to-idents, :spec/enum-name :e/status}]}
     (prepend-enum-idents {} :e/status (m/schema [:enum "one" "two"]))))


(defn add-malli-row-derivations-to-acc
  [{::keys [defined-props-tracker datomic-schema-acc] :as acc}
   [spec-item-id ; keyword
    spec-item-options ; map
    ^malli.core/schema spec-item-schema
    :as schema-entry]]

  (let [ident-name   (or (:db/ident spec-item-options) spec-item-id)
        schema-form  (m/form spec-item-schema)
        copied-attrs (select-keys spec-item-options datomic-copied-props)
        doc          (:description spec-item-options)


        seq-prop?    (u/seq-schema? spec-item-schema) ;; for list like
        ref-prop?    (u/ref-schema? spec-item-schema)
        comp-prop?   (u/composite-schema? spec-item-schema)

        simple-seq-prop? (and seq-prop? (u/simple-seq-schema? spec-item-schema))
        ref-seq-prop? (and seq-prop? (u/is-seq-of-refs? spec-item-schema))
        reffed-schema (or (and ref-seq-prop? (u/ref-coll->reffed spec-item-schema))
                          (and ref-prop? (m/deref spec-item-schema)))
        enum-ref?     (and reffed-schema (= :enum (m/type reffed-schema)))
        map-ref?      (and reffed-schema (= :map (m/type reffed-schema)))
        add-enum?     (and enum-ref? (not (get defined-props-tracker schema-form)))

        ;_ (prn spec-item-schema comp-prop? ref-prop? seq-prop? simple-seq-prop?)
        value-type (or (:db/valueType spec-item-options)
                       (if ref-seq-prop? :db.type/ref)
                       (if map-ref? :db.type/ref)
                       (if enum-ref? :db.type/ref)
                       (if comp-prop? (derive-value-type (u/try-get-atomic spec-item-schema)))
                       (if simple-seq-prop? (simple-seq-type spec-item-schema))
                       (derive-value-type spec-item-schema))
        it-will-be-tuple? simple-seq-prop?

        cardinality (or (:db/cardinality spec-item-options)
                        (if ref-seq-prop? :db.cardinality/many)
                        (if it-will-be-tuple? :db.cardinality/one)
                        :db.cardinality/one)

        simple-prop (cond-> (u/assign
                              {:db/ident       ident-name
                               :db/valueType   value-type
                               :db/cardinality cardinality}
                              copied-attrs)
                            doc (assoc :db/doc doc))]

    (-> acc
        (cond-> add-enum? (prepend-enum-idents schema-form reffed-schema))
        (assoc-in [::defined-props-tracker spec-item-id] true)
        (update ::datomic-schema-acc (comp vec conj) simple-prop))))


(comment
  (m/type (m/deref (last (nth (m/children cs/schema:shipping-order) 5))))
  (= 'boolean? (m/type (m/deref (last (nth (m/children cs/schema:manufacturing-order) 4)))))

  (-> (u/get-from-registry cs/schema:manufacturing-order :e.order/status)
      (m/deref) (m/children) (type))

  (apply add-malli-row-derivations-to-acc a1)
  (-> (last (last a1)) m/deref m/deref)
  (u/simple-seq-schema? (last (last a1)))

  (add-malli-row-derivations-to-acc {}
    (nth (m/children cs/schema:manufacturing-order) 4)))



(defn malli-map->datomic-schema
  "Given a map spec – returns data to build a table
  Walk over schema entries,
  For each entry:
  - return a prop name if it's an atomic prop (1),
  - otherwise recur depending on the property type.
  et-schema – map schema or a m/MapSchema"
  [{::keys [defined-props-tracker datomic-schema-acc] :as acc}
   et-schema]
  ;(def e1 et-schema)
  (assert (u/composite-schema-types (m/type et-schema)) "Expecting a composite schema type")
  (reduce
    (fn [{::keys [defined-props-tracker datomic-schema-acc] :as acc}
         [^keyword spec-item-id
          ^map spec-item-options
          ^malli.core/schema spec-item-schema
          :as malli-map-row]]
      (if (get defined-props-tracker spec-item-id)
        acc
        (add-malli-row-derivations-to-acc acc malli-map-row)))
    acc
    (m/children et-schema)))

(comment
  (malli-map->datomic-schema {} cs/schema:manufacturing-order))



(defn malli-schema->datomic-schema
  "Generates table data from a schema
  Expects a RefSchema or a map schema."
  [{::keys [defined-props-tracker datomic-schema-acc] :as acc}
   schema]
  (comment "maybe use" (satisfies? m/RefSchema (m/schema schema)))
  (let [schema-type (m/type schema)
        _ (assert (#{:schema ::m/schema :map} schema-type) "Expecting a schema")
        schema* (m/schema schema)
        map-schema (m/deref (m/deref schema*))]
    (malli-map->datomic-schema acc map-schema)))


(comment
  "Doc comment"

  ;; m/form unwraps all, returns Clojure data
  (-> (m/form (m/deref (m/deref cs/schema:shipping-order)))
      (nth 2) (nth 2) type)

  ;; -> :map
  (-> (m/deref (m/deref cs/schema:shipping-order)) (m/type))

  ;; m/children to preserve nested specs
  (-> (m/children (m/deref (m/deref cs/schema:shipping-order)))
      (first) (last) m/deref m/form (eval) type)

  (type (m/deref cs/schema:shipping-order))
  (malli-schema->datomic-schema {} cs/schema:shipping-order))


(defn ^{:tag #?(:clj PersistentVector :cljs IVector)}
  malli-schemas->datomic-schema
  "Generate Datomic schema from malli spec
  Input is a vector of malli [:map] schemas that you plan to use in Datomic

  returns a map containing a map of defined props
  and ::datomic-schema-acc – vector with datomic idents"
  [malli-schemas]
  (reduce
    malli-schema->datomic-schema
    {::defined-props-tracker {}
     ::datomic-schema-acc    []}
    malli-schemas))

(comment
  (malli-schemas->datomic-schema
    [cs/schema:user
     cs/schema:session
     cs/schema:shipping-order
     cs/schema:manufacturing-order]))



