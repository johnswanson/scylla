(ns kube.core-test
  (:require [clojure.test :refer :all]
            [kube.core :refer :all]
            [cheshire.core :as json]))

(def tests
  [{:request-method :post
    :namespace      "default"
    :server         "http://localhost:8080"
    :type           :pod
    :name           "some-pod-name"
    :body           {:some-body :goes-here}}
   {:url            "http://localhost:8080/api/v1/namespaces/default/pods"
    :request-method :post
    :body           (json/encode {:some-body :goes-here})}

   {:request-method :put
    :namespace      "default"
    :server         "http://localhost:8080"
    :type           :pod
    :name           "some-pod-name"
    :body           {:some-body :goes-here}}
   {:url            "http://localhost:8080/api/v1/namespaces/default/pods/some-pod-name"
    :request-method :put
    :body           (json/encode {:some-body :goes-here})}

   {:request-method :get
    :namespace      "default"
    :server         "http://localhost:8080"
    :type           :pod
    :name           "some-pod-name"}
   {:url            "http://localhost:8080/api/v1/namespaces/default/pods/some-pod-name"
    :request-method :get}

   {:request-method :post
    :namespace      "jds"
    :server         "http://localhost:8080"
    :type           :deployment
    :name           "some-deployment"
    :body           {:some :deployment}}
   {:url            "http://localhost:8080/apis/extensions/v1beta1/namespaces/jds/deployments"
    :request-method :post
    :body           (json/encode {:some :deployment})}

   {:request-method :post
    :namespace      "jds"
    :server         "http://localhost:8080"
    :type           :namespace
    :body           {:this :body}
    :name           "blah"}
   {:request-method :post
    :url            "http://localhost:8080/api/v1/namespaces"
    :body           (json/encode {:this :body})}])

(deftest a-test
  (testing "FIXME, I fail."
    (doseq [[req expected] (partition 2 tests)]
      (is (= (ring-request req) expected)))))
