(ns com.fabriclj.swiss-knife.common.gameplay.recipes
  "配方和数据包系统

   提供 Minecraft 配方注册、战利品表、数据生成的 Clojure 友好接口。
   核心功能:
   - 配方创建( 有序/无序/熔炼/切石/锻造)
   - 战利品表定义
   - 数据生成和导出
   - DSL 宏简化配方定义"
  (:require [clojure.string :as str]
            [clojure.data.json :as json])
  (:import (net.minecraft.world.item Item Items ItemStack)
           (net.minecraft.world.item.crafting Ingredient RecipeSerializer
            ShapedRecipe ShapelessRecipe
            SimpleCookingSerializer)
           (net.minecraft.world.level.block Block Blocks)
           (net.minecraft.resources ResourceLocation)
           (net.minecraft.core NonNullList)
           (net.minecraft.world.item.crafting CraftingBookCategory)
           (net.minecraft.data.loot LootTableProvider)
           (net.minecraft.world.level.storage.loot LootTable LootPool
            LootContext$Builder)
           (net.minecraft.world.level.storage.loot.entries LootItem LootPoolEntryContainer)
           (net.minecraft.world.level.storage.loot.functions SetItemCountFunction
            ApplyBonusCount)
           (net.minecraft.world.level.storage.loot.predicates LootItemCondition)
           (net.minecraft.world.level.storage.loot.providers.number UniformGenerator
            ConstantValue)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 辅助函数
;; ============================================================================

(defn- ->resource-location
  "转换为 ResourceLocation"
  [id]
  (cond
    (instance? ResourceLocation id) id
    (string? id) (if (str/includes? id ":")
                   (ResourceLocation. id)
                   (ResourceLocation. "minecraft" id))
    (keyword? id) (ResourceLocation. (name id))
    :else (throw (IllegalArgumentException. (str "Invalid resource location: " id)))))

(defn- ->ingredient
  "转换为 Ingredient"
  [item]
  (cond
    (instance? Ingredient item) item
    (instance? Item item) (Ingredient/of ^Item item)
    (instance? ItemStack item) (Ingredient/of ^"[Lnet.minecraft.world.item.ItemStack;"
                                (into-array ItemStack [item]))
    (keyword? item) (->ingredient (eval (symbol "Items" (str/upper-case (name item)))))
    :else (Ingredient/of ^Item item)))

(defn- ->item
  "转换为 Item"
  [item]
  (cond
    (instance? Item item) item
    (instance? ItemStack item) (.getItem ^ItemStack item)
    (keyword? item) (eval (symbol "Items" (str/upper-case (name item))))
    :else item))

(defn- ->item-stack
  "转换为 ItemStack"
  ([item] (->item-stack item 1))
  ([item count]
   (cond
     (instance? ItemStack item) item
     (instance? Item item) (ItemStack. ^Item item (int count))
     (keyword? item) (->item-stack (->item item) count)
     :else (ItemStack. ^Item item (int count)))))

;; ============================================================================
;; 配方创建 - 有序合成
;; ============================================================================

(defn shaped-recipe
  "创建有序合成配方

   参数:
   - id: 配方 ID( ResourceLocation 或字符串)
   - pattern: 图案( 字符串向量，最大 3x3)
   - keys: 键映射( 字符 -> 物品)
   - result: 结果物品( Item 或 ItemStack)
   - opts: 可选参数
     - :category - 合成书分类( 默认 :misc)
     - :group - 配方组

   示例:
   ```clojure
   (shaped-recipe \"mymod:diamond_sword\"
     [\" A \"
      \" A \"
      \" B \"]
     {\\A Items/DIAMOND
      \\B Items/STICK}
     (ItemStack. Items/DIAMOND_SWORD 1))
   ```"
  [id pattern keys result & {:keys [category group] :or {category :misc}}]
  (let [res-loc (->resource-location id)
        result-stack (->item-stack result)
        width (count (first pattern))
        height (count pattern)
        ingredients (NonNullList/create)]

    ;; 构建 ingredients
    (doseq [row pattern
            char row]
      (if-let [item (get keys char)]
        (.add ingredients (->ingredient item))
        (.add ingredients Ingredient/EMPTY)))

    {:id res-loc
     :type :shaped
     :pattern pattern
     :keys keys
     :result result-stack
     :category (case category
                 :building CraftingBookCategory/BUILDING
                 :redstone CraftingBookCategory/REDSTONE
                 :equipment CraftingBookCategory/EQUIPMENT
                 :misc CraftingBookCategory/MISC
                 CraftingBookCategory/MISC)
     :group (or group "")
     :ingredients ingredients}))

;; ============================================================================
;; 配方创建 - 无序合成
;; ============================================================================

(defn shapeless-recipe
  "创建无序合成配方

   参数:
   - id: 配方 ID
   - ingredients: 材料列表( Item 或 Ingredient 列表)
   - result: 结果物品
   - opts: 可选参数
     - :category - 合成书分类
     - :group - 配方组

   示例:
   ```clojure
   (shapeless-recipe \"mymod:magic_essence\"
     [Items/DIAMOND Items/EMERALD Items/GOLD_INGOT]
     (ItemStack. magic-essence-item 1))
   ```"
  [id ingredients result & {:keys [category group] :or {category :misc}}]
  (let [res-loc (->resource-location id)
        result-stack (->item-stack result)
        ing-list (NonNullList/create)]

    (doseq [ing ingredients]
      (.add ing-list (->ingredient ing)))

    {:id res-loc
     :type :shapeless
     :ingredients ing-list
     :result result-stack
     :category (case category
                 :building CraftingBookCategory/BUILDING
                 :redstone CraftingBookCategory/REDSTONE
                 :equipment CraftingBookCategory/EQUIPMENT
                 :misc CraftingBookCategory/MISC
                 CraftingBookCategory/MISC)
     :group (or group "")}))

;; ============================================================================
;; 配方创建 - 熔炼类
;; ============================================================================

(defn smelting-recipe
  "创建熔炼配方

   参数:
   - id: 配方 ID
   - ingredient: 材料
   - result: 结果物品
   - experience: 经验值( 浮点数)
   - cooking-time: 烹饪时间( tick，默认 200)

   示例:
   ```clojure
   (smelting-recipe \"mymod:magic_ingot_from_ore\"
     Items/MAGIC_ORE
     Items/MAGIC_INGOT
     1.0
     200)
   ```"
  [id ingredient result experience & [cooking-time]]
  {:id (->resource-location id)
   :type :smelting
   :ingredient (->ingredient ingredient)
   :result (->item-stack result)
   :experience (float experience)
   :cooking-time (int (or cooking-time 200))
   :group ""})

(defn blasting-recipe
  "创建高炉配方( 熔炼时间减半) "
  [id ingredient result experience & [cooking-time]]
  (assoc (smelting-recipe id ingredient result experience (or cooking-time 100))
         :type :blasting))

(defn smoking-recipe
  "创建烟熏炉配方"
  [id ingredient result experience & [cooking-time]]
  (assoc (smelting-recipe id ingredient result experience (or cooking-time 100))
         :type :smoking))

(defn campfire-cooking-recipe
  "创建营火烹饪配方"
  [id ingredient result experience & [cooking-time]]
  (assoc (smelting-recipe id ingredient result experience (or cooking-time 600))
         :type :campfire_cooking))

;; ============================================================================
;; 配方创建 - 切石机
;; ============================================================================

(defn stonecutting-recipe
  "创建切石配方

   参数:
   - id: 配方 ID
   - ingredient: 材料
   - result: 结果物品
   - count: 结果数量( 默认 1)

   示例:
   ```clojure
   (stonecutting-recipe \"mymod:magic_bricks_from_stone\"
     Items/MAGIC_STONE
     Items/MAGIC_BRICKS
     4)
   ```"
  [id ingredient result & [count]]
  {:id (->resource-location id)
   :type :stonecutting
   :ingredient (->ingredient ingredient)
   :result (->item-stack result (or count 1))
   :group ""})

;; ============================================================================
;; 配方创建 - 锻造台
;; ============================================================================

(defn smithing-transform-recipe
  "创建锻造台转换配方( 1.20+)

   参数:
   - id: 配方 ID
   - template: 锻造模板
   - base: 基础物品
   - addition: 添加物品
   - result: 结果物品

   示例:
   ```clojure
   (smithing-transform-recipe \"mymod:netherite_upgrade\"
     Items/NETHERITE_UPGRADE_SMITHING_TEMPLATE
     Items/DIAMOND_SWORD
     Items/NETHERITE_INGOT
     Items/NETHERITE_SWORD)
   ```"
  [id template base addition result]
  {:id (->resource-location id)
   :type :smithing_transform
   :template (->ingredient template)
   :base (->ingredient base)
   :addition (->ingredient addition)
   :result (->item-stack result)})

(defn smithing-trim-recipe
  "创建锻造台装饰配方( 盔甲纹饰) "
  [id template base addition]
  {:id (->resource-location id)
   :type :smithing_trim
   :template (->ingredient template)
   :base (->ingredient base)
   :addition (->ingredient addition)})

;; ============================================================================
;; 战利品表 - 核心组件
;; ============================================================================

(defn loot-entry
  "创建战利品条目

   参数:
   - item: 物品
   - opts: 可选参数
     - :weight - 权重( 默认 1)
     - :quality - 品质( 影响时运)
     - :functions - 战利品函数列表
     - :conditions - 条件列表

   示例:
   ```clojure
   (loot-entry Items/DIAMOND
     :weight 1
     :functions [(set-count 1 3)])
   ```"
  [item & {:keys [weight quality functions conditions]
           :or {weight 1 quality 0}}]
  {:type :item
   :item (->item item)
   :weight (int weight)
   :quality (int quality)
   :functions (or functions [])
   :conditions (or conditions [])})

(defn loot-pool
  "创建战利品池

   参数:
   - entries: 战利品条目列表
   - opts: 可选参数
     - :rolls - 抽取次数( 可以是数字或 [min max])
     - :bonus-rolls - 额外抽取次数
     - :conditions - 条件列表

   示例:
   ```clojure
   (loot-pool
     [(loot-entry Items/DIAMOND :weight 1)
      (loot-entry Items/EMERALD :weight 2)]
     :rolls [1 3]
     :bonus-rolls 0.5)
   ```"
  [entries & {:keys [rolls bonus-rolls conditions]
              :or {rolls 1 bonus-rolls 0}}]
  {:entries entries
   :rolls (if (vector? rolls)
            {:min (first rolls) :max (second rolls)}
            {:value rolls})
   :bonus-rolls bonus-rolls
   :conditions (or conditions [])})

;; ============================================================================
;; 战利品表 - 函数
;; ============================================================================

(defn set-count
  "设置物品数量

   参数:
   - min: 最小数量
   - max: 最大数量( 可选，默认等于 min)

   示例:
   ```clojure
   (set-count 1 3)  ; 1-3 个
   (set-count 5)    ; 固定 5 个
   ```"
  ([count]
   {:function :set_count
    :count {:type :constant :value count}})
  ([min max]
   {:function :set_count
    :count {:type :uniform :min min :max max}}))

(defn apply-fortune-bonus
  "应用时运加成

   参数:
   - formula: 公式类型( :ore_drops/:uniform_bonus/:binomial_with_bonus_count)

   示例:
   ```clojure
   (apply-fortune-bonus :ore_drops)
   ```"
  [formula]
  {:function :apply_bonus
   :enchantment "minecraft:fortune"
   :formula (name formula)
   :parameters {}})

(defn explosion-decay
  "爆炸衰减( 爆炸破坏时减少掉落) "
  []
  {:function :explosion_decay})

(defn set-nbt
  "设置 NBT 数据"
  [nbt-string]
  {:function :set_nbt
   :tag nbt-string})

(defn set-damage
  "设置损坏值

   参数:
   - damage: 损坏值( 0.0-1.0) 或 [min max]"
  ([damage]
   {:function :set_damage
    :damage {:type :constant :value damage}})
  ([min max]
   {:function :set_damage
    :damage {:type :uniform :min min :max max}}))

(defn enchant-randomly
  "随机附魔"
  [& enchantments]
  {:function :enchant_randomly
   :enchantments (vec enchantments)})

(defn enchant-with-levels
  "按等级附魔

   参数:
   - levels: 附魔等级( 数字或 [min max])
   - treasure: 是否包含宝藏附魔( 默认 false) "
  [levels & {:keys [treasure] :or {treasure false}}]
  {:function :enchant_with_levels
   :levels (if (vector? levels)
             {:type :uniform :min (first levels) :max (second levels)}
             {:type :constant :value levels})
   :treasure treasure})

;; ============================================================================
;; 战利品表 - 条件
;; ============================================================================

(defn match-tool-condition
  "工具匹配条件

   参数:
   - predicate: 工具断言映射
     - :items - 物品列表
     - :tag - 物品标签
     - :enchantments - 附魔要求"
  [predicate]
  {:condition :match_tool
   :predicate predicate})

(defn survives-explosion-condition
  "生存爆炸条件( 通常用于非爆炸破坏) "
  []
  {:condition :survives_explosion})

(defn random-chance-condition
  "随机概率条件

   参数:
   - chance: 概率( 0.0-1.0) "
  [chance]
  {:condition :random_chance
   :chance (float chance)})

(defn killed-by-player-condition
  "被玩家击杀条件"
  []
  {:condition :killed_by_player})

;; ============================================================================
;; 战利品表 - 便捷构建器
;; ============================================================================

(defn simple-block-loot
  "创建简单方块战利品表( 自掉落)

   参数:
   - block: 方块
   - opts: 可选参数
     - :requires-tool - 是否需要工具( 默认 false)
     - :fortune - 是否受时运影响( 默认 false)
     - :count - 掉落数量( [min max])

   示例:
   ```clojure
   (simple-block-loot Blocks/MAGIC_ORE
     :requires-tool true
     :fortune true
     :count [2 5])
   ```"
  [block & {:keys [requires-tool fortune count]
            :or {requires-tool false fortune false count 1}}]
  (let [entry-opts {:weight 1}
        functions (cond-> []
                    count (conj (if (vector? count)
                                  (set-count (first count) (second count))
                                  (set-count count)))
                    fortune (conj (apply-fortune-bonus :ore_drops)))
        conditions (cond-> [(survives-explosion-condition)]
                     requires-tool (conj (match-tool-condition
                                          {:items [(->item block)]})))]
    {:type :block
     :block block
     :pools [(loot-pool
              [(loot-entry block
                           :weight 1
                           :functions functions
                           :conditions conditions)]
              :rolls 1)]}))

(defn entity-loot-table
  "创建实体战利品表

   参数:
   - entity-type: 实体类型
   - pools: 战利品池列表

   示例:
   ```clojure
   (entity-loot-table EntityType/ZOMBIE
     [(loot-pool
        [(loot-entry Items/ROTTEN_FLESH :weight 1)]
        :rolls [0 2])])
   ```"
  [entity-type pools]
  {:type :entity
   :entity entity-type
   :pools pools})

;; ============================================================================
;; 数据生成 - JSON 导出
;; ============================================================================

(defn- recipe->json
  "将配方数据转换为 JSON 映射"
  [recipe]
  (case (:type recipe)
    :shaped
    {"type" "minecraft:crafting_shaped"
     "pattern" (vec (:pattern recipe))
     "key" (into {}
                 (map (fn [[k v]]
                        [(str k) {"item" (str (.getRegistryName (->item v)))}])
                      (:keys recipe)))
     "result" {"item" (str (.getItem ^ItemStack (:result recipe)))
               "count" (.getCount ^ItemStack (:result recipe))}
     "group" (:group recipe "")}

    :shapeless
    {"type" "minecraft:crafting_shapeless"
     "ingredients" (vec (map (fn [ing]
                               {"item" (str (.getItem ^ItemStack
                                             (first (.getItems ^Ingredient ing))))})
                             (:ingredients recipe)))
     "result" {"item" (str (.getItem ^ItemStack (:result recipe)))
               "count" (.getCount ^ItemStack (:result recipe))}
     "group" (:group recipe "")}

    (:smelting :blasting :smoking :campfire_cooking)
    {"type" (str "minecraft:" (name (:type recipe)))
     "ingredient" {"item" (str (.getItem ^ItemStack
                                (first (.getItems ^Ingredient (:ingredient recipe)))))}
     "result" (str (.getItem ^ItemStack (:result recipe)))
     "experience" (:experience recipe)
     "cookingtime" (:cooking-time recipe)}

    :stonecutting
    {"type" "minecraft:stonecutting"
     "ingredient" {"item" (str (.getItem ^ItemStack
                                (first (.getItems ^Ingredient (:ingredient recipe)))))}
     "result" (str (.getItem ^ItemStack (:result recipe)))
     "count" (.getCount ^ItemStack (:result recipe))}

    :smithing_transform
    {"type" "minecraft:smithing_transform"
     "template" {"item" (str (.getItem ^ItemStack
                              (first (.getItems ^Ingredient (:template recipe)))))}
     "base" {"item" (str (.getItem ^ItemStack
                          (first (.getItems ^Ingredient (:base recipe)))))}
     "addition" {"item" (str (.getItem ^ItemStack
                              (first (.getItems ^Ingredient (:addition recipe)))))}
     "result" {"item" (str (.getItem ^ItemStack (:result recipe)))}}))

(defn generate-recipe-json
  "生成配方 JSON 字符串

   参数:
   - recipe: 配方数据
   - pretty?: 是否美化输出( 默认 true)

   返回: JSON 字符串"
  [recipe & {:keys [pretty?] :or {pretty? true}}]
  (json/write-str (recipe->json recipe)
                  :indent (when pretty? 2)))

(defn save-recipe-json!
  "保存配方 JSON 到文件

   参数:
   - recipe: 配方数据
   - file-path: 文件路径"
  [recipe file-path]
  (spit file-path (generate-recipe-json recipe)))

;; ============================================================================
;; DSL 宏
;; ============================================================================

(defmacro defshaped
  "定义有序配方

   示例:
   ```clojure
   (defshaped diamond-sword
     \"mymod:diamond_sword\"
     [\" A \"
      \" A \"
      \" B \"]
     {\\A Items/DIAMOND
      \\B Items/STICK}
     Items/DIAMOND_SWORD)
   ```"
  [name id pattern keys result & options]
  `(def ~name
     (shaped-recipe ~id ~pattern ~keys ~result ~@options)))

(defmacro defshapeless
  "定义无序配方

   示例:
   ```clojure
   (defshapeless magic-essence
     \"mymod:magic_essence\"
     [Items/DIAMOND Items/EMERALD Items/GOLD_INGOT]
     magic-essence-item)
   ```"
  [name id ingredients result & options]
  `(def ~name
     (shapeless-recipe ~id ~ingredients ~result ~@options)))

(defmacro defsmelting
  "定义熔炼配方

   示例:
   ```clojure
   (defsmelting magic-ingot-from-ore
     \"mymod:magic_ingot_from_ore\"
     Items/MAGIC_ORE
     Items/MAGIC_INGOT
     1.0
     200)
   ```"
  [name id ingredient result experience & [cooking-time]]
  `(def ~name
     (smelting-recipe ~id ~ingredient ~result ~experience ~cooking-time)))

(defmacro defloot
  "定义战利品表

   示例:
   ```clojure
   (defloot magic-ore-loot
     :block Blocks/MAGIC_ORE
     :requires-tool true
     :fortune true
     :count [2 5])
   ```"
  [name & options]
  `(def ~name
     (simple-block-loot ~@options)))

(comment
  ;; 使用示例

  ;; ========== 有序合成 ==========

  ;; 1. 基本有序配方
  (def sword-recipe
    (shaped-recipe "mymod:diamond_sword"
                   [" A "
                    " A "
                    " B "]
                   {\A Items/DIAMOND
                    \B Items/STICK}
                   Items/DIAMOND_SWORD))

  ;; 2. 使用宏定义
  (defshaped pickaxe-recipe
    "mymod:diamond_pickaxe"
    ["AAA"
     " B "
     " B "]
    {\A Items/DIAMOND
     \B Items/STICK}
    Items/DIAMOND_PICKAXE
    :category :equipment)

  ;; ========== 无序合成 ==========

  ;; 3. 无序配方
  (def essence-recipe
    (shapeless-recipe "mymod:magic_essence"
                      [Items/DIAMOND Items/EMERALD Items/GOLD_INGOT]
                      magic-essence-item))

  ;; ========== 熔炼配方 ==========

  ;; 4. 熔炼
  (def ingot-recipe
    (smelting-recipe "mymod:magic_ingot"
                     magic-ore-item
                     magic-ingot-item
                     1.0
                     200))

  ;; 5. 高炉
  (def fast-ingot-recipe
    (blasting-recipe "mymod:magic_ingot_blasting"
                     magic-ore-item
                     magic-ingot-item
                     1.0))

  ;; ========== 战利品表 ==========

  ;; 6. 简单方块战利品
  (def ore-loot
    (simple-block-loot magic-ore-block
                       :requires-tool true
                       :fortune true
                       :count [2 5]))

  ;; 7. 复杂战利品池
  (def chest-loot
    {:type :chest
     :pools [(loot-pool
              [(loot-entry Items/DIAMOND
                           :weight 1
                           :functions [(set-count 1 3)])
               (loot-entry Items/EMERALD
                           :weight 2
                           :functions [(set-count 2 5)])]
              :rolls [3 5])]})

  ;; 8. 实体战利品表
  (def zombie-loot
    (entity-loot-table EntityType/ZOMBIE
                       [(loot-pool
                         [(loot-entry Items/ROTTEN_FLESH
                                      :weight 1
                                      :functions [(set-count 0 2)]
                                      :conditions [(killed-by-player-condition)])]
                         :rolls 1)]))

  ;; ========== 数据生成 ==========

  ;; 9. 生成 JSON
  (def json-str (generate-recipe-json sword-recipe))
  (println json-str)

  ;; 10. 保存到文件
  (save-recipe-json! sword-recipe
                     "data/mymod/recipes/diamond_sword.json"))
