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
   [weebmanager.anime :as a]
   [clojure.string :as str]))

;; this can't be included in basic settings, because if it was, the entire settings page would
;; refresh everytime the user changes some basic setting
(def dark? (r/atom true))

(def basic-settings
  (r/atom {:title-language :romaji}))

(def mal-settings
  (r/atom {:username "funkschy"}))

(def state
  (r/atom {:animes []
           :loading? false}))

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

(defn fetch-anime-data []
  (go
    (swap! state assoc :loading? true)
    (let [{:keys [username]} @mal-settings
          animes   (<! (a/fetch-behind-schedule username))]
      (swap! state assoc :animes animes)
      (swap! state assoc :loading? false))))

(defn get-title [title-options preferred-title-language]
  (get title-options
       preferred-title-language
       (first (vals title-options))))

(defn main-screen []
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

(defn settings-entry [component & {:keys [title description style]}]
  [:> (. p/List -Item)
   {:title title
    :description description
    :right (fn [] (r/as-element component))
    :style style}])

(defn username-input []
  (let [{:keys [username]} @mal-settings]
    (fn []
      [:> rn/View
       {:style {:align-items :stretch
                :margin 15}}
       [:> p/TextInput
        {:label "Username"
         :default-value username
         :on-change-text #(swap! mal-settings assoc :username %)
         :placeholder "Your myanimelist.net username"}]])))

(defn radio-button [key settings-atom settings-path]
  (let [status   (if (= key (get-in @settings-atom settings-path)) "checked" "unchecked")
        on-press #(swap! settings-atom assoc-in settings-path key)]
    (fn []
      (r/as-element
       [:> p/RadioButton
        {:value (str/capitalize (name key)) :status status :on-press on-press}]))))

(defn title-language-setting []
  (fn []
    [:> (. p/List -Accordion)
     {:title "Title Language"}

     [:> (. p/List -Item)
      {:title "Native"
       :right (radio-button :native basic-settings [:title-language])}]
     [:> (. p/List -Item)
      {:title "English"
       :right (radio-button :english basic-settings [:title-language])}]
     [:> (. p/List -Item)
      {:title "Romaji"
       :right (radio-button :romaji basic-settings [:title-language])}]]))

(defn settings-screen []
  (let [is-dark? @dark?]
    (p/withTheme
     (fn [^js props]
       (r/as-element
        [:> rn/View
         {:style {:flex 1
                  :background-color (-> props .-theme .-colors .-background)
                  :padding-top 0}}

         [:> (. p/List -Section)
          [:> (. p/List -Subheader) "Basics"]
          (settings-entry
           [:> p/Switch
            {:value is-dark?
             :on-value-change #(swap! dark? not)}]
           :title "Dark theme"
           :description "Change this if you hate your eyes")
          [title-language-setting]]

         [:> p/Divider]

         [:> (. p/List -Section)
          [:> (. p/List -Subheader) "MyAnimeList"]
          [username-input]]])))))

(def colors
  {})

(def theme-options
  {"roundness" 2
   "mode" "adaptive"})

(defn theme []
  (let [dark? @dark?
        base-theme (js->clj (if dark? p/DarkTheme p/DefaultTheme))]
    (merge base-theme
           theme-options
           {:colors (merge (get base-theme "colors") colors)})))

(defn app-root []
  (let [stack  (createNativeStackNavigator)]
    (fetch-anime-data)
    (fn []
      [:> p/Provider
       {:theme (theme)}
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

(defn ^:export init []
  (start))
