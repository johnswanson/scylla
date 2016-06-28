(ns scylla.parser
  (:require [om.next :as om]
            [taoensso.timbre :as log :include-macros true]))

(defmulti mutate om/dispatch)

(def mutate-wrapper mutate)

(defmulti read om/dispatch)

(def read-wrapper read)

(defmethod read :default
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:remote true})))

(defmethod read :app/builds
  [{:keys [state] :as env} key params]
  (let [st @state]
    (pr st)
    (if-let [[_ value] (find st key)]
      {:value (mapv #(get-in st %) value)
       :remote true}
      {:remote true})))

(defmethod read :app/active-build
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value (get-in st value)}
      {:value nil})))

(defmethod mutate 'build/create
  [{:keys [state] :as env} key params]
  {:value {:keys [:app/builds]}
   :remote true})

(defmethod mutate 'build/edit
  [{:keys [state] :as env} k {:keys [path value]}]
  {:value {:keys [:app/builds]}
   :remote true
   :action #(swap! state assoc-in path value)})

(defmethod mutate 'app/open-build-editor
  [{:keys [state] :as env} key {:keys [build]}]
  {:value {:keys [:app/active-build]}
   :action #(swap! state assoc :app/active-build build)})

(defmethod mutate 'app/close-build-editor
  [{:keys [state] :as env} key _]
  {:value {:keys [:app/active-build]}
   :action #(swap! state dissoc :app/active-build)})

(defmethod mutate 'app/navigate
  [{:keys [state] :as env} key {:keys [match]}]
  {:value {:keys [:app/path]}
   :action #(js/console.log (pr-str match))})
