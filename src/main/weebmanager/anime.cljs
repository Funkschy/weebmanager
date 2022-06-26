(ns weebmanager.anime
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [clojure.string :as str]
   [weebmanager.error :refer [Error error?]]
   [weebmanager.config :refer [config]]))

(def client-id (get-in config [:mal :client-id]))
(def timeout 3000)

(defrecord HttpError [status-code error-code]
  Error
  (reason [_] (name error-code)))

(defrecord UserError [reason]
  Error
  (reason [_] reason))

(defn get-url [url params headers]
  (->> {:query-params params
        :headers headers
        :timeout timeout}
       (http/get url)
       <!
       go))

(defn fetch-mal-api [url params]
  (go
    (let [{:keys [status body error-code]} (<! (get-url url params {"X-MAL-CLIENT-ID" client-id}))]
      (if-not (= 200 status)
        (do (println "Could not get myanimelist data" status error-code)
            (HttpError. status error-code))
        body))))

(defn fetch-mal-api-seq
  "Creates a lazy sequence of paging entries, which will only perform their request when consumed"
  [start-url params]
  (letfn
   [(inner [url]
      (go
        (when-not (nil? url)
          (let [res (<! (fetch-mal-api url params))]
            (if-not (error? res)
              (let [{data :data {next-url :next} :paging} res]
                (apply conj
                       (<! (inner next-url))
                       (reverse (map #(apply conj (vals %)) data))))
              res)))))]
    (inner start-url)))

(defn url-escape [s]
  (js/encodeURIComponent s))

(defn mal-url [username endpoint]
  (str "https://api.myanimelist.net/v2/users/"
       (url-escape username)
       endpoint))

(defn fetch-mal-watching [username]
  (fetch-mal-api-seq (mal-url username "/animelist")
                     {"fields" "list_status,alternative_titles" "status" "watching" "limit" 500}))

(def airing-eps-query
  "query media($page: Int = 1, $type: MediaType, $season: MediaSeason,  $year: String, $sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]) {
       Page(page: $page, perPage: 100) {
           media(type: $type, season: $season, startDate_like: $year, sort: $sort) {
               id:idMal
               title {
                   english
                   romaji
                   native
               }
               episodes
               nextAiringEpisode {
                   airingAt
                   timeUntilAiring
                   episode
               }
           }
       }
   }")

(defn fetch-anilist-airing [year season]
  (let [params {"query" airing-eps-query
                "variables" {"season" (str/upper-case season)
                             "type"   "ANIME"
                             "year"   (str year \%)}}]
    (go
      (let [{:keys [status body error-code]}
            (<! (http/post "https://graphql.anilist.co"
                           {:headers {"Content-Type" "application/json"}
                            :body (. js/JSON stringify (clj->js params))
                            :timeout timeout}))]
        (if-not (= 200 status)
          (do (println "Could not get anilist data" status error-code)
              (HttpError. status error-code))
          (get-in body [:data :Page :media]))))))

(defn current-episode [anime-info]
  (if (anime-info :nextAiringEpisode)
    (dec (get-in anime-info [:nextAiringEpisode :episode]))
    (anime-info :episodes)))

(defn behind-schedule [anime-info]
  (let [watched (anime-info :num_episodes_watched)
        current (current-episode anime-info)]
    (if (and current watched)
      (- current watched)
      0)))

(defn get-year-and-season []
  (let [seasons (into [] (mapcat (partial repeat 3) ["WINTER" "SPRING" "SUMMER" "FALL"]))
        date    (new js/Date)]
    [(.getFullYear date)
     (-> date .getMonth seasons)]))

(defn transduce-merged-data [mal-username xf f map-result]
  (go
    (if-not (empty? mal-username)
      (let [mal-data (fetch-mal-watching mal-username)
            ani-data (apply fetch-anilist-airing (get-year-and-season))
            mal-data (<! mal-data)
            ani-data (<! ani-data)
            combined-xf (comp (filter #(= 2 (count %)))
                              (map (partial apply merge))
                              xf)]
        (cond
          (nil? mal-data) (println "could not fetch mal data for" mal-username)
          (nil? ani-data) (println "could not currently running shows for" (get-year-and-season))
          (error? mal-data) mal-data
          (error? ani-data) ani-data
          :else
          (->> (concat mal-data ani-data)
               (group-by :id)
               vals
               (transduce combined-xf f)
               map-result)))
      (UserError. "Please enter your MAL username in the settings"))))

(defn fetch-merged-data [mal-username xf]
  (transduce-merged-data mal-username xf conj identity))

(defn fetch-behind-schedule [mal-username]
  (prn "fetching backlog")
  (let [xf (comp (map #(conj % [:behind (behind-schedule %)]))
                 (filter (comp not zero? :behind)))]
    (fetch-merged-data mal-username xf)))

(defn fetch-countdowns [mal-username]
  (prn "fetching countdowns")
  (transduce-merged-data mal-username
                         (filter :nextAiringEpisode)
                         conj
                         (partial sort-by (comp :timeUntilAiring :nextAiringEpisode))))

;; TODO: find a better solution for this
(def app-icon "https://raw.githubusercontent.com/Funkschy/weebmanager/master/android/app/src/main/play_store_512.png")

(defn fetch-user-profile-picture [mal-username]
  (prn "fetching user pfp from jikan")
  (go
    (let [url  (str "https://api.jikan.moe/v4/users/" (url-escape mal-username))
          data (<! (get-url url {} {}))]
      (get-in data [:body :data :images :jpg :image_url] app-icon))))
