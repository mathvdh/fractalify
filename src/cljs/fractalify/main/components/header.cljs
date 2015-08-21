(ns fractalify.main.components.header
  (:require
    [re-frame.core :as f]
    [reagent.core :as r]
    [material-ui.core :as ui]
    [fractalify.main.components.sidenav :as sidenav]
    [fractalify.router :as t]
    [fractalify.styles :as y]))

(defn- right-button [label href]
  [ui/flat-button {:label      label
                   :linkButton true
                   :href       href}])

(defn header []
  (let [user (f/subscribe [:user])]
    (fn []
      (let [right-btn (if @user
                        (right-button (:username @user) (str "#/user/" (:username @user)))
                        (right-button "Login / Join" (t/url :login)))]
        [ui/app-bar {
                     :title                    (r/as-element [:h1 {:style y/page-title}
                                                              [:a {:href "#/" :class "no-dec"} "Fractalify"]])
                     :iconElementRight         (r/as-element right-btn)
                     :showMenuIconButton       (not (empty? @user))
                     :onLeftIconButtonTouchTap #(sidenav/toggle-sidenav!)}]))))