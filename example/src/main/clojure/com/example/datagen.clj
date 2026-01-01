(ns com.example.datagen
  "示例 Mod - DataGen 资源文件生成

   演示如何使用 Swiss Knife 的 DataGen 工具自动生成:
   - 物品模型( item models)
   - 方块模型( block models)
   - 方块状态( blockstates)
   - 语言文件( lang files)
   - 占位纹理( placeholder textures)

   使用方法:
   在 nREPL 中运行 (generate-all-assets!) 自动生成所有资源文件。"
  (:require [com.fabriclj.swiss-knife.common.datagen.models :as models]
            [com.fabriclj.swiss-knife.common.datagen.blockstates :as bs]
            [com.fabriclj.swiss-knife.common.datagen.lang :as lang]
            [clojure.java.io :as io])
  (:import (java.awt.image BufferedImage)
           (javax.imageio ImageIO)
           (java.awt Color Graphics2D)))

;; ============================================================================
;; 资源定义
;; ============================================================================

(def items-to-generate
  "需要生成模型的物品列表"
  {:generated ["magic_shard" "forest_soul_potion" "nature_affinity_book"]
   :handheld []})

(def blocks-to-generate
  "需要生成模型和状态的方块列表"
  ["magic_crystal_ore"])

;; ============================================================================
;; 模型生成
;; ============================================================================

(defn generate-item-models!
  "生成所有物品模型"
  []
  (let [base-path "./src/main/resources"]
    ;; 生成普通物品( generated 模型)
    (doseq [item (:generated items-to-generate)]
      (models/save-item-model! base-path "example" item
        (models/generated-item-model (str "example:item/" item))))

    ;; 生成手持物品( handheld 模型，如剑、工具)
    (doseq [item (:handheld items-to-generate)]
      (models/save-item-model! base-path "example" item
        (models/handheld-item-model (str "example:item/" item))))

    ;; 魔法宝石 - 特殊的发光物品( 带额外层)
    (models/save-item-model! base-path "example" "magic_gem"
      (models/generated-item-model "example:item/magic_gem"
                                    {:layer1 "example:item/magic_gem_glow"}))

    (println "[DataGen] Generated"
             (+ (count (:generated items-to-generate))
                (count (:handheld items-to-generate))
                1)
             "item models")))

(defn generate-block-models!
  "生成所有方块模型"
  []
  (let [base-path "./src/main/resources"]
    ;; 魔法水晶矿 - 发光方块
    (models/save-block-model! base-path "example" "magic_crystal_ore"
      {:parent "minecraft:block/cube_all"
       :textures {:all "example:block/magic_crystal_ore"}})

    ;; 同时生成方块物品模型( 继承方块模型)
    ;; 注意: core.clj 中注册为 "magic_crystal_ore_item"，所以我们也生成这个名称
    (models/save-item-model! base-path "example" "magic_crystal_ore_item"
      {:parent "example:block/magic_crystal_ore"})

    (println "[DataGen] Generated" (count blocks-to-generate) "block models")))

;; ============================================================================
;; 方块状态生成
;; ============================================================================

(defn generate-blockstates!
  "生成所有方块状态文件"
  []
  (let [base-path "./src/main/resources"]
    ;; 简单方块( 无变体)
    (bs/save-simple-blockstate! base-path "example" "magic_crystal_ore")

    (println "[DataGen] Generated" (count blocks-to-generate) "blockstates")))

;; ============================================================================
;; 语言文件生成
;; ============================================================================

(defn generate-lang-files!
  "生成所有语言文件"
  []
  (let [base-path "./src/main/resources"]
    ;; 英文
    (lang/create-lang-file! base-path "example" "en_us"
      {:item {:magic_gem "Magic Gem"
              :magic_shard "Magic Shard"
              :magic_crystal_ore "Magic Crystal Ore"
              :forest_soul_potion "Forest Soul Potion"
              :nature_affinity_book "Nature Affinity Book"}
       :block {:magic_crystal_ore "Magic Crystal Ore"}
       :entity {:forest_guardian "Forest Guardian"}
       :custom {"example.welcome" "Welcome to the Magic World!"
                "example.gem_activated" "Magic Gem Activated!"
                "example.teleport_success" "Teleported successfully!"
                "key.example.special_ability" "Special Ability (Teleport)"}})

    ;; 中文
    (lang/create-lang-file! base-path "example" "zh_cn"
      {:item {:magic_gem "魔法宝石"
              :magic_shard "魔法碎片"
              :magic_crystal_ore "魔法水晶矿"
              :forest_soul_potion "森林之魂药水"
              :nature_affinity_book "自然亲和附魔书"}
       :block {:magic_crystal_ore "魔法水晶矿"}
       :entity {:forest_guardian "森林守卫"}
       :custom {"example.welcome" "欢迎来到魔法世界！"
                "example.gem_activated" "魔法宝石已激活！"
                "example.teleport_success" "传送成功！"
                "key.example.special_ability" "特殊能力( 传送) "}})

    (println "[DataGen] Generated language files: en_us, zh_cn")))

;; ============================================================================
;; 纹理生成 - 占位纹理
;; ============================================================================

