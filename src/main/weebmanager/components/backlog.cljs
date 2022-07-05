(ns weebmanager.components.backlog
  (:require
   [uix.core :refer [$ defui]]
   [weebmanager.anime :as a]
   [weebmanager.components.common :refer [anime-list-screen]]
   [weebmanager.components.util :refer [get-title pluralize]]))

(defn backlog-description-fn [{:keys [behind]}]
  (str "You are " (pluralize "episode" behind) " behind"))

(defn backlog-make-anime-prop [title-language {:keys [title main_picture behind]}]
  (let [title (get-title title title-language)]
    {:id title
     :name title
     :behind behind
     :image (get main_picture :medium "")}))

(defui backlog-screen [_]
  ($ anime-list-screen
     {:make-anime-prop backlog-make-anime-prop
      :fetch-data a/fetch-behind-schedule
      :description-fn backlog-description-fn}))
