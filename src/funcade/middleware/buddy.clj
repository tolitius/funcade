(ns funcade.middleware.buddy
  (:require [funcade.jwks :as jk]
            [buddy.auth :as auth]
            [buddy.auth.protocols :as proto]
            [buddy.sign.jwt :as jwt]
            [buddy.auth.backends :as backends]
            [buddy.auth.protocols :as proto]))

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

(defn jwks-backend
  [{:keys [keyset authfn unauthorized-handler options token-name on-error]
    :or {authfn identity token-name "Token"}}]
  {:pre [(ifn? authfn)]}
  (reify
    proto/IAuthentication
    (-parse [_ request]
      (#'buddy.auth.backends.token/parse-header request token-name))

    (-authenticate [_ request data]
      (try
        (let [tkey (jk/find-token-key keyset data)]
          (authfn (jwt/unsign data tkey options)))
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (when (fn? on-error)
              (on-error request e))
            nil))))

    proto/IAuthorization
    (-handle-unauthorized [_ request metadata]
      (if unauthorized-handler
        (unauthorized-handler request metadata)
        (#'buddy.auth.backends.token/handle-unauthorized-default request)))))

(defn jwks-authenticator
  "JSON Web Key Set specific:
   i.e. needs a 'https://foo.com/bar/jwks' URI that returns unsign keys"
  [{:keys [uri]}]
  (jwks-backend {:keyset     (jk/jwks->keys uri)
                 :token-name "Bearer"
                 :options    {:alg :rs256}}))
