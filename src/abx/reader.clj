(ns abx.reader
  (:require [clojure.data.xml :as xml]))

(defn- read-2byte-le [brdr]
  (let [b1 (.read brdr)
        b2 (.read brdr)]
    (bit-or b1 (bit-shift-left b2 8))))

(defn- read-4byte-le [brdr]
  (let [b1 (.read brdr)
        b2 (.read brdr)
        b3 (.read brdr)
        b4 (.read brdr)]
    (bit-or b1
            (bit-shift-left b2 8)
            (bit-shift-left b3 16)
            (bit-shift-left b4 24))))

(defn- error
  ([text] (error text nil))
  ([text mp] (throw (ex-info text mp))))

(defn- handle-file-header [brdr]
  (let [sign (read-2byte-le brdr)
        header-size (read-2byte-le brdr)
        file-size (read-4byte-le brdr)]
    (if (and (= sign 3) (= header-size 8))
      file-size
      (error "Invalid file header."
             {:sign sign :header-size header-size}))))

(defn- integer->pool-flags [integer]
  (cond-> #{}
    (not (zero? (bit-and integer 1))) (conj :sorted)
    (not (zero? (bit-and integer 128))) (conj :utf-8)))

(defn- handle-pool-header [brdr]
  (let [sign (read-2byte-le brdr)
        header-size (read-2byte-le brdr)]
    (if (and (= sign 1) (= header-size 28))
      (let [pool-size (read-4byte-le brdr)
            n-pooled-string (read-4byte-le brdr)
            n-style-span-arrays (read-4byte-le brdr)
            flag (integer->pool-flags (read-4byte-le brdr))
            pooled-string-offset (read-4byte-le brdr)
            style-offset (read-4byte-le brdr)]
        {:pool-size pool-size
         :n-pooled-string n-pooled-string
         :n-style-span-arrays n-style-span-arrays
         :flag flag
         :pooled-string-offset pooled-string-offset
         :style-offset style-offset})
      (error "Invalid pool header."
             {:sign sign :header-size header-size}))))

(defn- read-offset-tables [brdr n-pooled-string]
  (mapv (fn [_] (read-4byte-le brdr)) (range n-pooled-string)))

(defn- read-each-string-pool [brdr]
  ;;reading-utf16
  (let [len (read-2byte-le brdr)]
    (->> (range len)
         (mapv (fn [_] (char (read-2byte-le brdr))))
         (apply str))))

(defn- skip-bytes [brdr n-skips]
  (dotimes [_ n-skips]
    (.read brdr)))

(defn- read-string-pool [brdr offsets {:keys [pool-size n-pooled-string]}]
  (let [last-offset (- pool-size 28 (* n-pooled-string 4))
        offset-table (conj offsets last-offset)]
    (mapv (fn [index]
            (let [res (read-each-string-pool brdr)]
              (skip-bytes brdr (- (nth offset-table (inc index))
                                  (+ (nth offset-table index)
                                     2
                                     (* 2 (count res)))))

              res))
          (range (count offsets)))))

(defn- handle-xml-info-header [brdr]
  (let [sign (read-2byte-le brdr)
        header-size (read-2byte-le brdr)
        file-size (read-4byte-le brdr)]
    (if (and (= sign 384) (= header-size 8))
      file-size
      (error "Invalid xml-info header."
             {:sign sign :header-size header-size}))))

(defn- read-attribute-id-table [brdr n-attribute-ids]
  (mapv (fn [_] (read-4byte-le brdr)) (range n-attribute-ids)))

