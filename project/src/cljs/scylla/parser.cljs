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
  [{:keys [state] :as env} k {:keys [path value key]}]
  {:value {:keys [:app/builds]}
   :remote (= key "Enter")
   :action #(swap! state assoc-in path value)})

(defmethod mutate 'build/activate
  [{:keys [state] :as env} key {:keys [build]}]
  {:value {:keys [:app/active-build]}
   :action #(swap! state assoc :app/active-build build)})
