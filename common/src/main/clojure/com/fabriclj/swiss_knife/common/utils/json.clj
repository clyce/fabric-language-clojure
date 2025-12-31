(ns com.fabriclj.swiss-knife.common.utils.json
  "JSON 工具 - 使用 Minecraft 自带的 Gson 库

   提供 JSON 编码和解码功能，使用 Minecraft 内置的 Gson 库，
   避免引入外部依赖。"
  (:import (com.google.gson Gson GsonBuilder JsonParser JsonElement)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; Gson 实例
;; ============================================================================

(defonce ^:private gson-instance
  (-> (GsonBuilder.)
      (.setPrettyPrinting)
      (.setLenient)
      (.create)))

(defonce ^:private gson-compact
  (Gson.))

;; ============================================================================
;; JSON 编码（Clojure 数据 -> JSON 字符串）
;; ============================================================================

(defn- clj->gson
  "将 Clojure 数据转换为 Gson 可处理的类型"
  [data]
  (cond
    (nil? data) nil
    (string? data) data
    (number? data) data
    (boolean? data) data
    (keyword? data) (name data)
    (map? data)
    (let [m (java.util.HashMap.)]
      (doseq [[k v] data]
        (.put m (if (keyword? k) (name k) (str k)) (clj->gson v)))
      m)
    (vector? data)
    (let [l (java.util.ArrayList.)]
      (doseq [item data]
        (.add l (clj->gson item)))
      l)
    (seq? data)
    (let [l (java.util.ArrayList.)]
      (doseq [item data]
        (.add l (clj->gson item)))
      l)
    :else (str data)))

(defn generate-string
  "生成 JSON 字符串

   参数:
   - data: Clojure 数据（Map, Vector, 等）
   - options: 选项映射，支持 :pretty（是否美化输出）

   返回: JSON 字符串

   示例:
   ```clojure
   (generate-string {:foo \"bar\" :baz 5})
   (generate-string {:foo \"bar\"} {:pretty true})
   ```"
  ([data]
   (generate-string data {}))
  ([data options]
   (let [gson (if (:pretty options true)
                gson-instance
                gson-compact)
         gson-data (clj->gson data)]
     (.toJson gson gson-data))))

(defn write
  "将 JSON 数据写入 Writer

   参数:
   - data: Clojure 数据
   - writer: java.io.Writer
   - options: 选项映射，支持 :pretty, :indent, :escape-slash

   示例:
   ```clojure
   (with-open [writer (io/writer \"file.json\")]
     (json/write {:foo \"bar\"} writer {:pretty true}))
   ```"
  ([data writer]
   (write data writer {}))
  ([data writer options]
   (let [gson (if (or (:pretty options) (:indent options))
                gson-instance
                gson-compact)
         gson-data (clj->gson data)]
     (.toJson gson gson-data writer))))

;; 兼容性别名
(def write-str generate-string)

;; ============================================================================
;; JSON 解码（JSON 字符串 -> Clojure 数据）
;; ============================================================================

(defn- gson-element->clj
  "将 Gson JsonElement 转换为 Clojure 数据"
  [element]
  (cond
    (.isJsonNull element) nil
    (.isJsonObject element)
    (let [obj (.getAsJsonObject element)
          entry-set (.entrySet obj)]
      (into {}
            (map (fn [entry]
                   [(keyword (.getKey entry)) (gson-element->clj (.getValue entry))])
                 entry-set)))
    (.isJsonArray element)
    (let [arr (.getAsJsonArray element)]
      (mapv gson-element->clj (range (.size arr))))
    (.isJsonPrimitive element)
    (let [primitive (.getAsJsonPrimitive element)]
      (cond
        (.isBoolean primitive) (.getAsBoolean primitive)
        (.isNumber primitive)
        (let [num-str (.getAsString primitive)]
          (if (or (.contains num-str ".") (.contains num-str "e") (.contains num-str "E"))
            (Double/parseDouble num-str)
            (Long/parseLong num-str)))
        :else (.getAsString primitive)))
    :else (str element)))

(defn parse-string
  "解析 JSON 字符串

   参数:
   - json-str: JSON 字符串
   - key-fn: 键转换函数（可选，默认 keyword）

   返回: Clojure 数据

   示例:
   ```clojure
   (parse-string \"{\\\"foo\\\":\\\"bar\\\"}\")
   (parse-string \"{\\\"foo\\\":\\\"bar\\\"}\" keyword)
   ```"
  ([json-str]
   (parse-string json-str keyword))
  ([json-str key-fn]
   (let [parser (JsonParser.)
         element (.parse parser json-str)
         result (gson-element->clj element)]
     (if (and (= key-fn keyword) (map? result))
       result
       (if (map? result)
         (into {} (map (fn [[k v]] [(key-fn (name k)) v]) result))
         result)))))
