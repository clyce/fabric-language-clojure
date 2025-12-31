(ns com.fabriclj.swiss-knife.common.gameplay.damage
  "瑞士军刀 - 伤害系统模块

   提供伤害计算、伤害类型创建和伤害应用。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.world.damagesource DamageSource DamageTypes DamageType)
           (net.minecraft.world.entity Entity LivingEntity)
           (net.minecraft.core.registries Registries)
           (net.minecraft.resources ResourceKey ResourceLocation)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 伤害类型
;; ============================================================================

(def damage-types
  "常用伤害类型"
  {:generic DamageTypes/GENERIC
   :player-attack DamageTypes/PLAYER_ATTACK
   :mob-attack DamageTypes/MOB_ATTACK
   :explosion DamageTypes/EXPLOSION
   :fire DamageTypes/IN_FIRE
   :lava DamageTypes/LAVA
   :drown DamageTypes/DROWN
   :starve DamageTypes/STARVE
   :cactus DamageTypes/CACTUS
   :fall DamageTypes/FALL
   :fly-into-wall DamageTypes/FLY_INTO_WALL
   :magic DamageTypes/MAGIC
   :wither DamageTypes/WITHER
   :dragon-breath DamageTypes/DRAGON_BREATH
   :lightning-bolt DamageTypes/LIGHTNING_BOLT})

(defonce ^:private custom-damage-types (atom {}))

(defn register-custom-damage-type!
  "注册自定义伤害类型

   参数:
   - key: 关键字标识
   - resource-key: ResourceKey

   示例:
   ```clojure
   (register-custom-damage-type! :my-magic
     (ResourceKey/create Registries/DAMAGE_TYPE
       (ResourceLocation/fromNamespaceAndPath \"mymod\" \"magic\")))
   ```"
  [key resource-key]
  (swap! custom-damage-types assoc key resource-key)
  key)

(defn get-damage-type
  "获取伤害类型( 支持原版和自定义) "
  [damage-type]
  (or (get damage-types damage-type)
      (get @custom-damage-types damage-type)
      damage-type))

(defn create-damage-source
  "创建伤害源

   参数:
   - level: Level
   - damage-type: 伤害类型( 关键字或 ResourceKey)
   - opts: 可选参数
     - :direct-entity - 直接伤害实体
     - :causing-entity - 造成伤害的实体

   返回: DamageSource

   示例:
   ```clojure
   (create-damage-source level :magic
     {:causing-entity player})
   ```"
  [level damage-type & [opts]]
  (let [{:keys [direct-entity causing-entity]} opts
        damage-type-key (if (keyword? damage-type)
                          (get-damage-type damage-type)
                          damage-type)
        registry (.registryAccess level)
        damage-types-registry (.registryOrThrow registry Registries/DAMAGE_TYPE)
        damage-type-holder (.getHolderOrThrow damage-types-registry damage-type-key)]
    (cond
      (and direct-entity causing-entity)
      (DamageSource. damage-type-holder direct-entity causing-entity)

      direct-entity
      (DamageSource. damage-type-holder direct-entity)

      :else
      (DamageSource. damage-type-holder))))

;; ============================================================================
;; 伤害应用
;; ============================================================================

(defn deal-damage!
  "对实体造成伤害

   参数:
   - entity: 受伤实体
   - amount: 伤害量
   - damage-source: DamageSource

   返回: 是否造成伤害

   示例:
   ```clojure
   (deal-damage! target 10.0
     (create-damage-source level :magic
       {:causing-entity player}))
   ```"
  [^Entity entity amount ^DamageSource damage-source]
  (.hurt entity damage-source amount))

(defn deal-damage-simple!
  "造成简单伤害( 无伤害源)

   参数:
   - entity: 受伤实体
   - amount: 伤害量
   - damage-type: 伤害类型( 关键字)

   示例:
   ```clojure
   (deal-damage-simple! target 5.0 :generic)
   ```"
  [^Entity entity amount damage-type]
  (let [level (.level entity)
        source (create-damage-source level damage-type)]
    (deal-damage! entity amount source)))

(defn deal-damage-from!
  "造成来自实体的伤害

   参数:
   - target: 目标实体
   - amount: 伤害量
   - attacker: 攻击者
   - damage-type: 伤害类型( 可选，默认 :mob-attack)

   示例:
   ```clojure
   (deal-damage-from! target 10.0 player :player-attack)
   ```"
  ([target amount attacker]
   (deal-damage-from! target amount attacker :mob-attack))
  ([^Entity target amount ^Entity attacker damage-type]
   (let [level (.level target)
         source (create-damage-source level damage-type
                                      {:direct-entity attacker
                                       :causing-entity attacker})]
     (deal-damage! target amount source))))

;; ============================================================================
;; 伤害计算
;; ============================================================================

(defonce ^:private damage-calculators (atom {}))

(defn register-damage-calculator!
  "为自定义伤害类型注册计算公式

   参数:
   - damage-type: 伤害类型关键字
   - calculator: 计算函数 (fn [base-damage armor toughness entity] -> final-damage)

   示例:
   ```clojure
   (register-damage-calculator! :my-magic
     (fn [base-damage armor toughness entity]
       ;; 魔法伤害忽略护甲
       base-damage))
   ```"
  [damage-type calculator]
  (swap! damage-calculators assoc damage-type calculator))

