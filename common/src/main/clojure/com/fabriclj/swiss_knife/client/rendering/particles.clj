(ns com.fabriclj.swiss-knife.client.rendering.particles
  "瑞士军刀 - 粒子系统模块

   提供便捷的粒子效果生成和控制功能。

   注意: 此命名空间仅在客户端环境可用！"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.client Minecraft)
           (net.minecraft.client.particle ParticleEngine)
           (net.minecraft.core.particles ParticleOptions ParticleTypes SimpleParticleType)
           (net.minecraft.world.phys Vec3)
           (net.minecraft.world.level Level)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 粒子引擎访问
;; ============================================================================

(defn get-particle-engine
  "获取粒子引擎

   返回: ParticleEngine"
  ^ParticleEngine []
  (.particleEngine (Minecraft/getInstance)))

;; ============================================================================
;; 内置粒子类型
;; ============================================================================

(def particle-types
  "内置粒子类型映射"
  {:explosion ParticleTypes/EXPLOSION
   :explosion-emitter ParticleTypes/EXPLOSION_EMITTER
   :firework ParticleTypes/FIREWORK
   :bubble ParticleTypes/BUBBLE
   :splash ParticleTypes/SPLASH
   :fishing ParticleTypes/FISHING
   :underwater ParticleTypes/UNDERWATER
   :crit ParticleTypes/CRIT
   :enchanted-hit ParticleTypes/ENCHANTED_HIT
   :smoke ParticleTypes/SMOKE
   :large-smoke ParticleTypes/LARGE_SMOKE
   :effect ParticleTypes/EFFECT
   :instant-effect ParticleTypes/INSTANT_EFFECT
   :entity-effect ParticleTypes/ENTITY_EFFECT
   :witch ParticleTypes/WITCH
   :dripping-water ParticleTypes/DRIPPING_WATER
   :dripping-lava ParticleTypes/DRIPPING_LAVA
   :angry-villager ParticleTypes/ANGRY_VILLAGER
   :happy-villager ParticleTypes/HAPPY_VILLAGER
   :heart ParticleTypes/HEART
   :barrier ParticleTypes/BARRIER
   :item-snowball ParticleTypes/ITEM_SNOWBALL
   :item-slime ParticleTypes/ITEM_SLIME
   :portal ParticleTypes/PORTAL
   :enchant ParticleTypes/ENCHANT
   :flame ParticleTypes/FLAME
   :soul ParticleTypes/SOUL
   :soul-fire-flame ParticleTypes/SOUL_FIRE_FLAME
   :lava ParticleTypes/LAVA
   :mycelium ParticleTypes/MYCELIUM
   :note ParticleTypes/NOTE
   :poof ParticleTypes/POOF
   :cloud ParticleTypes/CLOUD
   :dust ParticleTypes/DUST
   :snowflake ParticleTypes/SNOWFLAKE
   :dripping-honey ParticleTypes/DRIPPING_HONEY
   :falling-honey ParticleTypes/FALLING_HONEY
   :landing-honey ParticleTypes/LANDING_HONEY
   :falling-nectar ParticleTypes/FALLING_NECTAR
   :ash ParticleTypes/ASH
   :crimson-spore ParticleTypes/CRIMSON_SPORE
   :warped-spore ParticleTypes/WARPED_SPORE
   :dripping-obsidian-tear ParticleTypes/DRIPPING_OBSIDIAN_TEAR
   :falling-obsidian-tear ParticleTypes/FALLING_OBSIDIAN_TEAR
   :landing-obsidian-tear ParticleTypes/LANDING_OBSIDIAN_TEAR
   :reverse-portal ParticleTypes/REVERSE_PORTAL
   :white-ash ParticleTypes/WHITE_ASH
   :small-flame ParticleTypes/SMALL_FLAME
   :glow ParticleTypes/GLOW
   :glow-squid-ink ParticleTypes/GLOW_SQUID_INK
   :scrape ParticleTypes/SCRAPE
   :electric-spark ParticleTypes/ELECTRIC_SPARK
   :sonic-boom ParticleTypes/SONIC_BOOM
   :sculk-soul ParticleTypes/SCULK_SOUL
   :sculk-charge ParticleTypes/SCULK_CHARGE
   :sculk-charge-pop ParticleTypes/SCULK_CHARGE_POP
   :shriek ParticleTypes/SHRIEK
   :cherry-leaves ParticleTypes/CHERRY_LEAVES
   :egg-crack ParticleTypes/EGG_CRACK
   :dust-plume ParticleTypes/DUST_PLUME
   :gust ParticleTypes/GUST
   :small-gust ParticleTypes/SMALL_GUST
   :gust-emitter-large ParticleTypes/GUST_EMITTER_LARGE
   :gust-emitter-small ParticleTypes/GUST_EMITTER_SMALL
   :trial-spawner-detection ParticleTypes/TRIAL_SPAWNER_DETECTION
   :vault-connection ParticleTypes/VAULT_CONNECTION
   :infested ParticleTypes/INFESTED
   :item-cobweb ParticleTypes/ITEM_COBWEB
   :white-smoke ParticleTypes/WHITE_SMOKE
   :dust-pillar ParticleTypes/DUST_PILLAR
   :ominous-spawning ParticleTypes/OMINOUS_SPAWNING
   :raid-omen ParticleTypes/RAID_OMEN
   :trial-omen ParticleTypes/TRIAL_OMEN})

(defn get-particle-type
  "获取粒子类型

   参数:
   - type: 粒子类型( 关键字或 ParticleOptions)

   返回: ParticleOptions"
  ^ParticleOptions [type]
  (if (keyword? type)
    (get particle-types type)
    type))

;; ============================================================================
;; 粒子生成
;; ============================================================================

(defn spawn-particle!
  "生成单个粒子

   参数:
   - particle-type: 粒子类型( 关键字或 ParticleOptions)
   - x, y, z: 位置( 或 Vec3 或向量 [x y z])
   - vx, vy, vz: 速度( 可选，默认 0)

   示例:
   ```clojure
   (spawn-particle! :flame 100 64 200)
   (spawn-particle! :smoke [100 64 200] 0.1 0.2 0.1)
   ```"
  ([particle-type pos]
   (spawn-particle! particle-type pos 0.0 0.0 0.0))
  ([particle-type pos vx vy vz]
   (let [^ParticleOptions particle (get-particle-type particle-type)
         [x y z] (cond
                   (vector? pos) pos
                   (map? pos) [(:x pos) (:y pos) (:z pos)]
                   (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)]
                   :else pos)
         level (.level (Minecraft/getInstance))]
     (when level
       (.addParticle ^Level level particle x y z vx vy vz)))))

