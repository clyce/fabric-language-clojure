(ns com.fabriclj.swiss-knife.common.world.regions
  "方块区域操作工具

   提供对多个方块进行批量操作的功能，包括:
   - 区域填充
   - 方块替换
   - 几何形状生成( 球体、圆柱等)
   - 区域扫描

   注意: 大区域操作可能导致性能问题，建议分批执行。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.world.level Level)
           (net.minecraft.core BlockPos)
           (net.minecraft.world.level.block Block)
           (net.minecraft.world.level.block.state BlockState)
           (net.minecraft.world.phys Vec3)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 位置解析
;; ============================================================================

(defn parse-pos
  "解析位置参数为 BlockPos

   支持格式:
   - [x y z] - 向量
   - {:x x :y y :z z} - 映射
   - BlockPos - 直接返回
   - Vec3 - 转换为 BlockPos

   返回: BlockPos

   示例:
   ```clojure
   (parse-pos [100 64 200])
   (parse-pos {:x 100 :y 64 :z 200})
   ```"
  ^BlockPos [pos]
  (cond
    (instance? BlockPos pos) pos
    (vector? pos) (BlockPos. (int (nth pos 0))
                             (int (nth pos 1))
                             (int (nth pos 2)))
    (map? pos) (BlockPos. (int (:x pos))
                          (int (:y pos))
                          (int (:z pos)))
    (instance? Vec3 pos) (BlockPos. ^Vec3 pos)
    :else (throw (IllegalArgumentException. (str "Invalid position: " pos)))))

;; ============================================================================
;; 基础区域操作
;; ============================================================================

(defn fill-blocks!
  "填充立方体区域

   参数:
   - level: Level
   - from-pos: 起始位置( 任意支持的格式)
   - to-pos: 结束位置( 任意支持的格式)
   - block: 方块( Block 或 BlockState)

   性能提示:
   - 区域 >10000 方块会有明显延迟
   - 考虑使用 fill-blocks-async! 进行异步填充

   示例:
   ```clojure
   (fill-blocks! level [0 60 0] [10 70 10] Blocks/STONE)
   (fill-blocks! level {:x 0 :y 60 :z 0} {:x 10 :y 70 :z 10} Blocks/GLASS)
   ```"
  [^Level level from-pos to-pos block]
  (let [from (parse-pos from-pos)
        to (parse-pos to-pos)
        min-x (min (.getX from) (.getX to))
        max-x (max (.getX from) (.getX to))
        min-y (min (.getY from) (.getY to))
        max-y (max (.getY from) (.getY to))
        min-z (min (.getZ from) (.getZ to))
        max-z (max (.getZ from) (.getZ to))
        block-state (if (instance? BlockState block)
                      block
                      (.defaultBlockState ^Block block))
        volume (* (- max-x min-x -1) (- max-y min-y -1) (- max-z min-z -1))]
    ;; 性能警告
    (when (> volume 10000)
      (core/log-warn "Filling large region (" volume " blocks), may cause lag"))
    (doseq [x (range min-x (inc max-x))
            y (range min-y (inc max-y))
            z (range min-z (inc max-z))]
      (.setBlock level (BlockPos. x y z) block-state 3))))

(defn replace-blocks!
  "替换区域内特定方块

   参数:
   - level: Level
   - from-pos: 起始位置
   - to-pos: 结束位置
   - old-block: 要替换的方块( Block、BlockState 或资源定位符)
   - new-block: 新方块( Block 或 BlockState)

   示例:
   ```clojure
   (replace-blocks! level [0 60 0] [10 70 10] Blocks/DIRT Blocks/GRASS_BLOCK)
   (replace-blocks! level [0 60 0] [10 70 10] :minecraft:dirt Blocks/GRASS_BLOCK)
   ```"
  [^Level level from-pos to-pos old-block new-block]
  (let [from (parse-pos from-pos)
        to (parse-pos to-pos)
        min-x (min (.getX from) (.getX to))
        max-x (max (.getX from) (.getX to))
        min-y (min (.getY from) (.getY to))
        max-y (max (.getY from) (.getY to))
        min-z (min (.getZ from) (.getZ to))
        max-z (max (.getZ from) (.getZ to))
        old-state (cond
                    (instance? BlockState old-block) old-block
                    (instance? Block old-block) (.defaultBlockState ^Block old-block)
                    :else (.defaultBlockState ^Block (core/get-block-by-id old-block)))
        new-state (if (instance? BlockState new-block)
                    new-block
                    (.defaultBlockState ^Block new-block))]
    (doseq [x (range min-x (inc max-x))
            y (range min-y (inc max-y))
            z (range min-z (inc max-z))]
      (let [pos (BlockPos. x y z)]
        (when (= (.getBlockState level pos) old-state)
          (.setBlock level pos new-state 3))))))

