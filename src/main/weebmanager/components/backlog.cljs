(ns weebmanager.components.backlog
  (:require
   [uix.core :refer [$ defui]]
   [weebmanager.anime :as a]
   [weebmanager.components.common :refer [anime-list-screen]]
   [weebmanager.components.util :refer [extract-additional-info get-title
                                        pluralize]]))

(defn backlog-description-fn [{:keys [behind]}]
  (str "You are " (pluralize "episode" behind) " behind"))

(defn backlog-make-anime-prop
  [title-language {:keys [id ani_id title main_picture behind episodes] :as a}]
  (let [title (get-title title title-language)]
    {:id id
     :ani-id ani_id
     :name title
     :behind behind
     :image (get main_picture :medium "")
     :additional-info (extract-additional-info
                       a
                       {:score ["Rating" #(when-not (zero? %) (str % "/10"))]
                        :num_episodes_watched ["Episodes watched" #(if episodes (str % "/" episodes) %)]
                        :is_rewatching ["Rewatching" {true "yes"}]})}))

(defui backlog-screen [_]
  ($ anime-list-screen
     {:make-anime-prop backlog-make-anime-prop
      :fetch-data a/fetch-behind-schedule
      :description-fn backlog-description-fn}))
