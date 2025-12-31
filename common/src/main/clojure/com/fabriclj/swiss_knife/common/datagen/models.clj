(ns com.fabriclj.swiss-knife.common.datagen.models
  "瑞士军刀 - 数据生成: 模型

   提供物品和方块模型的生成功能。

   使用示例:
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.datagen.models :as models])

   ;; 生成简单物品模型
   (models/save-item-model! \"./src/main/resources\" \"mymod\" \"magic_sword\"
     {:parent \"minecraft:item/generated\"
      :textures {:layer0 \"mymod:item/magic_sword\"}})

   ;; 生成方块模型
   (models/save-block-model! \"./src/main/resources\" \"mymod\" \"magic_ore\"
     {:parent \"minecraft:block/cube_all\"
      :textures {:all \"mymod:block/magic_ore\"}})
   ```"
  (:require [com.fabriclj.swiss-knife.common.utils.json :as json]
            [clojure.java.io :as io]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 模型数据结构
;; ============================================================================

(defn item-model
  "创建物品模型数据

   参数:
   - parent: 父模型路径( 如 \"minecraft:item/generated\")
   - textures: 纹理映射( 如 {:layer0 \"mymod:item/sword\"})
   - display: 可选，显示设置

   示例:
   ```clojure
   (item-model \"minecraft:item/generated\"
               {:layer0 \"mymod:item/gem\"})
   ```"
  ([parent textures]
   (item-model parent textures nil))
  ([parent textures display]
   (cond-> {:parent parent
            :textures textures}
     display (assoc :display display))))

(defn block-model
  "创建方块模型数据

   参数:
   - parent: 父模型路径
   - textures: 纹理映射
   - elements: 可选，自定义元素

   示例:
   ```clojure
   (block-model \"minecraft:block/cube_all\"
                {:all \"mymod:block/ore\"})
   ```"
  ([parent textures]
   (block-model parent textures nil))
  ([parent textures elements]
   (cond-> {:parent parent
            :textures textures}
     elements (assoc :elements elements))))

(defn handheld-item-model
  "创建手持物品模型( 如工具、武器)

   参数:
   - texture-path: 纹理路径

   示例:
   ```clojure
   (handheld-item-model \"mymod:item/magic_sword\")
   ```"
  [texture-path]
  {:parent "minecraft:item/handheld"
   :textures {:layer0 texture-path}})

(defn generated-item-model
  "创建生成的物品模型( 如材料、食物)

   参数:
   - texture-path: 纹理路径
   - layers: 可选，额外的纹理层

   示例:
   ```clojure
   (generated-item-model \"mymod:item/gem\")
   (generated-item-model \"mymod:item/potion\"
                         {:layer1 \"mymod:item/potion_overlay\"})
   ```"
  ([texture-path]
   (generated-item-model texture-path nil))
  ([texture-path layers]
   (let [base-textures {:layer0 texture-path}
         all-textures (if layers
                        (merge base-textures layers)
                        base-textures)]
     {:parent "minecraft:item/generated"
      :textures all-textures})))

(defn cube-all-block-model
  "创建六面同纹理的立方体方块模型

   参数:
   - texture-path: 纹理路径

   示例:
   ```clojure
   (cube-all-block-model \"mymod:block/magic_ore\")
   ```"
  [texture-path]
  {:parent "minecraft:block/cube_all"
   :textures {:all texture-path}})

(defn cube-block-model
  "创建六面不同纹理的立方体方块模型

   参数:
   - textures: 纹理映射 {:down :up :north :south :west :east :particle}

   示例:
   ```clojure
   (cube-block-model {:down \"mymod:block/ore_bottom\"
                      :up \"mymod:block/ore_top\"
                      :north \"mymod:block/ore_side\"
                      :south \"mymod:block/ore_side\"
                      :west \"mymod:block/ore_side\"
                      :east \"mymod:block/ore_side\"
                      :particle \"mymod:block/ore_side\"})
   ```"
  [textures]
  {:parent "minecraft:block/cube"
   :textures textures})

(defn cube-column-block-model
  "创建柱状方块模型( 上下和侧面不同纹理)

   参数:
   - end-texture: 顶部和底部纹理
   - side-texture: 侧面纹理

   示例:
   ```clojure
   (cube-column-block-model \"mymod:block/log_top\"
                            \"mymod:block/log_side\")
   ```"
  [end-texture side-texture]
  {:parent "minecraft:block/cube_column"
   :textures {:end end-texture
              :side side-texture}})

;; ============================================================================
;; 文件保存
;; ============================================================================

(defn- ensure-directory
  "确保目录存在"
  [^String path]
  (let [dir (io/file path)]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn save-model!
  "保存模型到文件

   参数:
   - base-path: 基础路径( 如 \"./src/main/resources\")
   - namespace: 命名空间( mod id)
   - path: 相对路径( 如 \"block/magic_ore\" 或 \"item/magic_sword\")
   - model-data: 模型数据( map)

   示例:
   ```clojure
   (save-model! \"./src/main/resources\" \"mymod\" \"block/ore\"
                (cube-all-block-model \"mymod:block/ore\"))
   ```"
  [base-path namespace path model-data]
  (let [full-path (str base-path "/assets/" namespace "/models/" path ".json")
        dir-path (subs full-path 0 (.lastIndexOf full-path "/"))]
    (ensure-directory dir-path)
    (with-open [writer (io/writer full-path)]
      (json/write model-data writer {:pretty true}))
    (println "[DataGen] Generated model:" full-path)))

(defn save-item-model!
  "保存物品模型

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - item-name: 物品名称
   - model-data: 模型数据

   示例:
   ```clojure
   (save-item-model! \"./src/main/resources\" \"mymod\" \"magic_sword\"
                     (handheld-item-model \"mymod:item/magic_sword\"))
   ```"
  [base-path namespace item-name model-data]
  (save-model! base-path namespace (str "item/" item-name) model-data))

(defn save-block-model!
  "保存方块模型

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - block-name: 方块名称
   - model-data: 模型数据

   示例:
   ```clojure
   (save-block-model! \"./src/main/resources\" \"mymod\" \"magic_ore\"
                      (cube-all-block-model \"mymod:block/magic_ore\"))
   ```"
  [base-path namespace block-name model-data]
  (save-model! base-path namespace (str "block/" block-name) model-data))

;; ============================================================================
;; 批量生成
;; ============================================================================

(defn generate-simple-items!
  "批量生成简单物品模型

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - item-names: 物品名称列表
   - type: 模型类型，:generated( 默认) 或 :handheld

   示例:
   ```clojure
   (generate-simple-items! \"./src/main/resources\" \"mymod\"
                           [\"ruby\" \"sapphire\" \"emerald\"]
                           :generated)

   (generate-simple-items! \"./src/main/resources\" \"mymod\"
                           [\"iron_sword\" \"iron_axe\"]
                           :handheld)
   ```"
  ([base-path namespace item-names]
   (generate-simple-items! base-path namespace item-names :generated))
  ([base-path namespace item-names type]
   (doseq [item-name item-names]
     (let [texture-path (str namespace ":item/" item-name)
           model-data (case type
                        :handheld (handheld-item-model texture-path)
                        :generated (generated-item-model texture-path)
                        (generated-item-model texture-path))]
       (save-item-model! base-path namespace item-name model-data)))))

(defn generate-simple-blocks!
  "批量生成简单方块模型( cube_all)

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - block-names: 方块名称列表

   示例:
   ```clojure
   (generate-simple-blocks! \"./src/main/resources\" \"mymod\"
                            [\"copper_ore\" \"tin_ore\" \"zinc_ore\"])
   ```"
  [base-path namespace block-names]
  (doseq [block-name block-names]
    (let [texture-path (str namespace ":block/" block-name)
          model-data (cube-all-block-model texture-path)]
      (save-block-model! base-path namespace block-name model-data))))

(comment
  ;; 使用示例

  ;; 1. 生成单个物品模型
  (save-item-model! "./src/main/resources" "mymod" "magic_sword"
                    (handheld-item-model "mymod:item/magic_sword"))

  ;; 2. 生成方块模型
  (save-block-model! "./src/main/resources" "mymod" "magic_ore"
                     (cube-all-block-model "mymod:block/magic_ore"))

  ;; 3. 批量生成物品
  (generate-simple-items! "./src/main/resources" "mymod"
                          ["ruby" "sapphire" "emerald"])

  ;; 4. 批量生成方块
  (generate-simple-blocks! "./src/main/resources" "mymod"
                           ["copper_ore" "tin_ore"])

  ;; 5. 生成复杂方块
  (save-block-model! "./src/main/resources" "mymod" "log"
                     (cube-column-block-model "mymod:block/log_top"
                                              "mymod:block/log_side")))
