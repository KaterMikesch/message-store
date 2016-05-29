(ns message-store.message
  (:require [datomic.api :as d]
            [datomic-schema.schema :as s]
            [clojure.spec :as spec]
            [clojure.java.io :as io]
            [digest.core :as digest])
  (:import (javax.mail Session Folder Flags)
           (javax.mail.internet MimeMessage InternetAddress)
           (org.apache.commons.io IOUtils)))

(def schema
  [(s/schema message
             (s/fields
              [mid :string :indexed]
              [subject :string :indexed]
              [recipients :string :many :indexed]
              [date :instant :indexed]
              [from :string :indexed]
              [reference :string :indexed]
              [flags :string :many :indexed]
              [mod-date :instant :indexed]))])

(defn valid-email-address? [s]
  (try
    (not (nil? (InternetAddress. s)))
    (catch Exception _)))

(defn byte-array? [x]
  (= (type x) (Class/forName "[B")))

(defstruct message [::mid ::subject ::recipients ::date ::from ::reference ::flags ::mod-date])
(spec/def ::mid string?)
(spec/def ::subject string?)
(spec/def ::recipients (spec/cat :address (spec/+ valid-email-address?)))
(spec/def ::date #(instance? java.util.Date %))
(spec/def ::from valid-email-address?)
(spec/def ::reference (spec/nilable string?))
(spec/def ::flags (spec/cat :flag (spec/* string?)))
(spec/def ::mod-date #(instance? java.util.Date %))
(spec/def ::message (spec/keys :req [::mid ::subject ::recipients ::date ::from ::reference ::flags ::mod-date]))

(System/setProperty "mail.mime.address.strict" "false")

(defn raw-data->MimeMessage [d]
  (try
    (let [is (io/input-stream d)]
      (MimeMessage.
       (Session/getDefaultInstance (System/getProperties)) is))
    (catch Exception _)))

(spec/fdef raw-data->MimeMessage
           :args (spec/cat :d (spec/alt :file-path string?
                                        :byte-array byte-array?))
           :ret (spec/nilable #(instance? MimeMessage %)))

(defn reference-from-mimemessage
  [mm]
  (let [in-reply-to (last (.getHeader mm "In-Reply-To"))]
    (cond
     (not (nil? in-reply-to)) in-reply-to
     :else (let [references (last (.getHeader mm "References"))]
	     (if (not (nil? references)) (last (.split references "\\s+")))))))

(defn recipients-from-mimemessage
  [mm]
  (map #(.toString %) (.getAllRecipients mm)))

(defn from-from-mimemessage
  [mm]
  (if-let [f (first (.getFrom mm))]
    (if-let [personal (.getPersonal f)]
      (str personal " <" (.getAddress f) ">")
      (.getAddress f))))

(defn mimemessage->message [mm]
  (let [mid (.getMessageID mm)
	r (reference-from-mimemessage mm)]
    (struct-map message
                ::mid (if (nil? mid)
                       (str "<fakedMsgId" (str (java.util.UUID/randomUUID)) ">")
                       mid)
                ::subject (.getSubject mm)
                ::recipients (recipients-from-mimemessage mm)
                ::date (.getSentDate mm)
                ::from (from-from-mimemessage mm)
                ::reference (if-not (= mid r) r)
                ::flags []
                ::mod-date (java.util.Date.))))

(defn filename [m]
  (digest/md5 (::mid m)))
