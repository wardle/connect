(ns com.eldrix.connect.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [manifold.stream :as stream]
            [com.eldrix.connect.core :as connect]))

(s/check-asserts true)

(deftest test-tokens
  (let [priv-key (buddy.core.keys/private-key "test/resources/ecprivkey.pem")
        pub-key (buddy.core.keys/public-key "test/resources/ecpubkey.pem")
        m {:foo "bar" :exp (@#'connect/jwt-expiry 30)}
        token (@#'connect/make-token m priv-key)
        result (@#'connect/valid-token? token pub-key)]
    (is result)
    (is (= "bar" (:foo result)))))

(defn test-internal-client
  [valid-token?]
  (let [priv-key (buddy.core.keys/private-key "test/resources/ecprivkey.pem")
        pub-key (buddy.core.keys/public-key "test/resources/ecpubkey.pem")]
    (with-open [server (connect/run-server {:server-port 0 :internal-client-public-key pub-key :external-client-public-key pub-key})]
      (let [port (aleph.netty/port server)
            url (str "ws://localhost:" port "/ws")
            client @(aleph.http/websocket-client url)
            token (if valid-token? (@#'connect/make-token {:system "concierge"} priv-key) "invalid-token")]
        (log/info "testing with" (if valid-token? "valid" "invalid") "token")
        (is @(stream/put! client token))
        (Thread/sleep 1000)                                 ;; rather non-deterministic, but we have to wait for server to close channel
        (if valid-token?
          (is (not (stream/closed? client)))
          (is (stream/closed? client)))))))

(deftest test-server-valid-token
  (test-internal-client true))

(deftest test-server-invalid-token
  (test-internal-client false))

(comment
  (test-tokens)
  (test-server-valid-token)
  (test-server-invalid-token)
  (run-tests))