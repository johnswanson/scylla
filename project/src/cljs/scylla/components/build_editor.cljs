(ns scylla.components.build-editor
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))

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
