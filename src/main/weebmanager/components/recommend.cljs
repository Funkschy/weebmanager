(ns weebmanager.components.recommend
  (:require-macros
   [weebmanager.macros :refer [react-$]]
   [cljs.core.async.macros :refer [go]])
  (:require
   ["react-native" :as rn]
   ["react-native-paper" :as p]
   [cljs.core.async :refer [<!]]
   [clojure.set :as set]
   [clojure.string :as str]
   [uix.core :refer [$ defui use-effect use-state]]
   [weebmanager.anime :as a]
   [weebmanager.components.common :refer [accordion anime-list-screen checkbox
                                          list-item]]
   [weebmanager.components.util :refer [get-title]]
   [weebmanager.error :refer [error?]]))

(defn recommend-make-anime-prop [title-language {:keys [title main_picture genres]}]
  (let [title (get-title title title-language)]
    {:id title
     :name title
     :image (get main_picture :medium "")
     :genres (map :name genres)}))

(defn recommend-description-fn [{:keys [genres]}]
  (str/join ", " genres))

(defui checkbox-set [{:keys [header item-set swap-item]}]
  ($ (. p/List -Section) {}
     ($ accordion {:title header}
        ($ rn/ScrollView {}
           (for [{:keys [name checked? id]} (sort-by :name item-set)]
             ($ list-item
                {:key id
                 :title name
                 :right (react-$ checkbox {:checked? checked? :on-press #(swap-item id)})}))))))

(defui recommend-screen [_]
  (let [theme (p/useTheme)
        bg-color (-> theme .-colors .-background)

        make-item-map (fn [item-set] (into {} (map vector (map :id item-set) item-set)))
        [item-map set-item-map!] (use-state {})

        [shows set-shows!]   (use-state [])
        [genres set-genres!] (use-state #{})
        [displayed-shows set-displayed-shows!] (use-state [])

        contains-all-selected? (fn [selected {genres :genres}]
                                 (set/subset? selected (set (map :id genres))))

        filter-relevant (fn [shows]
                          (let [selected (set (map :id (filter :checked? (vals item-map))))]
                            (->> shows
                                 (filter (partial contains-all-selected? selected))
                                 (sort-by (comp - :score)))))

        set-filtered-shows! (fn [shows]
                              (set-displayed-shows! (filter-relevant shows)))

        fetch-shows
        (fn [username timeout-secs]
          (go
            (let [res (<! (a/fetch-completed-with-genres username timeout-secs))]
              (if-not (error? res)
                (let [[shows genres] res]
                  (set-genres! genres)
                  (set-shows! shows)
                  (filter-relevant shows))
                res))))]

    ;; this will run after we fetch new genres from MAL
    ;; it will completely wipe the users selections
    (use-effect (fn []
                  (set-item-map! (make-item-map genres)))
                [genres])

    ;; this will run everytime the user selects a genre in the checkbox-set
    ;; it will set the displayed-shows, therefore re-rendering the anime-list-screen
    (use-effect (fn []
                  (set-filtered-shows! shows))
                [item-map])

    ($ rn/View
       {:style {:flex 1
                :background-color bg-color
                :padding 0}}
       ($ checkbox-set
          {:header "Genres"
           :item-set (vals item-map)
           :swap-item (fn [id] (set-item-map! #(update-in % [id :checked?] (fnil not false))))})
       ($ anime-list-screen
          {:make-anime-prop recommend-make-anime-prop
           :fetch-data fetch-shows
           :shows displayed-shows
           :description-fn recommend-description-fn}))))
