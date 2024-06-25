(ns funcade.jwks
  (:require [jsonista.core :as json]
            [buddy.core.keys :as bk]
            [org.httpkit.client :as http]
            [funcade.tools :as t]))

(defonce ^:private
  keyset (atom nil))

(defonce ^:private
  uri (atom nil))

(defn- find-key-by-kid
  "Given the token's kid find the key from keyset"
  [& kids]
  (when (and (some? @keyset)
           (seq kids))
    (get-in @keyset kids)))

(defn find-current-kids
  "Find all current kids"
  []
  (when-let [keyset-value @keyset]
    (keys keyset-value)))

(defn- group-by-kid [certs]
 (->> (for [{:keys [kid] :as cert} certs]
        [kid (bk/jwk->public-key cert)])
      (into {})))

(defn- parse-jwk-response [{:keys [body] :as response}]
  (try
    (->> (json/read-value body t/mapper)
         :keys
         group-by-kid
         (reset! keyset))
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

(defn refresh-kids
  "Refresh new set of jwks-keyset from auth-provider"
  []
  (if (some? @uri)
    (let [refreshed-keyset (jwks->keys @uri)]
      (prn "[funcade]: refreshed keyset")
      (keys refreshed-keyset))
    (-> "[funcade] : error auth-uri not found"
        (ex-info {:uri @uri})
        throw)))

(defn jwks->keys-fn
  "Generate the keyset and return getter-fn"
  [url]
  (let [_ (->> (reset! uri url)
               jwks->keys)]
    find-key-by-kid))

(defn find-kid [token]
  (some-> token
          t/decode64
          (json/read-value t/mapper)
          :kid))

(defn find-token-key [jwks token]
  (some-> token
          find-kid  
          jwks))