(defn spawn-particles!
  "批量生成粒子

   参数:
   - particle-type: 粒子类型
   - center: 中心位置
   - count: 粒子数量
   - spread: 扩散范围
   - speed: 速度

   示例:
   ```clojure
   (spawn-particles! :heart [100 64 200] 10 0.5 0.1)
   ```"
  [particle-type center count spread speed]
  (let [[cx cy cz] (cond
                     (vector? center) center
                     (map? center) [(:x center) (:y center) (:z center)]
                     (instance? Vec3 center) [(.x ^Vec3 center) (.y ^Vec3 center) (.z ^Vec3 center)])]
    (dotimes [_ count]
      (let [offset-x (- (rand spread) (/ spread 2.0))
            offset-y (- (rand spread) (/ spread 2.0))
            offset-z (- (rand spread) (/ spread 2.0))
            vx (* (- (rand) 0.5) speed)
            vy (* (- (rand) 0.5) speed)
            vz (* (- (rand) 0.5) speed)]
        (spawn-particle! particle-type
                         [(+ cx offset-x) (+ cy offset-y) (+ cz offset-z)]
                         vx vy vz)))))

;; ============================================================================
;; 粒子效果模式
;; ============================================================================

(defn circle-particles!
  "在圆形路径上生成粒子

   参数:
   - particle-type: 粒子类型
   - center: 中心位置
   - radius: 半径
   - count: 粒子数量
   - opts: 可选参数
     - :vertical? - 是否垂直圆( 默认 false，水平圆)
     - :speed - 粒子速度

   示例:
   ```clojure
   (circle-particles! :flame [100 64 200] 2.0 20)
   ```"
  [particle-type center radius count & [opts]]
  (let [{:keys [vertical? speed] :or {vertical? false speed 0.0}} opts
        [cx cy cz] (cond
                     (vector? center) center
                     (map? center) [(:x center) (:y center) (:z center)])
        angle-step (/ (* 2 Math/PI) count)]
    (dotimes [i count]
      (let [angle (* i angle-step)]
        (if vertical?
          ;; 垂直圆( XY 平面)
          (spawn-particle! particle-type
                           [(+ cx (* radius (Math/cos angle)))
                            (+ cy (* radius (Math/sin angle)))
                            cz]
                           0.0 0.0 0.0)
          ;; 水平圆( XZ 平面)
          (spawn-particle! particle-type
                           [(+ cx (* radius (Math/cos angle)))
                            cy
                            (+ cz (* radius (Math/sin angle)))]
                           0.0 speed 0.0))))))

