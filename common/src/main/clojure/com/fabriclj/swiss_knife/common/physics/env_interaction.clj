(ns com.fabriclj.swiss-knife.common.physics.env-interaction
  "瑞士军刀 - 物理环境交互模块

   提供爆炸、闪电、震动等物理效果与环境的交互功能。

   与 physics.core 的区别:
   - physics.core: 纯物理计算（向量、射线、碰撞）
   - env-interaction: 物理效果对环境的影响（破坏方块、伤害实体）"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.swiss-knife.common.physics.core :as physics])
  (:import (net.minecraft.world.level Level)
           (net.minecraft.world.entity Entity LivingEntity)
           (net.minecraft.world.phys Vec3)
           (net.minecraft.core BlockPos)
           (net.minecraft.server.level ServerLevel)
           (net.minecraft.world.level.levelgen Heightmap$Types)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 爆炸
;; ============================================================================

(defn create-explosion!
  "在世界中创建爆炸

   参数:
   - level: Level
   - source: 爆炸源实体（可选，nil 表示无来源）
   - x, y, z: 爆炸中心坐标（或使用 :pos）
   - power: 爆炸威力（默认 4.0，TNT 为 4.0）
   - options: 配置选项（可选）
     - :fire? - 是否产生火焰（默认 false）
     - :break-blocks? - 是否破坏方块（已弃用，使用 :interaction）
     - :interaction - 爆炸交互模式（默认 :mob）
       可选值: :none（不破坏方块）, :block（破坏方块但不掉落）,
              :mob（玩家模式，正常掉落）, :tnt（TNT 模式）
     - :pos - 使用 Vec3 指定位置（可选，替代 x y z）

   返回: Explosion 对象

   示例:
   ```clojure
   ;; 基本爆炸
   (create-explosion! level nil 100 64 200 3.0)

   ;; 使用 Vec3 位置
   (create-explosion! level nil 0 0 0 3.0
     {:pos (physics/vec3 100 64 200)})

   ;; TNT 爆炸（产生火焰）
   (create-explosion! level tnt-entity 100 64 200 4.0
     {:fire? true :interaction :tnt})

   ;; 不破坏方块的爆炸（仅伤害和击退）
   (create-explosion! level player 100 64 200 2.0
     {:interaction :none})
   ```"
  ([level source x y z power]
   (create-explosion! level source x y z power {}))
  ([level source x-or-power y-or-opts z power-or-opts & [opts-or-nil]]
   ;; 处理多种参数形式
   (let [[x y z power opts] (cond
                              ;; 5参数: level source x y z power
                              (and (number? x-or-power) (number? y-or-opts)
                                   (number? z) (number? power-or-opts))
                              [x-or-power y-or-opts z power-or-opts opts-or-nil]

                              ;; 4参数: level source x y z power {:opts}
                              (and (number? x-or-power) (number? y-or-opts)
                                   (number? z) (map? power-or-opts))
                              [x-or-power y-or-opts z (:power power-or-opts 4.0) power-or-opts]

                              :else
                              (throw (IllegalArgumentException.
                                     "Invalid arguments. Use: (create-explosion! level source x y z power {:opts})")))
         {:keys [fire? break-blocks? interaction pos]
          :or {fire? false interaction :mob}} opts

         ;; 处理位置
         [ex ey ez] (if pos
                      (let [^Vec3 p (physics/vec3 pos)]
                        [(.x p) (.y p) (.z p)])
                      [x y z])

         ;; 处理交互模式
         interaction-mode (if break-blocks?
                           net.minecraft.world.level.Level$ExplosionInteraction/MOB
                           (case interaction
                             :none net.minecraft.world.level.Level$ExplosionInteraction/NONE
                             :block net.minecraft.world.level.Level$ExplosionInteraction/BLOCK
                             :mob net.minecraft.world.level.Level$ExplosionInteraction/MOB
                             :tnt net.minecraft.world.level.Level$ExplosionInteraction/TNT
                             net.minecraft.world.level.Level$ExplosionInteraction/MOB))]
     (.explode ^Level level source ex ey ez (float power) fire? interaction-mode))))

(defn create-explosion-at!
  "在指定位置创建爆炸（Vec3 版本）

   参数:
   - level: Level
   - pos: Vec3 或向量 [x y z]
   - power: 爆炸威力
   - options: 同 create-explosion!

   示例:
   ```clojure
   (create-explosion-at! level (.position entity) 3.0)
   (create-explosion-at! level [100 64 200] 3.0 {:fire? true})
   ```"
  ([level pos power]
   (create-explosion-at! level pos power {}))
  ([level pos power options]
   (let [^Vec3 p (physics/vec3 pos)]
     (create-explosion! level nil (.x p) (.y p) (.z p) power options))))

;; ============================================================================
;; 闪电
;; ============================================================================

(defn summon-lightning!
  "在指定位置召唤闪电

   参数:
   - level: ServerLevel
   - x, y, z: 坐标（或使用 :pos）
   - options: 配置选项（可选）
     - :damage? - 是否造成伤害（默认 true）
     - :visual-only? - 是否仅视觉效果（默认 false，等同于 :damage? false）
     - :pos - 使用 Vec3 或向量指定位置

   返回: LightningBolt 实体

   示例:
   ```clojure
   ;; 普通闪电（造成伤害）
   (summon-lightning! level 100 64 200)

   ;; 视觉闪电（不造成伤害）
   (summon-lightning! level 100 64 200 {:visual-only? true})

   ;; 使用位置向量
   (summon-lightning! level 0 0 0 {:pos [100 64 200]})
   ```"
  ([^ServerLevel level x y z]
   (summon-lightning! level x y z {}))
  ([^ServerLevel level x-or-pos y-or-opts z & [opts-or-nil]]
   (let [[x y z opts] (cond
                        ;; 3参数形式: x y z
                        (and (number? x-or-pos) (number? y-or-opts) (number? z))
                        [x-or-pos y-or-opts z opts-or-nil]

                        ;; 2参数形式: x {:pos [...] ...}
                        (and (number? x-or-pos) (map? y-or-opts))
                        (let [pos (:pos y-or-opts)
                              [px py pz] (cond
                                          (vector? pos) pos
                                          (instance? Vec3 pos)
                                          [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)])]
                          [px py pz y-or-opts])

                        :else
                        (throw (IllegalArgumentException.
                               "Invalid arguments. Use: (summon-lightning! level x y z {:opts})")))
         {:keys [damage? visual-only?]
          :or {damage? true visual-only? false}} opts
         lightning-type (net.minecraft.world.entity.EntityType/LIGHTNING_BOLT)
         lightning (.create lightning-type level)]
     (.moveTo lightning x y z)
     (when (or visual-only? (not damage?))
       (.setVisualOnly lightning true))
     (.addFreshEntity level lightning)
     lightning)))

(defn summon-lightning-at!
  "在指定位置召唤闪电（Vec3 版本）

   示例:
   ```clojure
   (summon-lightning-at! level (.position entity))
   (summon-lightning-at! level [100 64 200] {:visual-only? true})
   ```"
  ([level pos]
   (summon-lightning-at! level pos {}))
  ([level pos options]
   (let [^Vec3 p (physics/vec3 pos)]
     (summon-lightning! level (.x p) (.y p) (.z p) options))))

;; ============================================================================
;; 区域效果云（Area Effect Cloud）
;; ============================================================================

(defn create-effect-cloud!
  "创建区域效果云

   参数:
   - level: ServerLevel
   - pos: Vec3 或向量 [x y z]
   - options: 配置选项
     - :effects - 药水效果列表 [{:effect :speed :duration 100 :amplifier 0}]
     - :duration - 持续时间（tick，默认 600）
     - :radius - 半径（默认 3.0）
     - :radius-per-tick - 每 tick 半径变化（默认 0）
     - :radius-on-use - 使用时半径变化（默认 -0.5）
     - :particle - 粒子类型（关键字或 ParticleOptions）
     - :color - 颜色（RGB 整数）

   返回: AreaEffectCloud 实体

   示例:
   ```clojure
   ;; 速度效果云
   (create-effect-cloud! level [100 64 200]
     {:effects [{:effect :speed :duration 200 :amplifier 1}]
      :duration 600
      :radius 4.0})

   ;; 多重效果云
   (create-effect-cloud! level pos
     {:effects [{:effect :regeneration :duration 100 :amplifier 0}
                {:effect :resistance :duration 100 :amplifier 0}]
      :radius 5.0
      :color 0xFF00FF})
   ```"
  [^ServerLevel level pos options]
  (let [^Vec3 p (physics/vec3 pos)
        {:keys [effects duration radius radius-per-tick radius-on-use particle color]
         :or {duration 600 radius 3.0 radius-per-tick 0 radius-on-use -0.5}} options
        cloud-type (net.minecraft.world.entity.EntityType/AREA_EFFECT_CLOUD)
        ^net.minecraft.world.entity.AreaEffectCloud cloud (.create cloud-type level)]

    ;; 设置位置
    (.moveTo cloud (.x p) (.y p) (.z p))

    ;; 设置持续时间和半径
    (.setDuration cloud duration)
    (.setRadius cloud (float radius))
    (.setRadiusPerTick cloud (float radius-per-tick))
    (.setRadiusOnUse cloud (float radius-on-use))

    ;; 添加药水效果
    (when effects
      (doseq [effect-data effects]
        (let [effect-keyword (:effect effect-data)
              effect-duration (:duration effect-data 100)
              amplifier (:amplifier effect-data 0)
              mob-effect ((requiring-resolve 'com.fabriclj.swiss-knife.common.gameplay.potions/get-effect)
                         effect-keyword)
              effect-instance (net.minecraft.world.effect.MobEffectInstance.
                              mob-effect (int effect-duration) (int amplifier))]
          (.addEffect cloud effect-instance))))

    ;; 设置颜色
    (when color
      (.setFixedColor cloud color))

    ;; 生成实体
    (.addFreshEntity level cloud)
    cloud))

;; ============================================================================
;; 震动/声波（Minecraft 1.19+）
;; ============================================================================

(defn create-game-event!
  "创建游戏事件（震动/声波）

   参数:
   - level: Level
   - event: 事件类型（关键字或 GameEvent）
     可选: :block-attach, :block-change, :block-destroy, :block-place,
          :container-open, :container-close, :drink, :eat, :elytra-glide,
          :entity-damage, :entity-die, :entity-interact, :entity-place,
          :entity-roar, :equip, :explode, :flap, :fluid-pickup, :fluid-place,
          :hit-ground, :instrument-play, :item-interact-finish, :lightning-strike,
          :note-block-play, :prime-fuse, :projectile-land, :projectile-shoot,
          :sculk-sensor-tendrils-clicking, :shear, :shriek, :splash, :step,
          :swim, :teleport, :unequip
   - pos: Vec3 或向量
   - entity: 触发实体（可选）

   示例:
   ```clojure
   ;; 创建爆炸声波
   (create-game-event! level :explode [100 64 200])

   ;; 创建实体交互声波
   (create-game-event! level :entity-interact pos player)
   ```"
  ([level event pos]
   (create-game-event! level event pos nil))
  ([^Level level event pos entity]
   (let [^Vec3 p (physics/vec3 pos)
         game-event (if (keyword? event)
                     (case event
                       :explode net.minecraft.world.level.gameevent.GameEvent/EXPLODE
                       :lightning-strike net.minecraft.world.level.gameevent.GameEvent/LIGHTNING_STRIKE
                       :entity-die net.minecraft.world.level.gameevent.GameEvent/ENTITY_DIE
                       :entity-damage net.minecraft.world.level.gameevent.GameEvent/ENTITY_DAMAGE
                       :teleport net.minecraft.world.level.gameevent.GameEvent/TELEPORT
                       :shriek net.minecraft.world.level.gameevent.GameEvent/SHRIEK
                       ;; 更多事件...
                       event)
                     event)]
     (.gameEvent level entity game-event p))))

;; ============================================================================
;; 组合效果
;; ============================================================================

(defn create-dramatic-explosion!
  "创建戏剧性爆炸效果（爆炸 + 闪电 + 粒子）

   参数:
   - level: ServerLevel
   - pos: 位置
   - power: 威力
   - options: 配置选项
     - :lightning-count - 闪电数量（默认 3）
     - :lightning-radius - 闪电半径（默认 5.0）
     - :fire? - 是否产生火焰
     - :interaction - 爆炸交互模式

   示例:
   ```clojure
   (create-dramatic-explosion! level [100 64 200] 5.0
     {:lightning-count 5 :fire? true})
   ```"
  [^ServerLevel level pos power options]
  (let [^Vec3 p (physics/vec3 pos)
        {:keys [lightning-count lightning-radius]
         :or {lightning-count 3 lightning-radius 5.0}} options]

    ;; 主爆炸
    (create-explosion! level nil (.x p) (.y p) (.z p) power options)

    ;; 周围闪电
    (when (pos? lightning-count)
      (let [positions (physics/circle-positions p lightning-radius lightning-count)]
        (doseq [^Vec3 lightning-pos positions]
          (summon-lightning! level (.x lightning-pos) (.y lightning-pos) (.z lightning-pos)
                           {:visual-only? true}))))

    ;; 创建游戏事件
    (create-game-event! level :explode p nil)))

(comment
  ;; 使用示例

  ;; 1. 基本爆炸
  (create-explosion! level nil 100 64 200 3.0)

  ;; 2. TNT 爆炸
  (create-explosion! level tnt-entity 100 64 200 4.0
    {:fire? true :interaction :tnt})

  ;; 3. 不破坏方块的爆炸
  (create-explosion! level nil 100 64 200 2.0
    {:interaction :none})

  ;; 4. 召唤闪电
  (summon-lightning! level 100 64 200)
  (summon-lightning! level 100 64 200 {:visual-only? true})

  ;; 5. 区域效果云
  (create-effect-cloud! level [100 64 200]
    {:effects [{:effect :speed :duration 200 :amplifier 1}
               {:effect :jump-boost :duration 200 :amplifier 1}]
     :duration 600
     :radius 5.0})

  ;; 6. 戏剧性爆炸
  (create-dramatic-explosion! level [100 64 200] 5.0
    {:lightning-count 5 :fire? true}))
