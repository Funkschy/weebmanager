(ns weebmanager.components.scaffolding
  (:require-macros
   [weebmanager.macros :refer [react-$]]
   [cljs.core.async.macros :refer [go]])
  (:require
   ["@react-navigation/drawer" :as d]
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   [clojure.core.async :refer [<!]]
   [clojure.string :as str]
   [uix.core :refer [$ defui use-effect use-state]]
   [weebmanager.anime :as a]
   [weebmanager.components.common :refer [avatar material-icon]]
   [weebmanager.preferences :refer [use-preferences]]))

(defui appbar-action [{:keys [icon-name on-press]}]
  ($ (. p/Appbar -Action)
     {:icon (react-$ material-icon {:name icon-name})
      :on-press on-press}))

(defui appbar [{:keys [route navigation]}]
  (let [route-name  (get route :name)
        navigate    (get navigation :navigate)
        open-drawer (get navigation :openDrawer)]
    ($ (. p/Appbar -Header)
       ($ appbar-action {:icon-name "menu" :on-press open-drawer})

       ($ (. p/Appbar -Content) {:title route-name})

       (when-not (= "Settings" route-name)
         ($ appbar-action {:icon-name "more-vert" :on-press #(navigate "Settings")})))))

(def app-icon (js/require "../assets/app-icon.png"))

(defui drawer-header [_]
  (let [preferences (use-preferences)
        username (get-in preferences [:mal :username])
        [year season] (a/get-year-and-seasons)
        season-text   (-> season str/lower-case str/capitalize (str " " year))

        [profile-pic set-profile-pic!] (use-state nil)
        update-profile-pic! #(-> (a/fetch-user-profile-picture username)
                                 <!
                                 set-profile-pic!
                                 go)]

    ;; if the username changed we set the pfp to the users MAL pfp
    (use-effect (fn []
                  ;; ... however only if the last keystroke was > a second ago
                  (let [delay-debounce (js/setTimeout update-profile-pic! 1000)]
                    (fn [] (js/clearTimeout delay-debounce))))
                [username])

    ($ rn/View
       {:style {:height 100
                :justify-content :center}}
       ($ (. p/List -Section)
          ($ (. p/List -Item)
             {:left (react-$ avatar {:uri profile-pic :img (when-not profile-pic app-icon)})
              :title (if-not (empty? username) (str/capitalize username) "No User")
              :description season-text
              :description-style #js {:paddingLeft 10}
              :title-style #js {:fontSize 24
                                :paddingLeft 10}})))))

(defui drawer-item [{:keys [name on-press active?]}]
  ($ (. p/Drawer -Item)
     {:label name
      :active active?
      :on-press on-press}))

(defui drawer-content [{:keys [route navigation]}]
  (let [theme      (p/useTheme)
        navigate   (get navigation :navigate)
        route-name (get route :name)
        make-item  (fn [screen-name]
                     ($ drawer-item
                        {:name screen-name
                         :on-press #(navigate screen-name)
                         :active? (= route-name screen-name)}))]
    ($ d/DrawerContentScrollView
       {:flex 1
        :contentContainerStyle {:padding-top 0}
        :background-color (-> theme .-colors .-background)
        :padding-top 0}
       ($ drawer-header)
       ($ (. p/Drawer -Section) {:title "Lists"}
          (make-item "Backlog")
          (make-item "Countdown")
          (make-item "Recommend")))))
