(ns com.fabriclj.swiss-knife.common.gameplay.enchantments
  "瑞士军刀 - 附魔系统模块

   提供附魔查询、添加和管理功能。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.world.item ItemStack)
           (net.minecraft.world.item.enchantment Enchantment EnchantmentHelper Enchantments)
           (net.minecraft.core.component DataComponents)
           (net.minecraft.core Holder)
           (net.minecraft.core.registries Registries)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 附魔查询
;; ============================================================================

(defn get-enchantments
  "获取物品的所有附魔

   返回: {Enchantment level} Map"
  [^ItemStack stack]
  (let [enchantments (.get stack DataComponents/ENCHANTMENTS)]
    (when enchantments
      (into {}
            (map (fn [[holder level]]
                   [(.value ^Holder holder) level])
                 (.entrySet enchantments))))))

(defn get-enchantment-level
  "获取特定附魔的等级

   参数:
   - stack: ItemStack
   - enchantment: Enchantment 或关键字

   返回: 附魔等级( 0 表示没有)

   示例:
   ```clojure
   (get-enchantment-level sword :sharpness)
   ```"
  [^ItemStack stack enchantment]
  (let [enchantments (get-enchantments stack)]
    (if enchantments
      (get enchantments enchantment 0)
      0)))

(defn has-enchantment?
  "检查物品是否有指定附魔"
  [^ItemStack stack enchantment]
  (> (get-enchantment-level stack enchantment) 0))

;; ============================================================================
;; 附魔添加/移除
;; ============================================================================

(defn add-enchantment!
  "添加附魔到物品

   参数:
   - stack: ItemStack
   - enchantment: Enchantment
   - level: 等级

   示例:
   ```clojure
   (add-enchantment! sword Enchantments/SHARPNESS 5)
   ```"
  [^ItemStack stack ^Enchantment enchantment level]
  (.enchant stack enchantment level))

(defn add-enchantments!
  "批量添加附魔

   参数:
   - stack: ItemStack
   - enchantments: {enchantment level} Map

   示例:
   ```clojure
   (add-enchantments! sword
     {Enchantments/SHARPNESS 5
      Enchantments/FIRE_ASPECT 2
      Enchantments/LOOTING 3})
   ```"
  [^ItemStack stack enchantments]
  (doseq [[enchantment level] enchantments]
    (add-enchantment! stack enchantment level)))

(defn remove-enchantment!
  "移除附魔

   参数:
   - stack: ItemStack
   - enchantment: Enchantment"
  [^ItemStack stack ^Enchantment enchantment]
  (let [enchantments (.get stack DataComponents/ENCHANTMENTS)]
    (when enchantments
      (.remove enchantments enchantment))))

(defn clear-enchantments!
  "清除所有附魔"
  [^ItemStack stack]
  (.remove stack DataComponents/ENCHANTMENTS))

;; ============================================================================
;; 附魔效果计算
;; ============================================================================

(defn calculate-damage-bonus
  "计算附魔伤害加成

   参数:
   - stack: ItemStack
   - target: 目标实体( 可选)

   返回: 伤害加成"
  ([^ItemStack stack]
   (calculate-damage-bonus stack nil))
  ([^ItemStack stack target]
   (if target
     (EnchantmentHelper/getDamageBonus stack target)
     0.0)))

(defn calculate-protection
  "计算护甲附魔保护值

   参数:
   - armor-items: 护甲物品列表
   - damage-source: 伤害源

   返回: 保护值"
  [armor-items ^net.minecraft.world.damagesource.DamageSource damage-source]
  (EnchantmentHelper/getDamageProtection armor-items damage-source))

;; ============================================================================
;; 常用原版附魔
;; ============================================================================

(defn get-vanilla-enchantment
  "获取原版附魔

   参数:
   - enchantment-key: 附魔关键字

   返回: Enchantment

   支持的关键字:
   :protection, :fire-protection, :feather-falling, :blast-protection,
   :projectile-protection, :respiration, :aqua-affinity, :thorns,
   :depth-strider, :frost-walker, :soul-speed, :sharpness, :smite,
   :bane-of-arthropods, :knockback, :fire-aspect, :looting, :sweeping,
   :efficiency, :silk-touch, :unbreaking, :fortune, :power, :punch,
   :flame, :infinity, :luck-of-the-sea, :lure, :loyalty, :impaling,
   :riptide, :channeling, :multishot, :quick-charge, :piercing, :mending,
   :vanishing-curse, :binding-curse"
  ^Enchantment [enchantment-key]
  (case enchantment-key
    ;; 这里列出一些常用的，实际使用时需要通过注册表获取
    :sharpness (throw (UnsupportedOperationException. "Use Enchantments/SHARPNESS"))
    :protection (throw (UnsupportedOperationException. "Use Enchantments/PROTECTION"))
    ;; ... 更多附魔
    (throw (IllegalArgumentException. (str "Unknown enchantment: " enchantment-key)))))

