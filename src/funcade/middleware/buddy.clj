(ns funcade.middleware.buddy
  (:require [funcade.jwks :as jk]
            [buddy.auth :as auth]
            [buddy.auth.protocols :as proto]
            [buddy.sign.jwt :as jwt]
            [buddy.auth.backends]
            [calip.core :as calip]))

(defn validate-scope [handler request required-scopes]
  (let [decoded-token (request :identity)]
    (let [scopes (->> (decoded-token :scope)
                      (map #(keyword %)))
          allowed-scopes (if (coll? required-scopes)
                           (set required-scopes)
                           #{required-scopes})]
      (if (some allowed-scopes scopes)
        (handler request)
        {:status 401
         :body   {:message  "missing required scope"
                  :required required-scopes
                  :scopes   scopes}}))))

(defn- -authenticate-request
  "I authenticate the request"
  [request token {:keys [keyset options authfn on-error]}]
  (try
    (let [tkey (jk/find-token-key keyset token)]
      (when-not tkey
        (throw (ex-info "jwt token is signed by unknown key (i.e. no public key in JSON Web Key Sets to verify the signature)"
                        {:type :validation :cause :incorrect-sign-key})))
      (authfn (jwt/unsign token tkey options)))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (when (fn? on-error)
          (on-error {:request         request
                     :exception/data  data}
                    e))
        nil))))

(defn authenticate-scope [handler scope]
  (fn [request]
    (if (auth/authenticated? request)
      (validate-scope handler request scope)
      {:status 401
       :body   {:error   "invalid authorization header"
                :message (str "access to " (request :uri) " is not authorized")}})))

(defn jwks-backend
  [{:keys [keyset authfn unauthorized-handler options token-name on-error]
    :or   {authfn identity token-name "Bearer" options {:alg :rs256}
           on-error #(println "[funcade] error: " %&)}}]
  {:pre [(ifn? authfn)]}
  (calip/measure #{#'funcade.middleware.buddy/-authenticate-request}
                 {:on-error? true})
  (reify
    proto/IAuthentication
    (-parse [_ request]
      (#'buddy.auth.backends.token/parse-header request token-name))

    (-authenticate [_ request data]
      (-authenticate-request request data {:keyset    keyset
                                           :options   options
                                           :on-error  on-error
                                           :authfn    authfn}))

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
