(ns abx.writer)

(defn deconstruct-xml [{:keys [attributes tag content] :as xml}]
  (concat
   [{:tag tag
     :meta (meta xml)
     :attributes attributes
     :open-or-close :open}]
   (mapcat deconstruct-xml content)
   [{:tag tag
     :meta (meta xml)
     :open-or-close :close}]))
