(ns com.fabriclj.swiss-knife.common.datagen.blockstates
  "瑞士军刀 - 数据生成: 方块状态

   提供方块状态文件( blockstates) 的生成功能。

   使用示例:
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.datagen.blockstates :as bs])

   ;; 简单方块( 单一模型)
   (bs/save-simple-blockstate! \"./src/main/resources\" \"mymod\" \"magic_ore\")

   ;; 旋转方块( 四个方向)
   (bs/save-rotatable-blockstate! \"./src/main/resources\" \"mymod\" \"furnace\")
   ```"
  (:require [com.fabriclj.swiss-knife.common.utils.json :as json]
            [clojure.java.io :as io]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 方块状态数据结构
;; ============================================================================

(defn simple-blockstate
  "创建简单方块状态( 单一模型，无变体)

   参数:
   - namespace: 命名空间
   - block-name: 方块名称

   返回: 方块状态 JSON 数据

   示例:
   ```clojure
   (simple-blockstate \"mymod\" \"magic_ore\")
   ; => {:variants {\"\" {:model \"mymod:block/magic_ore\"}}}
   ```"
  [namespace block-name]
  {:variants {"" {:model (str namespace ":block/" block-name)}}})

(defn rotatable-blockstate
  "创建可旋转方块状态( 水平四向)

   参数:
   - namespace: 命名空间
   - block-name: 方块名称
   - facing-property: 朝向属性名( 默认 \"facing\")

   示例:
   ```clojure
   (rotatable-blockstate \"mymod\" \"furnace\")
   ```"
  ([namespace block-name]
   (rotatable-blockstate namespace block-name "facing"))
  ([namespace block-name facing-property]
   (let [model (str namespace ":block/" block-name)]
     {:variants
      {(str facing-property "=north") {:model model}
       (str facing-property "=south") {:model model :y 180}
       (str facing-property "=west")  {:model model :y 270}
       (str facing-property "=east")  {:model model :y 90}}})))

(defn axis-blockstate
  "创建轴向方块状态( 如原木)

   参数:
   - namespace: 命名空间
   - block-name: 方块名称

   示例:
   ```clojure
   (axis-blockstate \"mymod\" \"log\")
   ```"
  [namespace block-name]
  (let [model (str namespace ":block/" block-name)]
    {:variants
     {"axis=y" {:model model}
      "axis=z" {:model model :x 90}
      "axis=x" {:model model :x 90 :y 90}}}))

(defn multipart-blockstate
  "创建多部分方块状态( 如栅栏)

   参数:
   - parts: 部分列表，每个部分包含 :when 和 :apply

   示例:
   ```clojure
   (multipart-blockstate
     [{:apply {:model \"mymod:block/fence_post\"}}
      {:when {:north true}
       :apply {:model \"mymod:block/fence_side\" :y 0}}
      {:when {:east true}
       :apply {:model \"mymod:block/fence_side\" :y 90}}])
   ```"
  [parts]
  {:multipart (vec parts)})

;; ============================================================================
;; 文件保存
;; ============================================================================

(defn- ensure-directory
  "确保目录存在"
  [^String path]
  (let [dir (io/file path)]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn save-blockstate!
  "保存方块状态文件

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - block-name: 方块名称
   - blockstate-data: 方块状态数据

   示例:
   ```clojure
   (save-blockstate! \"./src/main/resources\" \"mymod\" \"magic_ore\"
                     (simple-blockstate \"mymod\" \"magic_ore\"))
   ```"
  [base-path namespace block-name blockstate-data]
  (let [full-path (str base-path "/assets/" namespace "/blockstates/" block-name ".json")
        dir-path (subs full-path 0 (.lastIndexOf full-path "/"))]
    (ensure-directory dir-path)
    (with-open [writer (io/writer full-path)]
      (json/write blockstate-data writer {:pretty true}))
    (println "[DataGen] Generated blockstate:" full-path)))

(defn save-simple-blockstate!
  "保存简单方块状态

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - block-name: 方块名称

   示例:
   ```clojure
   (save-simple-blockstate! \"./src/main/resources\" \"mymod\" \"magic_ore\")
   ```"
  [base-path namespace block-name]
  (save-blockstate! base-path namespace block-name
                    (simple-blockstate namespace block-name)))

(defn save-rotatable-blockstate!
  "保存可旋转方块状态

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - block-name: 方块名称

   示例:
   ```clojure
   (save-rotatable-blockstate! \"./src/main/resources\" \"mymod\" \"furnace\")
   ```"
  [base-path namespace block-name]
  (save-blockstate! base-path namespace block-name
                    (rotatable-blockstate namespace block-name)))

(defn save-axis-blockstate!
  "保存轴向方块状态

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - block-name: 方块名称

   示例:
   ```clojure
   (save-axis-blockstate! \"./src/main/resources\" \"mymod\" \"magic_log\")
   ```"
  [base-path namespace block-name]
  (save-blockstate! base-path namespace block-name
                    (axis-blockstate namespace block-name)))

;; ============================================================================
;; 批量生成
;; ============================================================================

(defn generate-simple-blockstates!
  "批量生成简单方块状态

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - block-names: 方块名称列表

   示例:
   ```clojure
   (generate-simple-blockstates! \"./src/main/resources\" \"mymod\"
                                 [\"copper_ore\" \"tin_ore\" \"zinc_ore\"])
   ```"
  [base-path namespace block-names]
  (doseq [block-name block-names]
    (save-simple-blockstate! base-path namespace block-name)))

(comment
  ;; 使用示例

  ;; 1. 简单方块
  (save-simple-blockstate! "./src/main/resources" "mymod" "magic_ore")

  ;; 2. 可旋转方块
  (save-rotatable-blockstate! "./src/main/resources" "mymod" "furnace")

  ;; 3. 轴向方块
  (save-axis-blockstate! "./src/main/resources" "mymod" "log")

  ;; 4. 批量生成
  (generate-simple-blockstates! "./src/main/resources" "mymod"
                                ["copper_ore" "tin_ore" "zinc_ore"])

  ;; 5. 多部分方块( 栅栏)
  (save-blockstate! "./src/main/resources" "mymod" "fence"
                    (multipart-blockstate
                      [{:apply {:model "mymod:block/fence_post"}}
                       {:when {:north "true"}
                        :apply {:model "mymod:block/fence_side" :y 0}}
                       {:when {:east "true"}
                        :apply {:model "mymod:block/fence_side" :y 90}}
                       {:when {:south "true"}
                        :apply {:model "mymod:block/fence_side" :y 180}}
                       {:when {:west "true"}
                        :apply {:model "mymod:block/fence_side" :y 270}}])))
