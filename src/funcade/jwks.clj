(ns funcade.jwks
  (:require [jsonista.core :as json]
            [buddy.core.keys :as bk]
            [org.httpkit.client :as http]
            [funcade.tools :as t]))

(defn- group-by-kid [certs]
  (->> (for [{:keys [kid] :as cert} certs]
         [kid (bk/jwk->public-key cert)])
       (into {})))

(defn- parse-jwk-response [{:keys [body] :as response}]
  (try
    (-> (json/read-value body t/mapper)
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
          (json/read-value t/mapper)
          :kid
          jwks))
