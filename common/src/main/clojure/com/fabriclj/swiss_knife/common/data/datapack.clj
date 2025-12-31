(ns com.fabriclj.swiss-knife.common.data.datapack
  "数据包完整支持系统

   扩展 recipes.clj，提供完整的数据包生成功能:
   - 标签( Tags)
   - 进度( Advancements)
   - 战利品表( Loot Tables)
   - 配方( Recipes)
   - 世界生成( World Generation)
   - 函数( Functions)
   - 谓词( Predicates)
   - 物品修饰器( Item Modifiers) "
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.swiss-knife.common.gameplay.recipes :as recipes]
            [com.fabriclj.swiss-knife.common.utils.json :as json]
            [clojure.java.io :as io])
  (:import (java.nio.file Paths Files)
           (java.nio.file.attribute FileAttribute)))

(set! *warn-on-reflection* true)

;; 前向声明
(declare loot-condition)

;; ============================================================================
;; 数据包结构管理
;; ============================================================================

(defn create-datapack-structure
  "创建数据包文件夹结构

   参数:
   - base-path: 基础路径
   - namespace: 命名空间

   创建标准的数据包目录结构"
  [base-path namespace]
  (let [paths [(str base-path "/data/" namespace "/tags/blocks")
               (str base-path "/data/" namespace "/tags/items")
               (str base-path "/data/" namespace "/tags/entity_types")
               (str base-path "/data/" namespace "/recipes")
               (str base-path "/data/" namespace "/advancements")
               (str base-path "/data/" namespace "/loot_tables/blocks")
               (str base-path "/data/" namespace "/loot_tables/entities")
               (str base-path "/data/" namespace "/loot_tables/chests")
               (str base-path "/data/" namespace "/worldgen/biome")
               (str base-path "/data/" namespace "/worldgen/configured_feature")
               (str base-path "/data/" namespace "/worldgen/placed_feature")
               (str base-path "/data/" namespace "/worldgen/structure")
               (str base-path "/data/" namespace "/functions")
               (str base-path "/data/" namespace "/predicates")
               (str base-path "/data/" namespace "/item_modifiers")]]
    (doseq [path paths]
      (.mkdirs (io/file path)))
    (core/log-info (str "Created datapack structure for namespace: " namespace))))

;; ============================================================================
;; 标签生成
;; ============================================================================

(defn create-tag
  "创建标签数据

   参数:
   - values: 标签值列表( 字符串或标签引用)
   - opts: 选项
     - :replace? - 是否替换( 默认 false)

   返回: 标签数据映射

   示例:
   ```clojure
   (create-tag
     [\"minecraft:stone\"
      \"minecraft:cobblestone\"
      \"#minecraft:stone_ore_replaceables\"]  ; # 表示标签引用
     :replace? false)
   ```"
  [values & {:keys [replace?]
             :or {replace? false}}]
  {:replace replace?
   :values (vec values)})

(defn save-tag!
  "保存标签到文件

   参数:
   - base-path: 数据包基础路径
   - namespace: 命名空间
   - tag-type: 标签类型( :blocks/:items/:entity_types/:fluids)
   - tag-name: 标签名称
   - tag-data: 标签数据

   示例:
   ```clojure
   (save-tag! \"./datapacks/mymod\"
              \"mymod\"
              :blocks
              \"ores\"
              (create-tag [\"mymod:copper_ore\" \"mymod:tin_ore\"]))
   ```"
  [base-path namespace tag-type tag-name tag-data]
  (let [type-folder (name tag-type)
        file-path (str base-path "/data/" namespace "/tags/" type-folder "/" tag-name ".json")
        json-str (json/generate-string tag-data {:pretty true})]
    (.mkdirs (.getParentFile (io/file file-path)))
    (spit file-path json-str)
    (core/log-info (str "Saved tag: " namespace ":" tag-name))))

;; ============================================================================
;; 进度数据生成( 完整版)
;; ============================================================================

