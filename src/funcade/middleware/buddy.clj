(ns funcade.middleware.buddy
  (:require [buddy.auth :as auth]
            [buddy.auth.backends.token :as token-backend]
            [buddy.auth.protocols :as proto]
            [buddy.sign.jwt :as jwt]
            [funcade.jwks :as jk])
  (:import (clojure.lang ExceptionInfo)))

(defn validate-scope [handler request required-scopes]
  (let [decoded-token  (request :identity)
        scopes         (->> (decoded-token :scope)
                            (map #(keyword %)))
        allowed-scopes (if (coll? required-scopes)
                         (set required-scopes)
                         #{required-scopes})]
    (if (some allowed-scopes scopes)
      (handler request)
      {:status 401
       :body   {:message  "missing required scope"
                :required required-scopes
                :scopes   scopes}})))

(defn authenticate [handler scope]
  (fn [request]
    (if (auth/authenticated? request)
      (validate-scope handler request scope)
      {:status 401
       :body   {:error   "invalid authorization header"
                :message (str "access to " (request :uri) " is not authorized")}})))

(defn jwks-backend
  [{:keys [keyset authfn unauthorized-handler options token-name on-error]
    :or   {authfn identity token-name "Bearer" options {:alg :rs256}}}]
  {:pre [(ifn? authfn)]}
  (reify
    proto/IAuthentication
    (-parse [_ request]
      (#'token-backend/parse-header request token-name))

    (-authenticate [_ request data]
      (try
        (let [tkey (jk/find-token-key keyset data)]
          (authfn (jwt/unsign data tkey options)))
        (catch ExceptionInfo e
          (when (fn? on-error)
            (on-error request e))
          nil)))

    proto/IAuthorization
    (-handle-unauthorized [_ request metadata]
      (if unauthorized-handler
        (unauthorized-handler request metadata)
        (#'buddy.auth.backends.token/handle-unauthorized-default request)))))

(defn jwks-authenticator
  "JSON Web Key Set specific:
   i.e. needs a 'https://foo.com/bar/jwks' URI that returns unsign keys"
  [{:keys [uri] :as options}]
  (jwks-backend (assoc options :keyset (jk/jwks->keys uri))))
