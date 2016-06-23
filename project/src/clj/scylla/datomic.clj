(ns scylla.datomic
  (:require [datomic-schema.schema :as s]
            [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(defrecord DatomicComponent [config]
  component/Lifecycle
  (start [component]
    (d/create-database (:url config))
    (let [conn (d/connect (:url config))]
      (assoc component :connection conn)))
  (stop [component]
    (dissoc component :connection)))

(defn datomic [config]
  (map->DatomicComponent
   {:config config}))

(def schema
  [(s/schema user
             (s/fields
              [username :string :unique-identity]
              [access-token :string]
              [build-specs :ref :many]))
   (s/schema build-spec
             (s/fields
              [image :string]
              [command :string]
              [env :string]))])

(def options {:index-all? true})

(def parts [(s/part "app")])

(def init-tx (concat (s/generate-parts parts)
                     (s/generate-schema schema options)))

(defn init [conn] (d/transact conn init-tx))

(defn user [gh-user]
  {:user/username (:login gh-user)
   :db/id (d/tempid :db.part/user)})

(defn add-or-update-user! [conn gh-user access-token]
  (log/debugf "tx: %s" (pr-str (-> gh-user (user) (assoc :user/access-token access-token))))
  (d/transact conn [(-> gh-user
                        (user)
                        (assoc :user/access-token access-token))]))

(defn get-user [conn username]
  (d/pull (d/db conn) '[*] [:user/username username]))

