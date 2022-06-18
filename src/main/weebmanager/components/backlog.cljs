(ns weebmanager.components.backlog
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   [reagent.core :as r]
   [weebmanager.settings :as s :refer [basic-settings]]
   [weebmanager.state :refer [state fetch-anime-data]]))

(defn pluralize [noun count]
  (str count " " noun (when (> count 1) "s")))

(defn anime-icon [^js anime]
  (fn []
    (r/as-element
     [:> (. p/Avatar -Image) {:source {:uri (-> anime .-item .-image)}}])))

(defn anime-list-entry [^js anime]
  (r/as-element
   [:> (. p/List -Item)
    {:left (anime-icon anime)
     :title (-> anime .-item .-name)
     :description (str "You are "
                       (pluralize "episode" (-> anime .-item .-behind))
                       " behind")}]))

(defn get-title [title-options preferred-title-language]
  (get title-options
       preferred-title-language
       (first (vals title-options))))

(defn backlog-screen []
  (let [{:keys [animes loading?]} @state
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
           :on-refresh fetch-anime-data
           :data (map (fn [{:keys [title main_picture behind]}]
                        (let [title (get-title title title-language)]
                          {:id title
                           :name title
                           :behind behind
                           :image (get main_picture :medium "")}))
                      animes)
           :render-item anime-list-entry}]])))))
