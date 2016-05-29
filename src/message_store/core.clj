(ns message-store.core
  (:require [datomic.api :as d]
            [datomic-schema.schema :as s]
            [message-store.tag :as tag]
            [message-store.conversation :as con]
            [message-store.message :as msg]
            [clojure.pprint :as pp]))

(def db-uri "datomic:mem://message-store")

(defn db-connection []
  (if (d/create-database db-uri)
    (let [c (d/connect db-uri)
          s (concat (s/generate-schema tag/schema)
                    (s/generate-schema con/schema)
                    (s/generate-schema msg/schema))]
      (pp/pprint s)
      (d/transact c s)
      c)
    (d/connect db-uri)))

(def conn (db-connection))
