(ns com.fabriclj.swiss-knife.common.game-objects.entities
  "瑞士军刀 - 实体工具模块

   提供实体操作、生成、属性修改等功能。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.world.entity Entity EntityType LivingEntity Mob)
           (net.minecraft.world.entity.player Player)
           (net.minecraft.world.entity.ai.attributes Attributes)
           (net.minecraft.world.level Level ServerLevelAccessor)
           (net.minecraft.world.phys Vec3)
           (net.minecraft.core BlockPos)
           (net.minecraft.server.level ServerLevel)
           (java.util UUID)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 实体查询
;; ============================================================================

(defn get-entity-by-uuid
  "根据 UUID 获取实体

   返回: Entity 或 nil"
  [^ServerLevel level ^UUID uuid]
  (.getEntity level uuid))

(defn get-entities-in-radius
  "获取范围内的所有实体

   参数:
   - level: Level
   - center: 中心坐标 (Vec3 或 Entity)
   - radius: 半径
   - predicate: 过滤函数( 可选)

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
   - max-distance: 最大距离( 可选)

   返回: Player 或 nil"
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

   返回: Vec3"
  ^Vec3 [^Entity entity]
  (.position entity))

(defn get-block-pos
  "获取实体所在方块位置

   返回: BlockPos"
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

(defn distance-to
  "计算两个实体之间的距离

   参数:
   - entity1: Entity
   - entity2: Entity 或 Vec3 或 [x y z]

   返回: 距离（double）

   示例:
   ```clojure
   ;; 计算两个实体之间的距离
   (distance-to player zombie)

   ;; 计算实体到位置的距离
   (distance-to player [100 64 200])
   (distance-to player (vec3 100 64 200))
   ```"
  [^Entity entity1 target]
  (let [^Vec3 pos1 (.position entity1)
        ^Vec3 pos2 (cond
                    (instance? Entity target)
                    (.position ^Entity target)

                    (instance? Vec3 target)
                    target

                    (vector? target)
                    (let [[x y z] target]
                      (net.minecraft.world.phys.Vec3. x y z))

                    :else
                    (throw (IllegalArgumentException.
                            (str "target must be Entity, Vec3, or [x y z], got: " (type target)))))]
    (.distanceTo pos1 pos2)))

(defn teleport!
  "传送实体

   参数:
   - entity: Entity
   - x, y, z: 坐标

   返回: 是否成功"
  ([^Entity entity ^Vec3 pos]
   (teleport! entity (.x pos) (.y pos) (.z pos)))
  ([^Entity entity x y z]
   (.teleportTo entity x y z)))

(defn get-velocity
  "获取实体速度

   返回: Vec3"
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

   返回: {:yaw 偏航角 :pitch 俯仰角}"
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
   - nbt: NBT 数据( 可选)

   返回: 生成的实体或 nil

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
  "生成生物( 带自然生成设置)

   参数:
   - level: ServerLevel
   - entity-type: EntityType 或关键字
   - pos: BlockPos 或 Vec3

   返回: 生成的生物或 nil"
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
   - duration: 持续时间( tick)
   - amplifier: 等级( 0 = I, 1 = II, ...)
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

   返回: Component 或 nil"
  [^Entity entity]
  (when (.hasCustomName entity)
    (.getCustomName entity)))

(defn set-custom-name!
  "设置实体自定义名称

   参数:
   - entity: Entity
   - name: 名称字符串或 Component
   - visible?: 是否总是显示( 可选) "
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
   - seconds: 燃烧时间( 秒) "
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

   返回: Entity 列表"
  [^Entity entity]
  (into [] (.getPassengers entity)))

(defn add-passenger!
  "添加乘客

   返回: 是否成功"
  [^Entity entity ^Entity passenger]
  (.startRiding passenger entity))

(defn remove-passenger!
  "移除乘客"
  [^Entity entity ^Entity passenger]
  (.stopRiding passenger))

(defn is-passenger?
  "检查实体是否为另一实体的乘客"
  [^Entity entity ^Entity vehicle]
  (and (.isPassenger entity) (.hasPassenger vehicle entity)))

;; ============================================================================
;; 实体视觉效果
;; ============================================================================

(defn set-glowing!
  "设置实体发光效果

   参数:
   - entity: Entity
   - glowing?: 是否发光
   - duration: 持续时间（tick，可选，仅在 glowing? 为 true 时有效）

   注意: 如果指定 duration，会通过添加发光药水效果实现，
        否则直接设置实体的发光状态（永久）

   示例:
   ```clojure
   ;; 永久发光
   (set-glowing! entity true)

   ;; 发光 5 秒
   (set-glowing! entity true 100)

   ;; 取消发光
   (set-glowing! entity false)
   ```"
  ([^Entity entity glowing?]
   (.setGlowingTag entity glowing?))
  ([^Entity entity glowing? duration]
   (if glowing?
     (add-effect! entity :minecraft:glowing duration 0 false)
     (.setGlowingTag entity false))))

(defn is-glowing?
  "检查实体是否发光"
  [^Entity entity]
  (.isCurrentlyGlowing entity))

(defn set-on-fire!
  "设置实体着火

   参数:
   - entity: Entity
   - seconds: 着火秒数（0 为熄灭）

   示例:
   ```clojure
   (set-on-fire! entity 5)  ; 着火 5 秒
   (set-on-fire! entity 0)  ; 熄灭
   ```"
  [^Entity entity seconds]
  (if (pos? seconds)
    (.setRemainingFireTicks entity (* seconds 20))
    (.clearFire entity)))

(defn is-on-fire?
  "检查实体是否着火"
  [^Entity entity]
  (.isOnFire entity))

(defn set-invisible!
  "设置实体隐身

   参数:
   - entity: Entity
   - invisible?: 是否隐身
   - duration: 持续时间（tick，可选）

   示例:
   ```clojure
   (set-invisible! entity true)
   (set-invisible! entity true 200)  ; 隐身 10 秒
   (set-invisible! entity false)
   ```"
  ([^Entity entity invisible?]
   (.setInvisible entity invisible?))
  ([^Entity entity invisible? duration]
   (if invisible?
     (add-effect! entity :minecraft:invisibility duration 0 false)
     (.setInvisible entity false))))

(defn is-invisible?
  "检查实体是否隐身"
  [^Entity entity]
  (.isInvisible entity))

(defn set-invulnerable!
  "设置实体无敌

   参数:
   - entity: Entity
   - invulnerable?: 是否无敌

   注意: 仅服务端有效

   示例:
   ```clojure
   (set-invulnerable! entity true)
   (set-invulnerable! entity false)
   ```"
  [^Entity entity invulnerable?]
  (.setInvulnerable entity invulnerable?))

(defn is-invulnerable?
  "检查实体是否无敌"
  [^Entity entity]
  (.isInvulnerable entity))

(defn set-silent!
  "设置实体静音（不发出声音）

   参数:
   - entity: Entity
   - silent?: 是否静音

   示例:
   ```clojure
   (set-silent! entity true)
   ```"
  [^Entity entity silent?]
  (.setSilent entity silent?))

(defn is-silent?
  "检查实体是否静音"
  [^Entity entity]
  (.isSilent entity))

(defn set-no-gravity!
  "设置实体无重力

   参数:
   - entity: Entity
   - no-gravity?: 是否无重力

   示例:
   ```clojure
   (set-no-gravity! entity true)
   ```"
  [^Entity entity no-gravity?]
  (.setNoGravity entity no-gravity?))

(defn has-no-gravity?
  "检查实体是否无重力"
  [^Entity entity]
  (.isNoGravity entity))

;; ============================================================================
;; 自定义实体构建器
;; ============================================================================

(defn create-entity-factory
  "创建实体工厂函数

   参数:
   - builder-fn: 构建函数 (fn [entity-type level] -> Entity)

   返回: EntityType$EntityFactory

   示例:
   ```clojure
   (create-entity-factory
     (fn [entity-type level]
       (proxy [Zombie RangedAttackMob] [entity-type level]
         (performRangedAttack [target distance]
           ;; 自定义攻击逻辑
           ))))
   ```"
  [builder-fn]
  (reify net.minecraft.world.entity.EntityType$EntityFactory
    (create [_ entity-type level]
      (builder-fn entity-type level))))

(defn build-entity-type
  "构建实体类型

   参数:
   - factory: EntityFactory 或构建函数
   - category: MobCategory 关键字或对象
   - options: 配置选项
     - :size [width height] - 实体尺寸（必需）
     - :name - 内部名称（必需）
     - :tracking-range - 客户端跟踪范围（默认 8）
     - :update-interval - 更新间隔（默认 3）
     - :fire-immune - 是否免疫火焰
     - :can-spawn-far-from-player - 是否可以远离玩家生成
     - :can-summon - 是否可以被召唤

   返回: EntityType

   示例:
   ```clojure
   (build-entity-type
     my-factory
     :monster
     {:size [0.6 1.95]
      :name \"custom_zombie\"
      :tracking-range 10
      :fire-immune true})
   ```"
  [factory category options]
  (let [{:keys [size name tracking-range update-interval fire-immune
                can-spawn-far-from-player can-summon]
         :or {tracking-range 8 update-interval 3}} options
        [width height] size
        category-enum (case category
                       :monster net.minecraft.world.entity.MobCategory/MONSTER
                       :creature net.minecraft.world.entity.MobCategory/CREATURE
                       :ambient net.minecraft.world.entity.MobCategory/AMBIENT
                       :water-creature net.minecraft.world.entity.MobCategory/WATER_CREATURE
                       :water-ambient net.minecraft.world.entity.MobCategory/WATER_AMBIENT
                       :misc net.minecraft.world.entity.MobCategory/MISC
                       category)
        actual-factory (if (fn? factory)
                        (create-entity-factory factory)
                        factory)
        builder (net.minecraft.world.entity.EntityType$Builder/of actual-factory category-enum)]

    ;; 设置尺寸
    (.sized builder (float width) (float height))

    ;; 设置跟踪范围
    (.clientTrackingRange builder tracking-range)

    ;; 设置更新间隔
    (.updateInterval builder update-interval)

    ;; 设置其他属性
    (when fire-immune
      (.fireImmune builder))

    (when can-spawn-far-from-player
      (.canSpawnFarFromPlayer builder))

    (when can-summon
      (.canSummon builder))

    ;; 构建
    (.build builder name)))

;; ============================================================================
;; 便捷实体工厂
;; ============================================================================

(defmacro defentity-factory
  "定义实体工厂（宏）

   简化 proxy 定义，自动处理类型提示和接口实现

   参数:
   - name: 工厂名称（符号）
   - base-class: 基础类（Zombie, Skeleton 等）
   - interfaces: 额外接口列表（可选）
   - methods: 方法实现映射

   示例:
   ```clojure
   (defentity-factory snowball-zombie-factory
     Zombie
     [RangedAttackMob]
     {:perform-ranged-attack
      (fn [this target distance]
        (let [snowball (Snowball. (.level this) this)]
          (.setPos snowball (.getX this) (+ (.getY this) 1.5) (.getZ this))
          (let [dx (- (.getX target) (.getX this))
                dy (- (+ (.getY target) (.getEyeHeight target)) (.getY snowball))
                dz (- (.getZ target) (.getZ this))]
            (.shoot snowball dx dy dz 1.0 5.0))
          (.addFreshEntity (.level this) snowball)))

      :register-goals
      (fn [this]
        (ai/clear-goals! this)
        (ai/add-goal! this 1 (ai/ranged-attack-goal this 1.0 60 16.0)))})
   ```"
  [factory-name base-class interfaces methods]
  (let [method-map (into {} (map (fn [[k v]] [(name k) v]) methods))
        perform-ranged-attack (get method-map "perform-ranged-attack")
        register-goals (get method-map "register-goals")]
    `(def ~factory-name
       (create-entity-factory
        (fn [entity-type# level#]
          (proxy [~base-class ~@interfaces] [entity-type# level#]
            ~@(when perform-ranged-attack
                [`(~'performRangedAttack [~'target ~'distance-factor]
                   (~perform-ranged-attack ~'this ~'target ~'distance-factor))])
            ~@(when register-goals
                [`(~'registerGoals []
                   (~register-goals ~'this))])))))))

;; ============================================================================
;; 实体注册辅助（Minecraft 1.21+）
;; ============================================================================

(defn register-entity-attributes!
  "注册实体属性（Minecraft 1.21 必需）

   所有自定义生物实体必须注册属性，否则会抛出 NullPointerException。

   TODO: support forge or use standard mojang API
   Currently using Fabric API's FabricDefaultAttributeRegistry via reflection.
   Reason: Architectury's EntityAttributeRegistry requires platform-specific code,
           and Fabric API provides stable attribute registration across versions.

   参数:
   - entity-type: EntityType 或 RegistrySupplier
   - attributes: AttributeSupplier$Builder 或 AttributeSupplier

   示例:
   ```clojure
   ;; 使用原版实体的属性
   (register-entity-attributes! my-zombie-type (Zombie/createAttributes))

   ;; 使用 RegistrySupplier
   (register-entity-attributes! @my-entity-supplier (Monster/createMonsterAttributes))

   ;; 自定义属性
   (register-entity-attributes! my-entity-type
     (-> (Monster/createMonsterAttributes)
         (.add Attributes/MAX_HEALTH 100.0)
         (.add Attributes/MOVEMENT_SPEED 0.3)
         (.build)))
   ```

   注意:
   - 仅支持 Fabric 平台（Forge 需要不同的实现）
   - 必须在实体类型注册后、首次使用前调用
   - 通常在 mod 初始化时调用"
  [entity-type attributes]
  (try
    (let [^net.minecraft.world.entity.EntityType et (if (instance? net.minecraft.world.entity.EntityType entity-type)
                                                       entity-type
                                                       (.get entity-type))
          ^net.minecraft.world.entity.ai.attributes.AttributeSupplier attr-supplier
          (if (instance? net.minecraft.world.entity.ai.attributes.AttributeSupplier attributes)
            attributes
            (.build attributes))
          fabric-registry (Class/forName "net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry")]
      (-> fabric-registry
          (.getMethod "register" (into-array Class [net.minecraft.world.entity.EntityType
                                                    net.minecraft.world.entity.ai.attributes.AttributeSupplier]))
          (.invoke nil (into-array Object [et attr-supplier])))
      (core/log-info (str "Registered attributes for entity: " et)))
    (catch ClassNotFoundException e
      (core/log-warn "Fabric API not found, cannot register entity attributes (Forge platform requires different implementation)"))
    (catch Exception e
      (core/log-error (str "Failed to register entity attributes: " (.getMessage e)))
      (.printStackTrace e))))

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
  (set-custom-name! zombie "§c精英僵尸" true)

  ;; 注册实体属性
  (register-entity-attributes! my-entity-type (Zombie/createAttributes)))
