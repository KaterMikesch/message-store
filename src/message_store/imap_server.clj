(ns message-store.imap-server
  (:use [somnium.congomongo]
	[message-store.core]
	[clojure.contrib.lazy-seqs]
	[clojure.contrib.server-socket]
;;	[clojure.contrib.string :as ccs]
	[clojure.contrib.logging])
  (:import (javax.mail Session Folder Flags)
	   (javax.mail.search FlagTerm MessageIDTerm)
	   (javax.mail Flags$Flag)
	   (javax.mail.event MessageChangedEvent MessageChangedListener)
	   (javax.mail.internet MimeMessage)
	   (java.io File FileInputStream BufferedInputStream ByteArrayInputStream BufferedReader InputStreamReader OutputStreamWriter)
	   (java.util Properties Date)))

(defn imap-server []
  (letfn [(imap [in out]
		(binding [*in* (BufferedReader. (InputStreamReader. in))
			  *out* (OutputStreamWriter. out)]
		  (println "* PREAUTH IMAP4rev1 server logged in")
		  (flush)
		  (loop []
		    (let [input (read-line)]
		      ;; TODO: get the prefix
		      (cond
		       (= (.toUpperCase input) "CAPABILITIES") (println "* CAPABILITY IMAP4rev1"))
		      (error input)
		      (flush))
		    (recur))))]
    (create-server 8143 imap)))