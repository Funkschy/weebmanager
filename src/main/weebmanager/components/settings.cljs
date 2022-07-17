(ns weebmanager.components.settings
  (:require-macros
   [weebmanager.macros :refer [react-$]])
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   [uix.core :refer [$ defui]]
   [weebmanager.components.common :refer [accordion list-item radio-list-item
                                          switch text-input]]
   [weebmanager.preferences :refer [use-preferences]]))

(defui settings-entry [{:keys [component title description]}]
  ($ list-item
     {:title title
      :description description
      :right component}))

(defui settings-screen [_]
  (let [theme (p/useTheme)
        preferences (use-preferences)

        {{:keys [dark? switch-dark! amoled? switch-amoled!]} :theme} preferences
        {{:keys [request-timeout set-request-timeout!]} :network} preferences
        {{:keys [title-language set-title-language!]} :anime} preferences
        {{:keys [username set-username!]} :mal} preferences

        bg-color (-> theme .-colors .-background)]
    ($ rn/KeyboardAvoidingView
       {:behavior :position
        :style {:flex 1
                :background-color bg-color
                :padding-top 0}}
       ($ rn/TouchableWithoutFeedback {}
          ($ rn/ScrollView {}
             ($ (. p/List -Section) {}
                ($ (. p/List -Subheader) "Display")
                ($ settings-entry
                   {:title "Dark theme"
                    :description "Change this if you hate your eyes"
                    :component (react-$ switch {:active? dark? :on-press switch-dark!})})
                ($ settings-entry
                   {:title "AMOLED"
                    :description "If you love your eyes and your battery"
                    :component (react-$ switch {:active? amoled? :on-press switch-amoled!})})

                ($ accordion
                   {:title "Title Language"}
                   ($ radio-list-item
                      {:title "Native"
                       :pressed? (= :native title-language)
                       :on-press #(set-title-language! :native)})
                   ($ radio-list-item
                      {:title "English"
                       :pressed? (= :english title-language)
                       :on-press #(set-title-language! :english)})
                   ($ radio-list-item
                      {:title "Romaji"
                       :pressed? (= :romaji title-language)
                       :on-press #(set-title-language! :romaji)})))

             ($ p/Divider)

             ($ (. p/List -Section) {}
                ($ (. p/List -Subheader) "MyAnimeList")
                ($ text-input
                   {:label "Username"
                    :placeholder "Your myanimelist.net username"
                    :default-value username
                    :on-change-text set-username!}))

             ($ (. p/List -Section) {}
                ($ (. p/List -Subheader) "Network")
                ($ text-input
                   {:label "Request Timeout"
                    :default-value (str request-timeout)
                    :on-change-text set-request-timeout!
                    :keyboard-type :numeric
                    :affix-text "secs"})))))))
