(ns weebmanager.core
  (:require
   [reagent.core :as r]
   ["react-native" :as rn :refer [AppRegistry]]))

(defn app-root []
  [:> rn/View {:style {:flex-direction "column"
                       :margin 40
                       :align-items "center"
                       :background-color "white"}}

   [:> rn/Text {:style {:font-size 30
                        :font-weight "100"
                        :margin-bottom 20
                        :text-align "center"}}

    "kekw"]])

(defn start []
  (.registerComponent AppRegistry
                      "weebmanager"
                      #(r/reactify-component app-root)))

(defn init []
  (start))
