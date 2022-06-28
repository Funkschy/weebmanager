(ns weebmanager.components.settings
  (:require
   [clojure.string :as str]
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   [reagent.core :as r]
   [weebmanager.settings :as s :refer [basic-settings mal-settings theme-settings]]))

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
          [settings-entry
           [:> p/Switch
            {:value dark?
             :on-value-change #(swap! theme-settings update :dark? not)}]
           :title "Dark theme"
           :description "Change this if you hate your eyes"]

          [settings-entry
           [:> p/Switch
            {:value amoled?
             :on-value-change #(swap! theme-settings update :amoled? not)}]
           :title "AMOLED"
           :description "If you love your eyes and your battery"]

          [title-language-setting]]

         [:> p/Divider]

         [:> (. p/List -Section)
          [:> (. p/List -Subheader) "MyAnimeList"]
          [username-input]]])))))
