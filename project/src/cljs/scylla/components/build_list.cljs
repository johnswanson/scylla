(ns scylla.components.build-list
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))

(defui BuildListItem
  static om/Ident
  (ident [this props]
    [:build/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :build/name])
  Object
  (render [this]
    (let [{:keys [db/id build/name build/specs]} (om/props this)]
      (dom/div #js {:className "build-list-item"
                    :onClick #(om/transact! this `[(app/open-build-editor {:build [:build/by-id ~id]})
                                                   :app/active-build])}
        "Build"
        name))))

(def build-list-item (om/factory BuildListItem))

(defn build-list [builds]
  (for [build builds]
    (build-list-item build)))
