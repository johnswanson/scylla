(ns scylla.parser
  (:require [datomic.api :as d]
            [scylla.github :as github]))

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

;; READS

(defmulti readf (fn [_ x _] x))

(defmethod readf :default
  [_ k _]
  {:value {:error {:no-handler-for-read-key k}}})

(defmethod readf :user/username
  [{:keys [user]} _ _]
  {:value (get user :user/username)})

(defmethod readf :app/builds
  [{{:keys [user db-conn]} :ring-req} _ _]
  {:value (if user
            (builds db-conn user)
            nil)})

(defmethod readf :app/user
  [{:keys [parser query ring-req] :as env} _ _]
  (if-let [[_ user] (find ring-req :user)]
    {:value (parser (assoc env :user user) query)}
    {:value nil}))

(defmethod readf :app/auth-url
  [_ _ _]
  {:value github/auth-url})

;; MUTATIONS

(defmulti mutatef (fn [_ x _] x))

(defmethod mutatef :default
  [_ k _]
  {:action (constantly nil)})

(defmethod mutatef 'build/create
  [{:keys [ring-req]} _ _]
  {:value {:keys [:app/builds]}
   :action (fn []
             (-> (d/transact (:db-conn ring-req)
                             [{:db/id       (get-in ring-req [:user :db/id])
                               :user/builds #db/id[:db.part/user -1]}
                              {:db/id      #db/id[:db.part/user -1]
                               :build/name ""}])
                 (deref)
                 (dissoc :db-before :db-after :tx-data)))})

(defmethod mutatef 'user/delete-build
  [{:keys [ring-req]} _ _]
  {:action (constantly nil)})

(defmethod mutatef 'build/edit
  [{:keys [ring-req]} _ {:keys [path value]}]
  (let [[_ id property] path]
    {:action (fn []
               (-> (d/transact (:db-conn ring-req)
                               [{:db/id   id
                                 property value}])
                   (deref)
                   (dissoc :db-before :db-after :tx-data)))
     :value {:keys [path]}}))

(defmethod mutatef 'build/save
  [{:keys [ring-req]} _ {:keys [build]}]
  (let [{:keys [db/id] :as build} build]
    {:action (fn []
               (-> (d/transact (:db-conn ring-req)
                               [build])
                   (deref)
                   (dissoc :db-before :db-after :tx-data)))
     :value {:keys [[:build/by-id id]]}}))