(defn sphere-particles!
  "在球形表面生成粒子

   参数:
   - particle-type: 粒子类型
   - center: 中心位置
   - radius: 半径
   - count: 粒子数量

   示例:
   ```clojure
   (sphere-particles! :portal [100 64 200] 3.0 50)
   ```"
  [particle-type center radius count]
  (let [[cx cy cz] (cond
                     (vector? center) center
                     (map? center) [(:x center) (:y center) (:z center)])]
    (dotimes [_ count]
      (let [theta (* (rand) Math/PI 2)
            phi (Math/acos (- (* (rand) 2) 1))
            x (+ cx (* radius (Math/sin phi) (Math/cos theta)))
            y (+ cy (* radius (Math/sin phi) (Math/sin theta)))
            z (+ cz (* radius (Math/cos phi)))]
        (spawn-particle! particle-type [x y z] 0.0 0.0 0.0)))))

(defn line-particles!
  "在两点之间生成粒子线

   参数:
   - particle-type: 粒子类型
   - start: 起点
   - end: 终点
   - count: 粒子数量

   示例:
   ```clojure
   (line-particles! :dust [100 64 200] [110 70 210] 20)
   ```"
  [particle-type start end count]
  (let [[sx sy sz] (cond
                     (vector? start) start
                     (map? start) [(:x start) (:y start) (:z start)])
        [ex ey ez] (cond
                     (vector? end) end
                     (map? end) [(:x end) (:y end) (:z end)])]
    (dotimes [i count]
      (let [t (/ i (dec count))
            x (+ sx (* t (- ex sx)))
            y (+ sy (* t (- ey sy)))
            z (+ sz (* t (- ez sz)))]
        (spawn-particle! particle-type [x y z] 0.0 0.0 0.0)))))

(defn helix-particles!
  "生成螺旋形粒子效果

   参数:
   - particle-type: 粒子类型
   - center: 中心位置
   - radius: 半径
   - height: 高度
   - count: 粒子数量
   - opts: 可选参数
     - :turns - 螺旋圈数( 默认 2)

   示例:
   ```clojure
   (helix-particles! :enchant [100 64 200] 1.5 5.0 50 {:turns 3})
   ```"
  [particle-type center radius height count & [opts]]
  (let [{:keys [turns] :or {turns 2}} opts
        [cx cy cz] (cond
                     (vector? center) center
                     (map? center) [(:x center) (:y center) (:z center)])
        angle-step (/ (* 2 Math/PI turns) count)
        height-step (/ height count)]
    (dotimes [i count]
      (let [angle (* i angle-step)
            y-offset (* i height-step)
            x (+ cx (* radius (Math/cos angle)))
            y (+ cy y-offset)
            z (+ cz (* radius (Math/sin angle)))]
        (spawn-particle! particle-type [x y z] 0.0 0.0 0.0)))))

