(ns com.fabriclj.swiss-knife.common.items
  "瑞士军刀 - 物品工具模块

   提供物品操作、NBT 数据处理、物品栈管理等功能。"
  (:require [com.fabriclj.swiss-knife.common.core :as core])
  (:import [net.minecraft.world.item ItemStack Item Items Rarity]
           [net.minecraft.world.entity.player Player]
           [net.minecraft.world.item.enchantment Enchantment]
           [net.minecraft.core.component DataComponents DataComponentType]
           [net.minecraft.nbt CompoundTag]
           [net.minecraft.world.level.Level]
           [net.minecraft.world.entity.item ItemEntity]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 物品栈创建
;; ============================================================================

(defn item-stack
  "创建物品栈

   参数:
   - item: Item、ResourceLocation、String 或 Keyword
   - count: 数量（默认 1）

   示例:
   ```clojure
   (item-stack :minecraft:diamond 64)
   (item-stack \"mymod:custom_item\")
   (item-stack Items/DIAMOND_SWORD)
   ```"
  (^ItemStack [item]
   (item-stack item 1))
  (^ItemStack [item count]
   (if (instance? Item item)
     (ItemStack. ^Item item count)
     (if-let [^Item item-obj (core/get-item item)]
       (ItemStack. item-obj count)
       (throw (IllegalArgumentException.
               (str "Unknown item: " item)))))))

(defn empty-stack?
  "检查物品栈是否为空"
  [^ItemStack stack]
  (.isEmpty stack))

(defn stack-size
  "获取物品栈数量"
  [^ItemStack stack]
  (.getCount stack))

(defn set-stack-size!
  "设置物品栈数量"
  [^ItemStack stack count]
  (.setCount stack count)
  stack)

;; ============================================================================
;; 物品属性查询
;; ============================================================================

(defn get-item-from-stack
  "从物品栈获取 Item 对象"
  ^Item [^ItemStack stack]
  (.getItem stack))

(defn max-stack-size
  "获取物品最大堆叠数"
  [^ItemStack stack]
  (.getMaxStackSize stack))

(defn is-stackable?
  "物品是否可堆叠"
  [^ItemStack stack]
  (> (.getMaxStackSize stack) 1))

(defn get-max-damage
  "获取最大耐久度"
  [^ItemStack stack]
  (.getMaxDamage stack))

(defn get-damage
  "获取当前损伤值"
  [^ItemStack stack]
  (.getDamageValue stack))

(defn set-damage!
  "设置损伤值"
  [^ItemStack stack damage]
  (.setDamageValue stack damage)
  stack)

(defn is-damageable?
  "物品是否可损坏"
  [^ItemStack stack]
  (.isDamageableItem stack))

(defn damage-item!
  "损坏物品

   参数:
   - stack: 物品栈
   - amount: 损伤量
   - entity: 使用物品的实体（可选）

   返回：是否损坏到完全破碎"
  ([^ItemStack stack amount]
   (.hurt stack amount nil (constantly nil)))
  ([^ItemStack stack amount entity]
   (.hurt stack amount nil (constantly nil))))

(defn repair-item!
  "修复物品

   参数:
   - stack: 物品栈
   - amount: 修复量"
  [^ItemStack stack amount]
  (set-damage! stack (max 0 (- (get-damage stack) amount))))

;; ============================================================================
;; NBT / Data Components (1.20.5+)
;; ============================================================================

(defn has-custom-name?
  "物品是否有自定义名称"
  [^ItemStack stack]
  (.hasCustomHoverName stack))

(defn get-display-name
  "获取显示名称

   返回：Component 对象"
  [^ItemStack stack]
  (.getHoverName stack))

(defn set-custom-name!
  "设置自定义名称

   参数:
   - stack: 物品栈
   - name: 名称字符串或 Component"
  [^ItemStack stack name]
  (if (string? name)
    (.set stack DataComponents/CUSTOM_NAME
          (net.minecraft.network.chat.Component/literal name))
    (.set stack DataComponents/CUSTOM_NAME name))
  stack)

(defn get-lore
  "获取物品描述（Lore）

   返回：Component 列表"
  [^ItemStack stack]
  (when-let [lore-component (.get stack DataComponents/LORE)]
    (.lines lore-component)))

(defn set-lore!
  "设置物品描述

   参数:
   - stack: 物品栈
   - lines: 描述文本列表（字符串或 Component）

   示例:
   ```clojure
   (set-lore! stack [\"Line 1\" \"Line 2\" \"Line 3\"])
   ```"
  [^ItemStack stack lines]
  (let [components (map #(if (string? %)
                           (net.minecraft.network.chat.Component/literal %)
                           %)
                        lines)
        lore (net.minecraft.world.item.component.ItemLore. components)]
    (.set stack DataComponents/LORE lore)
    stack))

(defn get-custom-data
  "获取自定义 NBT 数据

   返回：CompoundTag"
  ^CompoundTag [^ItemStack stack]
  (when-let [custom (.get stack DataComponents/CUSTOM_DATA)]
    (.copyTag custom)))

(defn set-custom-data!
  "设置自定义 NBT 数据

   参数:
   - stack: 物品栈
   - tag: CompoundTag 或 Map（自动转换）

   示例:
   ```clojure
   (set-custom-data! stack {\"mana\" 100 \"spell\" \"fireball\"})
   ```"
  [^ItemStack stack data]
  (let [^CompoundTag tag (if (instance? CompoundTag data)
                           data
                           (let [t (CompoundTag.)]
                             (doseq [[k v] data]
                               (cond
                                 (integer? v) (.putInt t (name k) v)
                                 (float? v) (.putFloat t (name k) v)
                                 (double? v) (.putDouble t (name k) v)
                                 (string? v) (.putString t (name k) v)
                                 (boolean? v) (.putBoolean t (name k) v)))
                             t))
        custom-data (net.minecraft.world.item.component.CustomData/of tag)]
    (.set stack DataComponents/CUSTOM_DATA custom-data)
    stack))

(defn get-custom-value
  "获取自定义数据字段

   参数:
   - stack: 物品栈
   - key: 字段名（关键字或字符串）
   - type: 数据类型 (:int/:float/:double/:string/:boolean)

   示例:
   ```clojure
   (get-custom-value stack :mana :int)
   ```"
  [^ItemStack stack key type]
  (when-let [^CompoundTag tag (get-custom-data stack)]
    (let [k (name key)]
      (when (.contains tag k)
        (case type
          :int (.getInt tag k)
          :float (.getFloat tag k)
          :double (.getDouble tag k)
          :string (.getString tag k)
          :boolean (.getBoolean tag k)
          (.get tag k))))))

(defn set-custom-value!
  "设置自定义数据字段

   示例:
   ```clojure
   (set-custom-value! stack :mana 100)
   ```"
  [^ItemStack stack key value]
  (let [^CompoundTag tag (or (get-custom-data stack) (CompoundTag.))
        k (name key)]
    (cond
      (integer? value) (.putInt tag k value)
      (float? value) (.putFloat tag k value)
      (double? value) (.putDouble tag k value)
      (string? value) (.putString tag k value)
      (boolean? value) (.putBoolean tag k value))
    (set-custom-data! stack tag)))

;; ============================================================================
;; 物品比较
;; ============================================================================

(defn same-item?
  "比较两个物品栈是否为相同物品（不考虑数量和 NBT）"
  [^ItemStack stack1 ^ItemStack stack2]
  (ItemStack/isSameItem stack1 stack2))

(defn same-item-components?
  "比较两个物品栈是否完全相同（包括 NBT/Components）"
  [^ItemStack stack1 ^ItemStack stack2]
  (ItemStack/isSameItemSameComponents stack1 stack2))

;; ============================================================================
;; 玩家物品操作
;; ============================================================================

(defn give-item!
  "给予玩家物品

   参数:
   - player: 玩家
   - stack: 物品栈

   返回：是否成功添加到背包"
  [^Player player ^ItemStack stack]
  (.add (.getInventory player) stack))

(defn remove-item!
  "从玩家背包移除物品

   参数:
   - player: 玩家
   - item: 物品
   - count: 数量

   返回：实际移除的数量"
  [^Player player item count]
  (let [^Item item-obj (if (instance? Item item)
                         item
                         (core/get-item item))]
    (.clearOrCountMatchingItems (.getInventory player)
                                (fn [^ItemStack s]
                                  (= (.getItem s) item-obj))
                                count
                                (.inventoryMenu player))))

(defn has-item?
  "检查玩家是否拥有物品

   参数:
   - player: 玩家
   - item: 物品
   - count: 数量（可选，默认 1）"
  ([^Player player item]
   (has-item? player item 1))
  ([^Player player item count]
   (let [^Item item-obj (if (instance? Item item)
                          item
                          (core/get-item item))
         inventory (.getInventory player)
         total (atom 0)]
     (doseq [i (range (.getContainerSize inventory))]
       (let [^ItemStack stack (.getItem inventory i)]
         (when (and (not (.isEmpty stack))
                    (= (.getItem stack) item-obj))
           (swap! total + (.getCount stack)))))
     (>= @total count))))

(defn get-held-item
  "获取玩家主手物品"
  ^ItemStack [^Player player]
  (.getMainHandItem player))

(defn get-offhand-item
  "获取玩家副手物品"
  ^ItemStack [^Player player]
  (.getOffhandItem player))

(defn set-held-item!
  "设置玩家主手物品"
  [^Player player ^ItemStack stack]
  (.setItemInHand player net.minecraft.world.InteractionHand/MAIN_HAND stack))

;; ============================================================================
;; 物品实体
;; ============================================================================

(defn spawn-item-entity!
  "在世界中生成物品实体

   参数:
   - level: Level
   - x, y, z: 坐标
   - stack: 物品栈

   返回：ItemEntity"
  ^ItemEntity [^Level level x y z ^ItemStack stack]
  (let [entity (ItemEntity. level x y z stack)]
    (.addFreshEntity level entity)
    entity))

(defn drop-item!
  "从实体位置掉落物品

   参数:
   - entity: 实体
   - stack: 物品栈
   - throw-randomly?: 是否随机抛出"
  ([entity ^ItemStack stack]
   (drop-item! entity stack false))
  ([^net.minecraft.world.entity.Entity entity ^ItemStack stack throw-randomly?]
   (.spawnAtLocation entity stack (if throw-randomly? 0.5 0.0))))

;; ============================================================================
;; 稀有度
;; ============================================================================

(defn get-rarity
  "获取物品稀有度

   返回：:common/:uncommon/:rare/:epic"
  [^ItemStack stack]
  (let [rarity (.getRarity stack)]
    (condp = rarity
      Rarity/COMMON :common
      Rarity/UNCOMMON :uncommon
      Rarity/RARE :rare
      Rarity/EPIC :epic
      :common)))

;; ============================================================================
;; 工具函数
;; ============================================================================

(defn copy-stack
  "复制物品栈"
  ^ItemStack [^ItemStack stack]
  (.copy stack))

(defn shrink-stack!
  "减少物品栈数量

   参数:
   - stack: 物品栈
   - amount: 减少量

   返回：修改后的物品栈（可能为空）"
  [^ItemStack stack amount]
  (.shrink stack amount)
  stack)

(defn grow-stack!
  "增加物品栈数量

   参数:
   - stack: 物品栈
   - amount: 增加量

   返回：修改后的物品栈"
  [^ItemStack stack amount]
  (.grow stack amount)
  stack)

(comment
  ;; 使用示例

  ;; 创建物品栈
  (def diamond-stack (item-stack :minecraft:diamond 64))
  (def sword (item-stack Items/DIAMOND_SWORD))

  ;; 设置自定义名称和描述
  (-> sword
      (set-custom-name! "§6Excalibur")
      (set-lore! ["§7A legendary sword"
                  "§9+10 Attack Damage"]))

  ;; 自定义数据
  (set-custom-value! sword :mana 100)
  (set-custom-value! sword :spell "fireball")
  (println "Mana:" (get-custom-value sword :mana :int))

  ;; 给予玩家物品
  (give-item! player diamond-stack)

  ;; 检查物品
  (when (has-item? player :minecraft:diamond 10)
    (println "Player has at least 10 diamonds"))

  ;; 掉落物品
  (drop-item! player (item-stack :minecraft:emerald 5) true))
