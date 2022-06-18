(ns weebmanager.components.backlog
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   [reagent.core :as r]
   [weebmanager.settings :as s :refer [basic-settings]]
   [weebmanager.state :refer [backlog fetch-backlog-data]]
   [weebmanager.components.common :refer [anime-icon]]
   [weebmanager.components.util :refer [pluralize get-title]]))

(defn anime-list-entry [^js anime]
  (r/as-element
   [:> (. p/List -Item)
    {:left (anime-icon anime)
     :title (-> anime .-item .-name)
     :description (str "You are "
                       (pluralize "episode" (-> anime .-item .-behind))
                       " behind")}]))

(defn backlog-screen []
  (let [{:keys [animes loading?]} @backlog
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
           :on-refresh fetch-backlog-data
           :data (map (fn [{:keys [title main_picture behind]}]
                        (let [title (get-title title title-language)]
                          {:id title
                           :name title
                           :behind behind
                           :image (get main_picture :medium "")}))
                      animes)
           :render-item anime-list-entry}]])))))
