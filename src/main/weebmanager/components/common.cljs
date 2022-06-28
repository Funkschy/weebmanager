(ns weebmanager.components.common
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.core.async :refer [<!]]
   ["@react-navigation/drawer" :as d]
   [clojure.string :as str]
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   ["react-native-vector-icons/MaterialIcons" :as m]
   [reagent.core :as r]
   [weebmanager.anime :as a]
   [weebmanager.settings :refer [mal-settings]]
   [weebmanager.state :refer [user-profile-picture]]))

;; sometimes imports from js/typescript libs are a bit weird
(def MaterialIcon (. m -default))

(defn appbar-icon [name]
  [:> MaterialIcon
   {:name name
    :color "white"
    :size 24}])

(defn appbar-action [icon-name on-press]
  [:> (. p/Appbar -Action)
   {:icon (fn [] (r/as-element [appbar-icon icon-name]))
    :on-press on-press}])

(defn avatar [uri]
  (fn []
    (r/as-element
     [:> (. p/Avatar -Image) {:source {:uri uri}}])))

(defn header-bar []
  (fn [^js props]
    (let [route-name  (-> props .-route .-name)]
      (r/as-element
       [:> (. p/Appbar -Header)
        [appbar-action "menu" #(-> props .-navigation .openDrawer)]

        [:> (. p/Appbar -Content)
         {:title route-name}]

        (when-not (= route-name "Settings")
          [appbar-action "more-vert" #(-> props .-navigation (.navigate "Settings"))])]))))

(defn- drawer-item [props name]
  (let [^js navigation (. props -navigation)
        ^js nav-state  (. navigation getState)
        current-route  (aget (. nav-state -routes) (. nav-state -index))]
    [:> (. p/Drawer -Item)
     {:label name
      :active (= (. current-route -name) name)
      :on-press #(. navigation (navigate name))}]))

(defn- set-profile-picture [iref username]
  (go
    (let [{current-url :url} @iref
          profile-picture (<! (user-profile-picture username))]
      (when (and profile-picture (not= profile-picture current-url))
        (prn "setting pfp for" username "to" profile-picture)
        (reset! iref {:url profile-picture :component (avatar profile-picture)})))))

(defn- drawer-header []
  (let [profile-picture (r/atom {:url nil :component nil})]
    (fn [theme]
      (let [{username :username} @mal-settings
            [year season] (a/get-year-and-season)
            season-text   (-> season str/lower-case str/capitalize (str " " year))
            username      (-> username str/lower-case str/capitalize)
            [bg-color text-color] (if (theme "dark")
                                    ["surface" "onSurface"]
                                    ["primary" "surface"])]
        (go (set-profile-picture profile-picture username))
        [:> rn/View
         {:style {:background-color (get-in theme ["colors" bg-color])
                  :height 100
                  :justify-content "center"}}
         [:> (. p/List -Section)
          {:style {:background-color (get-in theme ["colors" bg-color])}}
          [:> (. p/List -Item)
           {:left (:component @profile-picture)
            :title username
            :description season-text
            :description-style {:color (get-in theme ["colors" text-color])
                                :padding-left 10}
            :title-style {:color (get-in theme ["colors" text-color])
                          :font-size 24
                          :padding-left 10}}]]]))))

(defn drawer-content [theme]
  (fn [^js props]
    (r/as-element
     [:> d/DrawerContentScrollView
      {:contentContainerStyle {:padding-top 0}}
      [drawer-header theme]
      [:> (. p/Drawer -Section)
       {:title "Lists"}
       [drawer-item props "Backlog"]
       [drawer-item props "Countdown"]]])))

(defn anime-icon [^js anime]
  (avatar (-> anime .-item .-image)))

(defn make-anime-list-entry [description-fn]
  (fn [^js anime]
    (r/as-element
     [:> (. p/List -Item)
      {:left (anime-icon anime)
       :title (-> anime .-item .-name)
       :description (description-fn anime)}])))

(defn list-empty-component [text icon-name color]
  [:> rn/View
   {:style {:justify-content :center
            :align-items :center
            :flex 1}}
   [:> MaterialIcon
    {:name icon-name
     :color color
     :size 40}]
   [:> p/Text
    {:style {:text-align :center}}
    text]])

(defn- empty-list-view [text color]
  [list-empty-component text "sentiment-dissatisfied" color])

(defn- error-list-view [reason color]
  [list-empty-component (str "Could not fetch animes:\n" reason) "sentiment-very-dissatisfied" color])

(defn- anime-flat-list [loading? refresh-data data make-list-item empty-component]
  [:> rn/FlatList
   {:style {:margin 4
            :margin-bottom 40
            :flex 1}
    :content-container-style {:flex-grow 1}
    :refreshing loading?
    :on-refresh refresh-data
    :data data
    :render-item make-list-item
    :ListEmptyComponent empty-component}])

(defn anime-list-screen
  [make-anime-prop
   make-list-item
   refresh-data
   & {:keys [animes loading? error]}]
  (p/withTheme
   (fn [^js props]
     (let [bg-color   (-> props .-theme .-colors .-background)
           text-color (-> props .-theme .-colors .-text)]
       (r/as-element
        [:> rn/View
         {:style {:flex 1
                  :background-color bg-color
                  :padding-top 0}}

         [anime-flat-list loading?
          refresh-data
          (map make-anime-prop animes)
          make-list-item
          (r/as-element
           (if-not error
             [empty-list-view "No anime here" text-color]
             [error-list-view error text-color]))]])))))
