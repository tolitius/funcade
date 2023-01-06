(ns funcade.tools
  (:require [jsonista.core :as json]
            [clojure.string :as s]
            [jsonista.core :as j])
  (:import [org.apache.commons.codec.binary Base64]
           [java.time Instant]))

(def mapper (j/object-mapper {:decode-key-fn keyword}))

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

(defn safe-params [config]
  (select-keys config [:access-token-url
                       :grant-type
                       :scope
                       :token-headers
                       :refresh-percent
                       :jwt?]))

(defn fmv
  "apply f to each value v of map m"
  [m f]
  (into {}
        (for [[k v] m]
          [k (f v)])))

(defn fmk
  "apply f to each key k of map m"
  [m f]
  (into {}
        (for [[k v] m]
          [(f k) v])))
