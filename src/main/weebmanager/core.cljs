(ns weebmanager.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   ["@react-navigation/drawer" :as d]
   ["@react-navigation/native" :as n]
   [cljs.core.async :refer [<!]]
   ["react-native" :as rn :refer [AppRegistry]]
   ["react-native-paper" :as p]
   [reagent.core :as r]
   [weebmanager.components.backlog :as b]
   [weebmanager.components.common :refer [drawer-content header-bar]]
   [weebmanager.components.settings :refer [settings-screen]]
   [weebmanager.settings :as s :refer [theme-settings]]
   [weebmanager.state :refer [fetch-anime-data]]))

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
             :component (b/backlog-screen)
             :options {:animation "fade_from_bottom"}}]
           [:> (. drawer -Screen)
            {:name "Countdown"
             :component (b/backlog-screen)
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
