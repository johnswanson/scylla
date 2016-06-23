(ns scylla.github
  (:require [oauth.github :as github]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]))

(def github-client-id (env :github-client-id))
(def github-client-secret (env :github-client-secret))

(def callback-url "http://localhost:8080/callback")

(defn get-user [client]
  (client
   {:method :get
    :url "https://api.github.com/user"}))

(defn access-token [code]
  (let [t (github/oauth-access-token
           github-client-id
           github-client-secret
           code
           callback-url)]
    (log/debugf "access-token (%s): %s" (pr-str code) (pr-str t))
    t))

(defn client [code]
  (let [{:keys [access-token]} (access-token code)]
    (when access-token
      (github/oauth-client access-token))))

(defn user [code]
  (when-let [client (client code)]
    (get-user client)))

(def auth-url (github/oauth-authorization-url github-client-id callback-url))
