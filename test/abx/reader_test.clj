(ns abx.reader-test
  (:require [clojure.java.io :as jio]
            [abx.reader :as abx-reader]
            [clojure.test :refer [deftest is]]))

(deftest read-abx-test
  (with-open [reader (jio/input-stream "test-resources/mini-apk-xmls/AndroidManifest.xml")]
    (let [xml (abx-reader/read-abx reader)
          android-xmlns "http://schemas.android.com/apk/res/android"]
      (is (= (:tag xml) :manifest))
      (is (= (get-in xml [:attrs (keyword android-xmlns "versionCode")]) 1)))))
