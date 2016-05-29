(ns message-store.conversation
  (:require [datomic.api :as d]
            [datomic-schema.schema :as s]))

(def schema
  [(s/schema conversation
             (s/fields
              [messages :ref :many]
              [mod-date :instant :indexed]))])

