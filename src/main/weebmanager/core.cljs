(ns weebmanager.core
  (:require-macros
   [weebmanager.macros :refer [react-$]]
   [cljs.core.async.macros :refer [go]])
  (:require
   ["@react-navigation/drawer" :as d]
   ["@react-navigation/native" :as n]
   ["react-native" :as rn :refer [AppRegistry]]
   ["react-native-paper" :as p]
   [cljs.core.async :refer [<!] :as async]
   [uix.core :refer [$ defui use-effect use-state]]
   [weebmanager.components.backlog :refer [backlog-screen]]
   [weebmanager.components.countdown :refer [countdown-screen]]
   [weebmanager.components.scaffolding :refer [appbar drawer-content]]
   [weebmanager.components.settings :refer [settings-screen]]
   [weebmanager.preferences :refer [default-prefs load-all-prefs
                                    PreferencesContext update-with-save]]
   [weebmanager.theme :refer [theme]]))

(def appbar-component (react-$ appbar))
(def drawer-component (react-$ drawer-content))

(def backlog-component (react-$ backlog-screen))
(def countdown-component (react-$ countdown-screen))
(def settings-component (react-$ settings-screen))

(defui navigation-container [{:keys [drawer]}]
  ($ n/NavigationContainer
     ($ (. drawer -Navigator)
        {:initial-route-name "Backlog"
         ;; https://github.com/react-navigation/react-navigation/issues/10503
         :use-legacy-implementation true
         :screen-options #js {:header appbar-component}
         :drawer-content drawer-component}

        ($ (. drawer -Screen)
           {:name "Backlog"
            :component backlog-component})
        ($ (. drawer -Screen)
           {:name "Countdown"
            :component countdown-component})
        ($ (. drawer -Screen)
           {:name "Settings"
            :component settings-component}))))

(defn make-prefs [& triples]
  (->> triples
       (partition 3)
       (map (fn [[key value setter]]
              {key value
               (keyword (str "set-" (name key) "!")) (update-with-save setter (str "@" (name key)))}))
       (reduce merge)))

(defui preferences-context [{:keys [drawer backlog settings]}]
  (let [[{:keys [dark? amoled?]} set-theme!] (use-state (get default-prefs :theme))
        theme (theme dark? amoled?)

        switch-dark! (fn [] (set-theme! #(update % :dark? not)))
        switch-amoled!  (fn [] (set-theme! #(update % :amoled? not)))

        [username set-username!] (use-state (get-in default-prefs [:mal :username]))
        [title-language set-title-language!] (use-state (get-in default-prefs [:anime :title-language]))
        [request-timeout set-request-timeout!] (use-state (get-in default-prefs [:network :request-timeout]))

        mal-prefs (make-prefs :username username set-username!)
        theme-prefs {:dark? dark?
                     :switch-dark! (update-with-save switch-dark! "@dark")
                     :amoled? amoled?
                     :switch-amoled! (update-with-save switch-amoled! "@amoled")}
        anime-prefs (make-prefs :title-language title-language set-title-language!)
        network-prefs (make-prefs :request-timeout request-timeout set-request-timeout!)]

    (use-effect (fn []
                  (go (let [{:keys [theme anime mal network] :as prefs} (<! (load-all-prefs))]
                        (prn "prefs" prefs)
                        (set-theme! theme)
                        (set-username! (:username mal))
                        (set-title-language! (:title-language anime))
                        (set-request-timeout! (:request-timeout network)))))
                [])

    ($ (. PreferencesContext -Provider)
       {:value {:theme theme-prefs
                :anime anime-prefs
                :mal mal-prefs
                :network network-prefs}}
       ($ p/Provider {:theme theme}
          ($ navigation-container
             {:drawer drawer
              :backlog backlog
              :settings settings})))))

(defui uix-root [_]
  (let [drawer (d/createDrawerNavigator)]
    ($ preferences-context
       {:drawer drawer})))

(defn start []
  (.registerComponent AppRegistry
                      "weebmanager"
                      #(react-$ uix-root)))

(defn ^:export init []
  (start))
