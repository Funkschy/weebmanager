(ns weebmanager.state
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.core.async :refer [<!]]
   [reagent.core :as r]
   [weebmanager.anime :as a]
   [weebmanager.settings :refer [mal-settings]]))

(def user-pictures
  (r/atom {}))

(defn user-profile-picture [username]
  (go
    (let [pictures @user-pictures]
      (or (get pictures username)
          (get (swap! user-pictures
                      assoc
                      username
                      (<! (a/fetch-user-profile-picture username)))
               username)))))

(def backlog
  (r/atom {:animes []
           :loading? false}))

(def countdown
  (r/atom {:animes []
           :loading? false}))

(defn- fetch-data [iref f]
  (go
    (swap! iref assoc :loading? true)
    (let [{:keys [username]} @mal-settings
          animes   (<! (f username))]
      (swap! iref assoc :animes animes)
      (swap! iref assoc :loading? false))))

(defn fetch-backlog-data []
  (fetch-data backlog a/fetch-behind-schedule))

(defn fetch-countdown-data []
  (fetch-data countdown a/fetch-countdowns))
