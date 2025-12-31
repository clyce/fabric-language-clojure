(ns com.fabriclj.swiss-knife.common.game-objects.items
  "瑞士军刀 - 物品工具模块

   提供物品操作、NBT 数据处理、物品栈管理等功能。

   注意: 玩家物品操作( give-item!, has-item! 等) 已移至 game-objects.players 模块。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.world.item ItemStack Item Items Rarity)
           (net.minecraft.world.entity.player Player)
           (net.minecraft.world.item.enchantment Enchantment)
           (net.minecraft.core.component DataComponents)
           (net.minecraft.nbt CompoundTag)
           (net.minecraft.world.level Level)
           (net.minecraft.world.entity.item ItemEntity)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 物品栈创建
;; ============================================================================

(defn item-stack
  "创建物品栈

   参数:
   - item: Item、ResourceLocation、String 或 Keyword
   - count: 数量( 默认 1)

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
   - entity: 使用物品的实体( 可选)

   返回: 是否损坏到完全破碎"
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

(defn get-durability
  "获取当前耐久度

   参数:
   - stack: 物品栈

   返回: 当前耐久度值

   示例:
   ```clojure
   (get-durability sword-stack)  ; => 1550 (假设最大 1561，损伤 11)
   ```"
  [^ItemStack stack]
  (- (get-max-damage stack) (get-damage stack)))

(defn get-durability-ratio
  "获取耐久度百分比

   参数:
   - stack: 物品栈

   返回: 0.0-1.0 之间的浮点数

   示例:
   ```clojure
   (get-durability-ratio sword-stack)  ; => 0.993 (99.3% 耐久)
   ```"
  ^double [^ItemStack stack]
  (let [max-damage (get-max-damage stack)
        damage (get-damage stack)]
    (if (pos? max-damage)
      (/ (double (- max-damage damage)) max-damage)
      1.0)))

(defn hurt-and-break!
  "损坏物品( 带实体和破碎回调)

   这是对 ItemStack.hurtAndBreak 的封装，正确处理耐久度和破碎逻辑。

   参数:
   - stack: 物品栈
   - amount: 损伤量
   - entity: 使用物品的实体( 可选)
   - on-break: 物品破碎时的回调函数( 可选)

   返回: boolean( 是否完全破碎)

   示例:
   ```clojure
   ;; 基本用法
   (hurt-and-break! sword-stack 1)

   ;; 带实体( 会触发 Unbreaking 等附魔效果)
   (hurt-and-break! sword-stack 1 player)

   ;; 带破碎回调
   (hurt-and-break! sword-stack 1 player
     (fn [entity]
       (println \"物品破碎了！\")))
   ```"
  ([^ItemStack stack amount]
   (hurt-and-break! stack amount nil nil))
  ([^ItemStack stack amount entity]
   (hurt-and-break! stack amount entity nil))
  ([^ItemStack stack amount entity on-break]
   (if entity
     (let [callback (if on-break
                      (reify java.util.function.Consumer
                        (accept [_ e] (on-break e)))
                      (reify java.util.function.Consumer
                        (accept [_ _] nil)))]
       (.hurtAndBreak stack (int amount) entity callback))
     (do
       (set-damage! stack (+ (get-damage stack) amount))
       (when (and on-break (>= (get-damage stack) (get-max-damage stack)))
         (on-break nil))
       (>= (get-damage stack) (get-max-damage stack))))))

;; ============================================================================
;; NBT / Data Components (1.20.5+)
;; ============================================================================

(defn has-custom-name?
  "物品是否有自定义名称"
  [^ItemStack stack]
  (.hasCustomHoverName stack))

(defn get-display-name
  "获取显示名称

   返回: Component 对象"
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
  "获取物品描述( Lore)

   返回: Component 列表"
  [^ItemStack stack]
  (when-let [lore-component (.get stack DataComponents/LORE)]
    (.lines lore-component)))

(defn set-lore!
  "设置物品描述

   参数:
   - stack: 物品栈
   - lines: 描述文本列表( 字符串或 Component)

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

   返回: CompoundTag"
  ^CompoundTag [^ItemStack stack]
  (when-let [custom (.get stack DataComponents/CUSTOM_DATA)]
    (.copyTag custom)))

(defn set-custom-data!
  "设置自定义 NBT 数据

   参数:
   - stack: 物品栈
   - tag: CompoundTag 或 Map( 自动转换)

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
   - key: 字段名( 关键字或字符串)
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
  "比较两个物品栈是否为相同物品( 不考虑数量和 NBT) "
  [^ItemStack stack1 ^ItemStack stack2]
  (ItemStack/isSameItem stack1 stack2))

(defn same-item-components?
  "比较两个物品栈是否完全相同( 包括 NBT/Components) "
  [^ItemStack stack1 ^ItemStack stack2]
  (ItemStack/isSameItemSameComponents stack1 stack2))

;; ============================================================================
;; 玩家物品操作
;; ============================================================================

;; 保留手持物品操作( 与 ItemStack 直接相关)
(defn get-held-item
  "获取玩家主手物品

   注意: 此函数返回 ItemStack，如需玩家物品管理请使用 game-objects.players 模块"
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

   返回: ItemEntity"
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
;; 食物属性构建器
;; ============================================================================

(defn food-properties
  "创建食物属性构建器

   参数( 关键字参数) :
   - :nutrition - 饥饿值恢复量( 默认 0)
   - :saturation - 饱和度修正值( 默认 0.0)
   - :always-eat? - 是否总是可以吃( 即使不饿) ( 默认 false)
   - :fast-food? - 是否快速食用( 默认 false)
   - :meat? - 是否为肉类( 默认 false)
   - :effects - 效果列表，每个效果为 map:
     {:effect :effect-id :duration ticks :amplifier level :probability 0.0-1.0}

   返回: FoodProperties

   示例:
   ```clojure
   ;; 简单食物
   (food-properties :nutrition 4 :saturation 0.5)

   ;; 带效果的食物
   (food-properties
     :nutrition 2
     :saturation 0.3
     :always-eat? true
     :effects [{:effect :speed
                :duration 200
                :amplifier 0
                :probability 1.0}
               {:effect :jump_boost
                :duration 100
                :amplifier 1
                :probability 0.5}])

   ;; 肉类食物
   (food-properties
     :nutrition 8
     :saturation 0.8
     :meat? true
     :fast-food? false)
   ```"
  [& {:keys [nutrition saturation always-eat? fast-food? meat? effects]
      :or {nutrition 0
           saturation 0.0
           always-eat? false
           fast-food? false
           meat? false
           effects []}}]
  (let [^net.minecraft.world.food.FoodProperties$Builder builder
        (net.minecraft.world.food.FoodProperties$Builder.)]
    (.nutrition builder (int nutrition))
    (.saturationMod builder (float saturation))
    (when always-eat?
      (.alwaysEat builder))
    (when fast-food?
      (.fast builder))
    (when meat?
      (.meat builder))
    (doseq [effect-data effects]
      (let [effect-id (:effect effect-data)
            duration (:duration effect-data 100)
            amplifier (:amplifier effect-data 0)
            probability (:probability effect-data 1.0)
            mob-effect (if (keyword? effect-id)
                         (case effect-id
                           :speed net.minecraft.world.effect.MobEffects/MOVEMENT_SPEED
                           :slowness net.minecraft.world.effect.MobEffects/MOVEMENT_SLOWDOWN
                           :haste net.minecraft.world.effect.MobEffects/DIG_SPEED
                           :mining-fatigue net.minecraft.world.effect.MobEffects/DIG_SLOWDOWN
                           :strength net.minecraft.world.effect.MobEffects/DAMAGE_BOOST
                           :instant-health net.minecraft.world.effect.MobEffects/HEAL
                           :instant-damage net.minecraft.world.effect.MobEffects/HARM
                           :jump-boost net.minecraft.world.effect.MobEffects/JUMP
                           :nausea net.minecraft.world.effect.MobEffects/CONFUSION
                           :regeneration net.minecraft.world.effect.MobEffects/REGENERATION
                           :resistance net.minecraft.world.effect.MobEffects/DAMAGE_RESISTANCE
                           :fire-resistance net.minecraft.world.effect.MobEffects/FIRE_RESISTANCE
                           :water-breathing net.minecraft.world.effect.MobEffects/WATER_BREATHING
                           :invisibility net.minecraft.world.effect.MobEffects/INVISIBILITY
                           :blindness net.minecraft.world.effect.MobEffects/BLINDNESS
                           :night-vision net.minecraft.world.effect.MobEffects/NIGHT_VISION
                           :hunger net.minecraft.world.effect.MobEffects/HUNGER
                           :weakness net.minecraft.world.effect.MobEffects/WEAKNESS
                           :poison net.minecraft.world.effect.MobEffects/POISON
                           :wither net.minecraft.world.effect.MobEffects/WITHER
                           :health-boost net.minecraft.world.effect.MobEffects/HEALTH_BOOST
                           :absorption net.minecraft.world.effect.MobEffects/ABSORPTION
                           :saturation net.minecraft.world.effect.MobEffects/SATURATION
                           :glowing net.minecraft.world.effect.MobEffects/GLOWING
                           :levitation net.minecraft.world.effect.MobEffects/LEVITATION
                           :luck net.minecraft.world.effect.MobEffects/LUCK
                           :unluck net.minecraft.world.effect.MobEffects/UNLUCK
                           :slow-falling net.minecraft.world.effect.MobEffects/SLOW_FALLING
                           :conduit-power net.minecraft.world.effect.MobEffects/CONDUIT_POWER
                           :dolphins-grace net.minecraft.world.effect.MobEffects/DOLPHINS_GRACE
                           :bad-omen net.minecraft.world.effect.MobEffects/BAD_OMEN
                           :hero-of-the-village net.minecraft.world.effect.MobEffects/HERO_OF_THE_VILLAGE
                           :darkness net.minecraft.world.effect.MobEffects/DARKNESS
                           effect-id)
                         effect-id)
            effect-instance (net.minecraft.world.effect.MobEffectInstance.
                             mob-effect (int duration) (int amplifier))]
        (.effect builder effect-instance (float probability))))
    (.build builder)))

;; ============================================================================
;; 稀有度
;; ============================================================================

(defn get-rarity
  "获取物品稀有度

   返回: :common/:uncommon/:rare/:epic"
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

   返回: 修改后的物品栈( 可能为空) "
  [^ItemStack stack amount]
  (.shrink stack amount)
  stack)

(defn grow-stack!
  "增加物品栈数量

   参数:
   - stack: 物品栈
   - amount: 增加量

   返回: 修改后的物品栈"
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
  (drop-item! player (item-stack :minecraft:emerald 5) true)

  ;; 手持物品操作
  (get-main-hand-item player)
  (get-off-hand-item player)
  (holding-item? player Items/DIAMOND_SWORD)
  (holding-item? player Items/SHIELD :off-hand)
  (holding-item? player Items/TORCH :either)

  ;; 耐久度操作
  (get-durability sword-stack)
  (get-durability-ratio sword-stack)
  (hurt-and-break! sword-stack 1 player)
  (repair-item! sword-stack 10)

  ;; 食物属性
  (food-properties
   :nutrition 4
   :saturation 0.5
   :always-eat? true
   :fast-food? false
   :effects [{:effect :speed
              :duration 200
              :amplifier 0
              :probability 1.0}]))
