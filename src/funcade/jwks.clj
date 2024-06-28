(ns funcade.jwks
  (:require [jsonista.core :as json]
            [buddy.core.keys :as bk]
            [org.httpkit.client :as http]
            [funcade.tools :as t]
            [yang.scheduler :as scheduler]))

(defonce ^:private
  keyset (atom nil))

(defonce ^:private
  keyset-refresh-scheduler (atom nil))

(defn stop-scheduled-refresh
  "stop the scheduled refresh"
  []
  (some-> keyset-refresh-scheduler deref scheduler/stop))

(defn find-token-key-by-kid
  "Given the token's kid find the key from keyset"
  [kid]
  (some-> @keyset
          (get kid)))

(defn find-current-kids
  "Find all current kids"
  []
  (some-> @keyset keys))

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
  "refresh jwks keyset from auth provider"
  [uri callback]
  (let [current-kids      (-> uri jwks->keys keys) 
        refresh-callback  (or callback
                             (fn [_]
                               (prn "[funcade]: refreshed keyset")))] 
    (refresh-callback current-kids)
    current-kids))

(defn jwks->keyset
  "Generate the keyset and return getter-fn
  also given the refresh interval will schedule a 
  scheduler to refresh keyset for every interval"
  [url {:keys [refresh-interval-ms
               refresh-callback]}]
  (or (when (some? refresh-interval-ms)
        (when (nil? @keyset-refresh-scheduler)
          (->> (scheduler/every refresh-interval-ms
                                (partial refresh-kids url refresh-callback))
               (reset! keyset-refresh-scheduler))))
      (jwks->keys url)))

(defn find-kid [token]
  (some-> token
          t/decode64
          (json/read-value t/mapper)
          :kid))

(defn ^{:deprecated "0.1.25"} find-token-key [jwks token]
  (some-> token
          find-kid  
          jwks))