(def texture-colors
  "物品和方块的占位纹理颜色映射"
  {:magic_gem [255 100 255]        ; 品红色（魔法宝石）
   :magic_gem_glow [255 200 255]   ; 浅品红色（发光层）
   :magic_shard [150 50 255]       ; 紫色（魔法碎片）
   :forest_soul_potion [50 200 50] ; 绿色（森林之魂药水）
   :nature_affinity_book [100 150 100] ; 浅绿色（自然亲和书）
   :magic_crystal_ore [200 100 255] ; 紫罗兰色（魔法水晶矿）
   })

(defn create-placeholder-texture
  "创建占位纹理 PNG 图像

   参数:
   - width: 图像宽度( 默认 16)
   - height: 图像高度( 默认 16)
   - color: RGB 颜色 [r g b] 或整数颜色值

   返回: BufferedImage"
  ([color]
   (create-placeholder-texture 16 16 color))
  ([width height color]
   (let [image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
         graphics (.createGraphics image)
         [r g b] (if (vector? color) color [255 0 0])]
     (try
       (.setColor graphics (Color. r g b))
       (.fillRect graphics 0 0 width height)
       ;; 添加简单的边框以区分纹理
       (.setColor graphics (.darker (Color. r g b)))
       (.drawRect graphics 0 0 (dec width) (dec height))
       image
       (finally
         (.dispose graphics))))))

(defn save-texture!
  "保存纹理 PNG 文件

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - texture-type: :item 或 :block
   - texture-name: 纹理名称( 不含扩展名)
   - image: BufferedImage

   示例:
   ```clojure
   (save-texture! \"./src/main/resources\" \"example\" :item \"magic_gem\"
                  (create-placeholder-texture [255 100 255]))
   ```"
  [base-path namespace texture-type texture-name image]
  (let [type-str (name texture-type)
        full-path (str base-path "/assets/" namespace "/textures/" type-str "/" texture-name ".png")
        dir-path (subs full-path 0 (.lastIndexOf full-path "/"))
        dir-file (io/file dir-path)]
    (.mkdirs dir-file)
    (ImageIO/write image "png" (io/file full-path))
    (println "[DataGen] Generated texture:" full-path)))

(defn generate-placeholder-textures!
  "生成所有占位纹理

   为所有需要纹理的物品和方块创建简单的单色占位纹理。
   这些是临时纹理，你应该替换为自己的纹理文件。"
  []
  (let [base-path "./src/main/resources"]
    ;; 生成物品纹理
    (doseq [item-name (concat (:generated items-to-generate)
                              (:handheld items-to-generate)
                              ["magic_gem"])]
      (when-let [color (get texture-colors (keyword item-name))]
        (save-texture! base-path "example" :item item-name
                      (create-placeholder-texture color))))
    
    ;; 魔法宝石发光层
    (save-texture! base-path "example" :item "magic_gem_glow"
                  (create-placeholder-texture (:magic_gem_glow texture-colors)))
    
    ;; 生成方块纹理
    (doseq [block-name blocks-to-generate]
      (when-let [color (get texture-colors (keyword block-name))]
        (save-texture! base-path "example" :block block-name
                      (create-placeholder-texture color))))
    
    (println "[DataGen] Generated placeholder textures")))

;; ============================================================================
;; 一键生成所有资源
;; ============================================================================

(defn generate-all-assets!
  "生成所有资源文件

   在 nREPL 中运行此函数可以一键生成:
   - 所有物品模型
   - 所有方块模型
   - 所有方块状态文件
   - 所有语言文件

   用法:
   ```clojure
   ;; 连接 nREPL 后运行
   (require '[com.example.datagen :as datagen])
   (datagen/generate-all-assets!)
   ```"
  []
  (println "")
  (println "================================================")
  (println "[DataGen] Starting resource generation...")
  (println "================================================")

  (generate-item-models!)
  (generate-block-models!)
  (generate-blockstates!)
  (generate-lang-files!)
  (generate-placeholder-textures!)

  (println "================================================")
  (println "[DataGen] All resources generated successfully!")
  (println "================================================")
  (println "")
  (println "Generated files:")
  (println "  - Item models: 5 files")
  (println "  - Block models: 1 file")
  (println "  - Blockstates: 1 file")
  (println "  - Language files: 2 files (en_us, zh_cn)")
  (println "  - Placeholder textures: multiple PNG files")
  (println "")
  (println "Note: The placeholder textures are simple colored squares.")
  (println "      You should replace them with your own texture files.")
  (println ""))

(comment
  ;; ============================================================================
  ;; REPL 使用示例
  ;; ============================================================================

  ;; 连接 nREPL 后可以运行:

  ;; 1. 生成所有资源文件
  (generate-all-assets!)

  ;; 2. 单独生成某类资源
  (generate-item-models!)
  (generate-block-models!)
  (generate-blockstates!)
  (generate-lang-files!)

  ;; 3. 查看生成的文件
  ;; 文件位置: ./src/main/resources/assets/example/
  ;;   - models/item/*.json
  ;;   - models/block/*.json
  ;;   - blockstates/*.json
  ;;   - lang/*.json
  ;;   - textures/item/*.png (占位纹理)
  ;;   - textures/block/*.png (占位纹理)

  ;; 4. 自定义生成
  (models/save-item-model! "./src/main/resources" "example" "custom_item"
    (models/generated-item-model "example:item/custom_item"))

  ;; 5. 批量生成新物品
  (models/generate-simple-items! "./src/main/resources" "example"
    ["new_item1" "new_item2" "new_item3"])
  )
