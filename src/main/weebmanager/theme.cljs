(ns weebmanager.theme
  (:require
   ["react-native-paper" :as p]))

(def colors
  {"accent" "#6200ee"})

(def theme-options
  {"roundness" 2
   "mode" "adaptive"})

(def theme
  (memoize
   (fn [dark? amoled?]
     (let [base-theme (js->clj (if dark? p/DarkTheme p/DefaultTheme))]
       (->> {"colors" (merge (get base-theme "colors")
                             colors
                             (when dark?
                               {"accent" "#bb86fc"})
                             (when (and dark? amoled?)
                               {"background" "#000000"}))}
            (merge base-theme theme-options)
            clj->js)))))