(defn scan-blocks
  "扫描区域内的方块

   参数:
   - level: Level
   - from-pos: 起始位置
   - to-pos: 结束位置
   - pred: 谓词函数 (fn [pos state] -> boolean)

   返回: 满足条件的 [pos state] 列表

   示例:
   ```clojure
   ;; 查找钻石矿
   (def diamonds
     (scan-blocks level [0 0 0] [100 100 100]
       (fn [pos state]
         (= (.getBlock state) Blocks/DIAMOND_ORE))))

   ;; 查找所有非空气方块
   (def solid-blocks
     (scan-blocks level pos1 pos2
       (fn [pos state]
         (not (.isAir state)))))
   ```"
  [^Level level from-pos to-pos pred]
  (let [from (parse-pos from-pos)
        to (parse-pos to-pos)
        min-x (min (.getX from) (.getX to))
        max-x (max (.getX from) (.getX to))
        min-y (min (.getY from) (.getY to))
        max-y (max (.getY from) (.getY to))
        min-z (min (.getZ from) (.getZ to))
        max-z (max (.getZ from) (.getZ to))
        results (atom [])]
    (doseq [x (range min-x (inc max-x))
            y (range min-y (inc max-y))
            z (range min-z (inc max-z))]
      (let [pos (BlockPos. x y z)
            state (.getBlockState level pos)]
        (when (pred pos state)
          (swap! results conj [pos state]))))
    @results))

;; ============================================================================
;; 几何形状
;; ============================================================================

(defn fill-sphere
  "填充球体

   参数:
   - level: Level
   - center: 中心位置
   - radius: 半径
   - block: 方块
   - opts: 选项
     - :hollow? - 是否空心( 默认 false)

   示例:
   ```clojure
   ;; 实心球
   (fill-sphere level [100 64 200] 5 Blocks/GLASS)

   ;; 空心球
   (fill-sphere level [100 64 200] 5 Blocks/GLASS :hollow? true)
   ```"
  [^Level level center radius block & {:keys [hollow?] :or {hollow? false}}]
  (let [center-pos (parse-pos center)
        cx (.getX center-pos)
        cy (.getY center-pos)
        cz (.getZ center-pos)
        r-squared (* radius radius)
        block-state (if (instance? BlockState block)
                      block
                      (.defaultBlockState ^Block block))]
    (doseq [x (range (- cx radius) (+ cx radius 1))
            y (range (- cy radius) (+ cy radius 1))
            z (range (- cz radius) (+ cz radius 1))]
      (let [dist-squared (+ (Math/pow (- x cx) 2)
                            (Math/pow (- y cy) 2)
                            (Math/pow (- z cz) 2))]
        (when (and (<= dist-squared r-squared)
                   (or (not hollow?)
                       (> dist-squared (* (dec radius) (dec radius)))))
          (.setBlock level (BlockPos. x y z) block-state 3))))))

