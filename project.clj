(defproject tolitius/funcade "0.1.24"
  :description "creates, manages and refreshes oauth 2.0 jwt tokens"
  :url "https://github.com/tolitius/funcade"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :aot [funcade.java-api]

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "0.4.474"]
                 [buddy/buddy-sign "3.4.333"]
                 [buddy/buddy-auth "3.0.323"]
                 [http-kit "2.5.3"]
                 [com.auth0/java-jwt "3.19.2"]
                 [org.clojure/tools.logging "1.2.4"]
                 [funcool/cuerdas "2021.05.29-0"]
                 [metosin/jsonista "0.3.5"]
                 [com.rpl/specter "1.1.4"]
                 [camel-snake-kebab "0.4.2"]
                 [tolitius/yang "0.1.41"]])
