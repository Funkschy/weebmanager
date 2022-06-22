(ns weebmanager.components.countdown
  (:require
   [weebmanager.components.common :refer [anime-list-screen
                                          make-anime-list-entry]]
   [weebmanager.components.util :refer [format-time-until get-title]]
   [weebmanager.settings :as s :refer [basic-settings]]
   [weebmanager.state :refer [countdown fetch-countdown-data]]))

(def ^:private time-units [[604800 "week"] [86400 "day"] [3600 "hour"]])
(def ^:private format-time (partial format-time-until time-units))

(defn description-fn [^js anime]
  (str "Next episode in "
       (-> anime .-item .-next_ep .-timeUntilAiring format-time)))

(defn make-anime-prop [title-language {:keys [title main_picture nextAiringEpisode]}]
  (let [title (get-title title title-language)]
    {:id title
     :name title
     :next_ep nextAiringEpisode
     :image (get main_picture :medium "")}))

(defn countdown-screen []
  (let [{:keys [title-language]} @basic-settings]
    (anime-list-screen (partial make-anime-prop title-language)
                       (make-anime-list-entry description-fn)
                       fetch-countdown-data
                       @countdown)))
