(ns scylla.core-test
  (:require [clojure.test :refer :all]
            [scylla.core :refer :all]
            [cheshire.core :as json]))

(def user {:username "jds"
           :registries {:docker-hub {:url "https://index.docker.io/v1"
                                     :username "dockeruser"
                                     :password "some-secure-password"}}
           :verified-hosts #{"server.mkn.io" "worker.mkn.io" "socket.mkn.io"}
           :specs
           [{:image   "repo/org/image:tag"
             :command ["/usr/bin/echo" "$SECRET"]
             :env     {:secret "hello world"}
             :name    "server"
             :registry :docker-hub
             :expose  {:port 443
                       :host "server.mkn.io"
                       :ssl  true}}
            {:image   "repo/org/image:tag"
             :command ["/usr/bin/echo" "$SOMETHING"]
             :env     {:something "turkey"}
             :name    "worker"
             :expose  {:port 443
                       :host "worker.mkn.io"
                       :ssl  true}}
            {:image   "repo/org/image:tag"
             :command ["/usr/bin/echo" "hey"]
             :env     {}
             :name    "socket"
             :expose  {:port 443
                       :host "socket.mkn.io"
                       :ssl  true}}]})

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
