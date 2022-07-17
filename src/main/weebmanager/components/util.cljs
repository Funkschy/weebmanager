(ns weebmanager.components.util
  (:require
   [clojure.string :as str]))

(defn pluralize [noun count]
  (str count " " noun (when (not= (abs count) 1) "s")))

(defn get-title [title-options preferred-title-language]
  (get title-options
       preferred-title-language
       (first (vals title-options))))

(defn- split-time-units
  "Turn something like 513376 seconds into [5 22 36] (days, hours, minutes) based on time-units"
  [time-units seconds]
  (loop [[div & div-tail] (map first time-units)
         time-left seconds
         values []]
    (if div-tail
      (recur div-tail (mod time-left div) (conj values (quot time-left div)))
      (conj values (quot time-left div)))))

(defn- join-time-formats [time-units seconds]
  (->> time-units
       (map second)
       (map vector (split-time-units time-units seconds))
       (remove (comp zero? first))
       (map (fn [[value unit]] (pluralize unit value)))
       (str/join " - ")))

(defn format-time-until [time-units seconds]
  (let [formatted (join-time-formats time-units seconds)]
    (if (empty? formatted)
      "less than one hour"
      formatted)))

(defn extract-additional-info [anime key-name-map]
  (reduce (fn [m [k v]]
            (let [[new-name map-value] (key-name-map k)
                  new-value (map-value v)]
              (if new-value
                (assoc m new-name new-value)
                m)))
          {}
          (select-keys anime (keys key-name-map))))
