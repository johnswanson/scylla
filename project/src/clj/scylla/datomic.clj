(ns scylla.datomic
  (:require [datomic-schema.schema :as s]
            [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(def schema
  [(s/schema user
             (s/fields
              [username :string :unique-identity]
              [access-token :string]
              [builds :ref :many]))
   (s/schema build
             (s/fields
              [name :string]
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

(defn builds
  ([conn user] (builds conn user nil))
  ([conn user selector] (builds conn user selector nil))
  ([conn user selector {:keys [filter as-of]}]
   (let [db (cond-> (d/db conn)
              as-of (d/as-of as-of))
         q '[:find [(pull ?eid selector) ...]
             :in $ ?uid selector
             :where
             [?eid :build/name]
             [?uid :user/builds ?eid]]]
     (d/q q db (:db/id user) (or selector '[*])))))

(defrecord DatomicComponent [config]
  component/Lifecycle
  (start [component]
    (let [{:keys [url wipe?]} config]
      (d/create-database url)
      (let [conn (d/connect url)]
        (when wipe?
          (d/transact conn init-tx))
        (assoc component :connection conn))))
  (stop [component]
    (when (:wipe? config)
      (d/delete-database (:url config)))
    (dissoc component :connection)))

(defn datomic [config]
  (map->DatomicComponent
   {:config config}))
