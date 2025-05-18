# Abx.clj
Abx.clj is ABX(Android Binary XML) reader/writer Library for Clojure.


## Usage

```clj
(require '[abx.reader :as abx-rdr]
         '[clojure.java.io :as jio])

(with-open [rdr (jio/input-stream "./test.xml")]
  (abx-rdr/read-abx rdr))
;;=> #clojure.data.xml.Element{:tag :manifest, :attrs {:android/versionCode 1, :android/versionName "1.0", ...

```
