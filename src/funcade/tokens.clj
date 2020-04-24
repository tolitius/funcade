(ns funcade.tokens
  (:require [clojure.core.async :as a]
            [org.httpkit.client :as http]
            [jsonista.core :as json]
            [cuerdas.core :as s]
            [clojure.tools.logging :as log]
            [funcade.codec :as codec]
            [com.rpl.specter :as sp])
  (:import [java.time Instant]))

(defn in-open-interval? [begin end value] (< begin value end))

(defn token-in-interval? [{:keys [expires]} time-in-seconds]
  (when expires
    (< time-in-seconds expires)))

(defn renew-token? [percentage {:keys [issued expires] :as m} now]
  (assert (in-open-interval? 0 1 percentage) (str "percentage is not in (0,1) interval: " percentage))
  (let [diff (- expires now)
        delta (Math/abs diff)
        p (/ delta (Math/abs (- expires issued)))]
    (or (< delta 60)
        (neg? diff)
        (< p percentage))))

(defn token-valid? [m]
  (token-in-interval? m (.getEpochSecond (Instant/now))))

(defn parse-token-data [{:keys [access-token]}]
  (-> access-token
      (s/split ".")
      second
      codec/decode64
      String.
      codec/parse-json
      (select-keys [:iat :exp])
      (clojure.set/rename-keys {:iat :issued :exp :expires})))

(defn prepare-token [jwt? [token err :as r]]
  (if (or err (not jwt?))
    r
    (let [data token
          t (merge data {:issued (.getEpochSecond (Instant/now))} (parse-token-data data))]
      (if-not (token-valid? t)
        [nil (ex-info "token has expired" t)]
        [t nil]))))

(defn new-token! [{:keys [access-token-url
                          grant-type
                          client-id
                          client-secret
                          username
                          password
                          scope
                          token-headers
                          jwt?]
                   :or {grant-type "client_credentials"
                        token-headers {:Content-Type "application/x-www-form-urlencoded"}
                        jwt? true}}]
  (let [xf (fn [{:keys [status body error]}]
             (if (and (= status 200) (not error))
               [(json/read-value body codec/underscore->kebab-mapper) nil]
               [nil {:status status :body body :error error}]))
        ch (a/promise-chan (comp (map xf)
                                 (map (partial prepare-token jwt?))))
        payload (s/join "&" (map (fn [[k v]] (str (name k) "=" v)) {:grant_type    grant-type
                                                                    :client_id     client-id
                                                                    :client_secret client-secret
                                                                    :username username
                                                                    :password password
                                                                    :scope scope}))]
    (http/request {:url access-token-url
                   :method :post
                   :headers (sp/transform [sp/MAP-KEYS] name token-headers)
                   :body payload}
                  #(a/put! ch  %))
    ch))

(defn schedule-token-renewal [name-of-job token-key should-renew? new-token! stop-ch token-store]
  (log/infof "starting token refresh poll for %s (i.e. token key is %s)" name-of-job token-key)
  (a/go-loop []
    (let [now (fn [] (.getEpochSecond (Instant/now)))
          [_ ch] (a/alts! [stop-ch (a/timeout 60000)])]
      (cond
        (= ch stop-ch) (log/info "stopping" name-of-job "token poller...")
        (should-renew?
          (get @token-store token-key) (now)) (let [_ (log/infof "renewing token for %s" token-key)
                                                    [token err] (a/<! (new-token!))]
                                                (if err
                                                  (log/error "couldn't renew token for" name-of-job err)
                                                  (do
                                                    (log/info "renewed token:" (dissoc token :access-token))
                                                    (swap! token-store (fn [s] (assoc s token-key token)))))
                                                (recur))
        :else (recur)))))
