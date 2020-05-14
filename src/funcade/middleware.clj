(ns funcade.middleware
  (:require [buddy.auth :as auth]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :as middleware-auth]
            [buddy.core.keys :as keys]
            [org.httpkit.client :as http]
            [jsonista.core :as j]))


(def mapper (j/object-mapper {:decode-key-fn keyword}))

(defn- parse-jwk-response [{:keys [body] :as response}]
  (try
    (-> (j/read-value body mapper)
        :keys
        first
        keys/jwk->public-key)
    (catch Exception ex
      (-> "unable to retrieve key from jwk response"
          (ex-info {:response response} ex)
          throw))))

(defn jwks->pubkey [url]
  (let [response @(http/get url)]
    (if-not (response :error)
      (parse-jwk-response response)
      (-> (str "unable to call jwk url: " url)
          (ex-info response)
          throw))))

(defn token-authenticator [{:keys [uri]}]
  (let [secret (jwks->pubkey uri)]
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

(defn wrap-jwt-authentication [{:keys [jwk]}]
  "middleware for JWT oauth2 bearer token validation

  adds :identity to request map on successful token decoding"
  {:name ::jwt-auth
   :wrap #(middleware-auth/wrap-authentication % (token-authenticator jwk))})

(def scope-middleware
  "middleware for scope validation

  works in tandem with wrap-jwt-authentication middleware and rejects requests without :identity
  or the required scope

  | key          | description |
  | -------------|-------------|
  | `:scope`     | `:edit` required scope as keyword, does not mount if not set"
  {:name    ::scope
   :compile (fn [{:keys [scope]} _]
              (if scope #(authenticate % scope)))})
