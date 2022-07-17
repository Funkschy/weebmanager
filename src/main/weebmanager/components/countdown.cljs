(ns weebmanager.components.countdown
  (:require
   [uix.core :refer [$ defui]]
   [weebmanager.anime :as a]
   [weebmanager.components.common :refer [anime-list-screen]]
   [weebmanager.components.util :refer [extract-additional-info
                                        format-time-until get-title]]))

(def ^:private time-units [[604800 "week"] [86400 "day"] [3600 "hour"]])
(def ^:private format-time (partial format-time-until time-units))

(defn countdown-description-fn [{:keys [next_ep]}]
  (str "Next episode in "
       (-> next_ep :timeUntilAiring format-time)))

(defn countdown-make-anime-prop
  [title-language {:keys [id ani_id title main_picture nextAiringEpisode episodes] :as a}]
  (let [title (get-title title title-language)]
    {:id id
     :ani-id ani_id
     :name title
     :next_ep nextAiringEpisode
     :image (get main_picture :medium "")
     :additional-info (extract-additional-info
                       a
                       {:score ["Rating" #(when-not (zero? %) (str % "/10"))]
                        :num_episodes_watched ["Episodes watched" #(if episodes (str % "/" episodes) %)]
                        :is_rewatching ["Rewatching" {true "yes"}]})}))

(defui countdown-screen [_]
  ($ anime-list-screen
     {:make-anime-prop countdown-make-anime-prop
      :fetch-data a/fetch-countdowns
      :description-fn countdown-description-fn}))
