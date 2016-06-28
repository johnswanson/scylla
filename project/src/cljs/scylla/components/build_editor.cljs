(ns scylla.components.build-editor
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))

(defui BuildSpec
  static om/IQuery
  (query [this]
    [:build-spec/image :build-spec/command :build-spec/env]))

(defn key-down [c property e]
  (case (.-keyCode e)
    27 (om/update-state! c dissoc :editing? :edit-text)
    13 (do (om/transact! c `[(build/edit {:path [:build/by-id ~(:db/id (om/props c)) ~property]
                                          :value ~(.. e -target -value)})])
           (om/update-state! c dissoc :editing?))
    nil))

(defn edit [c property v]
  (om/update-state! c assoc :editing? true :edit-text v :needs-focus (pr-str property)))

(defn display [c property]
  (let [v (get (om/props c) property)]
    (dom/span #js {:onClick #(edit c property v)
                   :className "edit-value edit-display-value"}
      v)))

(defn editing [c property]
  (dom/input #js {:type      "text"
                  :className "edit-value edit-display-value"
                  :ref       (pr-str property)
                  :onChange  #(om/update-state! c assoc :edit-text (.. % -target -value))
                  :value     (:edit-text (om/get-state c))
                  :onKeyDown #(key-down c property %)}))

(defn editor [c property]
  (dom/div nil
    (dom/span nil
      (name property))
    (if (:editing? (om/get-state c))
      (editing c property)
      (display c property))))

(defui BuildEditor
  static om/Ident
  (ident [this props]
    [:build/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :build/name {:build/specs (om/get-query BuildSpec)}])
  Object
  (componentDidUpdate [this prev-props prev-state]
    (when-let [focus (om/get-state this :needs-focus)]
      (js/console.log "focusing")
      (let [node (dom/node this focus)
            len (.. node -value -length)]
        (.focus node)
        (.setSelectionRange node 0 len))
      (om/update-state! this assoc :needs-focus nil)))
  (render [this]
    (let [{:keys [db/id build/name]} (om/props this)]
      (dom/div #js {:className "edit-container"}
        (dom/div nil
          (dom/h1 nil "Build Editor")
          (editor this :build/name))))))

(def build-editor (om/factory BuildEditor))
