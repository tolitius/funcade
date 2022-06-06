(ns funcade.tools
  (:require [clojure.string :as s]
            [funcade.codec :as codec]
            [cheshire.core :as json])
  (:import (java.time Instant)))

(defn auth-header
  ([token]
   (auth-header token "Bearer"))
  ([token prefix]
   {"Authorization" (str prefix " " token)}))

(defn decode-jwt [token]
  (->> (s/split token #"\.")
       (take 2) ;; don't decode the signature (for now)
       (map (comp json/parse-string codec/decode64))))

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

(defn params->query-str
  "Convert a map of stuff to a query-string, e.g.:
  (params->query-str {:foo 'abc'
                      :bar 123
                      :baz false})
  => '&foo=abc&bar=123&baz=false"
  [params]
  {:pre  [(map? params)]
   :post [(string? %)]}
  (->> params
       (map (comp (partial apply str)
                  (juxt (comp name key)
                        (constantly "=")
                        val)))
       (interpose "&")
       (apply str)))

(defn fmk
  "apply f to each key k of map m"
  [m f]
  (into {}
        (for [[k v] m]
          [(f k) v])))
