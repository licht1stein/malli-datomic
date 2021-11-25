(ns blasterai.malli-datomic.malli-demo-schema
  (:require [malli.core :as m]
            [malli.generator :as mg]))


;; old order status
:e.order/status           [:enum "NEW" "IN PROGRESS" "LACKS MATERIAL" "READY FOR SHIPPING" "COMPLETED"]

;; old shipping service
:e/shipping-service [:enum "DHL" "Fedex" "Unknown" "Nova Poshta"]

(def order-status-options
  [{:option/label "NEW"
    :option/value :order-status/new}
   {:option/label "IN PROGRESS"
    :option/value :order-status/in-progress}
   {:option/label "LACKS MATERIAL"
    :option/value :order-status/lacks-material}
   {:option/label "READY FOR SHIPPING"
    :option/value :order-status/ready-for-shipping}
   {:option/label "COMPLETED"
    :option/value :order-status/completed}])

(def shipping-service-options
  [{:option/label "DHL"
    :option/value :shipping-service/dhl}
   {:option/label "Fedex"
    :option/value :shipping-service/fedex}
   {:option/label "Unknown"
    :option/value :shipping-service/unknown}
   {:option/label "Nova Poshta"
    :option/value :shipping-service/nova-poshta}])


(def MutualRegistry
  ; props
  {:e/blaster-email    [:re #"[a-z]{2}@blaster\.ai"] ; todo change to simple email regex
   :e/sku              [:re #"A\d{4}"]
   :e/price            [:double {:min 10.0 :max 200.0}]
   :e/quantity         [:int {:min 1 :max 15}]
   :e/email            [:re #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$"]
   :e/phone            [:re #"^[\+]?[(]?[0-9]{3}[)]?[-\s\.]?[0-9]{3}[-\s\.]?[0-9]{4,6}$"]
   :e/order-status     [:enum :order.status/complete]

   :e/role             [:enum
                        :roles/admin
                        :roles/finance
                        :roles/c-level
                        :roles/manufacturing
                        :roles/shipping]

   :user/gender        [:enum :gender/male :gender/female]
   :e/role-set         [:set :e/role]
   :e/id               pos-int?
   :e.order/status     (into [:enum] (mapv :option/value order-status-options))
   :e/shipping-service (into [:enum] (mapv :option/value shipping-service-options))
   :e/comment          string?
   :e/due-date         inst?
   :e/link             uri?
   :e/telegram-id      [:and int? [:> 10000000]]

   ;; all you need to send an email from the email service
   :e/email-map
   [:map
    [:email/html string?]
    [:email/subject string?]
    [:email/recipients [:+ :e/email]]]


   ; entities

   :e/user
   [:map
    [:user/id :e/id]
    :user/gender
    [:user/first-name string?]
    [:user/middle-name {:optional true} string?]
    [:user/last-name string?]
    [:user/birthday inst?]

    ; direct enums will throw
    ; [:user/enum2 [:enum "232" "232"]]

    ; :or not supported
    ; [:user/ting [:or int? string?]]

    [:user/email :e/blaster-email]
    [:user/telegram-id :e/telegram-id]
    [:user/roles :e/role-set]]


   :e/user-session
   [:map
    [:session/session-id {:db/unique :db.unique/value} string?]
    [:session/magic-link         string?] ;; probably https://blaster.ai/magic/token-uuid
    [:session/magic-link-token   string?] ;; uuid string
    [:session/link-expires-at    inst?]
    [:session/session-expires-at inst?]
    [:session/created-at         inst?]
    [:session/user-id            pos-int?]
    [:session/user-agent {:optional true} string?]
    [:session/device-id {:optional true} string?]
    [:session/user-ip {:optional true} string?]
    [:session/user {:optional true} map?]] ; allow relaxed dev


   :e/user-session--for-front
   [:map
    {:closed true}
    [:session/session-id {:db/unique :db.unique/value} string?]
    [:session/session-expires-at inst?]
    [:session/created-at         inst?]
    [:session/user-id            pos-int?]
    [:session/user map?]]


   :e/shipping-order
   [:map
    {:table/entity-type :e.type/order
     :table/id-prop     :order-id
     :table/title       "Shipping order"}
    [:order-id
     {:ui/label "Order id"}
     :e/id]
    [:status
     {:description    "Order status"
      :ui/editable?   true
      :ui/labels      ["NEW" "IN PROGRESS" "LACKS MATERIAL" "READY FOR SHIPPING" "COMPLETED"]
      :ui/default-val "NEW"}
     ;; todo? maybe use a map
     :e.order/status]
    [:customer-address string?]
    [:order-info string?]
    [:sent-from-llc? boolean?]
    [:invoice-link :e/link]
    [:ship-service :e/shipping-service]
    [:comment {:ui/editable? true} :e/comment]
    [:due-date :e/due-date]]


   :e/manufacturing-order
   [:map
    {:table/id-prop     :order-id
     :table/entity-type :e.type/order
     :table/title       "Manufacturing order"}

    [:order-id
     {:ui/label "Order id"}
     :e/id]

    [:status {:ui/editable? true} :e.order/status]
    [:order-info string?]
    [:material-info string?]
    [:enough-material? {:optional? true} boolean?]
    [:comment {:ui/editable? true} :e/comment]
    [:due-date :e/due-date]]


   :e/manufacturing-item
   [:map
    [:sku :e/sku]
    [:quantity :e/quantity]
    [:details string?]
    [:order-ids [:vector {:gen/min 1, :gen/max 10} string?]]
    [:comment {:ui/editable? true} :e/comment]]})



(def schema:session
  (m/schema [:schema {:registry MutualRegistry} :e/user-session]))

(def schema:session-frontend
  "frontend variant (what frontend should ever see)"
  (m/schema [:schema {:registry MutualRegistry} :e/user-session--for-front]))

(def session-frontend-allowed-keys
  [:session/session-id :session/session-expires-at :session/created-at :session/user-id :session/user])


(def schema:user
  (m/schema
    [:schema
     {:registry MutualRegistry}
     :e/user]))

(comment
  (mg/generate schema:user)
  (m/validate inst? #inst "2020-02-02")
  (m/validate uri? "https://google.com"))

(def schema:email
  [:schema {:registry MutualRegistry} :e/email])

(def schema:email-map
  [:schema {:registry MutualRegistry} :e/email-map])


(def schema:login-params
  (m/schema
    [:map
     {:registry MutualRegistry}
     [:email :e/blaster-email]]))

(def schema:shipping-order
  (m/schema
    [:schema
     {:registry MutualRegistry}
     :e/shipping-order]))

(def schema:manufacturing-order
  (m/schema
    [:schema
     {:registry MutualRegistry}
     :e/manufacturing-order]))

(def schema:manufacturing-item
  (m/schema
    [:schema
     {:registry MutualRegistry}
     :e/manufacturing-item]))

(comment
  (m/validate [:re #"\d{4}"] "1234567")
  (mg/generate [:re #"\d{4}"])
  (mg/generate uri?)
  (mg/generate schema:shipping-order))

