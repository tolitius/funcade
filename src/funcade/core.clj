(ns funcade.core
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [funcade.tools :as tools]
            [funcade.tokens :as t]))

(defn- stop-token-channel! [stop-chan]
  (a/put! stop-chan ::stop))

(defprotocol MasterTokens
  (current-token [_])
  (stop [_]))

(defrecord TokenMaster [token-name tokens =stop=]
  MasterTokens
  (current-token [_]
    (->  @tokens (get token-name) :access-token))
  (stop [_]
    (stop-token-channel! =stop=)))

(defn- init-token-channel!
  [token-key {:keys [jwt?]
              :or {jwt? true}
              :as params} token-store]
  (let [stop-chan (a/chan 10)
        [token err] (a/<!! (t/new-token! params))
        token (tools/timebox token)]
    (if err
      (do
        (log/error "can't acquire token!" err)
        (throw (ex-info "could not acquire the JWT token" err)))
      (do
        (swap! token-store (fn [s] (assoc s token-key token)))
        (t/schedule-token-renewal
          (name token-key)
          token-key
          (partial t/renew-token?  (/ (or (:refresh-percent params) 10) 100))
          (fn [] (t/new-token! params))
          stop-chan
          token-store)
        stop-chan))))

(defn wake-token-master
  "config is
  {:access-token-url OAuth 2.0 server url
   :grant-type OAuth 2.0 grant type (client_credentials, implicit, etc)
   :client-id OAuth 2.0 client id
   :client-secret OAuth 2.0 secret (a hex string)
   :scope OAuth 2.0 Scope
   :token-headers a map of headers {\"Cookie\" \"foo=bar\"}
   :refresh-percent (when less than x% of time between expires-at and issued-at remains, refresh the token)}"
  [token-name config]
  (let [tokens (atom {})
        =stop= (init-token-channel! token-name config tokens)]
    (TokenMaster. token-name tokens =stop=)))

(defn wake-token-masters
  "wakes up several token masters given the map of {source config}.
   i.e. {:orion {:client-id ...}
         :asgard {:client-id ...}}"
  [configs]
  (into {}
        (for [[source config] configs]
          [source (wake-token-master source config)])))
