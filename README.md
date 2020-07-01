# funcade

creates, manages and refreshes oauth 2.0 jwt tokens

[![<! release](https://img.shields.io/badge/dynamic/json.svg?label=release&url=https%3A%2F%2Fclojars.org%2Ftolitius%2Ffuncade%2Flatest-version.json&query=version&colorB=blue)](https://github.com/tolitius/funcade/releases)
[![<! clojars](https://img.shields.io/clojars/v/tolitius/funcade.svg)](https://clojars.org/tolitius/funcade)

## make them tokens

```clojure
=> (require '[funcade.core :as f])

=> (def conf {:client-id "planet-earth"
              :scope "solar-system"
              :access-token-url "https://milky-way-galaxy/token.oauth2"
              :client-secret "super-hexidecimal-secret"})
```

optional config properties:

name | default | description
------------ | ------------- | -------------
`:token-headers` | `{:Content-Type "application/x-www-form-urlencoded"}` | headers passed to aquire the token
`:grant-type` | `"client_credentials"` | [oauth 2.0 grant type](https://oauth.net/2/grant-types/)
`:refresh-percent` | `10` | when less than % of time between expires-at and issued-at remains, refreshes the token

```clojure
=> (def token-repo (f/wake-token-master :serpens conf))

=> (f/current-token token-repo)
;; "eyJhbGci...dc22w"
```

```clojure
user=> (f/stop token-repo)
true
```

### group many sources

```clojure
=> (def sources {:mars {:client-id "..."
                        :client-secret "..."
                        :access-token-url "..."
                        :scope "..."}
                 :asgard {:client-id "..."
                          :client-secret "..."
                          :access-token-url "..."
                          :scope "..."}})

=> (def jwt (f/wake-token-masters sources))
```

creates two token masters:

```clojure
=> jwt
;; {:mars #object[funcade.core.TokenMaster"],
;;  :asgard #object[funcade.core.TokenMaster"]}

=> (-> jwt :mars f/current-token)
;; "eyJhbGci...dc22w"

=> (-> jwt :asgard f/current-token)
;; "eyJhbRkv...id95p"
```

## using middleware

funcade has middleware and helpers to use auth requests protected behind JWT tokens with various scopes.

```clojure
=> (require '[reitit.ring :as ring])
=> (require '[funcade.middleware.reitit :as fun])

=> (def config {:jwk {:uri "https://milky-way-galaxy/ext/jwtsigningcert/jwks"})

=> (def app
      (ring/ring-handler
        (ring/router
          ["/ping" {:get {:scope :my-scope
                          :handler (fn [_]
                                     {:status 200
                                      :body "success"})}}]
          {:data {:middleware [(fun/wrap-jwt-authentication config})
                               fun/scope-middleware]}})))
```

valid request:

```clojure 
=> (def token "eyJhbGci...dc22w")

=> (app {:request-method :get :uri "/ping" :headers {:authorization (str "Bearer " token)}})
;; {:status 200, :body "success"}
```

invalid/missing token:

```clojure 
=> (app {:request-method :get :uri "/ping"}})
;; {:status 401, :body {:error "invalid authorization header", :message "access to /ping is not authorized"}}
```

invalid scope:

```clojure 
=> (def token "eyJhbGci...dc22w")

=> (app {:request-method :get :uri "/ping" :headers {:authorization (str "Bearer " token)}})
;; {:status 401, :body {:message "missing required scope", :required :my-scope, :scopes (:not-my-scope)}}
```

## Java API

```java
import funcade.core.TokenMaster;
import tolitius.Funcade;
```

```java
var config  = Map.of("client-id", clientId,
                     "client-secret", clientSecret,
                     "access-token-url", accessTokenUri,
                     "username", username,
                     "password", password,
                     "grant-type", grantType);

var tokenMaster = Funcade.wakeTokenMaster("asgard", config);
```

and then whenever you need a token:

```java
var token = Funcade.currentToken(tokenMaster);
```

## license

copyright © 2020 shvetsm

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
