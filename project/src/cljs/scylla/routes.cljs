(ns scylla.routes
  (:require [taoensso.timbre :as log]
            [pushy.core :as pushy]
            [bidi.bidi :as bidi]))

(def handlers (atom {}))

(defn register-handler! [k fn]
  (when (get @handlers k)
    (log/warn "replacing handler for %s" (pr-str k)))
  (swap! handlers assoc k fn))

(defn call! [{:keys [route-params handler]}]
  (log/debugf "call! %s, current state: %s" handler @handlers)
  (when-let [handler-fn (get @handlers handler)]
    (if route-params
      (handler-fn route-params)
      (handler-fn))))

(defn set-page! [match]
  (log/debugf "setting page: %s" (pr-str match))
  (call! match))

(def routes ["/" {"builds/" {""    :builds
                             [:id] :build}
                  "settings" :settings}])

(def history (pushy/pushy set-page! (partial bidi/match-route routes)))

(defn path-for [& args]
  (apply bidi/path-for routes args))

(defn navigate! [& args]
  (js/console.log "NAVIGATE TO: " (pr-str args))
  (pushy/set-token! history (apply path-for args)))

(defn start! []
  (pushy/start! history))

