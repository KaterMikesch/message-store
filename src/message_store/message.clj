(ns message-store.message
  (:require [clojure.spec :as spec]
            [clojure.java.io :as io]
            [digest.core :as digest]
            [gloss.core :as glc]
            [gloss.io :as gio])
  (:import (javax.mail Session Folder Flags)
           (javax.mail.internet MimeMessage InternetAddress)
           (org.apache.commons.io IOUtils)))

(def schema
  [{:db/ident ::mid
    :db/valueType :db.type/string
    :db/id #db/id[:db.part/db -100021]
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident ::subject
    :db/valueType :db.type/string
    :db/id #db/id[:db.part/db -100022]
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident ::recipients
    :db/valueType :db.type/string
    :db/id #db/id[:db.part/db -100023]
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident ::date
    :db/valueType :db.type/instant
    :db/id #db/id[:db.part/db -100024]
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident ::from
    :db/valueType :db.type/string
    :db/id #db/id[:db.part/db -100025]
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident ::reference
    :db/valueType :db.type/string
    :db/id #db/id[:db.part/db -100026]
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}
   {:db/ident ::flags
    :db/valueType :db.type/string
    :db/id #db/id[:db.part/db -100027]
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}
   {:db/ident ::mod-date
    :db/valueType :db.type/instant
    :db/id #db/id[:db.part/db -100028]
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}])

(defn valid-email-address? [s]
  (try
    (not (nil? (InternetAddress. s)))
    (catch Exception _)))

(defn byte-array? [x]
  (= (type x) (Class/forName "[B")))

(defstruct message ::mid ::subject ::recipients ::date ::from ::reference ::flags ::mod-date)
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

(defn raw-data->mimemessage [d]
  (try
    (let [is (io/input-stream d)]
      (MimeMessage.
       (Session/getDefaultInstance (System/getProperties)) is))
    (catch Exception _)))

(spec/fdef raw-data->mimemessage
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

(glc/defcodec emlx->raw-data-codec
  (glc/repeated :byte
                :prefix (glc/prefix (glc/string :ascii :delimiters ["\n" "\r\n"])
                                    #(Long/parseLong (re-find  #"\d+" %))
                                    str)))

(defn emlx->raw-data [e]
  (try
    (byte-array (gio/decode emlx->raw-data-codec (gio/to-byte-buffer (io/input-stream e)) false))
    (catch Exception _)))

(glc/defcodec emlx->raw-header-data-codec
  [:length (glc/string :ascii :delimiters ["\n" "\r\n"])
   :header (glc/repeated :byte
                 :delimiters ["\n\n" "\r\n\r\n"])])

(defn emlx->raw-header-data [e]
  (try
    (byte-array (nth (gio/decode emlx->raw-header-data-codec (gio/to-byte-buffer (io/input-stream e)) false) 3))
    (catch Exception _)))

(defn filename [m]
  (digest/md5 (::mid m)))
