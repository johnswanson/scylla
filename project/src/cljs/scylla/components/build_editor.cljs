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
    :edit-text (or v "")))

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
            v     (get props property "")]
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

(def factories
  [[:build/name (om/factory (Editor :build/name) {:keyfn (constantly "name")})]
   [:build/image (om/factory (Editor :build/image) {:keyfn (constantly "image")})]
   [:build/command (om/factory (Editor :build/command) {:keyfn (constantly "command")})]
   [:build/env (om/factory (Editor :build/env) {:keyfn (constantly "env")})]])

(defui BuildEditor
  static om/Ident
  (ident [this props]
    [:build/by-id (:db/id props)])
  static om/IQuery
  (query [this]
    [:db/id :build/name :build/image :build/command :build/env])
  Object
  (render [this]
    (let [{:keys [db/id] :as props} (om/props this)
          build (select-keys props [:db/id :build/name :build/image :build/command :build/env])]
      (dom/div #js {:className "edit-container"}
        (dom/h1 nil "Build Editor")
        (for [[k editor] factories]
          (editor {:db/id id
                   k (get props k)}))
        (dom/div nil
          (dom/a #js {:onClick #((:close (om/get-computed this)))} "X"))))))

(def build-editor (om/factory BuildEditor))
