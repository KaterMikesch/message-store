(ns message-store.core
  (:use somnium.congomongo clojure.contrib.lazy-seqs)
  (:import (javax.mail Session Folder Flags)
            (javax.mail.search FlagTerm MessageIDTerm)
            (javax.mail Flags$Flag)
	    (javax.mail.event MessageChangedEvent MessageChangedListener)
	    (javax.mail.internet MimeMessage)
	    (java.io File FileInputStream BufferedInputStream ByteArrayInputStream)
	    (java.util Properties Date)))

;; database configuration
(mongo! :db "message-store" :host "127.0.0.1")
(add-index! :messages [:mid] :unique true)
(add-index! :messages [:reference])
(add-index! :conversations [:messages])
(add-index! :conversations [:tags])

;; data structures
(defstruct message :mid :subject :to :date :from :conversation :reference :flags :mod-date)
(defstruct conversation :messages :tags :mod-date)
(defstruct tag :conversations :name)

;; this should be configurable
(def mimemessages-path "/Volumes/Macintosh HD/Users/axel/Documents/Gina/TransferData")

(defn filenames-in-path [p] (.list (File. p)))

;; Just for testing. Hack.
(def mimemessage-file (File. "/Volumes/Macintosh HD/Users/axel/Documents/Gina/TransferData/Msg00000000079988.gml"))

;(def defaultSession (Session/getDefaultInstance (.setProperty (Properties.) "mail.mime.address.strict" "false")))
     
(defn mimemessage-from-file
  "Returns the MimeMessage object from the given file f. The contents of the file must be in RFC 2822 format. Throws exception otherwise."
  [f]
  (System/setProperty "mail.mime.address.strict" "false")
  (MimeMessage. (Session/getDefaultInstance (System/getProperties)) (BufferedInputStream. (FileInputStream. f))))

(defn message-for-mid
  [mid]
  (fetch-one :messages :where {:mid mid}))

(defn message-referencing-mid
  [mid]
  (if mid
    (fetch-one :messages :where {:reference mid})))

(defn messages-referencing-mid
  [mid]
  (if mid 
    (fetch :messages :where {:reference mid})))

(defn find-conversation 
  "Returns the conversation for a message denoted by its message-id and reference."
  [mid r]
  (let [m (message-for-mid mid)
	c (if-not (nil? m) (fetch-by-id :conversations (m :conversation)))]
    (cond
     (nil? c)
     (let [m (message-for-mid r)]
       (let [c (if-not (nil? m) (fetch-by-id :conversations (m :conversation)))]
	 (cond
	  (nil? c) (let [rm (message-referencing-mid mid)]
		     (if (not (nil? rm)) (fetch-by-id :conversations (rm :conversation))))
	  :else c)))
     :else c)))

(defn conversation-of-message
  [m]
  (if-not (nil? m)
    (fetch-by-id :conversations (m :conversation))))

(defn ensure-conversation-has-message
  [c mid]
  (cond
   (contains? (set (c :messages)) mid) c
   :else (let [new-c (merge c {:messages (conj (c :messages) mid) :mod-date (Date.)})]
	   (update! :conversations {:_id (c :_id)} new-c)
	   new-c)))

(defn merge-conversations
  [destination source]
  (let [mids (source :messages)
	tags (source :tags)]
    ;;(println destination "and" source "need merging")
    ;; add all messages and all tags of source to messages of destination
    (let [all-messages (reduce conj (destination :messages) mids)
	  all-tags (reduce conj (destination :tags) tags)]
      (update! :conversations {:_id (destination :_id)} (merge destination {:messages all-messages :tags tags :mod-date (Date.)}))
      ;;(let [fetched-c (fetch-by-id :conversations (destination :_id))
      ;;saved-msgs (fetched-c :messages)]
      ;;(println "fetched-c" fetched-c "saved-msgs" saved-msgs "expected-msgs" all-messages)


      ;;(println "merged into:" (fetch-by-id :conversations (destination :_id)))
      ;;(assert (= saved-msgs all-messages))))
      )
    ;; change conversation of all messages from source to destination
    (doseq [mid mids]
      (let [m (message-for-mid mid)]
	(update! :messages {:_id (m :_id)} (merge m {:conversation (.toString (destination :_id)) :mod-date (Date.)}))))
    ;; delete source
    (destroy! :conversations source)
    ;; return updated destination
    (fetch-by-id :conversations (destination :_id))))

(defn check-referencing-messages-conversation
  [mid c]
  (doseq [rm (messages-referencing-mid mid)]
    (let [rc (conversation-of-message rm)]
      (if (and (c :_id) (rc :_id))
	(if-not (= (c :_id) (rc :_id))
	  (merge-conversations c rc)))))
  c)

(defn ensure-conversation
  "Ensure a conversation for the message with given message-id and reference exists and contains the message."
  [mid r]
  (let [c (find-conversation mid r)]
    (check-referencing-messages-conversation
     mid
     (ensure-conversation-has-message
      (cond
       (nil? c) (insert! :conversations (struct-map conversation :messages [mid] :tags [] :mod-date (Date.)))
       :else c)
      mid))))

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
  (let [f (first (.getFrom mm))]
    (if-not (nil? f)
      (.toString f))
    nil))

(defn create-message
  "Creates a persistent message struct from given mimemessage."
  [mm]
  (let [mid (.getMessageID mm)
	r (reference-from-mimemessage mm)]
    ;;(println "create-message with message-id: " mid " and reference: " r)
    (insert! :messages
	     (struct-map message
	       :mid (cond
		     (nil? mid) (str "<fakedMsgId" (System/currentTimeMillis) ">")
		     :else mid)
	       :subject (.getSubject mm)
	       :recipients (recipients-from-mimemessage mm)
	       :date (.getSentDate mm)
	       :from (from-from-mimemessage mm)
	       :conversation (.toString ((ensure-conversation mid r) :_id))
	       :reference (if-not (= mid r) r)
	       :flags []
	       :mod-date (Date.)))))

(defn add-message
  "Does nothing if a message with the same message-id is already in the store."
  [mm]
  (let [mid (.getMessageID mm)
	m (message-for-mid mid)]
    (if (nil? m)
      (create-message mm))))

(def counter (let [count (ref 0)] #(dosync (alter count inc))))

(defn import-messages-from-path [p]
  (doseq [filename (filenames-in-path p)]
    (let [count (counter)]
      (if (= 0 (mod count 1000))
	(println count))
      (add-message (mimemessage-from-file (File. (str p "/" filename)))))))
