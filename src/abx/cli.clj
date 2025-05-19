(ns abx.cli
  (:require [clojure.java.io :as jio]
            [abx.reader :as abx-rdr]
            [abx.emitter :as emitter]))

(defn run-decode* [input-file output-file]
  (with-open [rdr (jio/input-stream input-file)
              wtr (jio/output-stream output-file)]
    (emitter/emit (abx-rdr/read-abx rdr)
                  wtr)))

(defn run-decode [tail-args]
  (loop [args tail-args
         input-file nil
         output-file nil]
    (cond
      (empty? args) (run-decode* input-file output-file)

      (= (first args) "--input-file")
      (recur (nnext args) (second args) output-file)

      (= (first args) "--output-file")
      (recur (nnext args) input-file (second args))

      :else 'error)))

(defn -main [& args]
  (when (seq args)
    (case (first args)
      "decode" (run-decode (next args))
      :else 'error)))
