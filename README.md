# funcade

creates, manages and refreshes jwt tokens

[![<! release](https://img.shields.io/badge/dynamic/json.svg?label=release&url=https%3A%2F%2Fclojars.org%2Ftolitius%2Ffuncade%2Flatest-version.json&query=version&colorB=blue)](https://github.com/tolitius/funcade/releases)
[![<! clojars](https://img.shields.io/clojars/v/tolitius/funcade.svg)](https://clojars.org/tolitius/funcade)


- [make them tokens](#make-them-tokens)
  - [group many sources](#group-many-sources)
- [use key sets (JWKS)](#use-key-sets-jwks)
- [use in middleware](#using-middleware)
  - [support JWKS keyset refresh](#support-keyset-refresh)
- [Java API](#java-api)
- [license](#license)

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

## use key sets (JWKS)

```clojure
=> (require '[funcade.jwks :as jk])

=> (jk/jwks->keys "https://foo.com/bar/jwks")
{"key-for-cert-one" #object[bouncycastle..BCRSAPublicKey "RSA Public Key [e7:ec:...]
 "key-for-cert-two" #object[bouncycastle..BCRSAPublicKey "RSA Public Key [f1:25:...]
 "key-for-cert-three" #object[bouncycastle..BCRSAPublicKey "RSA Public Key [b4:39:...]}
```

keys are looked up by token's [kid](https://tools.ietf.org/html/rfc7515#section-4.1.4):

```clojure
=> (def keys (jk/jwks->keys "https://foo.com/bar/jwks"))

=> (def token "eyJhbGciOiJSUzI1Ni...")
#'user/token

=> (jk/find-token-key keys token)
#object[org.bouncycastle..BCRSAPublicKey "RSA Public Key [f1:25:]
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

### Support middleware `keyset` refresh

`funcade` as of version `0.1.25` supports `keyset-refresh` functionality, which refreshes the `keyset` used
for `token-validation` given a specific `interval-ms` _iff provided_

```clojure
=> (require '[reitit.ring :as ring])
=> (require '[funcade.middleware.reitit :as fun])

=> (def config {:jwk {:uri "https://milky-way-galaxy/ext/jwtsigningcert/jwks"
                      :refresh-interval-ms 1000 ;; enables refreshing `keyset` for every second
                     })

=> (def app
      (ring/ring-handler
        (ring/router
          ["/ping" {:get {:scope :my-scope
                          :handler (fn [_]
                                     {:status 200
                                      :body "success"})}}]
          {:data {:middleware [(fun/wrap-jwt-authentication config})
                               fun/scope-middleware]}})))

;; after every second you see `print-messages`
01-01-1999 00:00:00 [funcade]: refreshed keyset
01-01-1999 00:00:01 [funcade]: refreshed keyset
01-01-1999 00:00:02 [funcade]: refreshed keyset
```

Also support for `callback` is enabled for every `refresh` triggered

```clojure
=> (require '[reitit.ring :as ring])
=> (require '[funcade.middleware.reitit :as fun])
=> (require '[clojure.tools.logging :as log])

=> (defn log-token-refresh
     [refreshed-kids]
     (log/info "Wooohoooooo refreshed keyset newset of kids are: " refreshed-kids))

=> (def config {:jwk {:uri "https://milky-way-galaxy/ext/jwtsigningcert/jwks"
                      :refresh-interval-ms 1000 ;; enables refreshing `keyset` for every second
                      :refresh-callback log-token-refresh})

=> (def app
      (ring/ring-handler
        (ring/router
          ["/ping" {:get {:scope :my-scope
                          :handler (fn [_]
                                     {:status 200
                                      :body "success"})}}]
          {:data {:middleware [(fun/wrap-jwt-authentication config})
                               fun/scope-middleware]}})))

;; after every second you see `print-messages`
01-01-1999 00:00:00 [funcade]: refreshed keyset
01-01-1999 00:00:00 user [INFO] Wooohoooooo refreshed keyset newset of kids are: (kid-1 kid-2)
01-01-1999 00:00:01 [funcade]: refreshed keyset
01-01-1999 00:00:01  user [INFO] Wooohoooooo refreshed keyset newset of kids are: (kid-3 kid-4)
01-01-1999 00:00:02 [funcade]: refreshed keyset
01-01-1999 00:00:02  user [INFO] Wooohoooooo refreshed keyset newset of kids are: (kid-42 kid-42')
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

copyright © 2022 [@shvetsm](https://github.com/shvetsm) / tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
