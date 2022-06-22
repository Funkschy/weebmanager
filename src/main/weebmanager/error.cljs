(ns weebmanager.error)

(defprotocol Error
  (reason [this] "return the reason as a human readable string"))

(defn error? [x]
  (satisfies? Error x))
