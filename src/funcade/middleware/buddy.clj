(ns funcade.middleware.buddy
  (:require [funcade.tools :as tools]
            [buddy.auth :as auth]
            [buddy.auth.backends :as backends]))


(defn token-authenticator [{:keys [uri]}]
  (let [secret (tools/jwks->pubkey uri)]
    (backends/jws {:secret     secret
                   :token-name "Bearer"
                   :options    {:alg :rs256}})))

(defn validate-scope [handler request required-scope]
  (let [decoded-token (request :identity)]
    (let [scopes (->> (decoded-token :scope)
                      (map #(keyword %)))]
      (if (some #{required-scope} scopes)
        (handler request)
        {:status 401
         :body   {:message  "missing required scope"
                  :required required-scope
                  :scopes   scopes}}))))

(defn authenticate [handler scope]
  (fn [request]
    (if (auth/authenticated? request)
      (validate-scope handler request scope)
      {:status 401
       :body   {:error   "invalid authorization header"
                :message (str "access to " (request :uri) " is not authorized")}})))
