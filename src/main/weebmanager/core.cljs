(ns weebmanager.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   ["@react-native-material/core" :as m]
   ["react-native" :as rn :refer [AppRegistry]]
   [cljs.core.async :refer [<!]]
   [reagent.core :as r]
   [weebmanager.anime :as a]))

(defn pluralize [noun count]
  (str count " " noun (when (> count 1) "s")))

(defn anime-list-entry [^js anime]
  (r/as-element
   [:> m/ListItem {:leading-mode "avatar"
                   :leading (r/as-element
                             [:> m/Avatar {:image {:uri (-> anime .-item .-image)}}])
                   :title (-> anime .-item .-name)
                   :secondary-text (str "You are "
                                        (pluralize "episode" (-> anime .-item .-behind))
                                        " behind")}]))

(defn app-root []
  (let [animes (r/atom [])]
    (go (reset! animes (<! (a/fetch-behind-schedule "funkschy"))))
    (fn []
      [:> rn/View {:style {:flex 1
                           :padding-top 0}}

       [:> m/AppBar {:title "Weebmanager"
                     :trailing
                     (r/as-element [:> m/Button {:title "⫶"
                                                 :on-press #(println "settings")
                                                 :title-style {:font-weight "bold"
                                                               :font-size 26}
                                                 :style {:padding 0
                                                         :border :none
                                                         :justify-content :center
                                                         :align-content :center
                                                         :box-shadow :none
                                                         :elevation 0}}])}]

       [:> rn/FlatList {:style {:margin 4
                                :margin-bottom 40
                                :align-self "stretch"}
                        :data (map (fn [{:keys [title main_picture behind]}]
                                      ;; TODO: add option to switch between en and jp
                                     {:id title
                                      :name title
                                      :behind behind
                                      :image (get main_picture :medium "")})
                                   @animes)
                        :render-item anime-list-entry}]])))

(defn start []
  (.registerComponent AppRegistry
                      "weebmanager"
                      #(r/reactify-component app-root)))

(defn init []
  (start))
