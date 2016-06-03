(ns message-store.conversation
  (:require [datomic.api :as d]))

(def schema
   [{:db/ident ::messages
    :db/valueType :db.type/ref
    :db/id #db/id[:db.part/db -100011]
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident ::mod-date
    :db/valueType :db.type/instant
    :db/id #db/id[:db.part/db -100012]
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}])
