(ns funcade.jwks
  (:require [buddy.core.keys :as bk]
            [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [funcade.tools :as t]
            [org.httpkit.client :as http]))

(defn- group-by-kid [certs]
 (->> (for [{:keys [kid] :as cert} certs]
        [kid (bk/jwk->public-key cert)])
      (into {})))

(defn- parse-jwk-response [{:keys [body] :as response}]
  (try
    (-> (json/parse-string body csk/->kebab-case-keyword)
        :keys
        group-by-kid)
    (catch Exception ex
      (-> "unable to retrieve key from jwk response"
          (ex-info {:response response} ex)
          throw))))

(defn jwks->keys [url]
  (let [response @(http/get url)]
    (if-not (response :error)
      (parse-jwk-response response)
      (-> (str "unable to call jwk url: " url)
          (ex-info response)
          throw))))

(defn find-token-key [jwks token]
  (some-> token
          t/decode64
          (json/parse-string csk/->kebab-case-keyword)
          :kid
          jwks))
