(ns kube.core
  (:require [kubernetes.api.v1beta1 :as v1beta1]
            [kubernetes.api.v1 :as v1]
            [aleph.http :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [manifold.deferred :as d]
            [cemerick.url :refer [url url-encode]])
  (:refer-clojure :exclude [namespace]))

(def ctx (v1/make-context "http://192.168.99.100:8080"))

(defn- add-k [k spec manifest]
  (if-let [v (get manifest k)]
    (assoc spec k v)
    spec))

(def add-command (partial add-k :command))
(def add-name (partial add-k :name))
(def add-image (partial add-k :image))
(def add-env (partial add-k :env))

(defn- replicas [user manifest]
  (or
   (:replicas user)
   (:replicas manifest)
   1))

(def type-for
  {:deployment "deployments"
   :pod        "pods"
   :namespace "namespaces"})

(defn namespaced? [type]
  (not= type :namespace))

(defn api-prefix [type]
  (if (#{:deployment} type)
    ["apis" "extensions" "v1beta1"]
    ["api" "v1"]))

(defn url-prefix [server type]
  (into [server] (api-prefix type)))

(defn url-for [{:keys [server type name namespace request-method] :as v}]
  (log/infof "%s" (pr-str v))
  (let [named? (when (not= request-method :post) name)]
    (into (url-prefix server type)
          (if (namespaced? type)
            ["namespaces" namespace (type-for type) named?]
            [(type-for type) named?]))))

(defn body-for [kube-req]
  (if-let [body (:body kube-req)]
    (json/encode body)
    nil))

(defn merge-if-present [m k v]
  (log/infof "%s %s %s" m k v)
  (if v (assoc m k v) m))

(defn ring-request [kube-req]
  (-> {}
      (merge-if-present :request-method (:request-method kube-req))
      (merge-if-present :url (str (apply url (url-for kube-req))))
      (merge-if-present :body (body-for kube-req))))

(defn deployment [user manifest]
  (let [spec (-> {:name "blah"}
                 (add-command manifest)
                 (add-name manifest)
                 (add-image manifest)
                 (add-env manifest))]
    {:name (:name manifest)
     :apiVersion "extensions/v1beta1"
     :kind "Deployment"
     :metadata {:name (:name manifest)
                :namespace (:username user)}
     :spec {:replicas (replicas user manifest)
            :template {:metadata {:labels {:app (:name manifest)}
                                  :name "blah"
                                  :namespace (:username user)}
                       :spec {:containers [spec]}}}}))

(defn make-context [url] {:server url})

(defn deployment-url [request-type ctx user manifest]
  (url (:server ctx)
       "apis"
       "extensions"
       "v1beta1"
       "namespaces"
       (url-encode (:username user))
       "deployments"
       (case request-type
         (:get :put) (url-encode (:name manifest))
         nil)))

(defn request-new-deployment [{:keys [server] :as ctx} user manifest]
  (let [method :post]
    {:body           (json/generate-string (deployment user manifest))
     :url            (str (deployment-url method ctx user manifest))
     :request-method method}))

(defn request-modify-deployment [{:keys [server] :as ctx} user manifest]
  (let [method :put]
    {:body           (json/generate-string (deployment user manifest))
     :url            (str (deployment-url method ctx user manifest))
     :request-method method}))

(defn request [req]
  (log/info (pr-str req))
  (-> (http/request req)
      (d/catch
          (fn [e]
            (log/errorf
             "failed %s"
             (-> e ex-data :body slurp json/decode pr-str))))
      (d/chain
       (fn [r]
         (when r (-> r :body slurp json/decode))))))

(defn request-get-deployment [{:keys [server]} user manifest]
  {:request-method :get
   :type           :deployment
   :namespace      (:username user)
   :server         server
   :name           (:name manifest)})

(defn get-deployment [ctx user manifest]
  (-> (request-get-deployment ctx user manifest)
      (ring-request)
      (request)))

(defn create-deployment [ctx user manifest]
  (-> {:request-method :post
       :type           :deployment
       :namespace      (:username user)
       :server         (:server ctx)
       :name           (:name manifest)
       :body           (deployment user manifest)}
      (ring-request)
      (request)))

(defn update-deployment [ctx user manifest]
  (-> {:request-method :put
       :type           :deployment
       :namespace      (:username user)
       :server         (:server ctx)
       :name           (:name manifest)
       :body           (deployment user manifest)}
      (ring-request)
      (request)))

(defn create-or-update-deployment [ctx user manifest]
  (let [existing @(get-deployment ctx user manifest)]
    (if existing
      (update-deployment ctx user manifest)
      (create-deployment ctx user manifest))))

(defn namespace [user]
  {:kind "Namespace"
   :apiVersion "v1"
   :metadata {:name (url-encode (:username user))}})

(defn create-namespace [ctx user]
  (-> {:request-method :post
       :type           :namespace
       :name           (:username user)
       :body           (namespace user)
       :server         (:server ctx)}
      (ring-request)
      (request)))

