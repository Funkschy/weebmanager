(ns weebmanager.components.common
  (:require
   ["@react-navigation/drawer" :as d]
   [clojure.string :as str]
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   ["react-native-vector-icons/MaterialIcons" :as m]
   [reagent.core :as r]
   [weebmanager.anime :as a]))

;; sometimes imports from js/typescript libs are a bit weird
(def MaterialIcon (. m -default))

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
          [bg-color text-color] (if (theme "dark")
                                  ["surface" "onSurface"]
                                  ["primary" "surface"])]
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
