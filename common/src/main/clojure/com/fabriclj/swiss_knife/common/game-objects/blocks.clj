(ns com.fabriclj.swiss-knife.common.game-objects.blocks
  "瑞士军刀 - 方块工具模块

   提供方块操作、方块状态管理、方块实体交互等功能。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import [net.minecraft.world.level Level]
           [net.minecraft.world.level.block Block Blocks]
           [net.minecraft.world.level.block.state BlockState]
           [net.minecraft.world.level.block.state.properties Property]
           [net.minecraft.core BlockPos Direction]
           [net.minecraft.world.entity.player Player]
           [net.minecraft.world.phys BlockHitResult]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 方块位置
;; ============================================================================

(defn- ->pos-coords
  "将坐标参数转换为 [x y z] 向量

   支持:
   - [x y z] 向量
   - {:x x :y y :z z} 映射
   - BlockPos 对象
   - 返回 [x y z]"
  [pos]
  (cond
    (vector? pos) pos
    (map? pos) [(:x pos) (:y pos) (:z pos)]
    (instance? BlockPos pos) [(.getX ^BlockPos pos)
                              (.getY ^BlockPos pos)
                              (.getZ ^BlockPos pos)]
    :else pos))

(defn block-pos
  "创建方块位置

   参数:
   - 方式1: x, y, z 三个参数
   - 方式2: [x y z] 向量
   - 方式3: {:x x :y y :z z} 映射
   - 方式4: Vec3 对象

   示例:
   ```clojure
   (block-pos 100 64 200)
   (block-pos [100 64 200])
   (block-pos {:x 100 :y 64 :z 200})
   ```"
  (^BlockPos [pos-or-x]
   (cond
     (vector? pos-or-x)
     (let [[x y z] pos-or-x]
       (BlockPos. x y z))

     (map? pos-or-x)
     (BlockPos. (:x pos-or-x) (:y pos-or-x) (:z pos-or-x))

     (instance? net.minecraft.world.phys.Vec3 pos-or-x)
     (BlockPos/containing ^net.minecraft.world.phys.Vec3 pos-or-x)

     (instance? BlockPos pos-or-x)
     pos-or-x

     :else
     (throw (IllegalArgumentException. (str "Invalid position: " pos-or-x)))))
  (^BlockPos [x y z]
   (BlockPos. x y z)))

(defn pos-above
  "获取上方位置"
  (^BlockPos [^BlockPos pos]
   (.above pos))
  (^BlockPos [^BlockPos pos n]
   (.above pos n)))

(defn pos-below
  "获取下方位置"
  (^BlockPos [^BlockPos pos]
   (.below pos))
  (^BlockPos [^BlockPos pos n]
   (.below pos n)))

(defn pos-relative
  "获取相对位置

   参数:
   - pos: 方块位置
   - direction: 方向 (:north/:south/:east/:west/:up/:down)
   - distance: 距离（可选，默认 1）"
  (^BlockPos [^BlockPos pos direction]
   (pos-relative pos direction 1))
  (^BlockPos [^BlockPos pos direction distance]
   (let [dir (case direction
               :north Direction/NORTH
               :south Direction/SOUTH
               :east Direction/EAST
               :west Direction/WEST
               :up Direction/UP
               :down Direction/DOWN
               direction)]
     (.relative pos ^Direction dir distance))))

(defn pos-offset
  "按坐标偏移

   示例:
   ```clojure
   (pos-offset pos 1 0 -1)
   ```"
  ^BlockPos [^BlockPos pos dx dy dz]
  (.offset pos dx dy dz))

;; ============================================================================
;; 方块状态查询
;; ============================================================================

(defn get-block-state
  "获取位置的方块状态

   pos 可以是 BlockPos、向量 [x y z] 或映射"
  ^BlockState [^Level level pos]
  (.getBlockState level (block-pos pos)))

(defn get-block-at
  "获取世界中指定位置的方块

   参数:
   - level: Level
   - pos: 方块位置（BlockPos、向量 [x y z] 或映射）

   返回：Block 对象

   注意：这是获取世界中某个位置的方块。如果要从注册表获取方块定义，请使用 core/get-block-by-id

   示例:
   ```clojure
   (get-block-at level [100 64 200])
   (get-block-at level (block-pos 100 64 200))
   ```"
  ^Block [^Level level pos]
  (.getBlock (get-block-state level pos)))

;; 向后兼容的别名（已弃用，请使用 get-block-at）
(def get-block get-block-at)

(defn is-air?
  "检查位置是否为空气

   pos 可以是 BlockPos、向量 [x y z] 或映射"
  [^Level level pos]
  (.isAir (get-block-state level pos)))

(defn is-block?
  "检查位置是否为指定方块

   参数:
   - level: Level
   - pos: 方块位置
   - block: Block、ResourceLocation、String 或 Keyword

   示例:
   ```clojure
   (is-block? level pos :minecraft:stone)
   (is-block? level pos Blocks/DIAMOND_ORE)
   ```"
  [^Level level ^BlockPos pos block]
  (let [^Block target-block (if (instance? Block block)
                              block
                              (core/get-block block))
        ^Block current-block (get-block level pos)]
    (= current-block target-block)))

;; ============================================================================
;; 方块状态修改
;; ============================================================================

(defn set-block!
  "设置方块

   参数:
   - level: Level
   - pos: 方块位置
   - state: BlockState 或 Block
   - flags: 更新标志（可选，默认 3）

   标志说明:
   - 1: 发送更新给客户端
   - 2: 更新客户端
   - 4: 阻止重新渲染
   - 8: 强制重新渲染
   - 16: 阻止邻居更新

   常用组合:
   - 3 (1+2): 默认，发送更新并通知客户端
   - 11 (1+2+8): 强制重新渲染
   - 19 (1+2+16): 不通知邻居方块

   示例:
   ```clojure
   (set-block! level [100 64 200] Blocks/STONE)
   (set-block! level {:x 100 :y 64 :z 200} Blocks/STONE)
   (set-block! level pos (.defaultBlockState Blocks/STONE) 3)
   ```"
  ([^Level level pos state]
   (set-block! level pos state 3))
  ([^Level level pos state flags]
   (let [^BlockPos block-pos (block-pos pos)
         ^BlockState block-state (if (instance? BlockState state)
                                   state
                                   (.defaultBlockState ^Block state))]
     (.setBlock level block-pos block-state flags))))

(defn remove-block!
  "移除方块（设置为空气）

   参数:
   - level: Level
   - pos: 方块位置
   - do-drops?: 是否掉落物品（默认 true）"
  ([^Level level ^BlockPos pos]
   (remove-block! level pos true))
  ([^Level level ^BlockPos pos do-drops?]
   (.destroyBlock level pos do-drops?)))

(defn break-block!
  "破坏方块（模拟玩家破坏，会掉落物品）

   参数:
   - level: Level
   - pos: 方块位置
   - player: 玩家（可选）"
  ([^Level level ^BlockPos pos]
   (.destroyBlock level pos true))
  ([^Level level ^BlockPos pos ^Player player]
   (.destroyBlock level pos true player)))

;; ============================================================================
;; 方块属性
;; ============================================================================

(defn get-property-value
  "获取方块状态属性

   参数:
   - state: BlockState
   - property: 属性名（关键字或字符串）或 Property 对象

   示例:
   ```clojure
   (get-property-value state :facing)
   (get-property-value state \"facing\")
   ```"
  [^BlockState state property]
  (let [props (.getValues state)]
    (if (instance? Property property)
      (.get props property)
      (let [prop-name (name property)]
        (some (fn [[^Property prop value]]
                (when (= (.getName prop) prop-name)
                  value))
              props)))))

(defn set-property-value
  "设置方块状态属性

   返回新的 BlockState（不修改原状态）

   示例:
   ```clojure
   (set-property-value state :facing :north)
   ```"
  ^BlockState [^BlockState state property value]
  (let [props (.getProperties state)]
    (if-let [^Property prop (if (instance? Property property)
                              property
                              (some #(when (= (.getName ^Property %) (name property)) %)
                                    props))]
      (let [prop-value (if (keyword? value)
                        (some #(when (= (str %) (name value)) %)
                              (.getPossibleValues prop))
                        value)]
        (.setValue state prop prop-value))
      state)))

(defn list-properties
  "列出方块状态的所有属性

   返回：属性名列表"
  [^BlockState state]
  (map #(.getName ^Property %) (.getProperties state)))

;; ============================================================================
;; 方块交互
;; ============================================================================

(defn can-survive?
  "检查方块是否可以在该位置存在"
  [^BlockState state ^Level level ^BlockPos pos]
  (.canSurvive state level pos))

(defn get-light-emission
  "获取方块发光等级 (0-15)"
  [^BlockState state]
  (.getLightEmission state))

(defn is-solid?
  "检查方块是否为实心"
  [^BlockState state]
  (.isSolidRender state nil nil))

(defn get-destroy-speed
  "获取方块破坏速度（硬度）"
  [^BlockState state]
  (.getDestroySpeed state nil nil))

(defn get-explosion-resistance
  "获取爆炸抗性"
  [^Block block]
  (.getExplosionResistance block))

;; ============================================================================
;; 方块实体
;; ============================================================================

(defn has-block-entity?
  "检查位置是否有方块实体"
  [^Level level ^BlockPos pos]
  (.hasBlockEntity (get-block-state level pos)))

(defn get-block-entity
  "获取位置的方块实体

   返回：BlockEntity 或 nil"
  [^Level level ^BlockPos pos]
  (.getBlockEntity level pos))

(defn get-block-entity-data
  "获取方块实体的 NBT 数据

   返回：CompoundTag 或 nil"
  [^Level level ^BlockPos pos]
  (when-let [be (get-block-entity level pos)]
    (.saveWithFullMetadata be (.registryAccess level))))

(defn set-block-entity-data!
  "设置方块实体的 NBT 数据

   参数:
   - level: Level
   - pos: 方块位置
   - tag: CompoundTag"
  [^Level level ^BlockPos pos ^net.minecraft.nbt.CompoundTag tag]
  (when-let [be (get-block-entity level pos)]
    (.loadWithComponents be tag (.registryAccess level))
    (.setChanged be)))

;; ============================================================================
;; 邻居方块
;; ============================================================================

(defn notify-neighbors!
  "通知邻居方块更新

   参数:
   - level: Level
   - pos: 方块位置
   - block: 更新的方块"
  [^Level level ^BlockPos pos ^Block block]
  (.updateNeighborsAt level pos block))

(defn get-neighbors
  "获取所有邻居方块位置

   返回：6 个方向的方块位置列表"
  [^BlockPos pos]
  [(pos-relative pos :north)
   (pos-relative pos :south)
   (pos-relative pos :east)
   (pos-relative pos :west)
   (pos-relative pos :up)
   (pos-relative pos :down)])

(comment
  ;; 使用示例

  ;; 获取方块
  (def pos (block-pos 100 64 200))
  (def state (get-block-state level pos))
  (def block (get-block level pos))

  ;; 检查方块
  (when (is-block? level pos :minecraft:diamond_ore)
    (println "Found diamond ore!"))

  ;; 设置方块
  (set-block! level pos Blocks/STONE)
  (set-block! level pos Blocks/GRASS_BLOCK 3)

  ;; 方块属性
  (def door-state (.defaultBlockState Blocks/OAK_DOOR))
  (def open-door (set-property-value door-state :open true))
  (set-block! level pos open-door)

  ;; 区域操作请使用 common.world.regions 模块
  ;; (require '[com.fabriclj.swiss-knife.common.world.regions :as regions])
  ;; (regions/fill-blocks! level [0 60 0] [10 70 10] Blocks/STONE)
  ;; (regions/scan-blocks level [0 60 0] [10 70 10] pred-fn))
