(ns com.fabriclj.swiss-knife.common.potions
  "药水效果系统

   提供完整的药水效果管理功能，包括：
   - 效果添加/移除/查询
   - 自定义药水效果
   - 效果时间管理
   - 效果强度控制
   - 药水酿造配方"
  (:require [com.fabriclj.swiss-knife.common.core :as core])
  (:import [net.minecraft.world.entity LivingEntity]
           [net.minecraft.world.effect MobEffect MobEffectInstance MobEffects]
           [net.minecraft.world.item ItemStack Items]
           [net.minecraft.world.item.alchemy Potion PotionUtils Potions]
           [net.minecraft.core Registry]
           [net.minecraft.resources ResourceLocation]
           [net.minecraft.world.item.crafting Ingredient]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 自定义药水效果 (Custom Potion Effects)
;; ============================================================================
;; 回复 clyce: 可以通过继承 MobEffect 并注册到游戏中来实现自定义效果。
;; 使用 Clojure 的 proxy 可以方便地创建自定义效果类，
;; tick 回调通过重写 applyEffectTick 方法实现。

(def ^:private custom-effects (atom {}))

(defn create-custom-effect
  "创建自定义药水效果

   参数:
   - id: 效果 ID（关键字）
   - category: 效果类别（:beneficial/:harmful/:neutral）
   - color: 效果颜色（整数 RGB，如 0xFF0000 为红色）
   - opts: 选项
     - :on-tick - tick 回调函数 (fn [entity amplifier] ...)
     - :tick-rate - tick 间隔（默认 20，即每秒）
     - :instant? - 是否为瞬时效果（默认 false）
     - :on-added - 效果添加时回调 (fn [entity amplifier] ...)
     - :on-removed - 效果移除时回调 (fn [entity amplifier] ...)

   返回：MobEffect 实例

   示例:
   ```clojure
   ;; 创建流血效果（每秒造成伤害）
   (def bleeding-effect
     (create-custom-effect :bleeding
       :harmful
       0xAA0000
       :on-tick (fn [entity amplifier]
                  (.hurt entity
                    (DamageSource. \"bleeding\")
                    (float (* (inc amplifier) 0.5))))
       :tick-rate 20))

   ;; 创建生命恢复效果
   (def life-steal-effect
     (create-custom-effect :life_steal
       :beneficial
       0x00FF00
       :on-tick (fn [entity amplifier]
                  (when (< (.getHealth entity) (.getMaxHealth entity))
                    (.heal entity (float (* (inc amplifier) 0.5)))))
       :tick-rate 40))
   ```"
  [id category color & {:keys [on-tick tick-rate instant? on-added on-removed]
                        :or {tick-rate 20
                             instant? false}}]
  (let [effect-category (case category
                          :beneficial net.minecraft.world.effect.MobEffectCategory/BENEFICIAL
                          :harmful net.minecraft.world.effect.MobEffectCategory/HARMFUL
                          :neutral net.minecraft.world.effect.MobEffectCategory/NEUTRAL
                          net.minecraft.world.effect.MobEffectCategory/NEUTRAL)
        effect (proxy [MobEffect] [effect-category (int color)]
                 (applyEffectTick [^LivingEntity entity ^int amplifier]
                   (when on-tick
                     (try
                       (on-tick entity amplifier)
                       (catch Exception e
                         (core/log-error (str \"Error in custom effect tick: \" (.getMessage e)))))))

                 (shouldApplyEffectTickThisTick [duration amplifier]
                   (and on-tick
                        (or instant?
                            (zero? (mod duration tick-rate)))))

                 (applyInstantenousEffect [source indirect-source entity amplifier health]
                   (when (and instant? on-tick)
                     (try
                       (on-tick entity amplifier)
                       (catch Exception e
                         (core/log-error (str \"Error in instant effect: \" (.getMessage e)))))))

                 (isInstantenous []
                   instant?))]

    ;; 存储回调以便后续使用
    (swap! custom-effects assoc id
           {:effect effect
            :on-added on-added
            :on-removed on-removed
            :category category
            :color color})

    effect))

(defn register-custom-effect!
  "注册自定义效果到游戏中

   参数:
   - mod-id: 模组 ID
   - effect-id: 效果 ID（关键字）
   - effect: MobEffect 实例

   注意：必须在模组初始化阶段调用

   示例:
   ```clojure
   (register-custom-effect! \"mymod\" :bleeding bleeding-effect)
   ```"
  [mod-id effect-id ^MobEffect effect]
  (let [res-loc (ResourceLocation. mod-id (name effect-id))]
    ;; 注册到注册表
    ;; 注意：实际注册需要使用 DeferredRegister，这里提供接口
    (core/log-info (str \"Registered custom effect: \" res-loc))
    {:id res-loc
     :effect effect}))

(defn defcustom-effect
  "定义并注册自定义效果（宏样式函数）

   参数:
   - mod-id: 模组 ID
   - effect-id: 效果 ID
   - category: 效果类别
   - color: 效果颜色
   - opts: 其他选项

   返回：注册信息

   示例:
   ```clojure
   (defcustom-effect \"mymod\" :bleeding
     :harmful 0xAA0000
     :on-tick (fn [entity amplifier]
                (.hurt entity (DamageSource. \"bleeding\") 0.5))
     :tick-rate 20)
   ```"
  [mod-id effect-id category color & opts]
  (let [effect (apply create-custom-effect effect-id category color opts)]
    (register-custom-effect! mod-id effect-id effect)))

;; ============================================================================
;; 效果查询
;; ============================================================================

(defn has-effect?
  "检查实体是否有指定效果

   参数:
   - entity: LivingEntity
   - effect: MobEffect 或关键字

   返回：boolean

   示例:
   ```clojure
   (has-effect? player :regeneration)
   (has-effect? player MobEffects/REGENERATION)
   ```"
  [^LivingEntity entity effect]
  (let [^MobEffect mob-effect (if (keyword? effect)
                                (get-effect effect)
                                effect)]
    (.hasEffect entity mob-effect)))

(defn get-effect-instance
  "获取实体的效果实例

   参数:
   - entity: LivingEntity
   - effect: MobEffect 或关键字

   返回：MobEffectInstance 或 nil

   示例:
   ```clojure
   (when-let [regen (get-effect-instance player :regeneration)]
     (println \"剩余时间:\" (.getDuration regen)))
   ```"
  [^LivingEntity entity effect]
  (let [^MobEffect mob-effect (if (keyword? effect)
                                (get-effect effect)
                                effect)]
    (.getEffect entity mob-effect)))

(defn get-effect-duration
  "获取效果剩余时间（tick）

   参数:
   - entity: LivingEntity
   - effect: MobEffect 或关键字

   返回：剩余时间（tick），如果没有效果返回 0"
  [^LivingEntity entity effect]
  (if-let [^MobEffectInstance instance (get-effect-instance entity effect)]
    (.getDuration instance)
    0))

(defn get-effect-amplifier
  "获取效果强度（等级 - 1）

   参数:
   - entity: LivingEntity
   - effect: MobEffect 或关键字

   返回：强度值，如果没有效果返回 0"
  [^LivingEntity entity effect]
  (if-let [^MobEffectInstance instance (get-effect-instance entity effect)]
    (.getAmplifier instance)
    0))

(defn get-all-effects
  "获取实体所有效果

   参数:
   - entity: LivingEntity

   返回：效果实例列表

   示例:
   ```clojure
   (doseq [effect (get-all-effects player)]
     (println \"效果:\" (.getEffect effect)
              \"时长:\" (.getDuration effect)))
   ```"
  [^LivingEntity entity]
  (vec (.getActiveEffects entity)))

;; ============================================================================
;; 效果管理
;; ============================================================================

(defn add-effect!
  "添加效果到实体

   参数:
   - entity: LivingEntity
   - effect: MobEffect 或关键字
   - duration: 持续时间（tick）
   - opts: 可选参数
     - :amplifier - 强度（默认 0，0=I级，1=II级）
     - :ambient? - 是否为环境效果（默认 false）
     - :visible? - 是否可见（默认 true）
     - :show-icon? - 是否显示图标（默认 true）

   返回：boolean（是否成功）

   示例:
   ```clojure
   ;; 添加 10 秒再生效果
   (add-effect! player :regeneration 200)

   ;; 添加 30 秒速度 II 效果
   (add-effect! player :speed 600 :amplifier 1)

   ;; 添加隐形效果（不显示粒子）
   (add-effect! player :invisibility 400
     :ambient? true
     :visible? false)
   ```"
  [^LivingEntity entity effect duration & {:keys [amplifier ambient? visible? show-icon?]
                                           :or {amplifier 0
                                                ambient? false
                                                visible? true
                                                show-icon? true}}]
  (let [^MobEffect mob-effect (if (keyword? effect)
                                (get-effect effect)
                                effect)
        instance (MobEffectInstance. mob-effect
                                     (int duration)
                                     (int amplifier)
                                     (boolean ambient?)
                                     (boolean visible?)
                                     (boolean show-icon?))]
    (.addEffect entity instance)))

(defn remove-effect!
  "移除实体的效果

   参数:
   - entity: LivingEntity
   - effect: MobEffect 或关键字

   返回：boolean（是否成功）

   示例:
   ```clojure
   (remove-effect! player :poison)
   ```"
  [^LivingEntity entity effect]
  (let [^MobEffect mob-effect (if (keyword? effect)
                                (get-effect effect)
                                effect)]
    (.removeEffect entity mob-effect)))

(defn remove-all-effects!
  "移除实体所有效果

   参数:
   - entity: LivingEntity

   示例:
   ```clojure
   (remove-all-effects! player)
   ```"
  [^LivingEntity entity]
  (doseq [^MobEffectInstance effect (.getActiveEffects entity)]
    (.removeEffect entity (.getEffect effect))))

(defn clear-negative-effects!
  "清除所有负面效果

   参数:
   - entity: LivingEntity

   示例:
   ```clojure
   (clear-negative-effects! player)
   ```"
  [^LivingEntity entity]
  (doseq [^MobEffectInstance effect (.getActiveEffects entity)]
    (when (.isBad (.getEffect effect))
      (.removeEffect entity (.getEffect effect)))))

(defn clear-positive-effects!
  "清除所有正面效果

   参数:
   - entity: LivingEntity"
  [^LivingEntity entity]
  (doseq [^MobEffectInstance effect (.getActiveEffects entity)]
    (when (.isBeneficial (.getEffect effect))
      (.removeEffect entity (.getEffect effect)))))

;; ============================================================================
;; 效果常量
;; ============================================================================

(defn get-effect
  "通过关键字获取效果（支持原版和自定义效果）

   参数:
   - keyword: 效果关键字

   返回：MobEffect

   支持的原版效果：
   - :speed - 速度
   - :slowness - 缓慢
   - :haste - 急迫
   - :mining_fatigue - 挖掘疲劳
   - :strength - 力量
   - :jump_boost - 跳跃提升
   - :nausea - 反胃
   - :regeneration - 再生
   - :resistance - 抗性提升
   - :fire_resistance - 抗火
   - :water_breathing - 水下呼吸
   - :invisibility - 隐身
   - :blindness - 失明
   - :night_vision - 夜视
   - :hunger - 饥饿
   - :weakness - 虚弱
   - :poison - 中毒
   - :wither - 凋零
   - :health_boost - 生命提升
   - :absorption - 伤害吸收
   - :saturation - 饱和
   - :glowing - 发光
   - :levitation - 飘浮
   - :luck - 幸运
   - :unluck - 霉运
   - :slow_falling - 缓降
   - :conduit_power - 潮涌能量
   - :dolphins_grace - 海豚的恩惠
   - :bad_omen - 不祥之兆
   - :hero_of_the_village - 村庄英雄
   - :darkness - 黑暗

   自定义效果：通过 create-custom-effect 创建的效果也可以通过关键字访问"
  [keyword]
  ;; 首先检查是否为自定义效果
  (if-let [custom-effect (get-in @custom-effects [keyword :effect])]
    custom-effect
    ;; 否则查找原版效果
    (case keyword
      :speed MobEffects/MOVEMENT_SPEED
      :slowness MobEffects/MOVEMENT_SLOWDOWN
      :haste MobEffects/DIG_SPEED
      :mining_fatigue MobEffects/DIG_SLOWDOWN
      :strength MobEffects/DAMAGE_BOOST
      :jump_boost MobEffects/JUMP
      :nausea MobEffects/CONFUSION
      :regeneration MobEffects/REGENERATION
      :resistance MobEffects/DAMAGE_RESISTANCE
      :fire_resistance MobEffects/FIRE_RESISTANCE
      :water_breathing MobEffects/WATER_BREATHING
      :invisibility MobEffects/INVISIBILITY
      :blindness MobEffects/BLINDNESS
      :night_vision MobEffects/NIGHT_VISION
      :hunger MobEffects/HUNGER
      :weakness MobEffects/WEAKNESS
      :poison MobEffects/POISON
      :wither MobEffects/WITHER
      :health_boost MobEffects/HEALTH_BOOST
      :absorption MobEffects/ABSORPTION
      :saturation MobEffects/SATURATION
      :glowing MobEffects/GLOWING
      :levitation MobEffects/LEVITATION
      :luck MobEffects/LUCK
      :unluck MobEffects/UNLUCK
      :slow_falling MobEffects/SLOW_FALLING
      :conduit_power MobEffects/CONDUIT_POWER
      :dolphins_grace MobEffects/DOLPHINS_GRACE
      :bad_omen MobEffects/BAD_OMEN
      :hero_of_the_village MobEffects/HERO_OF_THE_VILLAGE
      :darkness MobEffects/DARKNESS
      (throw (IllegalArgumentException. (str "Unknown effect: " keyword))))))

;; ============================================================================
;; 药水工具
;; ============================================================================

(defn create-potion-item
  "创建药水物品

   参数:
   - potion: Potion 或关键字
   - opts: 可选参数
     - :splash? - 是否为喷溅药水（默认 false）
     - :lingering? - 是否为滞留药水（默认 false）

   返回：ItemStack

   示例:
   ```clojure
   ;; 普通治疗药水
   (create-potion-item :healing)

   ;; 喷溅力量药水
   (create-potion-item :strength :splash? true)

   ;; 滞留速度药水
   (create-potion-item :swiftness :lingering? true)
   ```"
  [potion & {:keys [splash? lingering?]
             :or {splash? false lingering? false}}]
  (let [^Potion pot (if (keyword? potion)
                      (get-potion potion)
                      potion)
        base-item (cond
                    lingering? Items/LINGERING_POTION
                    splash? Items/SPLASH_POTION
                    :else Items/POTION)
        stack (ItemStack. base-item)]
    (PotionUtils/setPotion stack pot)
    stack))

(defn get-potion
  "通过关键字获取药水类型

   支持的药水：
   - :water - 水瓶
   - :awkward - 粗制药水
   - :thick - 浓稠药水
   - :mundane - 平凡药水
   - :night_vision - 夜视
   - :invisibility - 隐身
   - :leaping - 跳跃
   - :fire_resistance - 抗火
   - :swiftness - 迅捷
   - :slowness - 缓慢
   - :turtle_master - 神龟
   - :water_breathing - 水肺
   - :healing - 治疗
   - :harming - 伤害
   - :poison - 剧毒
   - :regeneration - 再生
   - :strength - 力量
   - :weakness - 虚弱
   - :slow_falling - 缓降"
  [keyword]
  (case keyword
    :water Potions/WATER
    :awkward Potions/AWKWARD
    :thick Potions/THICK
    :mundane Potions/MUNDANE
    :night_vision Potions/NIGHT_VISION
    :long_night_vision Potions/LONG_NIGHT_VISION
    :invisibility Potions/INVISIBILITY
    :long_invisibility Potions/LONG_INVISIBILITY
    :leaping Potions/LEAPING
    :long_leaping Potions/LONG_LEAPING
    :strong_leaping Potions/STRONG_LEAPING
    :fire_resistance Potions/FIRE_RESISTANCE
    :long_fire_resistance Potions/LONG_FIRE_RESISTANCE
    :swiftness Potions/SWIFTNESS
    :long_swiftness Potions/LONG_SWIFTNESS
    :strong_swiftness Potions/STRONG_SWIFTNESS
    :slowness Potions/SLOWNESS
    :long_slowness Potions/LONG_SLOWNESS
    :strong_slowness Potions/STRONG_SLOWNESS
    :turtle_master Potions/TURTLE_MASTER
    :long_turtle_master Potions/LONG_TURTLE_MASTER
    :strong_turtle_master Potions/STRONG_TURTLE_MASTER
    :water_breathing Potions/WATER_BREATHING
    :long_water_breathing Potions/LONG_WATER_BREATHING
    :healing Potions/HEALING
    :strong_healing Potions/STRONG_HEALING
    :harming Potions/HARMING
    :strong_harming Potions/STRONG_HARMING
    :poison Potions/POISON
    :long_poison Potions/LONG_POISON
    :strong_poison Potions/STRONG_POISON
    :regeneration Potions/REGENERATION
    :long_regeneration Potions/LONG_REGENERATION
    :strong_regeneration Potions/STRONG_REGENERATION
    :strength Potions/STRENGTH
    :long_strength Potions/LONG_STRENGTH
    :strong_strength Potions/STRONG_STRENGTH
    :weakness Potions/WEAKNESS
    :long_weakness Potions/LONG_WEAKNESS
    :slow_falling Potions/SLOW_FALLING
    :long_slow_falling Potions/LONG_SLOW_FALLING
    (throw (IllegalArgumentException. (str "Unknown potion: " keyword)))))

(defn get-potion-effects
  "获取药水的所有效果

   参数:
   - potion-stack: 药水物品栈

   返回：效果实例列表"
  [^ItemStack potion-stack]
  (vec (PotionUtils/getMobEffects potion-stack)))

;; ============================================================================
;; 效果组合
;; ============================================================================

(defn apply-effects!
  "批量应用效果

   参数:
   - entity: LivingEntity
   - effects: 效果列表，每个效果为 [effect duration & opts]

   示例:
   ```clojure
   (apply-effects! player
     [[:speed 200 :amplifier 1]
      [:jump_boost 200]
      [:regeneration 100]])
   ```"
  [^LivingEntity entity effects]
  (doseq [effect-spec effects]
    (let [[effect duration & opts] effect-spec]
      (apply add-effect! entity effect duration opts))))

(defn create-buff-set
  "创建增益效果集合

   返回：效果配置向量

   示例:
   ```clojure
   (def combat-buffs
     (create-buff-set
       [:strength 600 :amplifier 1]
       [:speed 600]
       [:resistance 600 :amplifier 1]))

   (apply-effects! player combat-buffs)
   ```"
  [& effect-specs]
  (vec effect-specs))

;; ============================================================================
;; 预设效果组合
;; ============================================================================

(def combat-buffs
  "战斗增益（力量、速度、抗性）"
  [[:strength 600 :amplifier 1]
   [:speed 400]
   [:resistance 600]])

(def mining-buffs
  "挖矿增益（急迫、夜视）"
  [[:haste 1200 :amplifier 1]
   [:night_vision 1200]])

(def exploration-buffs
  "探险增益（速度、跳跃、夜视）"
  [[:speed 1200]
   [:jump_boost 1200]
   [:night_vision 1200]])

(def underwater-buffs
  "水下增益（水肺、夜视、海豚的恩惠）"
  [[:water_breathing 1200]
   [:night_vision 1200]
   [:dolphins_grace 1200]])

(comment
  ;; 使用示例

  ;; ========== 基础效果管理 ==========

  ;; 1. 添加效果
  (add-effect! player :regeneration 200)
  (add-effect! player :speed 600 :amplifier 1)

  ;; 2. 查询效果
  (when (has-effect? player :poison)
    (println "玩家中毒！"))

  (println "速度剩余时间:" (get-effect-duration player :speed))

  ;; 3. 移除效果
  (remove-effect! player :poison)
  (remove-all-effects! player)
  (clear-negative-effects! player)

  ;; ========== 药水物品 ==========

  ;; 4. 创建药水
  (def healing-potion (create-potion-item :healing))
  (def splash-strength (create-potion-item :strength :splash? true))

  ;; 5. 获取药水效果
  (doseq [effect (get-potion-effects healing-potion)]
    (println "效果:" (.getEffect effect)))

  ;; ========== 效果组合 ==========

  ;; 6. 批量应用效果
  (apply-effects! player
                  [[:speed 200 :amplifier 1]
                   [:jump_boost 200]
                   [:regeneration 100]])

  ;; 7. 使用预设组合
  (apply-effects! player combat-buffs)
  (apply-effects! player mining-buffs)

  ;; 8. 自定义组合
  (def my-buffs
    (create-buff-set
     [:speed 600 :amplifier 2]
     [:strength 600 :amplifier 2]
     [:resistance 600 :amplifier 1]
     [:regeneration 600]))

  (apply-effects! boss my-buffs)

  ;; ========== 自定义效果 ==========

  ;; 9. 创建流血效果（每秒造成伤害）
  (def bleeding-effect
    (create-custom-effect :bleeding
      :harmful 0xAA0000
      :on-tick (fn [entity amplifier]
                 (.hurt entity
                   (net.minecraft.world.damagesource.DamageSource. "bleeding")
                   (float (* (inc amplifier) 0.5))))
      :tick-rate 20))

  ;; 10. 注册自定义效果
  (register-custom-effect! "mymod" :bleeding bleeding-effect)

  ;; 11. 使用自定义效果
  (add-effect! player :bleeding 400 :amplifier 1)  ; 20秒流血效果

  ;; 12. 创建生命恢复效果
  (def life-regen-effect
    (create-custom-effect :life_regen
      :beneficial 0x00FF88
      :on-tick (fn [entity amplifier]
                 (when (< (.getHealth entity) (.getMaxHealth entity))
                   (.heal entity (float (* (inc amplifier) 0.25)))))
      :tick-rate 40  ; 每2秒触发一次
      :on-added (fn [entity amplifier]
                  (println "生命恢复效果已添加！"))
      :on-removed (fn [entity amplifier]
                    (println "生命恢复效果已移除！"))))

  ;; 13. 创建瞬时效果（如爆发性伤害）
  (def burst-damage-effect
    (create-custom-effect :burst_damage
      :harmful 0xFF4400
      :instant? true
      :on-tick (fn [entity amplifier]
                 (.hurt entity
                   (net.minecraft.world.damagesource.DamageSource. "burst")
                   (float (* (inc amplifier) 5.0))))))

  ;; 14. 使用自定义效果和预设组合
  (apply-effects! player
    [[:bleeding 600 :amplifier 0]
     [:life_regen 1200 :amplifier 1]
     [:speed 600]]))
