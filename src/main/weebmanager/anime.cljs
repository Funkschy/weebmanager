(ns weebmanager.anime
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [weebmanager.error :refer [Error error?]]
   [weebmanager.config :refer [config]]))

(def client-id (get-in config [:mal :client-id]))

(defrecord HttpError [status-code error-code]
  Error
  (reason [_] (name error-code)))

(defrecord UserError [reason]
  Error
  (reason [_] reason))

(defn get-url [url & {:keys [params headers timeout-secs] :or {timeout-secs 3}}]
  (->> {:query-params params
        :headers headers
        :timeout (* timeout-secs 1000)}
       (http/get url)
       <!
       go))

(defn fetch-mal-api [url params timeout-secs]
  (go
    (let [{:keys [status body error-code]}
          (<! (get-url url
                       :params params
                       :headers {"X-MAL-CLIENT-ID" client-id}
                       :timeout-secs timeout-secs))]
      (if-not (= 200 status)
        (do (println "Could not get myanimelist data" status error-code)
            (HttpError. status error-code))
        body))))

(defn fetch-mal-api-seq
  "Creates a lazy sequence of paging entries, which will only perform their request when consumed"
  [start-url params timeout-secs]
  (letfn
   [(inner [url]
      (go
        (when-not (nil? url)
          (let [res (<! (fetch-mal-api url params timeout-secs))]
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

(defn fetch-mal-watching [username timeout-secs]
  (fetch-mal-api-seq (mal-url username "/animelist")
                     {"fields" "list_status"
                      "nsfw"   "true"
                      "status" "watching"
                      "limit"  500}
                     timeout-secs))

(def last-season-residue-query
  "query seasonal($page: Int = 1, $type: MediaType, $lastSeason: MediaSeason, $year: Int, $sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]) {
     Page(page: $page, perPage: 100) {
       media(type: $type, season: $lastSeason, seasonYear: $year, sort: $sort, episodes_greater: 12, status: RELEASING) {
         id: idMal
         status
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

(def airing-eps-query
  ;; TODO: fetch additional pages
  ;;   this will only get the first 50 (that's the actual limit) shows, but the user might need the
  ;;   rest too
  "query seasonal ($page: Int = 1, $type: MediaType, $season: MediaSeason,  $year: Int, $sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]) {
       Page(page: $page, perPage: 100) {
           media(type: $type, season: $season, seasonYear: $year, sort: $sort) {
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

(defn- fetch-anilist [query variables timeout-secs]
  (let [params {"query" query
                "variables" variables}]
    (go
      (let [{:keys [status body error-code]}
            (<! (http/post "https://graphql.anilist.co"
                           {:headers {"Content-Type" "application/json"}
                            :body (. js/JSON stringify (clj->js params))
                            :timeout (* timeout-secs 1000)}))]
        (if-not (= 200 status)
          (do (println "Could not get anilist data" status error-code)
              (HttpError. status error-code))
          (get-in body [:data :Page :media]))))))

(def seasons ["WINTER" "SPRING" "SUMMER" "FALL"])
(defn get-year-and-seasons []
  (let [date        (new js/Date)
        year        (.getFullYear date)
        season-int  (quot (.getMonth date) 3)

        [last-season-year last-season-int]
        (if (zero? season-int)
          [(dec year) 3] ;; current season = WINTER <=> last season was fall of last year
          [year (dec season-int)])]
    [year
     (get seasons season-int)
     last-season-year
     (get seasons last-season-int)]))

(defn fetch-anilist-airing [timeout-secs]
  (let [[year season] (get-year-and-seasons)]
    (fetch-anilist airing-eps-query
                   {"season" season
                    "type"   "ANIME"
                    "year"   year}
                   timeout-secs)))

(defn fetch-anilist-last-season-residue [timeout-secs]
  (let [[_ _ last-season-year last-season] (get-year-and-seasons)]
    (fetch-anilist last-season-residue-query
                   {"lastSeason" last-season
                    "type"       "ANIME"
                    "year"       last-season-year}
                   timeout-secs)))

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

(defn transduce-merged-data [mal-username timeout-secs xf f map-result]
  (go
    (if-not (empty? mal-username)
      (let [mal-data (fetch-mal-watching mal-username timeout-secs)
            ani-data (fetch-anilist-airing timeout-secs)
            res-data (fetch-anilist-last-season-residue timeout-secs)
            mal-data (<! mal-data)
            ani-data (<! ani-data)
            res-data (<! res-data)
            combined-xf (comp (filter #(= 2 (count %)))
                              (map (partial apply merge))
                              xf)]
        (cond
          (nil? mal-data) (println "could not fetch mal data for" mal-username)
          (nil? ani-data) (println "could not currently running shows for" (get-year-and-seasons))
          (nil? res-data) (println "could not last seasons shows")
          (error? mal-data) mal-data
          (error? ani-data) ani-data
          (error? res-data) res-data
          :else
          (->> (concat mal-data ani-data res-data)
               (group-by :id)
               vals
               (transduce combined-xf f)
               map-result)))
      (UserError. "Please enter your MAL username in the settings"))))

(defn fetch-merged-data [mal-username timeout-secs xf]
  (transduce-merged-data mal-username timeout-secs xf conj identity))

(defn fetch-behind-schedule [mal-username timeout-secs]
  (prn "fetching backlog for" mal-username)
  (let [xf (comp (map #(conj % [:behind (behind-schedule %)]))
                 (filter (comp not zero? :behind)))]
    (fetch-merged-data mal-username timeout-secs xf)))

(defn fetch-countdowns [mal-username timeout-secs]
  (prn "fetching countdowns for" mal-username)
  (transduce-merged-data mal-username
                         timeout-secs
                         (filter :nextAiringEpisode)
                         conj
                         (partial sort-by (comp :timeUntilAiring :nextAiringEpisode))))

(defn fetch-user-profile-picture [mal-username]
  (prn "fetching user pfp from jikan")
  (go
    (let [url  (str "https://api.jikan.moe/v4/users/" (url-escape mal-username))
          data (<! (get-url url {} {}))
          img  (get-in data [:body :data :images :jpg :image_url])]
      (if img
        (println "using image:" img)
        (println mal-username "has no pfp"))
      img)))
