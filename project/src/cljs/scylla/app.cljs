(ns scylla.app
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! chan close!]]
            [taoensso.sente :as sente :refer [cb-success?]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [scylla.parser :as parser]))

(defonce sente
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk" {:type :auto })]
    {:chsk chsk
     :ch-chsk ch-recv
     :chsk-send! send-fn
     :chsk-state state}))

(defn chsk-send! [& args] (apply (:chsk-send! sente) args))

(defn send-query [{:keys [remote]} cb]
  (chsk-send! [:app/remote remote] 5000 cb))

(defui GithubSignup
  static om/IQuery
  (query [this]
    [:app/auth-url])
  Object
  (render [this]
    (dom/div nil
      (dom/a
       #js {:href (:app/auth-url (om/props this))}
       (dom/i #js {:className   "fa fa-github-square"
                   :aria-hidden true})
       "Sign In with GitHub"))))

(def github-signup (om/factory GithubSignup))

(defui App
  static om/IQuery
  (query [this]
    [{:app/user [:user/username :user/location :user/followers]}
     :app/auth-url])
  Object
  (render [this]
    (let [{:keys [app/user app/auth-url]} (om/props this)]
      (dom/div nil
        (when (= user :not-found)
          (github-signup (om/props this)))
        (dom/pre nil (pr-str (om/props this)))))))

(def reconciler
  (om/reconciler
   {:state     (atom {})
    :normalize true
    :send      send-query
    :remotes   [:remote]
    :parser    (om/parser {:read parser/read
                           :mutate parser/mutate})}))

;; sente event handlers (no server-side push handling yet)

(defmulti event-msg-handler :id)
(defmethod event-msg-handler :chsk/state [{:keys [?data] :as ev}]
  (when (:first-open? (second ?data))
    (om/add-root!
     reconciler
     App
     (gdom/getElement "app"))))
(defmethod event-msg-handler :default [ev-msg] :nothing)

(defonce -router (atom nil))
(defn stop-router! [] (when-let [stop-f @-router] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! -router (sente/start-client-chsk-router!
                   (:ch-chsk sente) event-msg-handler)))

(defn start! [] (start-router!))
(defonce _start-once (start!))