(defn advancement-to-json
  "将进度数据映射转换为 JSON 格式

   参数:
   - advancement-data: 进度数据( 来自 advancements.clj)

   返回: 可序列化的映射

   注意: 这是 advancements.clj 中 advancement-data 函数的补充"
  [advancement-data]
  (let [data (cond-> {}
               (:parent advancement-data)
               (assoc :parent (:parent advancement-data))

               (:display advancement-data)
               (assoc :display (:display advancement-data))

               (:criteria advancement-data)
               (assoc :criteria (:criteria advancement-data))

               (:requirements advancement-data)
               (assoc :requirements (:requirements advancement-data))

               (:rewards advancement-data)
               (assoc :rewards (:rewards advancement-data)))]
    data))

(defn save-advancement!
  "保存进度到文件

   参数:
   - base-path: 数据包基础路径
   - namespace: 命名空间
   - advancement-name: 进度名称( 可包含路径，如 \"story/mine_stone\")
   - advancement-data: 进度数据

   示例:
   ```clojure
   (save-advancement! \"./datapacks/mymod\"
                      \"mymod\"
                      \"root\"
                      my-advancement-data)
   ```"
  [base-path namespace advancement-name advancement-data]
  (let [file-path (str base-path "/data/" namespace "/advancements/" advancement-name ".json")
        json-data (advancement-to-json advancement-data)
        json-str (json/generate-string json-data {:pretty true})]
    (.mkdirs (.getParentFile (io/file file-path)))
    (spit file-path json-str)
    (core/log-info (str "Saved advancement: " namespace ":" advancement-name))))

;; ============================================================================
;; 战利品表( 扩展版)
;; ============================================================================

(defn create-loot-pool
  "创建战利品池( 扩展版，支持更多选项)

   参数:
   - entries: 战利品条目列表
   - opts: 选项
     - :rolls - 抽取次数( 数字或范围 {:min :max})
     - :bonus-rolls - 额外抽取次数
     - :conditions - 条件列表

   示例:
   ```clojure
   (create-loot-pool
     [(loot-entry \"minecraft:diamond\" :weight 1)]
     :rolls {:min 1 :max 3}
     :bonus-rolls 0.5
     :conditions [(loot-condition :killed-by-player)])
   ```"
  [entries & {:keys [rolls bonus-rolls conditions]
              :or {rolls 1}}]
  (cond-> {:entries (vec entries)}
    true (assoc :rolls (if (map? rolls) rolls rolls))
    bonus-rolls (assoc :bonus_rolls bonus-rolls)
    conditions (assoc :conditions (vec conditions))))

(defn loot-condition
  "创建战利品条件

   支持的条件类型:
   - :killed-by-player - 被玩家击杀
   - :random-chance {:chance 0.5} - 随机概率
   - :entity-properties {:entity :this :predicate {...}} - 实体属性
   - :block-state-property {:block block-id :properties {...}} - 方块状态

   示例:
   ```clojure
   (loot-condition :random-chance :chance 0.3)
   (loot-condition :killed-by-player)
   ```"
  [condition-type & {:as opts}]
  (case condition-type
    :killed-by-player
    {:condition "minecraft:killed_by_player"}

    :random-chance
    {:condition "minecraft:random_chance"
     :chance (:chance opts 0.5)}

    :survives-explosion
    {:condition "minecraft:survives_explosion"}

    :match-tool
    (cond-> {:condition "minecraft:match_tool"}
      (:predicate opts) (assoc :predicate (:predicate opts)))

    :entity-properties
    {:condition "minecraft:entity_properties"
     :entity (:entity opts :this)
     :predicate (:predicate opts)}

    :block-state-property
    {:condition "minecraft:block_state_property"
     :block (:block opts)
     :properties (:properties opts)}

    ;; 默认
    {:condition (str "minecraft:" (name condition-type))}))

(defn loot-function
  "创建战利品函数

   支持的函数类型:
   - :set-count {:count n} - 设置数量
   - :set-nbt {:tag nbt-string} - 设置 NBT
   - :set-damage {:damage 0.5} - 设置耐久度
   - :enchant-randomly {:enchantments [...]} - 随机附魔
   - :enchant-with-levels {:levels {...} :treasure? bool} - 等级附魔
   - :apply-bonus {:enchantment id :formula type :parameters {...}} - 应用奖励

   示例:
   ```clojure
   (loot-function :set-count :count 3)
   (loot-function :enchant-randomly :enchantments [\"sharpness\" \"fire_aspect\"])
   (loot-function :apply-bonus
     :enchantment \"minecraft:fortune\"
     :formula \"minecraft:ore_drops\")
   ```"
  [function-type & {:as opts}]
  (case function-type
    :set-count
    {:function "minecraft:set_count"
     :count (:count opts 1)}

    :set-nbt
    {:function "minecraft:set_nbt"
     :tag (:tag opts "{}")}

    :set-damage
    {:function "minecraft:set_damage"
     :damage (:damage opts 0.0)}

    :enchant-randomly
    (cond-> {:function "minecraft:enchant_randomly"}
      (:enchantments opts) (assoc :enchantments (vec (:enchantments opts))))

    :enchant-with-levels
    {:function "minecraft:enchant_with_levels"
     :levels (:levels opts {:min 1 :max 30})
     :treasure (:treasure? opts false)}

    :apply-bonus
    {:function "minecraft:apply_bonus"
     :enchantment (:enchantment opts)
     :formula (:formula opts "minecraft:uniform_bonus_count")
     :parameters (:parameters opts {})}

    :explosion-decay
    {:function "minecraft:explosion_decay"}

    :looting-enchant
    {:function "minecraft:looting_enchant"
     :count (:count opts {:min 0 :max 1})}

    ;; 默认
    {:function (str "minecraft:" (name function-type))}))

(defn save-loot-table!
  "保存战利品表到文件

   参数:
   - base-path: 数据包基础路径
   - namespace: 命名空间
   - loot-type: 战利品类型( :blocks/:entities/:chests)
   - loot-name: 战利品表名称
   - loot-data: 战利品表数据

   示例:
   ```clojure
   (save-loot-table! \"./datapacks/mymod\"
                     \"mymod\"
                     :blocks
                     \"copper_ore\"
                     my-loot-table)
   ```"
  [base-path namespace loot-type loot-name loot-data]
  (let [type-folder (name loot-type)
        file-path (str base-path "/data/" namespace "/loot_tables/" type-folder "/" loot-name ".json")
        json-str (json/generate-string loot-data {:pretty true})]
    (.mkdirs (.getParentFile (io/file file-path)))
    (spit file-path json-str)
    (core/log-info (str "Saved loot table: " namespace ":" loot-name))))

;; ============================================================================
;; 函数( Commands)
;; ============================================================================

(defn create-function
  "创建MC函数

   参数:
   - commands: 命令列表( 字符串)

   返回: 命令字符串( 每行一个命令)

   示例:
   ```clojure
   (create-function
     [\"say Hello, world!\"
      \"give @a minecraft:diamond 64\"
      \"tp @a 0 100 0\"])
   ```"
  [commands]
  (clojure.string/join "\n" commands))

(defn save-function!
  "保存函数到文件

   参数:
   - base-path: 数据包基础路径
   - namespace: 命名空间
   - function-name: 函数名称( 可包含路径)
   - function-data: 函数数据( 命令列表或字符串)

   示例:
   ```clojure
   (save-function! \"./datapacks/mymod\"
                   \"mymod\"
                   \"setup\"
                   [\"say Setting up...\"
                    \"gamerule doMobSpawning false\"])
   ```"
  [base-path namespace function-name function-data]
  (let [file-path (str base-path "/data/" namespace "/functions/" function-name ".mcfunction")
        content (if (string? function-data)
                  function-data
                  (create-function function-data))]
    (.mkdirs (.getParentFile (io/file file-path)))
    (spit file-path content)
    (core/log-info (str "Saved function: " namespace ":" function-name))))

;; ============================================================================
;; 谓词( Predicates)
;; ============================================================================

(defn create-predicate
  "创建谓词

   参数:
   - condition: 条件数据

   谓词用于测试游戏状态，可在战利品表、进度等处使用

   示例:
   ```clojure
   (create-predicate
     {:condition \"minecraft:random_chance\"
      :chance 0.5})

   (create-predicate
     [{:condition \"minecraft:weather_check\"
       :raining true}
      {:condition \"minecraft:time_check\"
       :period 24000
       :value {:min 12000 :max 24000}}])
   ```"
  [condition]
  (if (vector? condition)
    condition
    [condition]))

(defn save-predicate!
  "保存谓词到文件

   参数:
   - base-path: 数据包基础路径
   - namespace: 命名空间
   - predicate-name: 谓词名称
   - predicate-data: 谓词数据

   示例:
   ```clojure
   (save-predicate! \"./datapacks/mymod\"
                    \"mymod\"
                    \"is_raining\"
                    (create-predicate
                      {:condition \"minecraft:weather_check\"
                       :raining true}))
   ```"
  [base-path namespace predicate-name predicate-data]
  (let [file-path (str base-path "/data/" namespace "/predicates/" predicate-name ".json")
        json-str (json/generate-string predicate-data {:pretty true})]
    (.mkdirs (.getParentFile (io/file file-path)))
    (spit file-path json-str)
    (core/log-info (str "Saved predicate: " namespace ":" predicate-name))))

;; ============================================================================
;; 物品修饰器( Item Modifiers)
;; ============================================================================

(defn create-item-modifier
  "创建物品修饰器

   参数:
   - functions: 战利品函数列表

   物品修饰器使用与战利品表相同的函数系统

   示例:
   ```clojure
   (create-item-modifier
     [(loot-function :set-count :count 64)
      (loot-function :set-nbt :tag \"{display:{Name:'Special Item'}}\")])
   ```"
  [functions]
  (vec functions))

(defn save-item-modifier!
  "保存物品修饰器到文件

   参数:
   - base-path: 数据包基础路径
   - namespace: 命名空间
   - modifier-name: 修饰器名称
   - modifier-data: 修饰器数据

   示例:
   ```clojure
   (save-item-modifier! \"./datapacks/mymod\"
                        \"mymod\"
                        \"max_stack\"
                        (create-item-modifier
                          [(loot-function :set-count :count 64)]))
   ```"
  [base-path namespace modifier-name modifier-data]
  (let [file-path (str base-path "/data/" namespace "/item_modifiers/" modifier-name ".json")
        json-str (json/generate-string modifier-data {:pretty true})]
    (.mkdirs (.getParentFile (io/file file-path)))
    (spit file-path json-str)
    (core/log-info (str "Saved item modifier: " namespace ":" modifier-name))))

;; ============================================================================
;; 数据包生成器
;; ============================================================================

(defn create-datapack
  "创建完整的数据包

   参数:
   - base-path: 数据包基础路径
   - namespace: 命名空间
   - opts: 选项
     - :description - 数据包描述
     - :pack-format - 数据包格式版本( 默认 10)

   返回: 数据包配置

   示例:
   ```clojure
   (create-datapack \"./datapacks/mymod\"
                    \"mymod\"
                    :description \"My awesome datapack\"
                    :pack-format 10)
   ```"
  [base-path namespace & {:keys [description pack-format]
                          :or {description "Generated datapack"
                               pack-format 10}}]
  ;; 创建目录结构
  (create-datapack-structure base-path namespace)

  ;; 创建 pack.mcmeta
  (let [pack-meta {:pack {:pack_format pack-format
                          :description description}}
        meta-path (str base-path "/pack.mcmeta")
        json-str (json/generate-string pack-meta {:pretty true})]
    (spit meta-path json-str))

  (core/log-info (str "Created datapack: " namespace " at " base-path))

  {:base-path base-path
   :namespace namespace
   :pack-format pack-format})

(comment
  ;; 使用示例

  ;; ========== 创建数据包 ==========

  ;; 1. 创建数据包结构
  (def my-datapack
    (create-datapack "./datapacks/mymod"
                     "mymod"
                     :description "My Custom Datapack"
                     :pack-format 10))

  ;; ========== 标签 ==========

  ;; 2. 创建方块标签
  (save-tag! "./datapacks/mymod"
             "mymod"
             :blocks
             "mineable/pickaxe"
             (create-tag
               ["mymod:copper_ore"
                "mymod:tin_ore"
                "#minecraft:stone"]  ; 引用其他标签
               :replace? false))

  ;; 3. 创建物品标签
  (save-tag! "./datapacks/mymod"
             "mymod"
             :items
             "gems"
             (create-tag
               ["mymod:ruby"
                "mymod:sapphire"
                "minecraft:diamond"]))

  ;; ========== 战利品表 ==========

  ;; 4. 创建方块战利品表
  (def copper-ore-loot
    {:type "minecraft:block"
     :pools [(create-loot-pool
               [{:type "minecraft:item"
                 :name "mymod:raw_copper"
                 :functions [(loot-function :apply-bonus
                              :enchantment "minecraft:fortune"
                              :formula "minecraft:ore_drops")
                            (loot-function :explosion-decay)]}]
               :rolls 1
               :conditions [(loot-condition :survives-explosion)])]})

  (save-loot-table! "./datapacks/mymod"
                    "mymod"
                    :blocks
                    "copper_ore"
                    copper-ore-loot)

  ;; 5. 创建实体战利品表
  (def custom-mob-loot
    {:type "minecraft:entity"
     :pools [(create-loot-pool
               [{:type "minecraft:item"
                 :name "minecraft:diamond"
                 :functions [(loot-function :set-count :count {:min 1 :max 3})
                            (loot-function :looting-enchant :count {:min 0 :max 1})]}]
               :rolls 1
               :conditions [(loot-condition :killed-by-player)
                           (loot-condition :random-chance :chance 0.3)])]})

  (save-loot-table! "./datapacks/mymod"
                    "mymod"
                    :entities
                    "custom_mob"
                    custom-mob-loot)

  ;; ========== 函数 ==========

  ;; 6. 创建设置函数
  (save-function! "./datapacks/mymod"
                  "mymod"
                  "setup"
                  ["say Initializing mymod..."
                   "gamerule doMobSpawning true"
                   "time set day"
                   "weather clear"])

  ;; 7. 创建给予物品函数
  (save-function! "./datapacks/mymod"
                  "mymod"
                  "give_starter_kit"
                  ["give @p minecraft:diamond_sword{Enchantments:[{id:sharpness,lvl:5}]} 1"
                   "give @p minecraft:diamond_pickaxe{Enchantments:[{id:efficiency,lvl:5}]} 1"
                   "give @p minecraft:golden_apple 16"])

  ;; ========== 谓词 ==========

  ;; 8. 创建天气谓词
  (save-predicate! "./datapacks/mymod"
                   "mymod"
                   "is_raining"
                   (create-predicate
                     {:condition "minecraft:weather_check"
                      :raining true}))

  ;; 9. 创建组合谓词
  (save-predicate! "./datapacks/mymod"
                   "mymod"
                   "night_and_raining"
                   (create-predicate
                     [{:condition "minecraft:weather_check"
                       :raining true}
                      {:condition "minecraft:time_check"
                       :period 24000
                       :value {:min 13000 :max 23000}}]))

  ;; ========== 物品修饰器 ==========

  ;; 10. 创建物品修饰器
  (save-item-modifier! "./datapacks/mymod"
                       "mymod"
                       "enchant_randomly"
                       (create-item-modifier
                         [(loot-function :enchant-randomly
                            :enchantments ["minecraft:sharpness"
                                          "minecraft:fire_aspect"
                                          "minecraft:looting"])]))

  ;; ========== 进度 ==========

  ;; 11. 保存进度( 使用 advancements.clj 创建)
  (save-advancement! "./datapacks/mymod"
                     "mymod"
                     "root"
                     (advancements/advancement-data "mymod:root"
                                                    :display (advancements/display-info
                                                              (ItemStack. Items/DIAMOND)
                                                              "My Mod"
                                                              "Begin your adventure"
                                                              :frame :task
                                                              :background "minecraft:textures/gui/advancements/backgrounds/adventure.png")
                                                    :criteria {:auto {:trigger "minecraft:tick"}})))
