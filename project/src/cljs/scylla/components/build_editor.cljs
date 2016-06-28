(ns scylla.components.build-editor
  (:require [om.next :as om :refer-macros [ui defui]]
            [om.dom :as dom]
            [taoensso.timbre :as log]
            [clojure.string :as str]))

(defui BuildSpec
  static om/IQuery
  (query [this]
    [:build-spec/image :build-spec/command :build-spec/env]))

(defn stop-editing [c] (om/update-state! c dissoc :edit-text))

(defn on-change [c e]
  (om/update-state! c assoc :edit-text (.. e -target -value)))

(defn key-down [c property e]
  (case (.-keyCode e)
    27 (stop-editing c)
    13 (do (om/transact! (om/parent c)
             `[(build/edit {:path [:build/by-id ~(:db/id (om/props c)) ~property]
                            :value ~(.. e -target -value)})])
           (stop-editing c))
    nil))
(defn start-editing [c v]
  (om/update-state! c assoc
    :needs-focus true
    :edit-text v))

(defn Editor [property]
  (ui
    Object
    (componentDidUpdate [this prev-props prev-state]
      (when (and
              (om/get-state this :needs-focus)
              (om/get-state this :edit-text))
        (let [node (dom/node this "edit-input")
              len (.. node -value -length)]
          (.focus node)
          (.setSelectionRange node 0 len))
        (om/update-state! this assoc :needs-focus nil)))
    (render [this]
      (let [props (om/props this)
            v     (get props property)]
        (dom/div nil
          (dom/span #js {:className "edit-name"
                         :onClick #(start-editing this v)}
            (str/capitalize (name property)))
          (if (om/get-state this :edit-text)
            (dom/input #js {:type      "text"
                            :className "edit-value edit-edit-value"
                            :ref       "edit-input"
                            :value     (om/get-state this :edit-text)
                            :onChange  #(on-change this %)
                            :onKeyDown #(key-down this property %)
                            :onBlur    #(stop-editing this)})
            (dom/span #js {:onClick #(start-editing this v)
                           :className "edit-value edit-display-value"}
              (apply str [\" v \"]))))))))

(def name-editor (om/factory (Editor :build/name)))

(defui BuildEditor
  static om/Ident
  (ident [this props]
    [:build/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :build/name])
  Object
  (render [this]
    (let [{:keys [db/id build/name]} (om/props this)]
      (dom/div #js {:className "edit-container"}
        (dom/div nil
          (dom/h1 nil "Build Editor")
          (name-editor {:build/name name :db/id id}))))))

(def build-editor (om/factory BuildEditor))
