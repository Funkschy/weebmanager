(ns weebmanager.components.util)

(defn pluralize [noun count]
  (str count " " noun (when (not= (abs count) 1) "s")))

(defn get-title [title-options preferred-title-language]
  (get title-options
       preferred-title-language
       (first (vals title-options))))
