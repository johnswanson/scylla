(ns scylla.components.app
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [scylla.routes :as routes]
            [scylla.components.build-list :refer [build-list BuildListItem]]
            [scylla.components.build-editor :refer [build-editor BuildEditor]]
            [clojure.string :as str]))

(defn flex-container [& {:keys [left right]}]
  (dom/div #js {:className "uv-flex-container"}
    (dom/div #js {:className "uv-flex-child"} left)
    (dom/div #js {:className "uv-flex-child"} right)))

(defn logged-out-app [url]
  (dom/div #js {:className "sup-background"}
    (dom/h1 #js {:className "sup-title"}
      "Welcome to Scylla")
    (dom/a #js {:href url
                :className "sup-button"}
           (dom/i #js {:className "sup-github-icon fa fa-github-square"})
           "Sign In With Github")))

(defn topbar []
  (dom/div nil
    (dom/a #js {:href "/logout"} "Logout")))

(defn create-build-button [cb]
  (dom/div #js {:onClick cb}
    (dom/i #js {:className "fa fa-plus"})
    "Create Build"))

(def menu-options [:builds :settings])
(defn kw->menu [kw] (-> kw name (str/replace #"-" " ") str/capitalize))
(defn menu->kw [menu] (-> menu str/lower-case (str/replace #" " "-") keyword))

(defn menu-panel []
  (dom/div nil
    "Menu"
    (for [opt menu-options]
      (dom/div nil
        (dom/a #js {:href (routes/path-for opt)
                    :key opt}
               (kw->menu opt))))))

(defn logged-in-app [c {:keys [app/builds app/active-build] :as props}]
  (let [add-build #(om/transact! c `[(build/create) :app/builds])]
    (dom/div #js {:className "app-window"}
      (topbar)
      (dom/div #js {:className "app-window-container"}
        (dom/div #js {:className "app-panel app-menu"}
          (menu-panel))
        (dom/div #js {:className "app-panel app-primary"
                      :onClick   #(routes/navigate! :builds)}
          (create-build-button add-build)
          (build-list builds))
        (when active-build
          (dom/div #js {:className "app-panel app-secondary"}
            (build-editor active-build)))))))

(defui App
  static om/IQuery
  (query [this]
    [{:app/user [:user/username]}
     {:app/builds (om/get-query BuildListItem)}
     {:app/active-build (om/get-query BuildEditor)}
     :app/auth-url])
  Object
  (componentWillMount [this]
    (routes/register-handler! :builds
                              (fn []
                                (om/transact! this `[(app/close-build)])))
    (routes/register-handler! :build
                              (fn [{:keys [id]}]
                                (om/transact! this `[(app/open-build-editor {:build [:build/by-id ~(js/parseInt id)]})
                                                     :app/active-build]))))
  (componentDidMount [this]
    (routes/start!))
  (render [this]
    (let [{:keys [app/user app/auth-url] :as props} (om/props this)]
      (if user
        (logged-in-app this props)
        (logged-out-app auth-url)))))
