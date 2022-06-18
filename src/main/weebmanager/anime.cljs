(ns weebmanager.anime
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [weebmanager.config :refer [config]]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]))

(def client-id (get-in config [:mal :client-id]))

(defn get-url [url params headers]
  (go (<! (http/get url {:query-params params :headers headers}))))

(defn fetch-mal-api [url params]
  (go
    (let [{:keys [status body]} (<! (get-url url params {"X-MAL-CLIENT-ID" client-id}))]
      (if (= 200 status)
        body
        (prn "status" status)))))

(defn fetch-mal-api-seq
  "Creates a lazy sequence of paging entries, which will only perform their request when consumed"
  [start-url params]
  (letfn [(inner [url]
            (go
              (when-not (nil? url)
                (let [{data :data {next-url :next} :paging} (<! (fetch-mal-api url params))]
                  (apply conj
                         (<! (inner next-url))
                         (reverse (map #(apply conj (vals %)) data)))))))]
    (inner start-url)))

(defn mal-url [username endpoint]
  (str "https://api.myanimelist.net/v2/users/"
       (js/encodeURIComponent username)
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
      (let [res (<! (http/post "https://graphql.anilist.co"
                               {:headers {"Content-Type" "application/json"}
                                :body (. js/JSON stringify (clj->js params))}))]
        (when (not= 200 (:status res))
          (println (str "Could not get anilist data " (:status res) " " (:body res))))
        (get-in res [:body :data :Page :media])))))

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

(defn fetch-behind-schedule [mal-username]
  (go
    (let [mal-data (<! (fetch-mal-watching mal-username))
          ani-data (<! (apply fetch-anilist-airing (get-year-and-season)))]
      (when (nil? mal-data)
        (println "could not fetch mal data for" mal-username))
      (when (nil? ani-data)
        (println "could not currently running shows for" (get-year-and-season)))
      (println mal-username "is watching" (count mal-data) "shows")

      (when (and mal-data ani-data)
        (->> (concat mal-data ani-data)
             (group-by :id)
             vals
             (filter #(= 2 (count %)))
             (map (partial apply merge))
             (map #(conj % [:behind (behind-schedule %)]))
             (filter (comp not zero? :behind)))))))

(defn fetch-countdowns [mal-username]
  (go
    (let [mal-data (<! (fetch-mal-watching mal-username))
          ani-data (<! (apply fetch-anilist-airing (get-year-and-season)))]
      (when (nil? mal-data)
        (println "could not fetch mal data for" mal-username))
      (when (nil? ani-data)
        (println "could not currently running shows for" (get-year-and-season)))
      (println mal-username "is watching" (count mal-data) "shows")

      (when (and mal-data ani-data)
        (->> (concat mal-data ani-data)
             (group-by :id)
             vals
             (filter #(= 2 (count %)))
             (map (partial apply merge))
             (filter :nextAiringEpisode)
             (map #(rename-keys % {:nextAiringEpisode :next-airing-episode})))))))
