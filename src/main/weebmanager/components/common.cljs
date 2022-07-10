(ns weebmanager.components.common
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [weebmanager.macros :refer [react-$]])
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   ["react-native-vector-icons/MaterialIcons" :as m]
   [cljs.core.async :refer [<!]]
   [uix.core :refer [$ defui use-effect use-state]]
   [weebmanager.error :refer [error? reason]]
   [weebmanager.preferences :refer [use-preferences]]))

;; sometimes imports from js/typescript libs are a bit weird
(def MaterialIcon (. m -default))

(defui material-icon [{:keys [name icon-color size] :or {icon-color "white" size 24}}]
  ($ MaterialIcon
     {:name name
      :color icon-color
      :size size}))

(defui avatar [{:keys [uri img]}]
  ($ (. p/Avatar -Image)
     {:source (or img #js {:uri uri})}))

(defui anime-list-item [{:keys [title description image]}]
  ($ (. p/List -Item)
     {:left (react-$ avatar {:uri image})
      :title title
      :description description}))

(defn make-anime-list-item [description-fn]
  (fn [^js anime]
    (let [{:keys [name image] :as anime} (js->clj (.-item anime) :keywordize-keys true)]
      ($ anime-list-item
         {:title name
          :description (description-fn anime)
          :image image}))))

(defui empty-list-component [{:keys [text icon-name icon-color]}]
  ($ rn/View
     {:style {:justify-content :center
              :align-items :center
              :flex 1}}
     ($ material-icon
        {:name icon-name
         :icon-color icon-color
         :size 40})
     ($ p/Text
        {:style {:text-align :center}}
        text)))

(defui anime-flat-list [{:keys [loading? refresh-data initial-data render-item empty-component]}]
  ($ rn/FlatList
     {:style {:margin 4
              :margin-top 0
              :margin-bottom 0
              :padding-top 6
              :flex 1}
      :content-container-style #js {:flexGrow 1 :paddingBottom 12}
      :refreshing loading?
      :on-refresh refresh-data
      :data (clj->js initial-data)
      :render-item render-item
      :ListEmptyComponent empty-component}))

(defui anime-list-view [{:keys [loading? animes refresh-animes render-item]}]
  (let [theme (p/useTheme)
        bg-color (-> theme .-colors .-background)
        text-color (-> theme .-colors .-text)

        [data reason] (if (error? animes) [[] (reason animes)] [animes nil])]
    ($ rn/View
       {:style {:flex 1
                :background-color bg-color
                :padding 0}}
       ($ anime-flat-list
          {:loading? loading?
           :initial-data data
           :refresh-data refresh-animes
           :render-item render-item
           :empty-component
           (if (error? animes)
             ($ empty-list-component
                {:text (str "Could not fetch animes:\n" reason)
                 :icon-name "sentiment-very-dissatisfied"
                 :icon-color text-color})
             ($ empty-list-component
                {:text "No anime here"
                 :icon-name "sentiment-dissatisfied"
                 :icon-color text-color}))}))))

(defui anime-list-screen [{:keys [make-anime-prop fetch-data description-fn]}]
  (let [preferences (use-preferences)

        username        (get-in preferences [:mal :username])
        title-language  (get-in preferences [:anime :title-language])
        request-timeout (get-in preferences [:network :request-timeout])

        make-prop  (partial make-anime-prop title-language)

        [loading? set-loading!] (use-state false)
        [backlog set-backlog!]  (use-state [])
        [animes set-animes!]    (use-state [])

        update-animes!  #(go (set-loading! true)
                             (set-animes! (<! (fetch-data username request-timeout)))
                             (set-loading! false))]

    ;; recompute the props if either the data or the representation of that data changes
    (use-effect (fn []
                  (set-backlog!
                   (if (error? animes)
                     animes
                     (map make-prop animes))))
                [animes title-language])

    ;; if the username changed we need to refetch the data...
    (use-effect (fn []
                  ;; ... however only if the last keystroke was > a second ago
                  (let [delay-debounce (js/setTimeout update-animes! 1000)]
                    (fn [] (js/clearTimeout delay-debounce))))
                [username])

    ($ anime-list-view
       {:loading? loading?
        :animes backlog
        :refresh-animes update-animes!
        :render-item (make-anime-list-item description-fn)})))
