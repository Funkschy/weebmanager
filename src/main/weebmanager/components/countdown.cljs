(ns weebmanager.components.countdown
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   [reagent.core :as r]
   [weebmanager.settings :as s :refer [basic-settings]]
   [weebmanager.state :refer [countdown fetch-countdown-data]]
   [weebmanager.components.common :refer [anime-icon]]
   [weebmanager.components.util :refer [get-title pluralize]]
   [clojure.string :as str]))

(def time-units [[604800 "week"] [86400 "day"] [3600 "hour"]])

(defn- split-time-units
  "Turn something like 513376 seconds into [5 22 36] (days, hours, minutes) based on time-units"
  [seconds]
  (loop [[div & div-tail] (map first time-units)
         time-left seconds
         values []]
    (if div-tail
      (recur div-tail (mod time-left div) (conj values (quot time-left div)))
      (conj values (quot time-left div)))))

(defn- format-time-until [seconds]
  (let [formatted (->> time-units
                       (map second)
                       (map vector (split-time-units seconds))
                       (drop-while (comp zero? first))
                       (map (fn [[value unit]] (pluralize unit value)))
                       (str/join " - "))]
    (if (empty? formatted)
      "less than one hour"
      formatted)))

(defn anime-list-entry [^js anime]
  (r/as-element
   [:> (. p/List -Item)
    {:left (anime-icon anime)
     :title (-> anime .-item .-name)
     :description (str "Next episode in "
                       (format-time-until (-> anime .-item .-next_ep .-timeUntilAiring)))}]))

(defn countdown-screen []
  (let [{:keys [animes loading?]} @countdown
        {:keys [title-language]}  @basic-settings]
    (p/withTheme
     (fn [^js props]
       (r/as-element
        [:> rn/View
         {:style {:flex 1
                  :background-color (-> props .-theme .-colors .-background)
                  :padding-top 0}}

         [:> rn/FlatList
          {:style {:margin 4
                   :margin-bottom 40
                   :align-self :stretch}
           :refreshing loading?
           :on-refresh fetch-countdown-data
           :data (map (fn [{:keys [title main_picture nextAiringEpisode]}]
                        (let [title (get-title title title-language)]
                          {:id title
                           :name title
                           :next_ep nextAiringEpisode
                           :image (get main_picture :medium "")}))
                      animes)
           :render-item anime-list-entry}]])))))
