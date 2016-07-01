(ns scylla.server
  (:require [org.httpkit.server]
            [bidi.bidi :as bidi]
            [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [taoensso.timbre :as log]
            [scylla.github :as github]
            [scylla.datomic :as datomic]
            [datomic.api :as d]
            [om.next.server :as om]
            [hiccup.page :refer [html5]]))

(defn persist! [& args] nil)

(defn logout [{:keys [user] :as req}]
  {:status 307
   :headers {"Location" "/"}
   :body ""
   :session nil})

(defn callback [{{:keys [code]}       :params
                 {:keys [uid]}        :session
                 {:keys [chsk-send!]} :sente
                 :as req}]
  (let [access-token (github/access-token code)
        user         (github/user access-token)]
    (datomic/add-or-update-user! (:db-conn req) user access-token)
    {:status  307
     :headers {"Location" "/"}
     :body    ""
     :session (assoc (:session req) :uid (:login user))}))

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (html5
          [:html {:lang "en"}
           [:head
            [:meta {:charset "utf-8"}]
            [:link {:href "http://fonts.googleapis.com/css?family=Roboto:400,300,200"
                    :rel "stylesheet"
                    :type "text/css"}]
            [:link {:rel "stylesheet" :href "/css/app.css"}]]
           [:body
            [:div#app]
            [:script {:src "/js/compiled/app.js"}]
            [:script {:src "https://use.fontawesome.com/efa7507d6f.js"}]]])})

(def my-routes
  ["/" [["callback" :callback]
        ["logout" :logout]
        ["chsk" {:get :get-chsk :post :post-chsk}]
        ["builds" [[true :index]]]
        ["settings" [[true :index]]]
        ["" :index]]])

(defn wrap-sente [handler sente]
  (fn [req]
    (handler (assoc req :sente sente))))

(defn wrap-user [handler]
  (fn [req]
    (if-let [uid (get-in req [:session :uid])]
      (handler (assoc req :user (datomic/get-user (:db-conn req) uid)))
      (handler req))))

(defn wrap-datomic [handler datomic]
  (fn [req]
    (handler (assoc req :db-conn (:connection datomic)))))

(defrecord ServerComponent [config]
  component/Lifecycle
  (start [component]
    (let [datomic (:datomic component)
          {:keys [sente server-routes]} (:sente component)
          {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} server-routes
          handlers {:callback callback
                    :logout logout
                    :get-chsk ajax-get-or-ws-handshake-fn
                    :post-chsk ajax-post-fn
                    :index index}
          make-handler (fn [handler-fn]
                         (fn [{:keys [uri path-info] :as req}]
                           (let [path (or path-info uri)
                                 {:keys [route-params]
                                  :as match-context
                                  handler-kw :handler} (bidi/match-route* my-routes path req)
                                 handler (get handlers handler-kw)]
                             (when handler
                               ((handler-fn handler)
                                (-> req
                                    (update-in [:params] merge route-params)
                                    (update-in [:route-params] merge route-params)))))))]
      (assoc component :server (org.httpkit.server/run-server
                                (-> identity
                                    (make-handler)
                                    (wrap-user)
                                    (wrap-datomic datomic)
                                    (wrap-defaults site-defaults)
                                    (wrap-sente sente))
                                config))))
  (stop [component]
    (when-let [server (:server component)]
      (server :timeout 100))
    (dissoc component :server)))

(defn new-server [config]
  (map->ServerComponent {:config config}))

(defmulti event-msg-handler :id)

(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid session)]
    (when ?reply-fn
      (?reply-fn {:unmatched-event-as-echoed-from-server event}))))

(defmulti readf (fn [_ x _] x))
(defmulti mutatef (fn [_ x _] x))

(defmethod readf :default
  [_ k _]
  {:value {:error {:no-handler-for-read-key k}}})

(defmethod readf :user/username
  [{:keys [user]} _ _]
  {:value (get user :user/username)})

(defmethod readf :app/builds
  [{{:keys [user db-conn]} :ring-req} _ _]
  {:value (if user
            (datomic/builds db-conn user)
            nil)})

(defmethod readf :app/user
  [{:keys [parser query ring-req] :as env} _ _]
  (if-let [[_ user] (find ring-req :user)]
    {:value (parser (assoc env :user user) query)}
    {:value nil}))

(defmethod readf :app/auth-url
  [_ _ _]
  {:value github/auth-url})

(defmethod mutatef :default
  [_ k _]
  {:action {:error {:no-handler-for-mutate-key k}}})

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
  (log/debugf "delete-build %s" ring-req))

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

(defmethod event-msg-handler :app/remote
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [data ((om/parser {:read readf :mutate mutatef})
              {:ring-req ring-req}
              ?data)]
    (when ?reply-fn
      (?reply-fn data))))

