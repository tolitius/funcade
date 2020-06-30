(ns funcade.tools
  (:require [jsonista.core :as json]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [buddy.core.keys :as keys]
            [org.httpkit.client :as http]
            [jsonista.core :as j])
  (:import [org.apache.commons.codec.binary Base64]
           [java.time Instant]))

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

(defn value? [v]
  (or (number? v)
      (seq v)))

(defn value [v]
  (when (value? v)
    v))

(defn ts->seconds [ts]
  (some->> ts
           str                    ;; some return string, some return long
           (take 10)              ;; some return in ms, some in seconds
           (apply str)
           Long/valueOf))

(defn to-seconds [vs]
  (let [now (.toEpochMilli (Instant/now))]
    (-> (or (first (filter value vs)) ;; if any has value take that
            now)                      ;; otherwise take "now"
        ts->seconds)))

(defn timebox

  "some systems return issued, some issued-at, some iat, etc.
   some do not return expires/expires-at/exp
   some return ts is seconds, others in ms

   timebox picks values if provided,
   compliments with values that are not provided
   and assocs issued-at and expires-at in seconds"

  [{:keys [issued issued-at iat expires expires-at exp] :as t}]
  (let [issued-at (to-seconds [iat issued issued-at])
        expires-at (to-seconds [exp expires expires-at (+ issued-at 3600)])] ;; 1 hour in case expires-at is missing
    (-> t
        (assoc :issued-at issued-at)
        (assoc :expires-at expires-at))))
