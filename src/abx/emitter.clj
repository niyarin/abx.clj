(ns abx.emitter
  (:require [clojure.data.xml :as xml])
  (:import (javax.xml.stream XMLOutputFactory)
           (java.io StringWriter)))

(defn emit
  ([e stream]
   (emit e stream "android" "http://schemas.android.com/apk/res/android"))
  ([e stream prefix uri]
    (let [wtr (.createXMLStreamWriter
                    (XMLOutputFactory/newInstance)
                    stream)]
     (.setPrefix wtr prefix uri)
     (.writeStartDocument wtr "UTF-8" "1.0")
     (doseq [event (xml/flatten-elements [e])]
       (xml/emit-event event wtr))
     (.writeEndDocument wtr))))

(defn emit-str [e]
  (let [swtr  (StringWriter.)]
    (emit e swtr)
    (.toString swtr)))
