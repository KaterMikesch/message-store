(defproject message-store "1.0.0-SNAPSHOT"
  :description "FIXME: write"
  :repositories ["download.java.net" "http://download.java.net/maven/2/"]
  :dependencies [[org.clojure/clojure "1.2.0"]
                   [org.clojure/clojure-contrib "1.2.0"]
                   [javax.mail/mail "1.4.4-SNAPSHOT"]
                   [congomongo "0.1.3-SNAPSHOT"]]
  :dev-dependencies [[swank-clojure "1.2.1"]
                     [lein-clojars "0.5.0-SNAPSHOT"]]
  :jvm-opts ["-Xmx2G" "-server"])

