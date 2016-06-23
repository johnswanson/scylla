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
            [om.next.server :as om]))

(defn response [body & [status]]
  {:status  (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body    (pr-str body)})

(defn persist! [& args] nil)

(defn callback [{{:keys [code]}       :params
                 {:keys [uid]}        :session
                 {:keys [chsk-send!]} :sente
                 :as req}]
  (log/debugf "code: %s" req)
  (let [user (github/user code)]
    {:status  307
     :headers {"Location" "/index.html"}
     :body    ""
     :session (assoc (:session req) :user user)}))

(defn my-routes [post-fn get-fn]
  ["/" [["callback" callback]
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
    (handler req)))

(defrecord ServerComponent [config]
  component/Lifecycle
  (start [component]
    (let [{:keys [sente server-routes]} (:sente component)
          {:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} server-routes]
      (assoc component :server (org.httpkit.server/run-server
                                (-> (my-routes ajax-post-fn
                                               ajax-get-or-ws-handshake-fn)
                                    (bidi.ring/make-handler)
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
  [{:keys [query ring-req] :as r} _ _]
  (log/debugf "READ: %s" r)
  {:value :not-found})

(defmethod readf :user/username
  [{:keys [user]} _ _]
  {:value (get user :login)})

(defmethod readf :user/location [{:keys [user]} _ _] {:value (:location user)})
(defmethod readf :user/followers [{:keys [user]} _ _] {:value (:followers user)})

(defmethod readf :app/user
  [{:keys [parser query ring-req] :as env} _ _]
  (if-let [user (get-in ring-req [:session :user])]
    {:value (parser (assoc env :user user) query)}
    {:value :not-found}))

(defmethod readf :app/auth-url
  [_ _ _]
  {:value github/auth-url})

(defmethod mutatef :default
  [& args]
  (log/debugf "MUTATE: %s" args)
  {:action identity})

(defmethod event-msg-handler :app/remote
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [data ((om/parser {:read readf :mutate mutatef})
              {:ring-req ring-req}
              ?data)]
    (when ?reply-fn
      (?reply-fn data))))


