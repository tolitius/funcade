(ns funcade.middleware.buddy
  (:require [funcade.jwks :as jwks]
            [buddy.auth :as auth]
            [buddy.auth.protocols :as proto]
            [buddy.sign.jwt :as jwt]
            [buddy.auth.backends]))

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

(defn authenticate [handler scope]
  (fn [request]
    (if (auth/authenticated? request)
      (validate-scope handler request scope)
      {:status 401
       :body   {:error   "invalid authorization header"
                :message (str "access to " (request :uri) " is not authorized")}})))

(defn- authenticate-request
  [token-data
   {:keys [refresh-keyset?
           options
           uri]
    :as auth-opts}]
  (if-let [request-token-kid (-> token-data
                                 jwks/find-kid
                                 jwks/find-token-key-by-kid)]
    (jwt/unsign token-data request-token-kid (or options
                                                 {:alg :rs256})) 
    (if refresh-keyset?
      (do (jwks/refresh-kids uri)
          (->> (assoc auth-opts :refresh-keyset? false)
               (authenticate-request token-data))) 
      (throw (ex-info "jwt token is signed by unknown key (i.e. no public key in JSON Web Key Sets to verify the signature)"
                      {:type :validation :cause :incorrect-sign-key})))))

(defn jwks-backend
  [{jwt-header-options :options
    jwt-keyset-uri     :uri
    :keys [authfn
           unauthorized-handler
           token-name
           on-error
           refresh-keyset-on-error?]
    :or   {authfn                   identity
           token-name               "Bearer" 
           on-error                 #(println "[funcade] error: " %&)
           refresh-keyset-on-error? true}}]
  {:pre [(ifn? authfn)]}
  (reify
    proto/IAuthentication
    (-parse [_ request]
      (#'buddy.auth.backends.token/parse-header request token-name))

    (-authenticate [_ request data]
      (try
        (-> (authenticate-request data
                                  {:refresh-keyset? refresh-keyset-on-error?
                                   :options         jwt-header-options
                                   :uri             jwt-keyset-uri})
            authfn) 
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (when (fn? on-error)
              (on-error {:request         request
                         :exception/data  data}
                        e))
            nil))))

    proto/IAuthorization
    (-handle-unauthorized [_ request metadata]
      (if unauthorized-handler
        (unauthorized-handler request metadata)
        (#'buddy.auth.backends.token/handle-unauthorized-default request)))))

(defn jwks-authenticator
  "JSON Web Key Set specific:
   i.e. needs a 'https://foo.com/bar/jwks' URI that returns unsign keys"
  [{:keys [uri]
    :as options}]
  (jwks/jwks->keyset uri options)
  (jwks-backend options))
