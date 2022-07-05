(ns weebmanager.preferences
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   ["@react-native-async-storage/async-storage" :as storage]
   [cljs.core.async :refer [<!] :as async]
   [cljs.core.async.interop :refer-macros [<p!]]
   [uix.core :refer [create-context use-context]]))

;; sometimes imports from js/typescript libs are a bit weird
(def ^:private AsyncStorage (. storage -default))

;; this will be set to "funkschy" in debug builds by the shadow-cljs config
(goog-define default-username "")

(defn boolean-default-true [value]
  (not= "false" value))

(defn boolean-default-false [value]
  (= "true" value))

(defn simple-default [default]
  (fn [value]
    (or value default)))

(defn preference-spec [path save-key save-mapper load-mapper]
  {:path path :save-key save-key :save-mapper save-mapper :load-mapper load-mapper})

(def preferences-spec
  {"@username"
   (preference-spec [:mal :username] "@username" identity (simple-default default-username))
   "@dark"
   (preference-spec [:theme :dark?] "@dark" str boolean-default-true)
   "@amoled"
   (preference-spec [:theme :amoled?] "@amoled" str boolean-default-false)
   "@title-language"
   (preference-spec [:anime :title-language] "@title-language" name (comp (simple-default :romaji) keyword))
   "@request-timeout"
   (preference-spec [:network :request-timeout] "@request-timeout" str (fnil parse-double "3.0"))})

(def default-prefs {:theme {:dark? true :amoled? false}
                    :anime {:title-language :romaji}
                    :mal   {:username default-username}})

(defn save-preference [save-key value]
  (.setItem AsyncStorage save-key value))

(defn update-with-save [set-state-fn save-key]
  (fn [value]
    (let [preference   (get preferences-spec save-key)
          save-mapper  (get preference :save-mapper identity)
          mapped-value (save-mapper value)]
      (prn "saving" save-key "with value" value mapped-value)
      (save-preference save-key mapped-value)
      (set-state-fn value))))

(defn load-pref [{:keys [path save-key load-mapper]}]
  (->> save-key
       (.getItem AsyncStorage)
       <p!
       load-mapper
       (assoc-in {} path)
       go))

(defn load-all-prefs []
  (go
    (try
      (->> preferences-spec
           vals
           (map load-pref)
           async/merge
           (async/reduce (partial merge-with merge) {})
           <!)
      (catch js/Error e
        (prn e)))))

(def PreferencesContext (create-context default-prefs))

(defn use-preferences []
  (use-context PreferencesContext))
