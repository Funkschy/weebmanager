(ns weebmanager.state
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.core.async :refer [<!]]
   [reagent.core :as r]
   [weebmanager.anime :as a]
   [weebmanager.settings :refer [mal-settings]]))

(def state
  (r/atom {:animes []
           :loading? false}))

(defn fetch-anime-data []
  (go
    (swap! state assoc :loading? true)
    (let [{:keys [username]} @mal-settings
          animes   (<! (a/fetch-behind-schedule username))]
      (swap! state assoc :animes animes)
      (swap! state assoc :loading? false))))
