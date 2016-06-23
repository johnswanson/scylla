(ns scylla.server
  (:require [org.httpkit.server]
            [bidi.ring :refer [resources-maybe]]
            [bidi.bidi :as bidi]
            [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [taoensso.timbre :as log]
            [ring.util.response :as res]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [scylla.github :as github]
            [scylla.datomic :as datomic]
            [om.next.server :as om]))

(defn response [body & [status]]
  {:status  (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body    (pr-str body)})

(defn persist! [& args] nil)

(defn logout [{:keys [user] :as req}]
  {:status 307
   :headers {"Location" "/index.html"}
   :body ""
   :session nil})

(defn callback [{{:keys [code]}       :params
                 {:keys [uid]}        :session
                 {:keys [chsk-send!]} :sente
                 :as req}]
  (let [access-token (github/access-token code)
        user         (github/user access-token)]
    {:status  307
     :headers {"Location" "/index.html"}
     :body    ""
     :session (assoc (:session req) :uid (:login user))}))

(defn my-routes [post-fn get-fn]
  ["/" [["callback" callback]
        ["logout" logout]
        ["chsk" {:get get-fn :post post-fn}]
        ["session" #(assoc-in (response (:session %))
                              [:headers "Content-Type"]
                              "text/plain")]
        [""         (bidi.ring/->Redirect 307 "/index.html")]
        [""         (resources-maybe {:prefix "public/"})]]])

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
          {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} server-routes]
      (assoc component :server (org.httpkit.server/run-server
                                (-> (my-routes ajax-post-fn
                                               ajax-get-or-ws-handshake-fn)
                                    (bidi.ring/make-handler)
                                    (wrap-user)
                                    (wrap-datomic datomic)
                                    (wrap-defaults site-defaults)
                                    (wrap-anti-forgery)
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

(defmethod readf :app/user
  [{:keys [parser query ring-req] :as env} _ _]
  (if-let [[_ user] (find ring-req :user)]
    {:value (parser (assoc env :user user) query)}
    {:value :not-found}))

(defmethod readf :app/auth-url
  [_ _ _]
  {:value github/auth-url})

(defmethod mutatef :default
  [_ k _]
  (log/debugf "mutation received, not handled: %s" k)
  {:action {:error {:no-handler-for-mutate-key k}}})

(defmethod event-msg-handler :app/remote
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [data ((om/parser {:read readf :mutate mutatef})
              {:ring-req ring-req}
              ?data)]
    (when ?reply-fn
      (?reply-fn data))))


