(ns weebmanager.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   ["react-native" :as rn :refer [AppRegistry]]
   ["react-native-paper" :as p]
   ["@react-navigation/native" :as n]
   ["@react-navigation/native-stack" :refer [createNativeStackNavigator]]
   ["react-native-vector-icons/MaterialIcons" :as m]
   [cljs.core.async :refer [<!]]
   [reagent.core :as r]
   [weebmanager.anime :as a]))

(defn pluralize [noun count]
  (str count " " noun (when (> count 1) "s")))

(def MaterialIcon (. m -default))

(defn back-icon []
  (fn []
    (r/as-element
     [:> MaterialIcon
      {:name "arrow-back"
       :color "white"
       :size 24}])))

(defn header-bar []
  (fn [^js navigation]
    (let [route-name  (-> navigation .-route .-name)]
      (r/as-element
       [:> (. p/Appbar -Header)
        (when (.-back navigation)
          [:> (. p/Appbar -Action)
           {:icon (back-icon)
            :on-press #(-> navigation .-navigation .pop)}])

        [:> (. p/Appbar -Content)
         {:title route-name}]

        (when-not (= route-name "Settings")
          [:> (. p/Appbar -Action)
           {:icon "dots-vertical"
            :on-press #(-> navigation .-navigation (.push "Settings"))}])]))))

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

(def state
  (r/atom {:username "funkschy"
           :animes []
           :loading? false}))

(defn fetch-anime-data []
  (go
    (swap! state assoc :loading? true)
    (let [username (:username @state)
          animes   (<! (a/fetch-behind-schedule username))]
      (swap! state assoc :animes animes)
      (swap! state assoc :loading? false))))

(defn main-screen []
  (let [{:keys [animes loading?]} @state]
    (fn []
      (r/as-element
       [:> rn/View
        {:style {:flex 1
                 :padding-top 0}}

        [:> rn/FlatList
         {:style {:margin 4
                  :margin-bottom 40
                  :align-self "stretch"}
          :refreshing loading?
          :on-refresh fetch-anime-data
          :data (map (fn [{:keys [title main_picture behind]}]
                       ;; TODO: add option to switch between en and jp
                       {:id title
                        :name title
                        :behind behind
                        :image (get main_picture :medium "")})
                     animes)
          :render-item anime-list-entry}]]))))

(defn settings-screen []
  (fn []
    (r/as-element
     [:> rn/View {:style {:flex 1
                          :padding-top 0}}

      [:> rn/Text "Settings"]])))

(defn app-root []
  (let [stack  (createNativeStackNavigator)]
    (fetch-anime-data)
    (fn []
      [:> p/Provider
       [:> n/NavigationContainer
        [:> (. stack -Navigator)
         {:initial-route-name "Weebmanager"
          :screen-options {:header (header-bar)}}
         [:> (. stack -Screen)
          {:name "Weebmanager"
           :component (main-screen)
           :options {:animation "fade_from_bottom"}}]
         [:> (. stack -Screen)
          {:name "Settings"
           :component (settings-screen)
           :options {:animation "fade_from_bottom"}}]]]])))

(defn start []
  (.registerComponent AppRegistry
                      "weebmanager"
                      #(r/reactify-component app-root)))

(defn init []
  (start))
