(ns weebmanager.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   ["@react-navigation/drawer" :as d]
   ["@react-navigation/native" :as n]
   [cljs.core.async :refer [<!]]
   [clojure.string :as str]
   ["react-native" :as rn :refer [AppRegistry]]
   ["react-native-paper" :as p]
   ["react-native-vector-icons/MaterialIcons" :as m]
   [reagent.core :as r]
   [weebmanager.anime :as a]
   [weebmanager.settings :as s :refer [basic-settings mal-settings theme-settings]]))

;; sometimes imports from js/typescript libs are a bit weird
(def MaterialIcon (. m -default))

(def state
  (r/atom {:animes []
           :loading? false}))

(defn pluralize [noun count]
  (str count " " noun (when (> count 1) "s")))

(defn appbar-icon [name]
  (fn []
    (r/as-element
     [:> MaterialIcon
      {:name name
       :color "white"
       :size 24}])))

(defn header-bar []
  (fn [^js props]
    (let [route-name  (-> props .-route .-name)]
      (r/as-element
       [:> (. p/Appbar -Header)
        [:> (. p/Appbar -Action)
         {:icon (appbar-icon "menu")
          :on-press #(-> props .-navigation .openDrawer)}]

        [:> (. p/Appbar -Content)
         {:title route-name}]

        (when-not (= route-name "Settings")
          [:> (. p/Appbar -Action)
           {:icon "dots-vertical"
            :on-press #(-> props .-navigation (.navigate "Settings"))}])]))))

(defn drawer-item [props name]
  (let [^js navigation (. props -navigation)
        ^js nav-state  (. navigation getState)
        current-route  (aget (. nav-state -routes) (. nav-state -index))]
    [:> (. p/Drawer -Item)
     {:label name
      :active (= (. current-route -name) name)
      :on-press #(. navigation (navigate name))}]))

(defn drawer-content [theme]
  (fn [^js props]
    (let [[year season] (a/get-year-and-season)
          season-text   (-> season str/lower-case str/capitalize)
          header-text   (str season-text " " year)
          [bg-color text-color] (if (theme "dark") ["surface" "onSurface"] ["primary" "surface"])]
      (r/as-element
       [:> d/DrawerContentScrollView
        {:contentContainerStyle {:padding-top 0}}
        ;; TODO: display the selected users name and profile picture in addition
        [:> rn/View
         {:style {:background-color (get-in theme ["colors" bg-color])
                  :height 100
                  :align-items "center"
                  :justify-content "center"}}
         [:> p/Text
          {:style {:color (get-in theme ["colors" text-color])
                   :font-size 30}}
          header-text]]

        [:> (. p/Drawer -Section)
         (drawer-item props "Backlog")
         (drawer-item props "Countdown")]]))))

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
  (let [{:keys [dark? amoled?]} @theme-settings]
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
            {:value dark?
             :on-value-change #(swap! theme-settings update :dark? not)}]
           :title "Dark theme"
           :description "Change this if you hate your eyes")

          (settings-entry
           [:> p/Switch
            {:value amoled?
             :on-value-change #(swap! theme-settings update :amoled? not)}]
           :title "AMOLED"
           :description "If you love your eyes and your battery")

          [title-language-setting]]

         [:> p/Divider]

         [:> (. p/List -Section)
          [:> (. p/List -Subheader) "MyAnimeList"]
          [username-input]]])))))

(def colors
  {"accent" "#6200ee"})

(def theme-options
  {"roundness" 2
   "mode" "adaptive"})

(defn theme []
  (let [{:keys [dark? amoled?]} @theme-settings
        base-theme (js->clj (if dark? p/DarkTheme p/DefaultTheme))]
    (merge base-theme
           theme-options
           {"colors" (merge (get base-theme "colors")
                            colors
                            (when dark?
                              {"accent" "#bb86fc"})
                            (when (and dark? amoled?)
                              {"background" "#000000"}))})))

(defn app-root []
  (let [drawer (d/createDrawerNavigator)]
    (go (<! (s/load-all-settings))
        (<! (fetch-anime-data)))
    (fn []
      (let [theme    (theme)
            bg-color (get-in theme ["colors" "background"])]
        [:> p/Provider
         {:theme theme}
         [:> n/NavigationContainer
          [:> (. drawer -Navigator)
           {:initial-route-name "Backlog"
            :screen-options {:header (header-bar)
                             :drawer-style {:background-color bg-color}}
            :drawer-content (drawer-content theme)}
           [:> (. drawer -Screen)
            {:name "Backlog"
             :component (main-screen)
             :options {:animation "fade_from_bottom"}}]
           [:> (. drawer -Screen)
            {:name "Countdown"
             :component (main-screen)
             :options {:animation "fade_from_bottom"}}]
           [:> (. drawer -Screen)
            {:name "Settings"
             :component (settings-screen)
             :options {:animation "fade_from_bottom"}}]]]]))))

(defn start []
  (.registerComponent AppRegistry
                      "weebmanager"
                      #(r/reactify-component app-root)))

(defn ^:export init []
  (start))
