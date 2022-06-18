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
  (fn []
    (r/as-element
     [:> MaterialIcon
      {:name name
       :color "white"
       :size 24}])))

(defn avatar [uri]
  (fn []
    (r/as-element
     [:> (. p/Avatar -Image) {:source {:uri uri}}])))

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

(defn- set-profile-picture [iref username]
  (go
    (let [{current-url :url} @iref
          profile-picture (<! (user-profile-picture username))]
      (cond
        (and (nil? profile-picture) current-url)
        (do (prn "resetting pfp to nil")
            (reset! iref {}))

        (and profile-picture (not= profile-picture current-url))
        (do (prn "setting pfp for" username "to" profile-picture)
            (reset! iref {:url profile-picture :component (avatar profile-picture)}))))))

(defn drawer-header []
  (let [profile-picture (r/atom {:url nil :component nil})]
    (fn [theme]
      (let [{username :username} @mal-settings
            [year season] (a/get-year-and-season)
            season-text   (-> season str/lower-case str/capitalize (str " " year))
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
                          :font-size 30
                          :padding-left 10}}]]]))))

(defn drawer-content [theme]
  (fn [^js props]
    (r/as-element
     [:> d/DrawerContentScrollView
      {:contentContainerStyle {:padding-top 0}}
      [drawer-header theme]
      [:> (. p/Drawer -Section)
       {:title "Lists"}
       (drawer-item props "Backlog")
       (drawer-item props "Countdown")]])))

(defn anime-icon [^js anime]
  (avatar (-> anime .-item .-image)))
