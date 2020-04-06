(ns funcade.tools
  (:require [jsonista.core :as json]
            [clojure.string :as s])
  (:import [org.apache.commons.codec.binary Base64]))

(defn auth-header
  ([token]
   (auth-header token "Bearer"))
  ([token prefix]
   {"Authorization" (str prefix " " token)}))

(defn decode64 [xs]
  (-> xs Base64/decodeBase64 String.))

(defn decode-jwt [token]
  (->> (s/split token #"\.")
       (take 2) ;; don't decode the signature (for now)
       (map (comp json/read-value decode64))))

