(ns scylla.sente
  (:require [com.stuartsierra.component :as component]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [taoensso.timbre :as log]
            [scylla.github :as github]
            [scylla.server :as server]))

(defrecord SenteComponent []
  component/Lifecycle
  (start [component]
    (let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
          (sente/make-channel-socket! (get-sch-adapter) {})

          server-routes
          {:ajax-post-fn ajax-post-fn
           :ajax-get-or-ws-handshake-fn ajax-get-or-ws-handshake-fn}

          sente {:ch-chsk                       ch-recv
                 :chsk-send!                    send-fn
                 :connected-uids                connected-uids}]
      (assoc component
             :sente sente
             :server-routes server-routes
             :stop-fn (sente/start-server-chsk-router!
                       ch-recv
                       server/event-msg-handler))))
  (stop [component]
    (when-let [stop-fn (:stop-fn component)]
      (stop-fn))
    (dissoc component :sente :server-routes :stop-fn)))

(defn sente [] (->SenteComponent))