;; Response to clyce: 自定义附魔系统设计
;;
;; Clojure-style 的优雅附魔系统可以这样设计:
;;
;; 1. 基于数据的附魔定义:
;;    (defenchantment fire-aspect
;;      {:max-level 2
;;       :rarity :rare
;;       :applicable? (fn [item] (instance? SwordItem item))
;;       :effects {:on-hit (fn [level target attacker]
;;                           (.setSecondsOnFire target (* level 4)))}})
;;
;; 2. 组合式效果系统:
;;    (defenchantment-effect :ignite
;;      (fn [level target]
;;        (.setSecondsOnFire target (* level 4))))
;;
;;    (defenchantment-effect :poison
;;      (fn [level target]
;;        (.addEffect target (MobEffectInstance. ...))))
;;
;;    (compose-enchantment :deadly-blade
;;      [:ignite 2] [:poison 1])
;;
;; 3. 事件驱动模型:
;;    附魔监听特定事件( 攻击/防御/移动/挖掘等)
;;    使用 multimethod 根据附魔类型分发
;;
;; 建议单独创建 enchantments-dsl.clj 实现完整系统
;;
;; ============================================================================
;; 自定义附魔系统 - 组合式设计
;; ============================================================================

(defonce ^:private custom-enchantments (atom {}))
(defonce ^:private enchantment-effects (atom {}))

;; 附魔效果多态分发
(defmulti apply-enchantment-effect
  "应用附魔效果( 多态分发)

   分发键: [effect-id event-type]"
  (fn [effect-id event-type & _args]
    [effect-id event-type]))

;; 默认实现: 查找已注册的效果函数
(defmethod apply-enchantment-effect :default
  [effect-id event-type & args]
  (when-let [effect-map (get @enchantment-effects effect-id)]
    (when-let [effect-fn (get effect-map event-type)]
      (apply effect-fn args))))

;; ============================================================================
;; 效果注册
;; ============================================================================

(defn register-enchantment-effect!
  "注册可重用的附魔效果

   参数:
   - effect-id: 效果 ID( 关键字)
   - event-type: 事件类型( :on-hit/:on-defense/:on-tick 等)
   - effect-fn: 效果函数

   示例:
   ```clojure
   ;; 注册点燃效果
   (register-enchantment-effect! :ignite :on-hit
     (fn [level target attacker]
       (.setSecondsOnFire target (* level 4))))

   ;; 注册中毒效果
   (register-enchantment-effect! :poison :on-hit
     (fn [level target attacker]
       (.addEffect target
         (MobEffectInstance. MobEffects/POISON (* level 20) level))))
   ```"
  [effect-id event-type effect-fn]
  (swap! enchantment-effects
         assoc-in [effect-id event-type] effect-fn))

(defn compose-enchantment-effects
  "组合多个效果

   参数:
   - effects: 效果列表 [[effect-id level] ...]

   返回: 组合后的效果函数

   示例:
   ```clojure
   (def combo-effect
     (compose-enchantment-effects
       [[:ignite 2] [:poison 1] [:slow 1]]))
   ```"
  [effects]
  (fn [event-type & args]
    (doseq [[effect-id level] effects]
      (apply apply-enchantment-effect effect-id event-type level args))))

;; ============================================================================
;; 自定义附魔定义
;; ============================================================================

(defn defenchantment
  "定义自定义附魔( 数据驱动)

   参数:
   - enchantment-id: 附魔 ID
   - config: 配置映射
     - :max-level - 最大等级
     - :rarity - 稀有度
     - :applicable? - 适用性检查函数 (fn [item] -> boolean)
     - :effects - 效果定义
       - 可以是 {:event-type effect-fn} 映射
       - 也可以是 [dispatch-fn composed-effects]
     - :conflicts - 冲突的附魔列表

   示例:
   ```clojure
   ;; 方式1: 直接定义效果
   (defenchantment :fire-aspect
     {:max-level 2
      :rarity :rare
      :applicable? #(instance? SwordItem %)
      :effects {:on-hit (fn [level target attacker]
                          (.setSecondsOnFire target (* level 4)))}})

   ;; 方式2: 使用组合效果
   (defenchantment :deadly-blade
     {:max-level 3
      :rarity :epic
      :effects (compose-enchantment-effects
                 [[:ignite 2] [:poison 1]])})
   ```"
  [enchantment-id config]
  (swap! custom-enchantments assoc enchantment-id config)
  enchantment-id)

(defn get-enchantment-config
  "获取附魔配置"
  [enchantment-id]
  (get @custom-enchantments enchantment-id))

;; ============================================================================
;; 效果触发
;; ============================================================================

(defn trigger-enchantment-effect
  "触发附魔效果

   参数:
   - stack: ItemStack
   - event-type: 事件类型
   - args: 事件参数

   示例:
   ```clojure
   ;; 触发攻击效果
   (trigger-enchantment-effect sword :on-hit target attacker)

   ;; 触发防御效果
   (trigger-enchantment-effect armor :on-defense damage source)
   ```"
  [^ItemStack stack event-type & args]
  (let [enchantments (get-enchantments stack)]
    (doseq [[enchantment level] enchantments]
      (when-let [config (get-enchantment-config enchantment)]
        (let [effects (:effects config)]
          (cond
            ;; 映射形式: {:on-hit fn ...}
            (map? effects)
            (when-let [effect-fn (get effects event-type)]
              (apply effect-fn level args))

            ;; 函数形式: 组合效果
            (fn? effects)
            (apply effects event-type args)))))))

