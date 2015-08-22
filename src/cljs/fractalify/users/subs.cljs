(ns fractalify.users.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as r]))

(r/register-sub
  :user
  (fn [db _]
    (reaction (:user @db))))

(r/register-sub
  :username
  (fn [db _]
    (reaction (get-in @db [:user :username]))))