;; ============================================================================
;; 预设粒子效果
;; ============================================================================

(defn explosion-effect!
  "爆炸粒子效果

   参数:
   - pos: 位置
   - size: 大小( :small/:medium/:large) "
  [pos & [size]]
  (let [size (or size :medium)
        [count spread] (case size
                         :small [10 0.5]
                         :medium [30 1.0]
                         :large [50 2.0])]
    (spawn-particles! :explosion pos count spread 0.2)
    (spawn-particles! :smoke pos count spread 0.1)))

(defn magic-effect!
  "魔法粒子效果

   参数:
   - pos: 位置"
  [pos]
  (circle-particles! :enchant pos 1.5 20)
  (spawn-particles! :portal pos 10 0.5 0.1))

(defn heal-effect!
  "治疗粒子效果

   参数:
   - pos: 位置"
  [pos]
  (spawn-particles! :heart pos 5 0.5 0.2)
  (spawn-particles! :happy-villager pos 10 0.5 0.1))

(defn teleport-effect!
  "传送粒子效果

   参数:
   - pos: 位置"
  [pos]
  (spawn-particles! :portal pos 30 1.0 0.3)
  (spawn-particles! :reverse-portal pos 20 0.8 0.2))

(defn level-up-effect!
  "升级粒子效果

   参数:
   - pos: 位置"
  [pos]
  (helix-particles! :enchant pos 1.0 3.0 40 {:turns 2})
  (spawn-particles! :happy-villager pos 15 0.5 0.1))

;; ============================================================================
;; 持续粒子效果
;; ============================================================================

(defonce ^:private active-effects (atom {}))

(defn start-continuous-effect!
  "开始持续粒子效果

   参数:
   - id: 效果 ID( 用于停止)
   - effect-fn: 效果函数 (fn [] ...)
   - interval: 间隔( tick)

   返回: 效果 ID

   示例:
   ```clojure
   (start-continuous-effect! :my-effect
     (fn []
       (spawn-particle! :flame [100 64 200]))
     5)  ; 每 5 tick 执行一次
   ```"
  [id effect-fn interval]
  (swap! active-effects assoc id {:fn effect-fn
                                  :interval interval
                                  :counter 0})
  id)

(defn stop-continuous-effect!
  "停止持续粒子效果

   参数:
   - id: 效果 ID"
  [id]
  (swap! active-effects dissoc id))

(defn tick-continuous-effects!
  "更新持续粒子效果( 需要在客户端 tick 中调用)

   注意: 需要在客户端初始化时注册到 tick 事件"
  []
  (doseq [[id {:keys [fn interval counter]}] @active-effects]
    (let [new-counter (inc counter)]
      (if (>= new-counter interval)
        (do
          (fn)
          (swap! active-effects assoc-in [id :counter] 0))
        (swap! active-effects assoc-in [id :counter] new-counter)))))

(comment
  ;; 使用示例

  ;; 生成单个粒子
  (spawn-particle! :flame [100 64 200])
  (spawn-particle! :smoke [100 64 200] 0.1 0.2 0.1)

  ;; 批量生成
  (spawn-particles! :heart [100 64 200] 10 0.5 0.1)

  ;; 圆形粒子
  (circle-particles! :flame [100 64 200] 2.0 20)

  ;; 球形粒子
  (sphere-particles! :portal [100 64 200] 3.0 50)

  ;; 线条粒子
  (line-particles! :dust [100 64 200] [110 70 210] 20)

  ;; 螺旋粒子
  (helix-particles! :enchant [100 64 200] 1.5 5.0 50 {:turns 3})

  ;; 预设效果
  (explosion-effect! [100 64 200] :large)
  (magic-effect! [100 64 200])
  (heal-effect! [100 64 200])
  (teleport-effect! [100 64 200])
  (level-up-effect! [100 64 200])

  ;; 持续效果
  (start-continuous-effect! :my-flame
                            (fn []
                              (spawn-particle! :flame [100 64 200]))
                            5)

  (stop-continuous-effect! :my-flame))
