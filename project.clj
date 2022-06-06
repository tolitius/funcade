(defproject tolitius/funcade "0.1.18"
  :description "creates, manages and refreshes oauth 2.0 jwt tokens"
  :url "https://github.com/tolitius/funcade"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :aot [funcade.java-api]

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.5.648"]
                 [buddy/buddy-sign "3.4.333"]
                 [buddy/buddy-auth "3.0.323"]
                 [http-kit "2.5.3"]
                 [com.auth0/java-jwt "3.19.2"]
                 [org.clojure/tools.logging "1.2.4"]
                 [funcool/cuerdas "2.2.1"]
                 [metosin/jsonista "0.3.5"]
                 [camel-snake-kebab "0.4.2"]])
