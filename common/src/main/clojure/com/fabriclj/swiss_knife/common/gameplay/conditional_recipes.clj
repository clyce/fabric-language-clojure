(ns com.fabriclj.swiss-knife.common.gameplay.conditional-recipes
  "瑞士军刀 - 条件配方支持

   提供 Minecraft 1.19+ 的条件配方( Conditional Recipes) 功能。
   条件配方允许根据特定条件( 如 mod 是否加载) 动态启用或禁用配方。

   使用示例:
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.gameplay.conditional-recipes :as cond-recipes])

   ;; 仅当 JEI 加载时启用配方
   (cond-recipes/conditional-recipe
     {:type :crafting_shaped
      :pattern [[\"DDD\"]
                [\" S \"]
                [\" S \"]]
      :key {:D \"minecraft:diamond\" :S \"minecraft:stick\"}
      :result \"minecraft:diamond_sword\"}
     (cond-recipes/mod-loaded-condition \"jei\"))

   ;; 多个条件( AND)
   (cond-recipes/conditional-recipe
     recipe-data
     (cond-recipes/all-conditions
       (cond-recipes/mod-loaded-condition \"jei\")
       (cond-recipes/mod-loaded-condition \"nei\")))
   ```"
  (:require [com.fabriclj.swiss-knife.common.utils.json :as json]
            [clojure.java.io :as io]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 条件类型
;; ============================================================================

(defn mod-loaded-condition
  "创建 mod 加载条件

   参数:
   - mod-id: mod ID 字符串

   返回: 条件数据

   示例:
   ```clojure
   (mod-loaded-condition \"jei\")
   ; => {:type \"forge:mod_loaded\" :modid \"jei\"}
   ```"
  [mod-id]
  {:type "forge:mod_loaded"
   :modid mod-id})

(defn not-condition
  "创建否定条件( NOT)

   参数:
   - condition: 要否定的条件

   返回: 否定条件

   示例:
   ```clojure
   (not-condition (mod-loaded-condition \"jei\"))
   ; 仅当 JEI 未加载时
   ```"
  [condition]
  {:type "forge:not"
   :value condition})

(defn all-conditions
  "创建 AND 条件( 所有条件都必须满足)

   参数:
   - conditions: 条件列表( 可变参数)

   返回: AND 条件

   示例:
   ```clojure
   (all-conditions
     (mod-loaded-condition \"jei\")
     (mod-loaded-condition \"nei\"))
   ; 仅当 JEI 和 NEI 都加载时
   ```"
  [& conditions]
  {:type "forge:and"
   :values (vec conditions)})

(defn any-condition
  "创建 OR 条件( 至少一个条件满足)

   参数:
   - conditions: 条件列表( 可变参数)

   返回: OR 条件

   示例:
   ```clojure
   (any-condition
     (mod-loaded-condition \"jei\")
     (mod-loaded-condition \"nei\"))
   ; JEI 或 NEI 任一加载时
   ```"
  [& conditions]
  {:type "forge:or"
   :values (vec conditions)})

(defn item-exists-condition
  "创建物品存在条件

   参数:
   - item-id: 物品 ID

   返回: 条件数据

   示例:
   ```clojure
   (item-exists-condition \"mymod:custom_item\")
   ```"
  [item-id]
  {:type "forge:item_exists"
   :item item-id})

(defn tag-empty-condition
  "创建标签为空条件

   参数:
   - tag: 标签 ID

   返回: 条件数据

   示例:
   ```clojure
   (tag-empty-condition \"forge:ingots/copper\")
   ```"
  [tag]
  {:type "forge:tag_empty"
   :tag tag})

;; ============================================================================
;; 条件配方
;; ============================================================================

(defn conditional-recipe
  "创建条件配方

   参数:
   - recipe: 配方数据( map)
   - condition: 条件或条件列表

   返回: 带条件的配方数据

   示例:
   ```clojure
   (conditional-recipe
     {:type :crafting_shaped
      :pattern [[\"III\"]
                [\" S \"]
                [\" S \"]]
      :key {:I \"mymod:ingot\" :S \"minecraft:stick\"}
      :result \"mymod:tool\"}
     (mod-loaded-condition \"jei\"))
   ```"
  [recipe condition]
  (let [conditions (if (vector? condition) condition [condition])]
    {:type "forge:conditional"
     :recipes [{:conditions conditions
                :recipe recipe}]}))

(defn conditional-recipe-with-fallback
  "创建带回退的条件配方

   参数:
   - primary-recipe: 主配方
   - primary-condition: 主配方条件
   - fallback-recipe: 回退配方

   返回: 条件配方数据

   示例:
   ```clojure
   (conditional-recipe-with-fallback
     expensive-recipe
     (mod-loaded-condition \"hard_mode\")
     cheap-recipe)
   ; 如果 hard_mode 加载则使用昂贵配方，否则使用便宜配方
   ```"
  [primary-recipe primary-condition fallback-recipe]
  {:type "forge:conditional"
   :recipes [{:conditions (if (vector? primary-condition)
                            primary-condition
                            [primary-condition])
              :recipe primary-recipe}
             {:conditions []  ;; 空条件 = 总是满足
              :recipe fallback-recipe}]})

(defn multi-conditional-recipe
  "创建多条件配方( 多个条件-配方对)

   参数:
   - condition-recipe-pairs: 条件-配方对的向量
     格式: [[condition1 recipe1] [condition2 recipe2] ...]

   返回: 条件配方数据

   示例:
   ```clojure
   (multi-conditional-recipe
     [[(mod-loaded-condition \"easy_mode\") easy-recipe]
      [(mod-loaded-condition \"hard_mode\") hard-recipe]
      [[] normal-recipe]])  ; 默认配方
   ```"
  [condition-recipe-pairs]
  {:type "forge:conditional"
   :recipes (mapv (fn [[condition recipe]]
                    {:conditions (if (vector? condition)
                                   condition
                                   [condition])
                     :recipe recipe})
                  condition-recipe-pairs)})

;; ============================================================================
;; 文件保存
;; ============================================================================

(defn- ensure-directory
  "确保目录存在"
  [^String path]
  (let [dir (io/file path)]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn save-conditional-recipe!
  "保存条件配方到文件

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - recipe-name: 配方名称
   - conditional-recipe-data: 条件配方数据

   示例:
   ```clojure
   (save-conditional-recipe! \"./src/main/resources\" \"mymod\" \"diamond_sword_jei\"
     (conditional-recipe
       {:type :crafting_shaped
        :pattern [[\"DDD\"] [\" S \"] [\" S \"]]
        :key {:D \"minecraft:diamond\" :S \"minecraft:stick\"}
        :result \"minecraft:diamond_sword\"}
       (mod-loaded-condition \"jei\")))
   ```"
  [base-path namespace recipe-name conditional-recipe-data]
  (let [full-path (str base-path "/data/" namespace "/recipes/" recipe-name ".json")
        dir-path (subs full-path 0 (.lastIndexOf full-path "/"))]
    (ensure-directory dir-path)
    (with-open [writer (io/writer full-path)]
      (json/write conditional-recipe-data writer {:pretty true}))
    (println "[DataGen] Generated conditional recipe:" full-path)))

;; ============================================================================
;; 辅助函数
;; ============================================================================

(defn when-mod-loaded
  "仅当指定 mod 加载时启用配方

   参数:
   - mod-id: mod ID
   - recipe: 配方数据

   返回: 条件配方

   示例:
   ```clojure
   (when-mod-loaded \"jei\" my-recipe)
   ```"
  [mod-id recipe]
  (conditional-recipe recipe (mod-loaded-condition mod-id)))

(defn when-mods-loaded
  "仅当所有指定 mod 都加载时启用配方

   参数:
   - mod-ids: mod ID 列表
   - recipe: 配方数据

   返回: 条件配方

   示例:
   ```clojure
   (when-mods-loaded [\"jei\" \"nei\"] my-recipe)
   ```"
  [mod-ids recipe]
  (conditional-recipe recipe
                      (apply all-conditions
                             (map mod-loaded-condition mod-ids))))

(defn unless-mod-loaded
  "仅当指定 mod 未加载时启用配方

   参数:
   - mod-id: mod ID
   - recipe: 配方数据

   返回: 条件配方

   示例:
   ```clojure
   (unless-mod-loaded \"jei\" my-recipe)
   ```"
  [mod-id recipe]
  (conditional-recipe recipe
                      (not-condition (mod-loaded-condition mod-id))))

(comment
  ;; 使用示例

  ;; 1. 基本条件配方
  (def diamond-sword-recipe
    {:type "minecraft:crafting_shaped"
     :pattern ["DDD" " S " " S "]
     :key {:D {:item "minecraft:diamond"}
           :S {:item "minecraft:stick"}}
     :result {:item "minecraft:diamond_sword"}})

  (save-conditional-recipe! "./src/main/resources" "mymod" "diamond_sword_jei"
    (when-mod-loaded "jei" diamond-sword-recipe))

  ;; 2. 多条件配方
  (save-conditional-recipe! "./src/main/resources" "mymod" "special_recipe"
    (conditional-recipe
      diamond-sword-recipe
      (all-conditions
        (mod-loaded-condition "jei")
        (mod-loaded-condition "nei"))))

  ;; 3. 带回退的配方
  (def expensive-recipe
    {:type "minecraft:crafting_shaped"
     :pattern ["DDD" "DDD" "DDD"]
     :key {:D {:item "minecraft:diamond"}}
     :result {:item "mymod:expensive_item"}})

  (def cheap-recipe
    {:type "minecraft:crafting_shaped"
     :pattern ["III" "III" "III"]
     :key {:I {:item "minecraft:iron_ingot"}}
     :result {:item "mymod:expensive_item"}})

  (save-conditional-recipe! "./src/main/resources" "mymod" "adaptive_recipe"
    (conditional-recipe-with-fallback
      expensive-recipe
      (mod-loaded-condition "hard_mode")
      cheap-recipe))

  ;; 4. 多个条件-配方对
  (save-conditional-recipe! "./src/main/resources" "mymod" "difficulty_recipe"
    (multi-conditional-recipe
      [[(mod-loaded-condition "easy_mode") easy-recipe]
       [(mod-loaded-condition "normal_mode") normal-recipe]
       [(mod-loaded-condition "hard_mode") hard-recipe]
       [[] default-recipe]]))  ; 默认配方( 无条件)

  ;; 5. 条件组合
  (save-conditional-recipe! "./src/main/resources" "mymod" "complex_recipe"
    (conditional-recipe
      my-recipe
      (any-condition
        (all-conditions
          (mod-loaded-condition "jei")
          (not-condition (mod-loaded-condition "nei")))
        (mod-loaded-condition "rei")))))
