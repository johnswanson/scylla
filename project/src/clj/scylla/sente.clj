(ns scylla.sente
  (:require [com.stuartsierra.component :as component]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [om.next.server :as om]
            [scylla.parser :as parser]))

(defmulti event-msg-handler :id)

(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (when ?reply-fn
    (?reply-fn {:unmatched-event-as-echoed-from-server event})))

(defmethod event-msg-handler :app/remote
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [data ((om/parser {:read parser/readf :mutate parser/mutatef})
              {:ring-req ring-req}
              ?data)]
    (when ?reply-fn
      (?reply-fn data))))

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
                       event-msg-handler))))
  (stop [component]
    (when-let [stop-fn (:stop-fn component)]
      (stop-fn))
    (dissoc component :sente :server-routes :stop-fn)))

(defn sente [] (->SenteComponent))
