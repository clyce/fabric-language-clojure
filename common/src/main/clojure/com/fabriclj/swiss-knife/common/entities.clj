(ns com.fabriclj.swiss-knife.common.entities
  "瑞士军刀 - 实体工具模块

   提供实体操作、生成、属性修改等功能。"
  (:require [com.fabriclj.swiss-knife.common.core :as core])
  (:import [net.minecraft.world.entity Entity EntityType LivingEntity Mob]
           [net.minecraft.world.entity.player Player]
           [net.minecraft.world.entity.ai.attributes Attributes]
           [net.minecraft.world.level Level ServerLevelAccessor]
           [net.minecraft.world.phys Vec3]
           [net.minecraft.core BlockPos]
           [net.minecraft.server.level ServerLevel]
           [java.util UUID]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 实体查询
;; ============================================================================

(defn get-entity-by-uuid
  "根据 UUID 获取实体

   返回：Entity 或 nil"
  [^ServerLevel level ^UUID uuid]
  (.getEntity level uuid))

(defn get-entities-in-radius
  "获取范围内的所有实体

   参数:
   - level: Level
   - center: 中心坐标 (Vec3 或 Entity)
   - radius: 半径
   - predicate: 过滤函数（可选）

   示例:
   ```clojure
   ;; 获取玩家周围 10 格内的所有怪物
   (get-entities-in-radius level player 10.0
     (fn [e] (instance? Monster e)))
   ```"
  ([^Level level center radius]
   (get-entities-in-radius level center radius (constantly true)))
  ([^Level level center radius predicate]
   (let [^Vec3 pos (if (instance? Entity center)
                     (.position ^Entity center)
                     center)
         box (net.minecraft.world.phys.AABB.
              (.x pos) (.y pos) (.z pos)
              (.x pos) (.y pos) (.z pos))
         expanded (.inflate box radius)]
     (filter predicate
             (.getEntities level nil expanded (constantly true))))))

(defn get-players-in-radius
  "获取范围内的所有玩家"
  [^Level level center radius]
  (filter #(instance? Player %)
          (get-entities-in-radius level center radius)))

(defn get-nearest-player
  "获取最近的玩家

   参数:
   - level: Level
   - entity-or-pos: 实体或位置
   - max-distance: 最大距离（可选）

   返回：Player 或 nil"
  ([^Level level entity-or-pos]
   (get-nearest-player level entity-or-pos -1.0))
  ([^Level level entity-or-pos max-distance]
   (if (instance? Entity entity-or-pos)
     (.getNearestPlayer level ^Entity entity-or-pos max-distance)
     (let [^Vec3 pos entity-or-pos]
       (.getNearestPlayer level (.x pos) (.y pos) (.z pos) max-distance false)))))

;; ============================================================================
;; 实体属性
;; ============================================================================

(defn get-health
  "获取生物当前生命值"
  [^LivingEntity entity]
  (.getHealth entity))

(defn get-max-health
  "获取生物最大生命值"
  [^LivingEntity entity]
  (when-let [attr (.getAttribute entity Attributes/MAX_HEALTH)]
    (.getValue attr)))

(defn set-health!
  "设置生物生命值"
  [^LivingEntity entity health]
  (.setHealth entity health))

(defn heal!
  "治疗生物

   参数:
   - entity: LivingEntity
   - amount: 治疗量"
  [^LivingEntity entity amount]
  (.heal entity amount))

(defn is-alive?
  "检查实体是否存活"
  [^Entity entity]
  (.isAlive entity))

(defn is-dead?
  "检查实体是否死亡"
  [^LivingEntity entity]
  (.isDeadOrDying entity))

(defn kill!
  "杀死实体"
  [^Entity entity]
  (.kill entity))

;; ============================================================================
;; 实体位置和运动
;; ============================================================================

(defn get-position
  "获取实体位置

   返回：Vec3"
  ^Vec3 [^Entity entity]
  (.position entity))

(defn get-block-pos
  "获取实体所在方块位置

   返回：BlockPos"
  ^BlockPos [^Entity entity]
  (.blockPosition entity))

(defn set-position!
  "设置实体位置

   参数:
   - entity: Entity
   - x, y, z: 坐标或 Vec3"
  ([^Entity entity ^Vec3 pos]
   (set-position! entity (.x pos) (.y pos) (.z pos)))
  ([^Entity entity x y z]
   (.setPos entity x y z)))

(defn teleport!
  "传送实体

   参数:
   - entity: Entity
   - x, y, z: 坐标

   返回：是否成功"
  ([^Entity entity ^Vec3 pos]
   (teleport! entity (.x pos) (.y pos) (.z pos)))
  ([^Entity entity x y z]
   (.teleportTo entity x y z)))

(defn get-velocity
  "获取实体速度

   返回：Vec3"
  ^Vec3 [^Entity entity]
  (.getDeltaMovement entity))

(defn set-velocity!
  "设置实体速度

   参数:
   - entity: Entity
   - velocity: Vec3 或 x, y, z 分量"
  ([^Entity entity ^Vec3 velocity]
   (.setDeltaMovement entity velocity))
  ([^Entity entity x y z]
   (.setDeltaMovement entity x y z)))

(defn push!
  "推动实体

   参数:
   - entity: Entity
   - dx, dy, dz: 推动向量"
  [^Entity entity dx dy dz]
  (.push entity dx dy dz))

(defn get-look-angle
  "获取实体视角

   返回：{:yaw 偏航角 :pitch 俯仰角}"
  [^Entity entity]
  {:yaw (.getYRot entity)
   :pitch (.getXRot entity)})

(defn set-look-angle!
  "设置实体视角"
  [^Entity entity yaw pitch]
  (.setYRot entity yaw)
  (.setXRot entity pitch))

;; ============================================================================
;; 实体生成
;; ============================================================================

(defn spawn-entity!
  "在世界中生成实体

   参数:
   - level: ServerLevel
   - entity-type: EntityType 或关键字 (如 :minecraft:zombie)
   - x, y, z: 坐标
   - nbt: NBT 数据（可选）

   返回：生成的实体或 nil

   示例:
   ```clojure
   (spawn-entity! level :minecraft:zombie 100 64 200)
   ```"
  ([^ServerLevel level entity-type x y z]
   (spawn-entity! level entity-type x y z nil))
  ([^ServerLevel level entity-type x y z nbt]
   (let [^EntityType type (if (instance? EntityType entity-type)
                            entity-type
                            (core/get-entity-type entity-type))]
     (when-let [entity (.create type level)]
       (set-position! entity x y z)
       (when nbt
         (.load entity nbt))
       (.addFreshEntity level entity)
       entity))))

(defn spawn-mob!
  "生成生物（带自然生成设置）

   参数:
   - level: ServerLevel
   - entity-type: EntityType 或关键字
   - pos: BlockPos 或 Vec3

   返回：生成的生物或 nil"
  [^ServerLevel level entity-type pos]
  (let [^EntityType type (if (instance? EntityType entity-type)
                           entity-type
                           (core/get-entity-type entity-type))
        ^BlockPos block-pos (if (instance? BlockPos pos)
                              pos
                              (BlockPos/containing ^Vec3 pos))]
    (when-let [^Mob mob (.create type level)]
      (.setPos mob (.getX block-pos) (.getY block-pos) (.getZ block-pos))
      (.finalizeSpawn mob level (.getCurrentDifficultyAt level block-pos)
                      net.minecraft.world.entity.MobSpawnType/COMMAND
                      nil)
      (.addFreshEntity level mob)
      mob)))

;; ============================================================================
;; 实体效果
;; ============================================================================

(defn add-effect!
  "添加药水效果

   参数:
   - entity: LivingEntity
   - effect: MobEffect 或关键字
   - duration: 持续时间（tick）
   - amplifier: 等级（0 = I, 1 = II, ...）
   - show-particles?: 是否显示粒子

   示例:
   ```clojure
   (add-effect! player :minecraft:speed 200 1 true)
   ```"
  ([^LivingEntity entity effect duration amplifier]
   (add-effect! entity effect duration amplifier true))
  ([^LivingEntity entity effect duration amplifier show-particles?]
   (let [mob-effect (if (keyword? effect)
                      (core/get-item effect) ; 需要从注册表获取
                      effect)
         effect-instance (net.minecraft.world.effect.MobEffectInstance.
                          mob-effect duration amplifier false show-particles?)]
     (.addEffect entity effect-instance))))

(defn remove-effect!
  "移除药水效果

   参数:
   - entity: LivingEntity
   - effect: MobEffect 或关键字"
  [^LivingEntity entity effect]
  (let [mob-effect (if (keyword? effect)
                     (core/get-item effect)
                     effect)]
    (.removeEffect entity mob-effect)))

(defn has-effect?
  "检查实体是否有指定效果"
  [^LivingEntity entity effect]
  (let [mob-effect (if (keyword? effect)
                     (core/get-item effect)
                     effect)]
    (.hasEffect entity mob-effect)))

(defn clear-effects!
  "清除所有药水效果"
  [^LivingEntity entity]
  (.removeAllEffects entity))

;; ============================================================================
;; 实体数据
;; ============================================================================

(defn get-custom-name
  "获取实体自定义名称

   返回：Component 或 nil"
  [^Entity entity]
  (when (.hasCustomName entity)
    (.getCustomName entity)))

(defn set-custom-name!
  "设置实体自定义名称

   参数:
   - entity: Entity
   - name: 名称字符串或 Component
   - visible?: 是否总是显示（可选）"
  ([^Entity entity name]
   (set-custom-name! entity name true))
  ([^Entity entity name visible?]
   (let [component (if (string? name)
                     (net.minecraft.network.chat.Component/literal name)
                     name)]
     (.setCustomName entity component)
     (.setCustomNameVisible entity visible?))))

(defn is-on-fire?
  "检查实体是否着火"
  [^Entity entity]
  (.isOnFire entity))

(defn set-on-fire!
  "点燃实体

   参数:
   - entity: Entity
   - seconds: 燃烧时间（秒）"
  [^Entity entity seconds]
  (.setSecondsOnFire entity seconds))

(defn is-in-water?
  "检查实体是否在水中"
  [^Entity entity]
  (.isInWater entity))

(defn is-on-ground?
  "检查实体是否在地面"
  [^Entity entity]
  (.onGround entity))

;; ============================================================================
;; 实体关系
;; ============================================================================

(defn get-passengers
  "获取实体的所有乘客

   返回：Entity 列表"
  [^Entity entity]
  (into [] (.getPassengers entity)))

(defn add-passenger!
  "添加乘客

   返回：是否成功"
  [^Entity entity ^Entity passenger]
  (.startRiding passenger entity))

(defn remove-passenger!
  "移除乘客"
  [^Entity entity ^Entity passenger]
  (.stopRiding passenger))

(defn is-passenger?
  "检查实体是否为另一实体的乘客"
  [^Entity entity ^Entity vehicle]
  (.isPassenger entity) (.hasPassenger vehicle entity))

(comment
  ;; 使用示例

  ;; 查找附近的怪物
  (def monsters
    (get-entities-in-radius level player 20.0
                            (fn [e]
                              (instance? net.minecraft.world.entity.monster.Monster e))))

  ;; 生成僵尸
  (def zombie (spawn-entity! level :minecraft:zombie 100 64 200))

  ;; 设置生命值
  (set-health! zombie 40.0)

  ;; 添加药水效果
  (add-effect! player :minecraft:speed 200 1)

  ;; 传送玩家
  (teleport! player 0 100 0)

  ;; 推动实体
  (push! zombie 0 1.0 0)  ; 向上推

  ;; 设置自定义名称
  (set-custom-name! zombie "§c精英僵尸" true))
