(ns scylla.app
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! chan close!]]
            [taoensso.sente :as sente :refer [cb-success?]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [scylla.parser :as parser]
            [taoensso.timbre :as log :include-macros true]))

(enable-console-print!)

(defonce sente
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk" {:type :auto })]
    {:chsk chsk
     :ch-chsk ch-recv
     :chsk-send! send-fn
     :chsk-state state}))

(defn chsk-send! [& args] (apply (:chsk-send! sente) args))

(def send-chan (chan))

(defn send-query [{:keys [remote] :as f} cb]
  (put! send-chan [remote cb]))

(defn logged-out-app [url]
  (dom/div #js {:className "sup-background"}
    (dom/h1 #js {:className "sup-title"}
      "Welcome to Scylla")
    (dom/a #js {:href url
                :className "sup-button"}
      (dom/i #js {:className "sup-github-icon fa fa-github-square"})
      "Sign In With Github")))

(defui BuildSpec
  static om/IQuery
  (query [this]
    [:build-spec/image :build-spec/command :build-spec/env]))

(defui BuildEditor
  static om/Ident
  (ident [this props]
    [:build/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :build/name {:build/specs (om/get-query BuildSpec)}])
  Object
  (render [this]
    (let [{:keys [db/id build/name]} (om/props this)]
      (dom/div nil
        id

        (dom/input #js {:type        "text"
                        :placeholder "Name"
                        :onKeyUp     #(om/transact! this `[(build/edit {:path  [:build/by-id ~id :build/name]
                                                                        :key   ~(.-key %)
                                                                        :value ~(.-value (.-target %))})])})))))

(def build-editor (om/factory BuildEditor))

(defui BuildListItem
  static om/Ident
  (ident [this props]
    [:build/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :build/name {:build/specs (om/get-query BuildSpec)}])
  Object
  (render [this]
    (let [{:keys [db/id build/name build/specs]} (om/props this)]
      (dom/div #js {:onClick #(om/transact! this `[(build/activate {:build [:build/by-id ~id]})
                                                   :app/active-build])}
        "Build"
        name))))

(def build-list-item (om/factory BuildListItem))

(defn build-list [builds]
  (for [build builds]
    (build-list-item build)))

(defn logout-button []
  (dom/div nil (dom/a #js {:href "/logout"} "Logout")))

(defn create-build-button [cb]
  (dom/div #js {:onClick cb}
    (dom/i #js {:className "fa fa-plus"})
    "Create Build"))

(defui LoggedInApp
  Object
  (render [this]
    (let [{:keys [app/builds app/active-build]} (om/props this)
          {:keys [add-build]}  (om/get-computed this)]
      (dom/div nil
        (logout-button)
        (create-build-button add-build)
        (build-list builds)
        (when active-build
          (build-editor active-build))))))

(def logged-in-app (om/factory LoggedInApp))

(defui App
  static om/IQuery
  (query [this]
    [{:app/user [:user/username]}
     {:app/builds (om/get-query BuildListItem)}
     {:app/active-build (om/get-query BuildEditor)}
     :app/auth-url])
  Object
  (render [this]
    (let [{:keys [app/user app/auth-url]} (om/props this)
          add-build #(om/transact! this `[(build/create)
                                          :app/builds])]
      (dom/div nil
        (if user
          (logged-in-app (om/computed (om/props this)
                           {:add-build add-build}))
          (logged-out-app auth-url))))))

(def state (atom {}))

(defonce reconciler
  (om/reconciler
    {:state     state
     :normalize true
     :send      send-query
     :parser    (om/parser {:read parser/read-wrapper
                            :mutate parser/mutate-wrapper})}))
(om/add-root!
  reconciler
  App
  (gdom/getElement "app"))

;; sente event handlers (no server-side push handling yet)

(defmulti event-msg-handler :id)
(defmethod event-msg-handler :chsk/state [{:keys [?data] :as ev}]
  (when (:first-open? (second ?data))
    (go-loop []
      (when-let [v (<! send-chan)]
        (let [[remote cb] v]
          (chsk-send! [:app/remote remote] 5000 cb))
        (recur)))))

(defmethod event-msg-handler :default [ev-msg] :nothing)

(defonce -router (atom nil))
(defn stop-router! [] (when-let [stop-f @-router] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! -router (sente/start-client-chsk-router!
                    (:ch-chsk sente) event-msg-handler)))

(defn start! [] (start-router!))
(defonce _start-once (start!))

