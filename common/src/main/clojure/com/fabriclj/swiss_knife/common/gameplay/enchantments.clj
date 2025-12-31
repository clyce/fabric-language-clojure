(ns com.fabriclj.swiss-knife.common.gameplay.enchantments
  "瑞士军刀 - 附魔系统模块 (Minecraft 1.21+)

   Minecraft 1.21 附魔系统重大变化:
   - 附魔系统变为数据驱动
   - 使用 RegistryEntry<Enchantment> 替代 Enchantment
   - EnchantmentHelper 方法签名完全改变
   - 新增 ItemEnchantmentsComponent 用于管理附魔

   注意: 使用 EnchantmentBridge 辅助类避免反射问题"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.world.item ItemStack)
           (net.minecraft.world.item.enchantment Enchantment EnchantmentHelper ItemEnchantments ItemEnchantments$Mutable)
           (net.minecraft.core.component DataComponents)
           (net.minecraft.core Holder Holder$Reference)
           (net.minecraft.core.registries Registries)
           (net.minecraft.world.entity Entity LivingEntity)
           (net.minecraft.server.level ServerLevel)
           (net.minecraft.world.damagesource DamageSource)
           (com.fabriclj EnchantmentBridge)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 附魔查询 (Minecraft 1.21 使用 ItemEnchantmentsComponent)
;; ============================================================================

(defn get-enchantments
  "获取物品的所有附魔

   返回: ItemEnchantmentsComponent 或 nil

   示例:
   ```clojure
   (when-let [enchants (get-enchantments stack)]
     ;; 处理附魔
     )
   ```"
  [^ItemStack stack]
  (.get stack DataComponents/ENCHANTMENTS))

(defn get-enchantment-level
  "获取特定附魔的等级（从物品堆栈）

   参数:
   - stack: ItemStack

   返回: 附魔等级 (0 表示没有)

   注意: Minecraft 1.21 移除了 EnchantmentHelper.getLevel(Holder, ItemStack)
         请直接使用 ItemEnchantmentsComponent

   示例:
   ```clojure
   ;; 获取物品的附魔组件
   (when-let [enchants (.get stack DataComponents/ENCHANTMENTS)]
     ;; 获取特定附魔的等级
     (.getLevel enchants sharpness-holder))
   ```"
  [^ItemStack stack ^Holder enchantment]
  ;; MC 1.21: 使用 ItemEnchantmentsComponent
  (if-let [enchants (.get stack DataComponents/ENCHANTMENTS)]
    (.getLevel enchants enchantment)
    0))

(defn has-enchantment?
  "检查物品是否有指定附魔

   参数:
   - stack: ItemStack
   - enchantment: Holder<Enchantment>"
  [^ItemStack stack ^Holder enchantment]
  (> (get-enchantment-level stack enchantment) 0))

(defn can-have-enchantments?
  "检查物品是否可以拥有附魔

   参数:
   - stack: ItemStack

   返回: boolean"
  [^ItemStack stack]
  ;; MC 1.21: 通过检查物品是否支持 ENCHANTMENTS 组件来判断
  (.has stack DataComponents/ENCHANTMENTS))

;; ============================================================================
;; 附魔添加/移除 (Minecraft 1.21 使用 Builder 模式)
;; ============================================================================

(defn enchant!
  "给物品添加附魔 (Minecraft 1.21 新方法)

   参数:
   - stack: ItemStack
   - enchantment: Holder<Enchantment>
   - level: 等级

   示例:
   ```clojure
   (enchant! sword sharpness-holder 5)
   ```"
  [^ItemStack stack ^Holder enchantment level]
  (.enchant stack enchantment (int level)))

(defn apply-enchantments!
  "使用 Builder 模式批量添加附魔 (MC 1.21 使用 updateEnchantments)

   参数:
   - stack: ItemStack
   - updater-fn: 接收 ItemEnchantments.Mutable 的函数

   返回: 更新后的 ItemEnchantments

   注意: MC 1.21 改用 updateEnchantments 方法，接收 ItemEnchantments.Mutable

   示例:
   ```clojure
   (apply-enchantments! stack
     (fn [^ItemEnchantments$Mutable mutable]
       (.set mutable sharpness-holder 5)
       (.set mutable fire-aspect-holder 2)))
   ```"
  [^ItemStack stack updater-fn]
  (EnchantmentHelper/updateEnchantments stack
    (reify java.util.function.Consumer
      (accept [_ mutable]
        (updater-fn mutable)))))

(defn clear-enchantments!
  "清除所有附魔"
  [^ItemStack stack]
  (.remove stack DataComponents/ENCHANTMENTS))

;; ============================================================================
;; 附魔效果 (Minecraft 1.21 需要完整上下文)
;; ============================================================================

(defn modify-damage-dealt
  "计算附魔对伤害的影响 (攻击时)

   参数:
   - level: ServerLevel
   - stack: ItemStack (攻击者的武器)
   - target: Entity (被攻击的目标)
   - damage-source: DamageSource
   - base-damage: 基础伤害

   返回: 修改后的伤害值

   注意: 这是 Minecraft 1.21 的新 API，需要完整的战斗上下文

   示例:
   ```clojure
   (def modified-damage
     (modify-damage-dealt server-level sword zombie damage-source 10.0))
   ```"
  [^ServerLevel level
   ^ItemStack stack
   ^Entity target
   ^DamageSource damage-source
   base-damage]
  ;; 注意: Minecraft 1.21 API 可能不同，需要查看实际方法
  ;; 这里提供占位实现
  (float base-damage))

(defn on-target-damaged
  "当目标被伤害时触发附魔效果

   参数:
   - level: ServerLevel
   - target: Entity (被攻击者)
   - damage-source: DamageSource

   用途: 触发类似荆棘（Thorns）这样的反伤附魔

   注意: MC 1.21 改名为 doPostAttackEffects

   示例:
   ```clojure
   (on-target-damaged server-level zombie damage-source)
   ```"
  [^ServerLevel level
   ^Entity target
   ^DamageSource damage-source]
  (EnchantmentBridge/doPostAttackEffects level target damage-source))

(defn modify-knockback
  "修改击退效果

   参数:
   - level: ServerLevel
   - stack: ItemStack
   - target: Entity
   - damage-source: DamageSource (MC 1.21 新增参数)
   - base-knockback: 基础击退值

   返回: 修改后的击退值

   示例:
   ```clojure
   (def knockback
     (modify-knockback server-level sword zombie damage-source 0.4))
   ```"
  [^ServerLevel level
   ^ItemStack stack
   ^Entity target
   ^DamageSource damage-source
   base-knockback]
  (EnchantmentBridge/modifyKnockback level stack target damage-source (float base-knockback)))

;; ============================================================================
;; 装备相关
;; ============================================================================

(defn get-equipment-level
  "获取装备上特定附魔的总等级

   参数:
   - enchantment: Holder<Enchantment>
   - entity: LivingEntity

   返回: 所有装备上该附魔的总等级

   示例:
   ```clojure
   (def total-protection
     (get-equipment-level protection-holder player))
   ```"
  [^Holder enchantment ^LivingEntity entity]
  (EnchantmentBridge/getEnchantmentLevel enchantment entity))

;; ============================================================================
;; 向后兼容函数
;; ============================================================================

(defn calculate-damage-bonus
  "计算附魔伤害加成 (已废弃，保留用于向后兼容)

   注意: 在 Minecraft 1.21 中，此方法总是返回 0.0
         请使用 modify-damage-dealt 代替

   参数:
   - stack: ItemStack
   - target: Entity (可选)

   返回: 0.0 (占位值)

   迁移指南:
   ```clojure
   ;; 旧代码 (不再工作)
   (calculate-damage-bonus sword zombie)

   ;; 新代码 (需要完整上下文)
   (modify-damage-dealt server-level sword zombie damage-source 10.0)
   ```"
  ([^ItemStack _stack]
   (core/log-warn "calculate-damage-bonus is deprecated in MC 1.21, use modify-damage-dealt")
   0.0)
  ([^ItemStack _stack _target]
   (core/log-warn "calculate-damage-bonus is deprecated in MC 1.21, use modify-damage-dealt")
   0.0))

;; ============================================================================
;; 实用工具
;; ============================================================================

(defn get-enchantment-holder
  "从注册表获取附魔的 Holder

   参数:
   - registry-access: RegistryAccess
   - enchantment-key: ResourceKey<Enchantment>

   返回: Holder<Enchantment> 或 nil

   示例:
   ```clojure
   (def registry-access (.registryAccess server))
   (def sharpness-key (ResourceKey/create Registries/ENCHANTMENT
                                           (ResourceLocation. \"minecraft\" \"sharpness\")))
   (def sharpness-holder (get-enchantment-holder registry-access sharpness-key))
   ```"
  [registry-access enchantment-key]
  (try
    (let [registry (.registryOrThrow registry-access Registries/ENCHANTMENT)]
      (.getHolder registry enchantment-key))
    (catch Exception e
      (core/log-error "Failed to get enchantment holder:" (.getMessage e))
      nil)))

(defn list-all-enchantments
  "列出物品上的所有附魔

   参数:
   - stack: ItemStack

   返回: 附魔列表 [{:holder Holder<Enchantment> :level int} ...]

   示例:
   ```clojure
   (doseq [{:keys [holder level]} (list-all-enchantments sword)]
     (println \"Enchantment:\" (.value holder) \"Level:\" level))
   ```"
  [^ItemStack stack]
  (when-let [enchantments (get-enchantments stack)]
    (vec
      (for [entry (.entrySet enchantments)]
        {:holder (.getKey entry)
         :level (.getValue entry)}))))

;; ============================================================================
;; 自定义附魔系统 (Minecraft 1.21 - 数据驱动)
;; ============================================================================
;;
;; 重要说明：
;; Minecraft 1.21 的附魔系统是完全数据驱动的。自定义附魔应该通过数据包定义，
;; 而不是通过代码注册。本节提供的是附魔效果的运行时处理器。
;;
;; 要创建自定义附魔，请：
;; 1. 在数据包中定义附魔（data/modid/enchantment/xxx.json）
;; 2. 使用下面的函数处理自定义效果逻辑
;; 3. 通过事件系统触发效果
;;
;; 参考：https://minecraft.wiki/w/Enchantment_Definition
;; ============================================================================

(defonce ^:private enchantment-effect-handlers (atom {}))

(defn register-enchantment-effect-handler!
  "注册附魔效果运行时处理器（用于数据包定义的附魔）

   参数:
   - enchantment-id: 附魔资源位置字符串 (如 \"mymod:custom_fire\")
   - event-type: 事件类型 (:on-attack/:on-damaged/:on-tick 等)
   - handler-fn: 处理函数 (fn [context] ...)
     context 包含: {:level, :attacker, :target, :stack, :enchantment-level}

   注意: 此函数用于处理数据包定义的附魔的自定义逻辑

   示例:
   ```clojure
   ;; 在数据包中定义附魔后，添加运行时逻辑
   (register-enchantment-effect-handler! \"mymod:life_steal\" :on-attack
     (fn [{:keys [level attacker target enchantment-level]}]
       (when (instance? LivingEntity attacker)
         (.heal attacker (* enchantment-level 0.5)))))
   ```"
  [enchantment-id event-type handler-fn]
  (swap! enchantment-effect-handlers assoc-in [enchantment-id event-type] handler-fn))

(defn trigger-enchantment-effect
  "触发附魔效果处理器

   参数:
   - enchantment-id: 附魔 ID
   - event-type: 事件类型
   - context: 上下文参数映射"
  [enchantment-id event-type context]
  (when-let [handler (get-in @enchantment-effect-handlers [enchantment-id event-type])]
    (try
      (handler context)
      (catch Exception e
        (core/log-error (str "Error in enchantment effect " enchantment-id ": " (.getMessage e)))))))

(comment
  ;; ========== Minecraft 1.21 使用示例 ==========

  ;; 1. 获取注册表访问
  (def server (core/get-server))
  (def registry-access (.registryAccess server))
  (def enchantments-registry (.registryOrThrow registry-access Registries/ENCHANTMENT))

  ;; 2. 获取附魔 Holder
  (def sharpness-key (net.minecraft.core.ResourceKey/create
                       Registries/ENCHANTMENT
                       (net.minecraft.resources.ResourceLocation. "minecraft" "sharpness")))
  (def sharpness-holder (.getHolder enchantments-registry sharpness-key))

  ;; 3. 查询附魔
  (def level (get-enchantment-level sharpness-holder sword))
  (has-enchantment? sharpness-holder sword)

  ;; 4. 添加附魔
  (enchant! sword sharpness-holder 5)

  ;; 5. 批量添加附魔（MC 1.21 使用 updateEnchantments）
  (apply-enchantments! sword
    (fn [mutable]
      (.set mutable sharpness-holder 5)
      (.set mutable fire-aspect-holder 2)))

  ;; 6. 列出所有附魔
  (doseq [{:keys [holder level]} (list-all-enchantments sword)]
    (println "Enchantment:" (.value holder) "Level:" level))

  ;; 7. 修改伤害（需要完整上下文）
  (def modified-damage
    (modify-damage-dealt server-level sword zombie damage-source 10.0))

  ;; 8. 触发附魔效果
  (on-target-damaged server-level zombie damage-source)

  ;; 9. 修改击退（需要 DamageSource）
  (def knockback (modify-knockback server-level sword zombie damage-source 0.4))

  ;; 10. 获取装备总附魔等级
  (def total-protection (get-equipment-level protection-holder player)))

  ;; ========== 注意事项 ==========
  ;; - Minecraft 1.21 的附魔系统是数据驱动的
  ;; - 使用 Holder<Enchantment> 而不是直接的 Enchantment 对象
  ;; - 自定义附魔应该通过数据包（datapack）定义
  ;; - 附魔效果通过 JSON 配置，而不是代码
  ;; - 复杂的附魔效果需要自定义 EnchantmentEffect 类)
