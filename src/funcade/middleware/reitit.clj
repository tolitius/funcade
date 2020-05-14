(ns funcade.middleware.reitit
  (:require [funcade.middleware.buddy :as fmb]
            [buddy.auth.middleware :as bm]))


(defn wrap-jwt-authentication [{:keys [jwk]}]
  "middleware for JWT oauth2 bearer token validation

  adds :identity to request map on successful token decoding"
  {:name ::jwt-auth
   :wrap #(bm/wrap-authentication % (fmb/token-authenticator jwk))})

(def scope-middleware
  "middleware for scope validation

  works in tandem with wrap-jwt-authentication middleware and rejects requests without :identity
  or the required scope

  | key          | description |
  | -------------|-------------|
  | `:scope`     | `:edit` required scope as keyword, does not mount if not set"
  {:name    ::scope
   :compile (fn [{:keys [scope]} _]
              (if scope #(fmb/authenticate % scope)))})
