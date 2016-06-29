(ns scylla.app
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! chan close!]]
            [taoensso.sente :as sente :refer [cb-success?]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [scylla.parser :as parser]
            [taoensso.timbre :as log :include-macros true]
            [scylla.components.app :as components-app]))

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
 components-app/App
 (gdom/getElement "app"))

(go-loop []
  (let [v (<! send-chan)]
    (when v
      (let [[remote cb] v]
        (chsk-send! [:app/remote remote] 5000 cb))
      (recur))))

;; sente event handlers (no server-side push handling yet)

(defmulti event-msg-handler :id)

(defmethod event-msg-handler :default [ev-msg] :nothing)

(defonce -router (atom nil))
(defn stop-router! [] (when-let [stop-f @-router] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! -router (sente/start-client-chsk-router!
                    (:ch-chsk sente) event-msg-handler)))

(defn start! [] (start-router!))
(defonce _start-once (start!))

