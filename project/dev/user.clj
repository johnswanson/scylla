(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.repl :refer :all]
            [reloaded.repl :refer [system init start stop go reset]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [clojure.string :as str]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [taoensso.sente :as sente]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pp pprint]]
            [clojure.test :refer [run-all-tests run-tests]]
            [figwheel-sidecar.repl :as r]
            [figwheel-sidecar.repl-api :as ra]
            [figwheel-sidecar.system :as fs]
            [kube.core :as k]
            [kube.core-test]
            [scylla.datomic :as datomic]
            [scylla.server]
            [scylla.sente]))

(log/set-level! :debug)
(reset! sente/debug-mode?_ false)

(def ctx (k/make-context "http://192.168.99.100:8080"))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def figwheel-config
  {:figwheel-options {:css-dirs ["resources/public/css"]}
   :build-ids        ["dev" "cards"]
   :all-builds
   [{:id           "cards"
     :figwheel     {:devcards true}
     :source-paths ["src/cljs"]
     :compiler     {:main       'cards.core
                    :asset-path "js/compiled_cards"
                    :output-to  "resources/public/js/compiled_cards/app.js"
                    :output-dir "resources/public/js/compiled_cards"
                    :verbose    true}}
    {:id           "dev"
     :figwheel     true
     :source-paths ["src/cljs"]
     :compiler     {:main       'scylla.app
                    :asset-path "js/compiled"
                    :output-to  "resources/public/js/compiled/app.js"
                    :output-dir "resources/public/js/compiled"
                    :verbose    true}}]})

(defrecord Figwheel [config]
  component/Lifecycle
  (start [component]
    (log/infof "starting figwheel %s" (pr-str config))
    (assoc component :figwheel (ra/start-figwheel! config)))
  (stop [component]
    ; (ra/stop-figwheel!)
    (dissoc component :figwheel)))

(defn figwheel [config]
  (map->Figwheel {:config config}))

(defn dev-system []
  (component/system-map
   :server   (component/using
              (scylla.server/new-server {:port 8080})
              [:sente :datomic])
   :sente    (scylla.sente/sente)
   :datomic  (scylla.datomic/datomic {:url "datomic:dev://datomic:4334/scylla"})
   :figwheel (figwheel figwheel-config)))

(reloaded.repl/set-init! dev-system)


