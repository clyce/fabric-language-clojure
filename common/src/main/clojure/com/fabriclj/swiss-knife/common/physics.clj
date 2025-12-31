(ns com.fabriclj.swiss-knife.common.physics
   "瑞士军刀 - 物理系统模块

   提供射线追踪、碰撞检测、速度计算等物理相关功能。"
   (:require [com.fabriclj.swiss-knife.common.core :as core])
   (:import [net.minecraft.world.level Level ClipContext ClipContext$Block ClipContext$Fluid]
            [net.minecraft.world.phys Vec3 AABB BlockHitResult EntityHitResult HitResult$Type]
            [net.minecraft.world.entity Entity LivingEntity]
            [net.minecraft.core BlockPos Direction]
            [net.minecraft.world.level.block Blocks]))

 ;; 启用反射警告
 (set! *warn-on-reflection* true)

 ;; ============================================================================
 ;; 向量运算
 ;; ============================================================================

 (defn vec3
   "创建 Vec3

   参数:
   - x, y, z: 坐标
   - 或者 [x y z] 向量
   - 或者 {:x x :y y :z z} 映射"
   (^Vec3 [x-or-vec]
    (cond
      (vector? x-or-vec)
      (let [[x y z] x-or-vec]
        (Vec3. x y z))

      (map? x-or-vec)
      (Vec3. (:x x-or-vec) (:y x-or-vec) (:z x-or-vec))

      (instance? Vec3 x-or-vec)
      x-or-vec

      :else
      (throw (IllegalArgumentException. (str "Invalid vec3: " x-or-vec)))))
   (^Vec3 [x y z]
    (Vec3. x y z)))

 (defn vec3-add
   "向量加法"
   ^Vec3 [^Vec3 v1 ^Vec3 v2]
   (.add v1 v2))

 (defn vec3-subtract
   "向量减法"
   ^Vec3 [^Vec3 v1 ^Vec3 v2]
   (.subtract v1 v2))

 (defn vec3-scale
   "向量缩放"
   ^Vec3 [^Vec3 v scale]
   (.scale v scale))

 (defn vec3-normalize
   "向量归一化"
   ^Vec3 [^Vec3 v]
   (.normalize v))

 (defn vec3-length
   "向量长度"
   [^Vec3 v]
   (.length v))

 (defn vec3-distance
   "两点距离"
   [^Vec3 v1 ^Vec3 v2]
   (.distanceTo v1 v2))

 (defn vec3-dot
   "向量点积"
   [^Vec3 v1 ^Vec3 v2]
   (.dot v1 v2))

 (defn vec3-cross
   "向量叉积"
   ^Vec3 [^Vec3 v1 ^Vec3 v2]
   (.cross v1 v2))

 (defn vec3->map
   "Vec3 转换为映射"
   [^Vec3 v]
   {:x (.x v) :y (.y v) :z (.z v)})

 ;; ============================================================================
 ;; 射线追踪
 ;; ============================================================================

 (defn raycast
   "射线追踪

   参数:
   - level: Level
   - start: 起始位置（Vec3 或向量）
   - end: 结束位置（Vec3 或向量）
   - opts: 可选参数
     - :block-mode - 方块模式 (:collider/:outline/:visual)
     - :fluid-mode - 流体模式 (:none/:source-only/:any)
     - :entity - 实体（用于视角）

   返回：HitResult

   示例:
   ```clojure
   (raycast level [100 64 200] [110 64 200]
     {:block-mode :collider
      :fluid-mode :none})
   ```"
   [^Level level start end & [opts]]
   (let [{:keys [block-mode fluid-mode entity]
          :or {block-mode :collider
               fluid-mode :none}} opts
         ^Vec3 start-vec (vec3 start)
         ^Vec3 end-vec (vec3 end)
         block-mode-enum (case block-mode
                           :collider ClipContext$Block/COLLIDER
                           :outline ClipContext$Block/OUTLINE
                           :visual ClipContext$Block/VISUAL
                           block-mode)
         fluid-mode-enum (case fluid-mode
                           :none ClipContext$Fluid/NONE
                           :source-only ClipContext$Fluid/SOURCE_ONLY
                           :any ClipContext$Fluid/ANY
                           fluid-mode)
         context (ClipContext. start-vec end-vec
                               block-mode-enum fluid-mode-enum
                               entity)]
     (.clip level context)))

 (defn raycast-all
   "射线追踪所有命中（穿透式）

   参数:
   - level: Level
   - start: 起始位置
   - end: 结束位置
   - opts: 可选参数
     - :max-hits - 最大命中数（默认 -1 表示所有）
     - :predicate - 过滤函数 (fn [hit] -> boolean)
     - 其他参数同 raycast

   返回：HitResult 列表

   示例:
   ```clojure
   (raycast-all level [100 64 200] [110 64 200]
     {:max-hits 5
      :predicate #(= (.getType %) HitResult$Type/BLOCK)})
   ```"
   [^Level level start end & [opts]]
   (let [{:keys [max-hits predicate] :or {max-hits -1 predicate (constantly true)}} opts
         ^Vec3 start-vec (vec3 start)
         ^Vec3 end-vec (vec3 end)
         direction (.subtract end-vec start-vec)
         max-distance (.length direction)
         hits (atom [])]
     (loop [current-start start-vec
            remaining-hits (if (neg? max-hits) Integer/MAX_VALUE max-hits)]
       (if (or (zero? remaining-hits)
               (>= (.distance current-start end-vec) max-distance))
         @hits
         (let [hit (raycast level current-start end opts)]
           (if (and hit (predicate hit) (not= (.getType hit) HitResult$Type/MISS))
             (let [hit-pos (.getLocation hit)
                   ;; 稍微偏移以继续追踪
                   next-start (.add hit-pos (.scale (.normalize direction) 0.01))]
               (swap! hits conj hit)
               (recur next-start (dec remaining-hits)))
             @hits))))))

 (defn raycast-first
   "射线追踪第一个符合条件的命中（快捷函数）

   参数:
   - level: Level
   - start: 起始位置
   - end: 结束位置
   - opts: 可选参数
     - :predicate - 过滤函数 (fn [hit] -> boolean)
     - 其他参数同 raycast

   返回：第一个符合条件的 HitResult 或 nil

   示例:
   ```clojure
   ;; 获取第一个方块命中
   (raycast-first level [100 64 200] [110 64 200]
     {:predicate #(= (.getType %) HitResult$Type/BLOCK)})
   ```"
   [^Level level start end & [opts]]
   (let [{:keys [predicate] :or {predicate (constantly true)}} opts
         hit (raycast level start end opts)]
     (when (and hit (predicate hit))
       hit)))

 (defn raycast-block
   "射线追踪方块

   返回：BlockHitResult 或 nil"
   [level start end & [opts]]
   (let [result (raycast level start end opts)]
     (when (= (.getType result) HitResult$Type/BLOCK)
       result)))

 (defn raycast-entity
   "射线追踪实体

   参数:
   - level: Level
   - start: 起始位置
   - end: 结束位置
   - opts: 可选参数
     - :predicate - 过滤函数 (fn [entity] -> boolean)
     - :expand - 碰撞箱扩展量

   返回：{:entity Entity :hit-vec Vec3} 或 nil

   示例:
   ```clojure
   (raycast-entity level [100 64 200] [110 64 200]
     {:predicate #(instance? Player %)})
   ```"
   [^Level level start end & [opts]]
   (let [{:keys [predicate expand] :or {predicate (constantly true) expand 0.0}} opts
         ^Vec3 start-vec (vec3 start)
         ^Vec3 end-vec (vec3 end)
         direction (.subtract end-vec start-vec)
         max-distance (.length direction)
         aabb (AABB. (.x start-vec) (.y start-vec) (.z start-vec)
                     (.x end-vec) (.y end-vec) (.z end-vec))
         entities (.getEntities level nil (.inflate aabb expand expand expand))
         closest (atom {:entity nil :distance Double/MAX_VALUE :hit-vec nil})]
     (doseq [^Entity entity entities]
       (when (predicate entity)
         (let [entity-aabb (.getBoundingBox entity)
               optional-hit (.clip entity-aabb start-vec end-vec)]
           (when (.isPresent optional-hit)
             (let [hit-vec (.get optional-hit)
                   distance (.distanceTo start-vec hit-vec)]
               (when (< distance (:distance @closest))
                 (reset! closest {:entity entity
                                  :distance distance
                                  :hit-vec hit-vec})))))))
     (when (:entity @closest)
       (dissoc @closest :distance))))

 (defn get-look-vector
   "获取实体朝向向量

   参数:
   - entity: Entity
   - distance: 距离（可选，默认 1.0）

   返回：Vec3"
   (^Vec3 [^Entity entity]
    (get-look-vector entity 1.0))
   (^Vec3 [^Entity entity distance]
    (.scale (.getViewVector entity 1.0) distance)))

 (defn raycast-from-eyes
   "从实体视角发射射线

   参数:
   - entity: Entity
   - distance: 最大距离
   - opts: 可选参数（同 raycast）

   示例:
   ```clojure
   (raycast-from-eyes player 5.0)
   ```"
   [^Entity entity distance & [opts]]
   (let [eye-pos (.getEyePosition entity)
         look-vec (get-look-vector entity distance)
         end-pos (.add eye-pos look-vec)]
     (raycast (.level entity) eye-pos end-pos (assoc opts :entity entity))))

 ;; ============================================================================
 ;; 碰撞检测
 ;; ============================================================================

 (defn aabb
   "创建 AABB（碰撞箱）

   参数:
   - min-x, min-y, min-z, max-x, max-y, max-z: 坐标
   - 或者 [min-vec max-vec]

   示例:
   ```clojure
   (aabb 0 0 0 1 1 1)
   (aabb [[0 0 0] [1 1 1]])
   ```"
   (^AABB [min-x min-y min-z max-x max-y max-z]
    (AABB. min-x min-y min-z max-x max-y max-z))
   (^AABB [coords]
    (if (vector? (first coords))
      (let [[min-vec max-vec] coords
            [min-x min-y min-z] min-vec
            [max-x max-y max-z] max-vec]
        (AABB. min-x min-y min-z max-x max-y max-z))
      (let [[min-x min-y min-z max-x max-y max-z] coords]
        (AABB. min-x min-y min-z max-x max-y max-z)))))

 (defn aabb-intersects?
   "检查两个 AABB 是否相交"
   [^AABB aabb1 ^AABB aabb2]
   (.intersects aabb1 aabb2))

 (defn aabb-contains?
   "检查 AABB 是否包含点

   参数:
   - aabb: AABB
   - point: Vec3 或向量"
   [^AABB aabb point]
   (let [^Vec3 p (vec3 point)]
     (.contains aabb p)))

 (defn aabb-expand
   "扩展 AABB"
   ^AABB [^AABB aabb x y z]
   (.inflate aabb x y z))

 (defn aabb-offset
   "偏移 AABB"
   ^AABB [^AABB aabb x y z]
   (.move aabb x y z))

 (defn get-entities-in-aabb
   "获取 AABB 内的所有实体

   参数:
   - level: Level
   - aabb: AABB
   - predicate: 过滤函数（可选）

   示例:
   ```clojure
   (get-entities-in-aabb level (aabb 0 0 0 10 10 10)
     #(instance? Player %))
   ```"
   ([^Level level ^AABB aabb]
    (get-entities-in-aabb level aabb (constantly true)))
   ([^Level level ^AABB aabb predicate]
    (filter predicate
            (.getEntities level nil aabb (constantly true)))))

 (defn get-blocks-in-aabb
   "获取 AABB 内的所有方块位置和状态

   参数:
   - level: Level
   - aabb: AABB
   - predicate: 过滤函数（可选） (fn [pos state] -> boolean)

   返回：[{:pos BlockPos :state BlockState}] 列表

   示例:
   ```clojure
   (get-blocks-in-aabb level (aabb 0 0 0 10 10 10)
     (fn [pos state] (not (.isAir state))))
   ```"
   ([^Level level ^AABB aabb]
    (get-blocks-in-aabb level aabb (constantly true)))
   ([^Level level ^AABB aabb predicate]
    (let [min-x (int (Math/floor (.minX aabb)))
          min-y (int (Math/floor (.minY aabb)))
          min-z (int (Math/floor (.minZ aabb)))
          max-x (int (Math/ceil (.maxX aabb)))
          max-y (int (Math/ceil (.maxY aabb)))
          max-z (int (Math/ceil (.maxZ aabb)))
          blocks (atom [])]
      (doseq [x (range min-x (inc max-x))
              y (range min-y (inc max-y))
              z (range min-z (inc max-z))]
        (let [pos (BlockPos. x y z)
              state (.getBlockState level pos)]
          (when (predicate pos state)
            (swap! blocks conj {:pos pos :state state}))))
      @blocks)))

 ;; ============================================================================
 ;; 速度和推力
 ;; ============================================================================

 (defn get-velocity
   "获取实体速度向量"
   ^Vec3 [^Entity entity]
   (.getDeltaMovement entity))

 (defn set-velocity!
   "设置实体速度

   参数:
   - entity: Entity
   - velocity: Vec3 或向量 [vx vy vz]"
   [^Entity entity velocity]
   (let [^Vec3 v (vec3 velocity)]
     (.setDeltaMovement entity v)))

 (defn add-velocity!
   "增加实体速度"
   [^Entity entity velocity]
   (let [^Vec3 current (get-velocity entity)
         ^Vec3 add (vec3 velocity)]
     (set-velocity! entity (vec3-add current add))))

 (defn push-towards!
   "将实体推向目标位置

   参数:
   - entity: Entity
   - target: 目标位置（Vec3 或向量）
   - force: 推力强度

   示例:
   ```clojure
   (push-towards! entity [100 70 200] 0.5)
   ```"
   [^Entity entity target force]
   (let [entity-pos (.position entity)
         ^Vec3 target-vec (vec3 target)
         direction (vec3-normalize (vec3-subtract target-vec entity-pos))
         push-vec (vec3-scale direction force)]
     (add-velocity! entity push-vec)))

 (defn push-away!
   "将实体推离目标位置"
   [^Entity entity target force]
   (push-towards! entity target (- force)))

 (defn apply-knockback!
   "应用击退效果

   参数:
   - entity: Entity
   - strength: 击退强度
   - x, z: 击退方向（水平）

   示例:
   ```clojure
   (apply-knockback! entity 0.5 1.0 0.0)  ; 向东击退
   ```"
   [^Entity entity strength x z]
   (.knockback entity strength x z))

 (defn launch-upward!
   "向上发射实体

   参数:
   - entity: Entity
   - force: 力度"
   [^Entity entity force]
   (add-velocity! entity [0 force 0]))

 ;; ============================================================================
 ;; 重力和抛物线
 ;; ============================================================================

 (defn calculate-projectile-velocity
   "计算抛射物速度（抛物线运动）

   参数:
   - start: 起始位置
   - target: 目标位置
   - time: 飞行时间（tick）
   - gravity: 重力加速度（默认 0.03）

   返回：Vec3

   示例:
   ```clojure
   (def velocity (calculate-projectile-velocity
                   [100 64 200]
                   [110 64 210]
                   40))
   ```"
   [start target time & [gravity]]
   (let [gravity (or gravity 0.03)
         ^Vec3 start-vec (vec3 start)
         ^Vec3 target-vec (vec3 target)
         dx (- (.x target-vec) (.x start-vec))
         dy (- (.y target-vec) (.y start-vec))
         dz (- (.z target-vec) (.z start-vec))
         vx (/ dx time)
         vz (/ dz time)
         vy (/ (+ dy (* 0.5 gravity time time)) time)]
     (vec3 vx vy vz)))

 (defn predict-landing-position
   "预测抛射物落点

   参数:
   - start: 起始位置
   - velocity: 初始速度
   - gravity: 重力加速度（默认 0.03）
   - max-time: 最大时间（默认 100 tick）

   返回：Vec3"
   [start velocity & [gravity max-time]]
   (let [gravity (or gravity 0.03)
         max-time (or max-time 100)
         ^Vec3 pos (vec3 start)
         ^Vec3 vel (vec3 velocity)]
     (loop [t 0
            p pos
            v vel]
       (if (or (>= t max-time) (<= (.y p) 0))
         p
         (let [new-p (vec3-add p v)
               new-v (vec3-add v (vec3 0 (- gravity) 0))]
           (recur (inc t) new-p new-v))))))

 ;; ============================================================================
 ;; 实用工具
 ;; ============================================================================

 (defn is-solid-block?
   "检查位置是否为实心方块"
   [^Level level pos]
   (let [^BlockPos block-pos (if (instance? BlockPos pos)
                               pos
                               (BlockPos/containing (vec3 pos)))
         state (.getBlockState level block-pos)]
     (not (.isAir state))))

 (defn get-ground-level
   "获取地面高度

   参数:
   - level: Level
   - x, z: 水平坐标
   - start-y: 起始高度（默认 256）

   返回：y 坐标"
   ([^Level level x z]
    (get-ground-level level x z 256))
   ([^Level level x z start-y]
    (loop [y start-y]
      (if (<= y -64)
        -64
        (if (is-solid-block? level [x y z])
          (inc y)
          (recur (dec y)))))))

 (defn circle-positions
   "生成圆形路径上的位置列表

   参数:
   - center: 中心位置
   - radius: 半径
   - count: 位置数量

   返回：Vec3 列表

   示例:
   ```clojure
   (def positions (circle-positions [100 64 200] 5.0 16))
   ```"
   [center radius count]
   (let [[cx cy cz] (cond
                      (vector? center) center
                      (map? center) [(:x center) (:y center) (:z center)])
         angle-step (/ (* 2 Math/PI) count)]
     (for [i (range count)]
       (let [angle (* i angle-step)
             x (+ cx (* radius (Math/cos angle)))
             z (+ cz (* radius (Math/sin angle)))]
         (vec3 x cy z)))))

 (comment
   ;; 使用示例

   ;; 1. 射线追踪
   (def hit (raycast level [100 64 200] [110 64 200]))

   ;; 1.5 快捷函数
   (def first-hit (raycast-first level [100 64 200] [110 64 200]
                                 {:predicate #(= (.getType %) HitResult$Type/BLOCK)}))

   ;; 1.6 穿透射线
   (def all-hits (raycast-all level [100 64 200] [110 64 200]
                              {:max-hits 5}))

   ;; 2. 从玩家视角追踪
   (def player-hit (raycast-from-eyes player 5.0))

   ;; 3. 碰撞检测
   (def box (aabb 0 0 0 1 1 1))
   (def entities (get-entities-in-aabb level box))

   ;; 4. 速度操作
   (set-velocity! entity [1.0 0.5 0.0])
   (push-towards! entity [100 64 200] 0.5)
   (launch-upward! entity 1.0)

   ;; 5. 抛物线计算
   (def velocity (calculate-projectile-velocity
                  [100 64 200]
                  [110 70 210]
                  40))

   (def landing (predict-landing-position [100 64 200] velocity))

   ;; 6. 地形检测
   (def ground-y (get-ground-level level 100 200)))

 ;; 2. 从玩家视角追踪
 (def player-hit (raycast-from-eyes player 5.0))

 ;; 3. 碰撞检测
 (def box (aabb 0 0 0 1 1 1))
 (def entities (get-entities-in-aabb level box))

 ;; 4. 速度操作
 (set-velocity! entity [1.0 0.5 0.0])
 (push-towards! entity [100 64 200] 0.5)
 (launch-upward! entity 1.0)

 ;; 5. 抛物线计算
 (def velocity (calculate-projectile-velocity
                [100 64 200]
                [110 70 210]
                40))

 (def landing (predict-landing-position [100 64 200] velocity))

 ;; 6. 地形检测
 (def ground-y (get-ground-level level 100 200)))
