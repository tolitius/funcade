(ns funcade.tools
  (:require [jsonista.core :as json]
            [clojure.string :as s]
            [buddy.core.keys :as keys]
            [org.httpkit.client :as http]
            [jsonista.core :as j])
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

(def mapper (j/object-mapper {:decode-key-fn keyword}))

(defn- parse-jwk-response [{:keys [body] :as response}]
  (try
    (-> (j/read-value body mapper)
        :keys
        first
        keys/jwk->public-key)
    (catch Exception ex
      (-> "unable to retrieve key from jwk response"
          (ex-info {:response response} ex)
          throw))))

(defn jwks->pubkey [url]
  (let [response @(http/get url)]
    (if-not (response :error)
      (parse-jwk-response response)
      (-> (str "unable to call jwk url: " url)
          (ex-info response)
          throw))))
