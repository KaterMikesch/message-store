(ns message-store.tag
  (:require [datomic.api :as d]
            [datomic-schema.schema :as s]))

(def schema
  [(s/schema tag
             (s/fields
              [conversation :ref :many]
              [name :string :indexed]))])
