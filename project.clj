(defproject tolitius/funcade "0.1.4"
  :description "creates, manages and refreshes oauth 2.0 jwt tokens"
  :url "https://github.com/tolitius/funcade"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "0.4.474"]
                 [http-kit "2.4.0-alpha6"]
                 [com.auth0/java-jwt "3.8.1"]
                 [org.clojure/tools.logging "0.4.1"]
                 [funcool/cuerdas "2.0.5"]
                 [metosin/jsonista "0.1.1"]
                 [com.rpl/specter "1.1.1"]
                 [camel-snake-kebab "0.4.0"]])
