
(ns message-store.tag
  (:require [datomic.api :as d]))

(def schema
  [{:db/ident ::conversation
    :db/valueType :db.type/ref
    :db/id #db/id[:db.part/db -100001]
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident ::name
    :db/valueType :db.type/string
    :db/id #db/id[:db.part/db -100002]
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}])