(defn fill-cylinder
  "填充圆柱

   参数:
   - level: Level
   - center: 中心底部位置
   - radius: 半径
   - height: 高度
   - block: 方块
   - opts: 选项
     - :hollow? - 是否空心( 默认 false)
     - :axis - 轴向 (:y/:x/:z，默认 :y)

   示例:
   ```clojure
   ;; 竖直圆柱
   (fill-cylinder level [100 64 200] 3 10 Blocks/STONE)

   ;; 空心圆柱
   (fill-cylinder level [100 64 200] 3 10 Blocks/GLASS :hollow? true)

   ;; 水平圆柱( 沿 X 轴)
   (fill-cylinder level [100 64 200] 3 10 Blocks/STONE :axis :x)
   ```"
  [^Level level center radius height block & {:keys [hollow? axis] :or {hollow? false axis :y}}]
  (let [center-pos (parse-pos center)
        cx (.getX center-pos)
        cy (.getY center-pos)
        cz (.getZ center-pos)
        r-squared (* radius radius)
        block-state (if (instance? BlockState block)
                      block
                      (.defaultBlockState ^Block block))]
    (case axis
      :y (doseq [x (range (- cx radius) (+ cx radius 1))
                 y (range cy (+ cy height))
                 z (range (- cz radius) (+ cz radius 1))]
           (let [dist-squared (+ (Math/pow (- x cx) 2)
                                 (Math/pow (- z cz) 2))]
             (when (and (<= dist-squared r-squared)
                        (or (not hollow?)
                            (> dist-squared (* (dec radius) (dec radius)))))
               (.setBlock level (BlockPos. x y z) block-state 3))))
      :x (doseq [x (range cx (+ cx height))
                 y (range (- cy radius) (+ cy radius 1))
                 z (range (- cz radius) (+ cz radius 1))]
           (let [dist-squared (+ (Math/pow (- y cy) 2)
                                 (Math/pow (- z cz) 2))]
             (when (and (<= dist-squared r-squared)
                        (or (not hollow?)
                            (> dist-squared (* (dec radius) (dec radius)))))
               (.setBlock level (BlockPos. x y z) block-state 3))))
      :z (doseq [x (range (- cx radius) (+ cx radius 1))
                 y (range (- cy radius) (+ cy radius 1))
                 z (range cz (+ cz height))]
           (let [dist-squared (+ (Math/pow (- x cx) 2)
                                 (Math/pow (- y cy) 2))]
             (when (and (<= dist-squared r-squared)
                        (or (not hollow?)
                            (> dist-squared (* (dec radius) (dec radius)))))
               (.setBlock level (BlockPos. x y z) block-state 3)))))))

(defn fill-pyramid
  "填充金字塔

   参数:
   - level: Level
   - base-center: 底座中心位置
   - base-size: 底座边长
   - height: 高度
   - block: 方块
   - opts: 选项
     - :hollow? - 是否空心( 默认 false)
     - :inverted? - 是否倒置( 默认 false)

   示例:
   ```clojure
   ;; 正金字塔
   (fill-pyramid level [100 64 200] 10 8 Blocks/SANDSTONE)

   ;; 倒金字塔
   (fill-pyramid level [100 64 200] 10 8 Blocks/SANDSTONE :inverted? true)
   ```"
  [^Level level base-center base-size height block & {:keys [hollow? inverted?]
                                                       :or {hollow? false inverted? false}}]
  (let [center-pos (parse-pos base-center)
        cx (.getX center-pos)
        cy (.getY center-pos)
        cz (.getZ center-pos)
        block-state (if (instance? BlockState block)
                      block
                      (.defaultBlockState ^Block block))]
    (doseq [h (range height)]
      (let [y (if inverted? (- cy h) (+ cy h))
            size-at-h (max 1 (- base-size (int (* (/ h height) base-size))))
            half-size (/ size-at-h 2)]
        (doseq [x (range (int (- cx half-size)) (int (+ cx half-size 1)))
                z (range (int (- cz half-size)) (int (+ cz half-size 1)))]
          (when (or (not hollow?)
                    (or (= h 0) (= h (dec height))
                        (= x (int (- cx half-size))) (= x (int (+ cx half-size)))
                        (= z (int (- cz half-size))) (= z (int (+ cz half-size)))))
            (.setBlock level (BlockPos. x y z) block-state 3)))))))

;; ============================================================================
;; 辅助工具
;; ============================================================================

