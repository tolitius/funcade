(ns funcade.jwks-test
  (:require [funcade.jwks :as jwks]

            [org.httpkit.client :as http]
            [robert.hooke :as hook]

            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]))

(defn- cleanup-states
  []
  (reset! (deref #'jwks/keyset) nil)
  (jwks/stop-scheduled-refresh))

(defonce random-dummy-keyset-json-string
  "{\"keys\":[{\"kty\":\"RSA\",\"e\":\"AQAB\",\"use\":\"sig\",\"kid\":\"vtcn3t_xP47j-Cg2uUTLvk6qSfD-KOLlORWCZ1iWl_w\",\"alg\":\"RS512\",\"n\":\"ivOjDFcfFYWTYgz9rnRh6LcXsG5esXknyv0RiW8kK7sfbzgbuDblA4p6cxmKIyqdDWpnONk7g7ExASNPgrX23DTazEC-XTnDUdVNoQJQwwIErfwkfu2_ipWkCaRo54R5b7s-h7SYLEXkDSwZQFkFUM6u7Ul956xwdj1hKpnF3JZPRtN1jr9rOx-gLjaNgPo8xEJ2JR582rgVCRRol5czxPcvqVf1uDjkmshA9VKapbmXYb5pjQv9meZIwrEk_V9R6CcEXAPxzxIcJn5XUdWNCGVc5b7V92tEkSD4GdRjpC6w3hclzgzWC8sbCsmtBnv15tnAdi6vju95IUt5fgh0-Q\"}]}")

(deftest ^:unit
  jwks->keyset-should-not-call-API-for-every-route-use-from-state
  (let [case1 "When \"refresh-interval-ms\" is not provided which means \"funcade\"'s authentication middleware for \"reitit\" will not refresh keyset periodically
        in that case calling #'funcade.jwks/jwks->keyset for every route since its \"wrap-middleware\" should use value from state-variable
        #'funcade.jwks/keyset"]
    (log/info case1)
    (testing case1
      (let [api-call-counter (atom 0)
            jwks-opts        {}
            deref-sym        #(deref (deref %))
            _                (hook/prepend jwks/jwks->keys
                                           (swap! api-call-counter inc))]
        (with-redefs [http/get (constantly (delay {:body random-dummy-keyset-json-string}))]
          (let [_           (is (nil? (deref-sym #'jwks/keyset)))
                auth-keyset (jwks/jwks->keyset "https://milky-way-galaxy/ext/jwtsigningcert/jwks" jwks-opts)]
            (is (= auth-keyset (jwks/jwks->keyset "https://milky-way-galaxy/ext/jwtsigningcert/jwks" jwks-opts)))
            (is (some? #'jwks/keyset))
            (is (= 1 @api-call-counter))
            (cleanup-states))))))
  (let [case2 "When \"refresh-interval-ms\" is provided which means \"funcade\"'s authentication middleware for \"reitit\" will refresh keyset periodically
        in that case calling #'funcade.jwks/jwks->keyset for every route since its \"wrap-middleware\" should use value from state-variable
        #'funcade.jwks/keyset"]
    (log/info case2)
    (testing case2
      (let [api-call-counter (atom 0)
            deref-sym        #(deref (deref %))
            jwks-opts        {:refresh-interval-ms 10000
                              :refresh-callback    (fn [_] (println "[funcade]: token-refreshed"))}
            _                (hook/prepend jwks/jwks->keys
                                           (swap! api-call-counter inc))]
        (with-redefs [http/get (constantly (delay {:body random-dummy-keyset-json-string}))]
          (let [_           (is (nil? (deref-sym #'jwks/keyset)))
                _           (is (nil? (deref-sym #'jwks/keyset-refresh-scheduler)))
                auth-keyset (jwks/jwks->keyset "https://milky-way-galaxy/ext/jwtsigningcert/jwks" jwks-opts)]
            (is (= auth-keyset (jwks/jwks->keyset "https://milky-way-galaxy/ext/jwtsigningcert/jwks" jwks-opts)
                               (jwks/jwks->keyset "https://milky-way-galaxy/ext/jwtsigningcert/jwks" jwks-opts)))
            (is (some? (deref-sym #'jwks/keyset)))
            (is (some? (deref-sym #'jwks/keyset-refresh-scheduler)))
            (is (= 2 @api-call-counter)) ;; this is because of Scheduler triggers the same refresh on another thread which causes twice call
            (cleanup-states)))))))
