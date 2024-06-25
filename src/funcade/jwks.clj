(ns funcade.jwks
  (:require [jsonista.core :as json]
            [buddy.core.keys :as bk]
            [org.httpkit.client :as http]
            [funcade.tools :as t]
            [yang.scheduler :as scheduler]))

(defonce ^:private
  keyset (atom nil))

(defn- find-token-key-by-kid
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

(defn- jwks->keys [url]
  (let [response @(http/get url)]
    (if-not (response :error)
      (parse-jwk-response response)
      (-> (str "unable to call jwk url: " url)
          (ex-info response)
          throw))))

(defn refresh-kids
  "Refresh new set of jwks-keyset from auth-provider"
  [uri callback]
  (let [refreshed-keyset (jwks->keys uri)]
    (prn "[funcade]: refreshed keyset")
    (callback (keys refreshed-keyset))
    (keys refreshed-keyset)))

(defn jwks->keys-fn
  "Generate the keyset and return getter-fn
  also given the refresh interval will schedule a 
  scheduler to refresh keyset for every interval"
  [url {:keys [refresh-interval-ms
               refresh-callback]
        :or {refresh-callback identity}}]
  (let [_ (jwks->keys url)]
    (when (some? refresh-interval-ms)
      (scheduler/every refresh-interval-ms
                       (partial refresh-kids url refresh-callback)))
    find-token-key-by-kid))

(defn find-kid [token]
  (some-> token
          t/decode64
          (json/read-value t/mapper)
          :kid))

(defn ^{:deprecated "0.1.25"} find-token-key [jwks token]
  (some-> token
          find-kid  
          jwks))