;; ============================================================================
;; 预设效果
;; ============================================================================

;; 火焰效果
(register-enchantment-effect! :ignite :on-hit
                              (fn [level target _attacker]
                                (when (instance? net.minecraft.world.entity.Entity target)
                                  (.setSecondsOnFire ^net.minecraft.world.entity.Entity target (* level 4)))))

;; 中毒效果
(register-enchantment-effect! :poison :on-hit
                              (fn [level target _attacker]
                                (when (instance? net.minecraft.world.entity.LivingEntity target)
                                  (.addEffect ^net.minecraft.world.entity.LivingEntity target
                                              (net.minecraft.world.effect.MobEffectInstance.
                                               net.minecraft.world.effect.MobEffects/POISON
                                               (* level 20)
                                               (dec level))))))

;; 缓慢效果
(register-enchantment-effect! :slow :on-hit
                              (fn [level target _attacker]
                                (when (instance? net.minecraft.world.entity.LivingEntity target)
                                  (.addEffect ^net.minecraft.world.entity.LivingEntity target
                                              (net.minecraft.world.effect.MobEffectInstance.
                                               net.minecraft.world.effect.MobEffects/MOVEMENT_SLOWDOWN
                                               (* level 40)
                                               level)))))

;; 吸血效果
(register-enchantment-effect! :lifesteal :on-hit
                              (fn [level _target attacker]
                                (when (instance? net.minecraft.world.entity.LivingEntity attacker)
                                  (.heal ^net.minecraft.world.entity.LivingEntity attacker (* level 0.5)))))

;; 雷击效果
(register-enchantment-effect! :lightning :on-hit
                              (fn [level target _attacker]
                                (when (instance? net.minecraft.world.entity.Entity target)
                                  (let [^net.minecraft.world.entity.Entity entity target
                                        level (.level entity)
                                        pos (.position entity)]
                                    (when-not (.isClientSide level)
                                      (.addFreshEntity level
                                                       (net.minecraft.world.entity.EntityType/LIGHTNING_BOLT
                                                        .create level)))))))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro defenchant
  "简化的附魔定义宏

   示例:
   ```clojure
   (defenchant fire-aspect
     :max-level 2
     :rarity :rare
     :on-hit (fn [level target attacker]
               (.setSecondsOnFire target (* level 4))))
   ```"
  [name & {:keys [max-level rarity applicable? on-hit on-defense on-tick conflicts]
           :or {max-level 1 rarity :common}}]
  `(defenchantment ~(keyword name)
     {:max-level ~max-level
      :rarity ~rarity
      ~@(when applicable? [:applicable? applicable?])
      :effects ~(into {}
                      (filter (fn [[_k v]] v)
                              {:on-hit on-hit
                               :on-defense on-defense
                               :on-tick on-tick}))
      ~@(when conflicts [:conflicts conflicts])}))

(comment
  ;; 使用示例

  ;; 1. 查询附魔
  (def enchantments (get-enchantments sword))
  (def sharp-level (get-enchantment-level sword Enchantments/SHARPNESS))
  (has-enchantment? sword Enchantments/FIRE_ASPECT)

  ;; 2. 添加附魔
  (add-enchantment! sword Enchantments/SHARPNESS 5)
  (add-enchantments! sword
                     {Enchantments/SHARPNESS 5
                      Enchantments/FIRE_ASPECT 2
                      Enchantments/LOOTING 3})

  ;; 3. 计算伤害
  (def bonus-damage (calculate-damage-bonus sword zombie))

  ;; 4. 清除附魔
  (clear-enchantments! sword)

  ;; ========== 自定义附魔系统 ==========

  ;; 5. 注册可重用效果
  (register-enchantment-effect! :freeze :on-hit
                                (fn [level target attacker]
                                  (.addEffect target
                                              (MobEffectInstance. MobEffects/MOVEMENT_SLOWDOWN (* level 60) 4))))

  ;; 6. 定义简单附魔
  (defenchant frost-blade
    :max-level 3
    :rarity :rare
    :on-hit (fn [level target attacker]
              (.setTicksFrozen target (* level 100))))

  ;; 7. 定义组合附魔
  (defenchantment :ultimate-blade
    {:max-level 1
     :rarity :epic
     :effects (compose-enchantment-effects
               [[:ignite 2] [:poison 1] [:slow 1]])})

  ;; 8. 使用多态分发自定义效果
  (defmethod apply-enchantment-effect [:custom-power :on-hit]
    [_ _ level target attacker]
    ;; 自定义逻辑
    (println "Custom power activated!" level))

  ;; 9. 触发自定义附魔效果
  (trigger-enchantment-effect sword :on-hit target attacker))
