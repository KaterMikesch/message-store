(ns message-store.test.core
  (:use somnium.congomongo clojure.contrib.lazy-seqs)
  (:use [message-store.core] :reload)
  (:use [clojure.test])
  (:import (javax.mail Session Folder Flags)
	   (javax.mail.search FlagTerm MessageIDTerm)
	   (javax.mail Flags$Flag)
	   (javax.mail.event MessageChangedEvent MessageChangedListener)
	   (javax.mail.internet MimeMessage)
	   (java.io File FileInputStream BufferedInputStream ByteArrayInputStream)
	   (java.util Properties)))

;; tests

(def test-db-host "127.0.0.1")
(def test-db "messagestoretestdb")
(defn setup! [] (mongo! :db test-db :host test-db-host))
(defn teardown! []
  (drop-database! test-db))

(defmacro with-test-mongo [& body]
  `(do
     (setup!)
     ~@body
     (teardown!)))

(def testmessage-a-source "From: Test A <test.a@test.org>
Content-Type: text/plain
Subject: TestA
Date: Sat, 25 Sep 2010 02:52:54 +0200
Message-Id: <testa@test.org>
To: axel@test.org
Mime-Version: 1.0


A small test.
")

(def testmessage-b-source "From: Test B <test.b@test.org>
Content-Type: text/plain
Subject: TestB
Date: Sat, 26 Sep 2010 02:52:54 +0200
Message-Id: <testb@test.org>
In-Reply-To: <testa@test.org>
To:  Test A <test.a@test.org>
Mime-Version: 1.0


B small test.
")

(def testmessage-c-source "From: Test C <test.c@test.org>
Content-Type: text/plain
Subject: TestC
Date: Sat, 27 Sep 2010 02:52:54 +0200
Message-Id: <testc@test.org>
In-Reply-To: <testa@test.org>
To:  Test A <test.a@test.org>
Mime-Version: 1.0


C small test.
")

(defn mimemessage-from-string
  [s]
  (MimeMessage. (Session/getDefaultInstance (System/getProperties)) (ByteArrayInputStream. (.getBytes s "UTF-8"))))

(defn testmessagea []
  (mimemessage-from-string testmessage-a-source))

(defn testmessageb []
  (mimemessage-from-string testmessage-b-source))

(defn testmessagec []
  (mimemessage-from-string testmessage-c-source))

(deftest messages-in-conversations-variant1
  (with-test-mongo
    (let [a (add-message (testmessagea))
	  b (add-message (testmessageb))
	  c (add-message (testmessagec))
	  conversation (conversation-of-message a)]
      (is a)
      (is b)
      (is c)
      (is conversation)
      (is (a :conversation))
      (is (= (a :conversation) (b :conversation)))
      (is (= (a :conversation) (c :conversation))))))

(deftest messages-in-conversations-variant2
  (with-test-mongo
    (let [bmid ((add-message (testmessageb)) :mid)
	  amid ((add-message (testmessagea)) :mid)
	  cmid ((add-message (testmessagec)) :mid)
	  a (message-for-mid amid)
	  b (message-for-mid bmid)
	  c (message-for-mid cmid)
	  conversation (conversation-of-message a)]
      (is a)
      (is b)
      (is c)
      (is conversation)
      (is (a :conversation))
      (is (= (a :conversation) (b :conversation)))
      (is (= (a :conversation) (c :conversation))))))

(deftest messages-in-conversations-variant3
  (with-test-mongo
    (let [bmid ((add-message (testmessageb)) :mid)
	  cmid ((add-message (testmessagec)) :mid)
	  amid ((add-message (testmessagea)) :mid)
	  a (message-for-mid amid)
	  b (message-for-mid bmid)
	  c (message-for-mid cmid)
	  conversation (conversation-of-message a)]
      (is a)
      (is b)
      (is c)
      (is conversation)
      (is (a :conversation))
      (is (= (a :conversation) (b :conversation)))
      (is (= (a :conversation) (c :conversation))))))

