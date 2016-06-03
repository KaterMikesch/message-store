(ns message-store.core
  (:require [datomic.api :as d]
            [clojure.string :as str]
            [message-store.tag :as tag]
            [message-store.conversation :as con]
            [message-store.message :as msg]
            [clojure.pprint :as pp]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io])
  (:import [java.io File]))

(def db-uri "datomic:mem://message-store")
(def messages-dir (fs/temp-dir "message-store"))

(defn db-connection []
  (if (d/create-database db-uri)
    (let [c (d/connect db-uri)
          s (concat tag/schema
                    con/schema
                    msg/schema)]
      (pp/pprint s)
      (d/transact c s)
      c)
    (d/connect db-uri)))

(def conn (db-connection))

(defn headers-only-message-from-file [^File f]
  (if (str/ends-with? (str/lower-case f) ".emlx")
    (msg/mimemessage->message (msg/raw-data->mimemessage (msg/emlx->raw-header-data f)))))

(defn add-message-from-file [^File f]
  (let [m (headers-only-message-from-file f)
        dest-path (str (.getPath messages-dir)
                       (File/separatorChar)
                       (msg/filename m)
                       (fs/extension f))]
    (println m)
    (fs/copy f dest-path)))

(defn add-all-emlx-from-dir [^File dir]
  (doseq [f (file-seq dir)]
    (if-not (.isDirectory f)
      (add-message-from-file f))))
