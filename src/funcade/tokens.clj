(ns funcade.tokens
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clojure.core.async :as a]
            [clojure.set :as set]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [funcade.codec :as codec]
            [funcade.tools :as tools]
            [org.httpkit.client :as http])
  (:import (java.time Instant)))

(defn in-open-interval? [begin end value]
  (< begin value end))

(defn token-in-interval? [{:keys [expires-at]} time-in-seconds]
  (when expires-at
    (< time-in-seconds expires-at)))

(defn renew-token?
  ([percentage params]
   (renew-token? percentage params (.getEpochSecond (Instant/now))))
  ([percentage {:keys [issued-at expires-at]} when]
   (assert (in-open-interval? 0 1 percentage) (str "percentage is not in (0,1) interval: " percentage))
   (let [diff  (- expires-at when)
         delta (Math/abs ^int diff)
         p     (/ delta (Math/abs ^int (- expires-at issued-at)))]
     (or (< delta 60)
         (neg? diff)
         (< p percentage)))))

(defn token-current? [m]
  (token-in-interval? m (.getEpochSecond (Instant/now))))

(defn parse-token-data [{:keys [access-token]}]
  (-> access-token
      (s/split #"\.")
      second
      codec/decode64
      (json/parse-string true)
      (select-keys [:iat :exp])
      (set/rename-keys {:iat :issued-at :exp :expires-at})))

(defn prepare-token [jwt? [token err :as r]]
  (if (or err (not jwt?))
    r
    (let [token' (merge token {:issued-at (.getEpochSecond (Instant/now))} (parse-token-data token))]
      (if (token-current? token')
        [token' nil]
        [nil (ex-info "token has expired" token')]))))

(defn new-token! [{:keys [access-token-url grant-type client-id client-secret
                          username password scope token-headers
                          jwt?]
                   :or   {grant-type    "client_credentials"
                          token-headers {:Content-Type "application/x-www-form-urlencoded"}
                          jwt?          true}}]
  (let [xf (fn [{:keys [status body error]}]
             (if (and (= status 200) (not error))
               [(json/parse-string body csk/->kebab-case-keyword) nil]
               [nil {:status status :body body :error error}]))
        ch (a/promise-chan (comp (map xf)
                                 (map (partial prepare-token jwt?))))]
    (http/request {:url     access-token-url
                   :method  :post
                   :headers (into {} (map (juxt (comp name key) val) token-headers))
                   :body    (tools/params->query-str {:grant_type    grant-type
                                                      :client_id     client-id
                                                      :client_secret client-secret
                                                      :username      username
                                                      :password      password
                                                      :scope         scope})}
                  (partial a/>! ch))
    ch))

(defn schedule-token-renewal [token-key-name token-key should-renew? new-token! stop-ch token-store]
  (log/infof "starting token refresh poll for %s (i.e. token key is %s)" token-key-name token-key)
  (a/go-loop []
    (let [[_ ch] (a/alts! [stop-ch (a/timeout 60000)])]
      (cond
        (= ch stop-ch) (log/info "stopping" token-key-name "token poller...")
        (should-renew?
          (get @token-store token-key)) (let [_ (log/infof "renewing token for %s" token-key)
                                              [token err] (a/<! (new-token!))]
                                          (if err
                                            (log/error "couldn't renew token for" token-key-name err)
                                            (do
                                              (log/info "renewed token:" (dissoc token :access-token))
                                              (swap! token-store (fn [s] (assoc s token-key (tools/timebox token))))))
                                          (recur))
        :else (recur)))))
