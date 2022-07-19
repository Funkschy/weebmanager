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

(defui switch [{:keys [active? on-press]}]
  ($ p/Switch
     {:value active?
      :on-value-change on-press}))

(defui checkbox [{:keys [checked? on-press]}]
  ($ p/Checkbox
     {:status (if checked? "checked" "unchecked")
      :on-press on-press}))

(def list-item (. p/List -Item))

(defui radio-button [{:keys [value pressed? on-press]}]
  ($ p/RadioButton
     {:value value
      :status (if pressed? "checked" "unchecked")
      :on-press on-press}))

(defui radio-list-item [{:keys [title pressed? on-press]}]
  ($ list-item
     {:title title
      :right
      (react-$ radio-button
               {:value title
                :pressed? pressed?
                :on-press on-press})}))

(defui text-input [{:keys [label placeholder default-value on-change-text keyboard-type affix-text]}]
  ($ rn/View
     {:style {:align-items :stretch
              :margin 15}}
     ($ p/TextInput
        {:label label
         :placeholder placeholder
         :default-value default-value
         :on-change-text on-change-text
         :keyboard-type keyboard-type
         :right (when affix-text ($ (. p/TextInput -Affix) {:text affix-text}))})))

(defui accordion [{:keys [title children]}]
  ($ (. p/List -Accordion)
     {:title title}
     children))

(def mal-logo (js/require "../assets/mal-logo.png"))
(def ani-logo (js/require "../assets/ani-logo.png"))

(defui icon-link [{:keys [image url radius] :or {radius 30}}]
  ($ rn/TouchableOpacity
     {:on-press #(. rn/Linking openURL url)
      :style {:width radius
              :height radius
              :flex 0
              :margin-right (/ radius 3)}}
     ($ (. p/Avatar -Image)
        {:source image
         :size radius})))

(defui extended-description-view [{:keys [links extend-info]}]
  ($ rn/View
     {:margin-left 4}
     ($ rn/View
        {:flex 1
         :flex-direction :row}
        ($ rn/View
           {:flex 1}
           (for [k (keys extend-info)]
             ($ p/Text {:key k} (name k))))
        ($ rn/View
           {:flex 1}
           (for [v (vals extend-info)]
             ($ p/Text {:key v} (str v)))))
     ($ rn/View
        {:flex 1
         :margin-top 8
         :align-items :center
         :flex-direction :row}
        (for [{:keys [url image]} links]
          ($ icon-link {:key url :url url :image image})))))

(defui anime-list-item [{:keys [title links description image extend-info]}]
  (let [[extended? set-extended!] (use-state false)
        description (if extended?
                      (react-$ extended-description-view {:links links :extend-info extend-info})
                      description)]
    ($ list-item
       {:left (react-$ avatar {:uri image})
        :title title
        :title-number-of-lines (if extended? 4 1)
        :on-press #(set-extended! not)
        :description description})))

(defn make-anime-list-item [description-fn]
  (fn [^js anime]
    (let [{:keys [id ani-id name image additional-info] :as anime} (js->clj (.-item anime) :keywordize-keys true)]
      ($ anime-list-item
         {:title name
          :description (description-fn anime)
          :extend-info additional-info
          :links [{:image mal-logo :url (str "https://myanimelist.net/anime/" id)}
                  {:image ani-logo :url (str "https://anilist.co/anime/" ani-id)}]
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

(defui anime-list-screen [{:keys [make-anime-prop fetch-data description-fn shows]}]
  (let [preferences (use-preferences)

        username        (get-in preferences [:mal :username])
        title-language  (get-in preferences [:anime :title-language])
        request-timeout (get-in preferences [:network :request-timeout])

        make-prop  (partial make-anime-prop title-language)

        [loading? set-loading!] (use-state false)
        [animes set-animes!]    (use-state [])

        [displayed-shows set-displayed-shows!] (use-state [])

        update-animes!  #(go (set-loading! true)
                             (set-animes! (<! (fetch-data username request-timeout)))
                             (set-loading! false))]

    ;; allow for overloading of the actual data
    ;; this is needed for the recommendation page, which updates its entries without refetching
    ;; them via the fetch-data function
    (use-effect (fn []
                  (set-animes! shows))
                [shows])

    ;; recompute the props if either the data or the representation of that data changes
    (use-effect (fn []
                  (set-displayed-shows!
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
        :animes displayed-shows
        :refresh-animes update-animes!
        :render-item (make-anime-list-item description-fn)})))