(defn count-blocks
  "统计区域内的方块数量

   参数:
   - level: Level
   - from-pos: 起始位置
   - to-pos: 结束位置
   - block-type: 方块类型( Block 或资源定位符，可选)

   返回:
   - 如果指定 block-type: 该类型方块的数量
   - 如果不指定: {Block -> count} 映射

   示例:
   ```clojure
   ;; 统计所有钻石矿
   (count-blocks level [0 0 0] [100 100 100] Blocks/DIAMOND_ORE)

   ;; 统计所有方块类型
   (count-blocks level [0 60 0] [10 70 10])
   ; => {#object[Block] 150, #object[Block] 200, ...}
   ```"
  ([^Level level from-pos to-pos]
   (let [from (parse-pos from-pos)
         to (parse-pos to-pos)
         min-x (min (.getX from) (.getX to))
         max-x (max (.getX from) (.getX to))
         min-y (min (.getY from) (.getY to))
         max-y (max (.getY from) (.getY to))
         min-z (min (.getZ from) (.getZ to))
         max-z (max (.getZ from) (.getZ to))
         counts (atom {})]
     (doseq [x (range min-x (inc max-x))
             y (range min-y (inc max-y))
             z (range min-z (inc max-z))]
       (let [state (.getBlockState level (BlockPos. x y z))
             block (.getBlock state)]
         (swap! counts update block (fnil inc 0))))
     @counts))
  ([^Level level from-pos to-pos block-type]
   (let [target-block (if (instance? Block block-type)
                        block-type
                        (core/get-block-by-id block-type))
         from (parse-pos from-pos)
         to (parse-pos to-pos)
         min-x (min (.getX from) (.getX to))
         max-x (max (.getX from) (.getX to))
         min-y (min (.getY from) (.getY to))
         max-y (max (.getY from) (.getY to))
         min-z (min (.getZ from) (.getZ to))
         max-z (max (.getZ from) (.getZ to))
         count (atom 0)]
     (doseq [x (range min-x (inc max-x))
             y (range min-y (inc max-y))
             z (range min-z (inc max-z))]
       (let [state (.getBlockState level (BlockPos. x y z))]
         (when (= (.getBlock state) target-block)
           (swap! count inc))))
     @count)))

(comment
  ;; 使用示例

  ;; ========== 基础填充 ==========

  ;; 1. 填充立方体
  (fill-blocks! level [0 60 0] [10 70 10] Blocks/STONE)

  ;; 2. 替换方块
  (replace-blocks! level [0 60 0] [10 70 10] Blocks/DIRT Blocks/GRASS_BLOCK)

  ;; 3. 扫描查找
  (def diamond-ores
    (scan-blocks level [-50 0 -50] [50 100 50]
      (fn [pos state]
        (= (.getBlock state) Blocks/DIAMOND_ORE))))

  (println "Found" (count diamond-ores) "diamond ores")

  ;; ========== 几何形状 ==========

  ;; 4. 球体
  (fill-sphere level [100 64 200] 5 Blocks/GLASS)
  (fill-sphere level [100 64 200] 8 Blocks/GLASS :hollow? true)

  ;; 5. 圆柱
  (fill-cylinder level [100 64 200] 3 10 Blocks/STONE)
  (fill-cylinder level [100 64 200] 4 15 Blocks/GLASS :hollow? true)

  ;; 水平圆柱
  (fill-cylinder level [100 64 200] 3 10 Blocks/STONE :axis :x)

  ;; 6. 金字塔
  (fill-pyramid level [100 64 200] 20 10 Blocks/SANDSTONE)
  (fill-pyramid level [100 80 200] 15 8 Blocks/QUARTZ_BLOCK :inverted? true)

  ;; ========== 统计 ==========

  ;; 7. 统计方块数量
  (count-blocks level [0 60 0] [10 70 10] Blocks/STONE)
  ; => 150

  ;; 统计所有方块类型
  (let [counts (count-blocks level [0 60 0] [10 70 10])]
    (doseq [[block count] (sort-by val > counts)]
      (println count "x" (.getName (.asItem block)))))

  ;; ========== 复杂示例 ==========

  ;; 8. 创建空心立方体( 填充外壳)
  (let [size 10
        pos [100 64 200]]
    ;; 底面和顶面
    (fill-blocks! level pos [(+ (first pos) size) (second pos) (+ (nth pos 2) size)] Blocks/STONE)
    (fill-blocks! level [100 (+ 64 size) 200] [(+ 100 size) (+ 64 size) (+ 200 size)] Blocks/STONE)
    ;; 四面墙( 挖空内部)
    (replace-blocks! level [(inc (first pos)) (inc (second pos)) (inc (nth pos 2))]
                     [(+ (first pos) size -1) (+ (second pos) size -1) (+ (nth pos 2) size -1)]
                     Blocks/STONE Blocks/AIR))

  ;; 9. 创建塔楼
  (doseq [floor (range 0 50 5)]
    (fill-cylinder level [100 (+ 64 floor) 200] 5 5 Blocks/STONE_BRICKS :hollow? true))

  ;; 10. 创建迷宫( 简化版)
  (fill-blocks! level [0 64 0] [50 64 50] Blocks/STONE)  ; 底座
  (doseq [x (range 0 50 3)
          z (range 0 50 3)]
    (when (and (pos? x) (pos? z))
      (fill-blocks! level [x 64 z] [x 68 z] Blocks/STONE_BRICKS))))
