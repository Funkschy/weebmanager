(ns weebmanager.components.backlog
  (:require
   [weebmanager.components.common :refer [anime-list-screen make-anime-list-entry]]
   [weebmanager.components.util :refer [get-title pluralize]]
   [weebmanager.settings :as s :refer [basic-settings]]
   [weebmanager.state :refer [backlog fetch-backlog-data]]))

(defn description-fn [^js anime]
  (str "You are "
       (pluralize "episode" (-> anime .-item .-behind))
       " behind"))

(defn make-anime-prop [title-language {:keys [title main_picture behind]}]
  (let [title (get-title title title-language)]
    {:id title
     :name title
     :behind behind
     :image (get main_picture :medium "")}))

(defn backlog-screen []
  (let [{:keys [title-language]} @basic-settings]
    (anime-list-screen (partial make-anime-prop title-language)
                       (make-anime-list-entry description-fn)
                       fetch-backlog-data
                       @backlog)))
