(ns weebmanager.settings
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   ["@react-native-async-storage/async-storage" :as storage]
   [cljs.core.async :refer [<!]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [reagent.core :as r]))

;; sometimes imports from js/typescript libs are a bit weird
(def AsyncStorage (. storage -default))

;; this can't be included in basic settings, because if it was, the entire settings page would
;; refresh everytime the user changes some basic setting
(def theme-settings
  (r/atom {:dark? true
           :amoled? false}))

;; this will be set to "funkschy" in debug builds by the shadow-cljs config
(goog-define default-username "")

(def basic-settings
  (r/atom {:title-language :romaji}))

(def request-settings
  (r/atom {:request-timeout 3.0}))

(def mal-settings
  (r/atom {:username default-username}))

(defn boolean-default-true [value]
  (not= "false" value))

(defn boolean-default-false [value]
  (= "true" value))

(defn simple-default [default]
  (fn [value]
    (or value default)))

(defn save-setting [save-key value]
  (.setItem AsyncStorage save-key value))

(defn def-setting! [atom path save-key save-map load-map]
  (add-watch atom
             save-key
             (fn [_ _ old new]
               (when-not (= (get-in old path) (get-in new path))
                 (save-setting save-key (save-map (get-in new path))))))
  {:atom atom :path path :save-key save-key :save-map save-map :load-map load-map})

(def settings
  [(def-setting! mal-settings [:username] "@username" identity (simple-default default-username))
   (def-setting! theme-settings [:dark?] "@dark" str boolean-default-true)
   (def-setting! theme-settings [:amoled?] "@amoled" str boolean-default-false)
   (def-setting! basic-settings [:title-language] "@title-language" name (comp (simple-default :romaji) keyword))
   (def-setting! request-settings [:request-timeout] "@request-timeout" str (fnil parse-double "3.0"))])

(defn load-setting [{:keys [atom path save-key load-map]}]
  (->> save-key
       (.getItem AsyncStorage)
       <p!
       load-map
       (swap! atom assoc-in path)
       go))

(defn load-all-settings []
  (go
    (try
      (doseq [setting settings]
        (<! (load-setting setting)))
      (catch js/Error e
        (prn e)))))
