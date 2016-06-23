(ns scylla.parser
  (:require [om.next :as om]))

(defmulti mutate om/dispatch)
(defmulti read om/dispatch)

(defmethod read :default
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:remote true
       :value :not-found})))