(declare calculate-armor-damage)
(defn calculate-damage-with-type
  "根据伤害类型计算最终伤害

   参数:
   - damage-type: 伤害类型
   - base-damage: 基础伤害
   - armor-value: 护甲值
   - toughness: 护甲韧性
   - entity: 受伤实体( 可选)

   返回: 最终伤害"
  ([damage-type base-damage armor-value toughness]
   (calculate-damage-with-type damage-type base-damage armor-value toughness nil))
  ([damage-type base-damage armor-value toughness entity]
   (if-let [calculator (get @damage-calculators damage-type)]
     (calculator base-damage armor-value toughness entity)
     ;; 默认使用标准护甲计算
     (calculate-armor-damage base-damage armor-value toughness))))

(defn calculate-armor-damage
  "计算考虑护甲的伤害

   参数:
   - base-damage: 基础伤害
   - armor-value: 护甲值
   - toughness: 护甲韧性( 可选，默认 0)

   返回: 实际伤害

   公式: damage * (1 - min(20, max(armor / 5, armor - damage / (2 + toughness / 4))) / 25)"
  ([base-damage armor-value]
   (calculate-armor-damage base-damage armor-value 0.0))
  ([base-damage armor-value toughness]
   (let [armor-reduction (min 20.0
                              (max (/ armor-value 5.0)
                                   (- armor-value
                                      (/ base-damage
                                         (+ 2.0 (/ toughness 4.0))))))
         multiplier (- 1.0 (/ armor-reduction 25.0))]
     (* base-damage multiplier))))

(defn calculate-resistance-damage
  "计算考虑抗性效果的伤害

   参数:
   - base-damage: 基础伤害
   - resistance-level: 抗性等级( 0-4)

   返回: 实际伤害"
  [base-damage resistance-level]
  (* base-damage (- 1.0 (* 0.2 resistance-level))))

(defn calculate-enchantment-protection
  "计算附魔保护

   参数:
   - base-damage: 基础伤害
   - protection-level: 保护等级

   返回: 实际伤害"
  [base-damage protection-level]
  (let [reduction (min 0.8 (* 0.04 protection-level))]
    (* base-damage (- 1.0 reduction))))

;; ============================================================================
;; 伤害事件
;; ============================================================================

(defonce ^:private damage-listeners (atom {}))

(defn on-damage!
  "注册伤害监听器( 支持按类型过滤)

   参数:
   - handler: 处理函数 (fn [entity amount source] -> modified-amount)
   - opts: 可选参数
     - :damage-types - 监听的伤害类型列表( nil 表示所有)
     - :id - 自定义监听器 ID( 可选)

   返回: 监听器 ID

   示例:
   ```clojure
   ;; 监听所有伤害
   (on-damage!
     (fn [entity amount source]
       (if (instance? Player entity)
         (* amount 0.5)
         amount)))

   ;; 只监听魔法和火焰伤害
   (on-damage!
     (fn [entity amount source] (* amount 0.8))
     {:damage-types [:magic :fire]
      :id :my-protection})
   ```"
  ([handler]
   (on-damage! handler {}))
  ([handler opts]
   (let [{:keys [damage-types id] :or {id (java.util.UUID/randomUUID)}} opts]
     (swap! damage-listeners assoc id {:handler handler :damage-types damage-types})
     id)))

(defn on-damage-for-entity!
  "为特定实体注册伤害监听器

   参数:
   - entity: Entity 或 entity-id
   - handler: 处理函数
   - opts: 可选参数

   示例:
   ```clojure
   (on-damage-for-entity! player
     (fn [entity amount source] (* amount 0.5))
     {:id :player-armor-bonus})
   ```"
  ([entity handler]
   (on-damage-for-entity! entity handler {}))
  ([entity handler opts]
   (let [entity-id (if (instance? Entity entity)
                     (.getUUID ^Entity entity)
                     entity)
         id (:id opts (java.util.UUID/randomUUID))]
     (swap! damage-listeners assoc id
            (assoc opts
                   :handler handler
                   :entity-id entity-id))
     id)))

(defn remove-damage-listener!
  "移除伤害监听器

   参数:
   - id: 监听器 ID
   - entity: Entity( 可选，用于移除实体专属监听器) "
  ([id]
   (swap! damage-listeners dissoc id))
  ([entity id]
   (remove-damage-listener! id)))

(comment
  ;; 使用示例

  ;; 1. 造成伤害
  (deal-damage-simple! target 10.0 :magic)
  (deal-damage-from! target 15.0 player :player-attack)

  ;; 2. 自定义伤害源
  (def magic-damage
    (create-damage-source level :magic
                          {:causing-entity player}))
  (deal-damage! target 20.0 magic-damage)

  ;; 3. 伤害计算
  (def final-damage
    (-> 10.0
        (calculate-armor-damage 15 2)
        (calculate-resistance-damage 2)
        (calculate-enchantment-protection 4)))

  ;; 4. 伤害监听
  (on-damage!
   (fn [entity amount source]
     (println "Entity" entity "took" amount "damage")
     amount)))
