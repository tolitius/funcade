(ns funcade.jwks
  (:require [jsonista.core :as json]
            [buddy.core.keys :as bk]
            [org.httpkit.client :as http]
            [jsonista.core :as j]
            [funcade.tools :as t]))

(defn- group-by-kid [certs]
 (->> (for [{:keys [kid] :as cert} certs]
        [kid (bk/jwk->public-key cert)])
      (into {})))

(defn- parse-jwk-response [{:keys [body] :as response}]
  (try
    (-> (j/read-value body t/mapper)
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

(declare refresh-kids
         find-keyset
         current-kids)

(defn jwks->keys-fn
  "Generate the keyset and return getter-fn"
  [url]
  (let [keyset    (jwks->keys url)]
    (alter-var-root (var refresh-kids) (fn [existing-mapping]
                                         (with-meta
                                           ;; make sure don't alter the root more than once if already
                                           ;; set
                                           (if (-> existing-mapping meta :plugged? true?)
                                             existing-mapping
                                             (fn []
                                               (jwks->keys-fn url)
                                               (prn "[funcade]: done refreshing keyset")))
                                           {:plugged? true})))
    (alter-var-root (var current-kids) (fn [_]
                                         (with-meta
                                           (some-> keyset keys)
                                           (meta (var current-kids)))))
    (alter-var-root (var find-keyset) (fn [_]
                                        (with-meta
                                          (fn [& kids]
                                            (when (seq kids)
                                              (get-in keyset kids))) 
                                          (meta (var find-keyset)))))
    find-keyset))

(defn find-kid [token]
  (some-> token
          t/decode64
          (json/read-value t/mapper)
          :kid))

(defn find-token-key [jwks token]
  (some-> token
          find-kid  
          jwks))
