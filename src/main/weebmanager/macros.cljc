(ns weebmanager.macros)

(defmacro react-$
  ([tag]
   `(uix.core/as-react
     (fn [^js props#]
       (uix.core/$ ~tag (~'js->clj (.-argv props#) :keywordize-keys true)))))
  ([tag props & children]
   `(uix.core/as-react
     (fn [^js props#]
       (uix.core/$ ~tag
                   (merge (~'js->clj ~props :keywordize-keys true)
                          (~'js->clj (.-argv props#) :keywordize-keys true)) ~@children)))))
