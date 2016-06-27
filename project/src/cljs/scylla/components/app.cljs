(ns scylla.components.app
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [scylla.components.build-list :refer [build-list BuildListItem]]
            [scylla.components.build-editor :refer [build-editor BuildEditor]]))

(defn logged-out-app [url]
  (dom/div #js {:className "sup-background"}
    (dom/h1 #js {:className "sup-title"}
      "Welcome to Scylla")
    (dom/a #js {:href url
                :className "sup-button"}
           (dom/i #js {:className "sup-github-icon fa fa-github-square"})
           "Sign In With Github")))

(defn logout-button []
  (dom/div nil (dom/a #js {:href "/logout"} "Logout")))

(defn create-build-button [cb]
  (dom/div #js {:onClick cb}
    (dom/i #js {:className "fa fa-plus"})
    "Create Build"))

(defn logged-in-app [c {:keys [app/builds app/active-build] :as props}]
  (let [add-build #(om/transact! c `[(build/create) :app/builds])]
    (dom/div nil
      (logout-button)
      (create-build-button add-build)
      (build-list builds)
      (when active-build
        (build-editor active-build)))))

(defui App
  static om/IQuery
  (query [this]
    [{:app/user [:user/username]}
     {:app/builds (om/get-query BuildListItem)}
     {:app/active-build (om/get-query BuildEditor)}
     :app/auth-url])
  Object
  (render [this]
    (let [{:keys [app/user app/auth-url] :as props} (om/props this)]
      (dom/div nil
        (if user
          (logged-in-app this props)
          (logged-out-app auth-url))))))