(defn- read-namespace-info-node [brdr string-pool]
  (let [namespace' (nth string-pool (read-4byte-le brdr))
        uri (nth string-pool (read-4byte-le brdr))]
    {uri namespace'}))

(defn read-start-tag  [brdr]
  ;without sign
  (let [node-size (read-2byte-le brdr)
        total-node-size (read-4byte-le brdr)
        line-number (read-4byte-le brdr)
        comment-number (let [v (read-4byte-le brdr)]
                         (when-not (= v 0xffffffff) v))]
    {:line-number line-number
     :comment-number comment-number}))

(defn- read-start-treenode [brdr]
  (let [sign (read-2byte-le brdr)
        node-size (read-2byte-le brdr)
        tree+namespace-size (read-4byte-le brdr)
        linenumber (read-4byte-le brdr)
        comment-number (let [v (read-4byte-le brdr)]
                         (when-not (= v 0xffffffff) v))]
    (if (and (= sign 0x0100) (= node-size 16))
      {:linenumber linenumber
       :comment-number comment-number}
      (error "Invalid node." {:sign sign :node-size node-size}))))

(defn- read-start-extended-node [brdr string-pool]
  (let [namespace-pool-id (read-4byte-le brdr)
        tag-name (nth string-pool (read-4byte-le brdr))
        offset (read-2byte-le brdr)
        size (read-2byte-le brdr)
        n-attributes (read-2byte-le brdr)
        id (read-2byte-le brdr)
        class' (read-2byte-le brdr)
        style (read-2byte-le brdr)]
    {:tag-name tag-name
     :offset offset
     :class class'
     :style style
     :n-attributes n-attributes}))

(defn- read-attribute-info-node [brdr string-pool]
  (let [namespace' (let [index (read-4byte-le brdr)]
                     (when-not (= index 0xffffffff)
                       (nth string-pool index)))
        key' (nth string-pool (read-4byte-le brdr))
        val' (let [index (read-4byte-le brdr)]
               (when-not (= index 0xffffffff) (nth string-pool index)))]
    {:namespace namespace'
     :key key'
     :value val'}))

(def attribute-type-map
  {0x00 :null
   0x01 :reference
   0x02 :attribute
   0x03 :string
   0x04 :float
   0x05 :dimension
   0x06 :fraction
   0x07 :dynamic-reference
   0x08 :dynamic-attribute
   0x10 :int
   0x11 :hex
   0x12 :boolean
   0x1c :color-int
   0x1d :color-argb8
   0x1e :color-argb4
   0x1f :color-rgb4})

(defn- read-sttribute-value-node [brdr]
  (let [size (read-2byte-le brdr)
        _ (.read brdr)
        type' (get attribute-type-map (.read brdr))
        value (read-4byte-le brdr)]
    {:size size
     :type type'
     :value value}))

(defn- read-node-attribute [brdr string-pool]
  (let [info (read-attribute-info-node brdr string-pool)
        value (read-sttribute-value-node brdr)]
    [(keyword (:namespace info) (:key info))
     (if (:value info)
       (:value info)
       (:value value))]))

(defn- read-open-node [brdr string-pool]
  (let [start-tag (read-start-tag brdr)
        extended-tag (read-start-extended-node brdr string-pool)
        attributes
        (->> (range (:n-attributes extended-tag))
             (mapv (fn [_] (read-node-attribute brdr string-pool)))
             (into {}))]
    {:tag (keyword (:tag-name extended-tag))
     :meta start-tag
     :attributes attributes
     :open-or-close :open}))

(defn- read-close-tag [brdr string-pool]
  (let [size (read-2byte-le brdr)
        header-size (read-4byte-le brdr)
        linenumber (read-4byte-le brdr)
        comment-number (let [v (read-4byte-le brdr)]
                         (when-not (= v 0xffffffff) v))]
    {:linenumber linenumber
     :comment-number comment-number}))

(defn- read-extended-close-tag [brdr string-pool]
  (let [namespace (let [v (read-4byte-le brdr)]
                    (when-not (= v 0xffffffff)
                      (nth string-pool v)))
        tag-name (nth string-pool (read-4byte-le brdr))]
    {:namespace namespace
     :tag-name tag-name}))

(defn- read-close-node [brdr string-pool]
  (let [close-tag (read-close-tag brdr string-pool)
        extended-tag (read-extended-close-tag brdr string-pool)]
    {:tag extended-tag
     :meta close-tag
     :open-or-close :close}))

(defn- read-node [brdr string-pool]
  (let [sign (read-2byte-le  brdr)]
    (case sign
      0x0102 (read-open-node brdr string-pool)
      0x0103 (read-close-node brdr string-pool))))

(defn- read-flatten [brdr string-pool]
  (loop [node (read-node brdr string-pool)
         nest 0
         res []]
    (case (:open-or-close node)
      :open (recur (read-node brdr string-pool)
                   (inc nest)
                   (conj res node))
      :close (let [new-nest (dec nest)]
               (if (zero? new-nest)
                 (conj res node)
                 (recur (read-node brdr string-pool)
                        new-nest
                        (conj res node)))))))

(defn- read-end-treenode [brdr]
  (let [sign (read-2byte-le  brdr)
        node-size (read-2byte-le brdr)
        _ (read-4byte-le brdr)
        linenumber (read-4byte-le brdr)
        comment (let [v (read-4byte-le brdr)]
                  (when-not (= v 0xffffffff)
                    v))]
    (if (and (= sign 0x0101) (= node-size 16))
      {:linenumber linenumber
       :comment comment}
      (error "Invalid end treenode."
             {:sign sign :node-size node-size}))))

(defn read-abx-as-flatten [brdr]
  (handle-file-header brdr)
  (let [string-pool-header (handle-pool-header brdr)
        offset-table (read-offset-tables
                       brdr (:n-pooled-string string-pool-header))
        string-pool (read-string-pool brdr offset-table string-pool-header)]
    (let [n-attribute-ids (quot (- (handle-xml-info-header brdr) 8) 4)]
      (read-attribute-id-table brdr n-attribute-ids)
      (read-start-treenode brdr)
      (let [uri->ns (read-namespace-info-node brdr string-pool)]
        (let [tree (read-flatten brdr string-pool)]
          (read-end-treenode brdr)
          (read-namespace-info-node brdr string-pool)
          tree)))))

(defn- node->xml-element [{:keys [tag attributes] meta' :meta} children]
  (with-meta (apply xml/element tag attributes children)
             meta'))

(defn- construct-xml* [flatten-tree]
  (let [start-node (first flatten-tree)]
    (if (= (:open-or-close start-node) :open)
      (loop [[node next-flatten-tree] (construct-xml* (next flatten-tree))
             children []]
        (if (= node :close)
          [(node->xml-element start-node children) next-flatten-tree]
          (recur (construct-xml* next-flatten-tree)
                 (conj children node))))
      [:close (next flatten-tree)])))

(defn- construct-xml [flatten-tree]
  (first (construct-xml* flatten-tree)))

(defn read-abx [brdr]
  (construct-xml (read-abx-as-flatten brdr)))
