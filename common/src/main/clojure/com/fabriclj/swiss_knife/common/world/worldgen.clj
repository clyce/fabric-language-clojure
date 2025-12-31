(ns com.fabriclj.swiss-knife.common.world.worldgen
  "世界生成系统

   提供 Minecraft 世界生成的 Clojure 友好接口。
   核心功能:
   - 矿石生成配置
   - 树木和植被生成
   - 结构放置
   - 生物群系修改
   - 特征和放置器"
  (:require [clojure.string :as str])
  (:import (net.minecraft.world.level.levelgen.feature Feature
            ConfiguredFeature
            FeaturePlaceContext)
           (net.minecraft.world.level.levelgen.feature.configurations OreConfiguration
            TreeConfiguration RandomPatchConfiguration)
           (net.minecraft.world.level.levelgen.placement PlacedFeature PlacementModifier PlacementContext)
           (net.minecraft.world.level.levelgen.structure Structure StructurePiece StructureType)
           (net.minecraft.world.level.block Block Blocks)
           (net.minecraft.world.level.block.state BlockState)
           (net.minecraft.core BlockPos Registry Holder)
           (net.minecraft.resources ResourceLocation ResourceKey)
           (net.minecraft.world.level Level)
           (net.minecraft.world.level.biome Biome BiomeGenerationSettings)
           (net.minecraft.world.level.levelgen HeightmapType)
           (net.minecraft.world.level.levelgen.placement HeightRangePlacement
            CountPlacement
            RarityFilter BiomeFilter InSquarePlacement)
           (net.minecraft.world.level.levelgen.feature.stateproviders BlockStateProvider)
           (net.minecraft.data.worldgen.placement PlacementUtils)
           (net.minecraft.world.level.levelgen.blockpredicates BlockPredicate)
           (net.minecraft.world.level.levelgen VerticalAnchor)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 辅助函数
;; ============================================================================

(defn- ->resource-location
  "转换为 ResourceLocation"
  [id]
  (cond
    (instance? ResourceLocation id) id
    (string? id) (if (str/includes? id ":")
                   (ResourceLocation. id)
                   (ResourceLocation. "minecraft" id))
    (keyword? id) (ResourceLocation. (name id))
    :else (throw (IllegalArgumentException. (str "Invalid resource location: " id)))))

(defn- ->block-state
  "转换为 BlockState"
  [block]
  (cond
    (instance? BlockState block) block
    (instance? Block block) (.defaultBlockState ^Block block)
    :else (.defaultBlockState ^Block block)))

;; ============================================================================
;; 矿石生成配置
;; ============================================================================

(defn ore-target
  "创建矿石目标方块规则

   参数:
   - target: 目标方块或方块标签
   - ore: 矿石方块

   示例:
   ```clojure
   (ore-target Blocks/STONE Blocks/COAL_ORE)
   (ore-target Blocks/DEEPSLATE Blocks/DEEPSLATE_COAL_ORE)
   ```"
  [target ore]
  (OreConfiguration$TargetBlockState.
   (if (instance? net.minecraft.tags.TagKey target)
     (BlockPredicate/matchesTag target)
     (BlockPredicate/matchesBlocks [target]))
   (->block-state ore)))

(defn ore-configuration
  "创建矿石配置

   生成算法:
   Minecraft 的矿石生成使用椭球算法。每次尝试生成时，会在目标位置创建一个椭球形矿脉，矿脉中的每个方块都有 discard-chance 的概率被丢弃，从而形成不规则的矿脉形状。

   参数详解:
   - targets: 矿石目标列表( ore-target 的结果)
     定义在哪些方块( 如石头、深板岩) 上生成什么矿石
   - size: 矿脉大小( 方块数量，通常 3-17)
     决定矿脉中可能包含的最大方块数。实际数量受 discard-chance 影响
   - discard-chance: 丢弃概率( 0.0-1.0，默认 0.0)
     每个方块被丢弃的概率。0.0 = 规则矿脉，0.5 = 约一半方块被丢弃
     原版通常使用 0.0( 规则) 或 0.1( 略微不规则)

   工作原理:
   1. 在指定高度范围内随机选择一个位置
   2. 创建一个椭球形区域( 大小由 size 决定)
   3. 对区域内每个方块，检查是否匹配 targets 中的目标方块
   4. 如果匹配，按 (1 - discard-chance) 的概率替换为矿石

   示例:
   ```clojure
   ;; 规则的煤矿脉( 大而密集)
   (ore-configuration
     [(ore-target Blocks/STONE Blocks/COAL_ORE)]
     17  ; 大矿脉
     0.0) ; 规则形状

   ;; 不规则的钻石矿脉( 小而稀疏)
   (ore-configuration
     [(ore-target Blocks/STONE Blocks/DIAMOND_ORE)
      (ore-target Blocks/DEEPSLATE Blocks/DEEPSLATE_DIAMOND_ORE)]
     8   ; 小矿脉
     0.5) ; 约一半方块被丢弃，形成零散矿脉
   ```"
  [targets size & {:keys [discard-chance] :or {discard-chance 0.0}}]
  (OreConfiguration. targets (int size) (float discard-chance)))

;; ============================================================================
;; 高度放置配置
;; ============================================================================

(defn uniform-height
  "均匀高度分布

   参数:
   - min-height: 最小高度( 可以是整数或 :bottom/:top)
   - max-height: 最大高度

   示例:
   ```clojure
   (uniform-height 0 64)
   (uniform-height :bottom 0)
   ```"
  [min-height max-height]
  (HeightRangePlacement/uniform
   (if (keyword? min-height)
     (case min-height
       :bottom (VerticalAnchor/bottom)
       :top (VerticalAnchor/top)
       (VerticalAnchor/absolute (int min-height)))
     (VerticalAnchor/absolute (int min-height)))
   (if (keyword? max-height)
     (case max-height
       :bottom (VerticalAnchor/bottom)
       :top (VerticalAnchor/top)
       (VerticalAnchor/absolute (int max-height)))
     (VerticalAnchor/absolute (int max-height)))))

(defn triangle-height
  "三角形高度分布( 中间密集)

   参数:
   - min-height: 最小高度
   - max-height: 最大高度

   示例:
   ```clojure
   (triangle-height -64 64)  ; 在 Y=0 附近最密集
   ```"
  [min-height max-height]
  (HeightRangePlacement/triangle
   (VerticalAnchor/absolute (int min-height))
   (VerticalAnchor/absolute (int max-height))))

(defn absolute-height
  "绝对高度( 固定在某个高度)

   参数:
   - height: 高度值

   示例:
   ```clojure
   (absolute-height 64)
   ```"
  [height]
  (VerticalAnchor/absolute (int height)))

;; ============================================================================
;; 放置修饰器( Placement Modifiers)
;; ============================================================================

(defn count-placement
  "数量放置修饰器( 每区块放置次数)

   参数:
   - count: 次数( 整数)

   示例:
   ```clojure
   (count-placement 16)  ; 每区块尝试 16 次
   ```"
  [count]
  (CountPlacement/of (int count)))

(defn rarity-filter
  "稀有度过滤( 每 N 个区块一次)

   参数:
   - chance: 稀有度( 整数，值越大越稀有)

   示例:
   ```clojure
   (rarity-filter 32)  ; 平均每 32 个区块出现一次
   ```"
  [chance]
  (RarityFilter/onAverageOnceEvery (int chance)))

(defn in-square-placement
  "在区块内随机水平位置"
  []
  (InSquarePlacement/spread))

(defn biome-filter-placement
  "生物群系过滤( 仅在特定生物群系生成) "
  []
  (BiomeFilter/biome))

(defn heightmap-placement
  "基于高度图放置( 地表/海底/等)

   参数:
   - type: 高度图类型( :motion_blocking/:ocean_floor/:world_surface 等)

   示例:
   ```clojure
   (heightmap-placement :world_surface)
   ```"
  [type]
  (PlacementUtils/HEIGHTMAP
   (case type
     :motion_blocking HeightmapType/MOTION_BLOCKING
     :motion_blocking_no_leaves HeightmapType/MOTION_BLOCKING_NO_LEAVES
     :ocean_floor HeightmapType/OCEAN_FLOOR
     :world_surface HeightmapType/WORLD_SURFACE
     :world_surface_wg HeightmapType/WORLD_SURFACE_WG
     HeightmapType/WORLD_SURFACE)))

;; ============================================================================
;; 特征创建( 简化接口)
;; ============================================================================

(defn create-ore-feature-data
  "创建矿石特征数据( 用于数据生成)

   参数:
   - id: 特征 ID
   - ore-block: 矿石方块
   - target-blocks: 目标方块列表( 可以是方块或 {:block block :ore ore} 映射)
   - size: 矿脉大小
   - count: 每区块数量
   - height-range: 高度范围 [min max] 或高度放置修饰器
   - opts: 可选参数
     - :discard-chance - 丢弃概率
     - :rarity - 稀有度( 会替代 count)
     - :triangle - 是否使用三角分布

   返回: 特征数据映射

   示例:
   ```clojure
   ;; 简单矿石
   (create-ore-feature-data
     \"mymod:magic_ore\"
     Blocks/MAGIC_ORE
     [Blocks/STONE Blocks/DEEPSLATE]
     9
     16
     [0 64])

   ;; 不同目标使用不同矿石
   (create-ore-feature-data
     \"mymod:magic_ore_varied\"
     nil
     [{:block Blocks/STONE :ore Blocks/MAGIC_ORE}
      {:block Blocks/DEEPSLATE :ore Blocks/DEEPSLATE_MAGIC_ORE}]
     9
     16
     [0 64]
     :triangle true)

   ;; 稀有矿石
   (create-ore-feature-data
     \"mymod:rare_ore\"
     Blocks/RARE_ORE
     [Blocks/STONE]
     8
     nil
     [-64 16]
     :rarity 32)  ; 平均每 32 个区块一次
   ```"
  [id ore-block target-blocks size count height-range & {:keys [discard-chance rarity triangle]
                                                         :or {discard-chance 0.0}}]
  (let [targets (if (map? (first target-blocks))
                  (mapv (fn [{:keys [block ore]}]
                          (ore-target block ore))
                        target-blocks)
                  (mapv (fn [target]
                          (ore-target target ore-block))
                        target-blocks))
        config (ore-configuration targets size :discard-chance discard-chance)
        [min-h max-h] height-range
        height-placement (if triangle
                           (triangle-height min-h max-h)
                           (uniform-height min-h max-h))
        placement-modifiers (cond-> []
                              rarity (conj (rarity-filter rarity))
                              count (conj (count-placement count))
                              true (conj (in-square-placement))
                              true (conj height-placement)
                              true (conj (biome-filter-placement)))]
    {:id (->resource-location id)
     :type :ore
     :config config
     :placement placement-modifiers}))

;; ============================================================================
;; 树木和植被
;; ============================================================================

(defn simple-tree-feature-data
  "创建简单树木特征数据

   参数:
   - id: 特征 ID
   - trunk-block: 树干方块
   - leaves-block: 树叶方块
   - height: 树高 [min max]
   - opts: 可选参数
     - :count - 每区块数量
     - :rarity - 稀有度

   示例:
   ```clojure
   (simple-tree-feature-data
     \"mymod:magic_tree\"
     Blocks/MAGIC_LOG
     Blocks/MAGIC_LEAVES
     [5 8]
     :count 2)
   ```"
  [id trunk-block leaves-block [min-height max-height] & {:keys [count rarity]
                                                          :or {count 1}}]
  {:id (->resource-location id)
   :type :tree
   :trunk trunk-block
   :leaves leaves-block
   :height {:min min-height :max max-height}
   :count count
   :rarity rarity})

;; ============================================================================
;; 生物群系修改
;; ============================================================================

(defn biome-modification-data
  "创建生物群系修改数据

   参数:
   - biomes: 目标生物群系列表( ResourceLocation 或关键字)
   - modifications: 修改列表
     - [:add-feature decoration-step feature] - 添加特征
     - [:add-spawn entity-type weight min-group max-group] - 添加生物生成
     - [:remove-feature feature] - 移除特征

   示例:
   ```clojure
   (biome-modification-data
     [:plains :forest]
     [[:add-feature :underground_ores my-ore-feature]
      [:add-spawn EntityType/COW 10 4 4]])
   ```"
  [biomes modifications]
  {:biomes (mapv ->resource-location biomes)
   :modifications modifications})

;; ============================================================================
;; 结构放置
;; ============================================================================

(defn simple-structure-data
  "创建简单结构数据

   参数:
   - id: 结构 ID
   - structure-nbt: NBT 结构文件路径
   - spawn-overrides: 生成覆盖( 可选)
   - opts: 可选参数
     - :biomes - 生成的生物群系
     - :spacing - 结构间距
     - :separation - 结构分离
     - :salt - 盐值( 用于随机生成)

   示例:
   ```clojure
   (simple-structure-data
     \"mymod:magic_tower\"
     \"mymod:magic_tower\"
     {}
     :biomes [:plains :forest]
     :spacing 32
     :separation 8)
   ```"
  [id structure-nbt spawn-overrides & {:keys [biomes spacing separation salt]
                                       :or {spacing 32 separation 8 salt 0}}]
  {:id (->resource-location id)
   :type :structure
   :nbt structure-nbt
   :spawn-overrides spawn-overrides
   :biomes (mapv ->resource-location biomes)
   :spacing spacing
   :separation separation
   :salt salt})

;; ============================================================================
;; DSL 宏
;; ============================================================================

(defmacro defore
  "定义矿石生成

   示例:
   ```clojure
   (defore copper-ore
     :id \"mymod:copper_ore\"
     :ore Blocks/COPPER_ORE
     :targets [Blocks/STONE Blocks/DEEPSLATE]
     :size 9
     :count 16
     :height [0 112])
   ```"
  [name & {:keys [id ore targets size count height rarity triangle]
           :or {triangle false}}]
  `(def ~name
     (create-ore-feature-data
      ~id
      ~ore
      ~targets
      ~size
      ~count
      ~height
      :rarity ~rarity
      :triangle ~triangle)))

(defmacro deftree
  "定义树木生成

   示例:
   ```clojure
   (deftree magic-tree
     :id \"mymod:magic_tree\"
     :trunk Blocks/MAGIC_LOG
     :leaves Blocks/MAGIC_LEAVES
     :height [5 8]
     :count 2)
   ```"
  [name & {:keys [id trunk leaves height count rarity]}]
  `(def ~name
     (simple-tree-feature-data
      ~id
      ~trunk
      ~leaves
      ~height
      :count ~count
      :rarity ~rarity)))

;; ============================================================================
;; 常见配置预设
;; ============================================================================

(def common-ore-heights
  "常见矿石高度配置"
  {:coal {:min 0 :max 256 :triangle false}
   :iron {:min -64 :max 72 :triangle true}
   :copper {:min -16 :max 112 :triangle true}
   :gold {:min -64 :max 32 :triangle true}
   :lapis {:min -64 :max 64 :triangle true}
   :redstone {:min -64 :max 16 :triangle false}
   :diamond {:min -64 :max 16 :triangle true}
   :emerald {:min -16 :max 256 :triangle false}})

(def common-ore-sizes
  "常见矿石矿脉大小"
  {:coal 17
   :iron 9
   :copper 10
   :gold 9
   :lapis 7
   :redstone 8
   :diamond 8
   :emerald 3})

(def common-ore-counts
  "常见矿石每区块数量"
  {:coal 20
   :iron 20
   :copper 16
   :gold 4
   :lapis 2
   :redstone 8
   :diamond 1
   :emerald 100})  ; 但使用 rarity filter

(defn standard-ore
  "使用标准配置创建矿石特征

   参数:
   - id: 特征 ID
   - ore-type: 矿石类型( :coal/:iron/:diamond 等)
   - ore-block: 矿石方块
   - targets: 目标方块列表

   示例:
   ```clojure
   (standard-ore \"mymod:magic_ore\" :diamond
     Blocks/MAGIC_ORE
     [Blocks/STONE])
   ```"
  [id ore-type ore-block targets]
  (let [height-config (get common-ore-heights ore-type)
        size (get common-ore-sizes ore-type 9)
        count (get common-ore-counts ore-type 10)]
    (create-ore-feature-data
     id
     ore-block
     targets
     size
     count
     [(:min height-config) (:max height-config)]
     :triangle (:triangle height-config))))

;; ============================================================================
;; 世界生成工具
;; ============================================================================

(defn find-surface-pos
  "查找表面位置( 从上往下搜索第一个固体方块)

   参数:
   - level: Level
   - x: X 坐标
   - z: Z 坐标
   - max-y: 最大 Y( 默认 256)

   返回: BlockPos 或 nil"
  [^Level level x z & {:keys [max-y] :or {max-y 256}}]
  (loop [y max-y]
    (if (< y -64)
      nil
      (let [pos (BlockPos. (int x) (int y) (int z))
            state (.getBlockState level pos)]
        (if (and (not (.isAir state))
                 (.isSolidRender state level pos))
          pos
          (recur (dec y)))))))

(defn can-place-tree?
  "检查是否可以放置树木

   参数:
   - level: Level
   - pos: 位置( 树干底部)
   - height: 树高

   返回: boolean"
  [^Level level ^BlockPos pos height]
  (let [ground-pos (.below pos)
        ground-state (.getBlockState level ground-pos)]
    (and
     ;; 检查地面是否合适
     (or (.is ground-state Blocks/GRASS_BLOCK)
         (.is ground-state Blocks/DIRT)
         (.is ground-state Blocks/PODZOL))
     ;; 检查树干空间是否足够
     (every? (fn [y-offset]
               (let [check-pos (.above pos y-offset)
                     check-state (.getBlockState level check-pos)]
                 (.isAir check-state)))
             (range height)))))

(defn place-simple-tree!
  "放置简单树木( 直树干 + 球形树叶)

   参数:
   - level: Level
   - pos: 位置( 树干底部)
   - trunk-block: 树干方块
   - leaves-block: 树叶方块
   - height: 树高
   - leaves-radius: 树叶半径( 默认 2)

   返回: 是否成功放置"
  [^Level level ^BlockPos pos trunk-block leaves-block height & {:keys [leaves-radius]
                                                                 :or {leaves-radius 2}}]
  (when (can-place-tree? level pos height)
    ;; 放置树干
    (doseq [y (range height)]
      (let [trunk-pos (.above pos y)]
        (.setBlock level trunk-pos (->block-state trunk-block) 3)))

    ;; 放置树叶
    (let [leaves-center (.above pos (dec height))]
      (doseq [dx (range (- leaves-radius) (inc leaves-radius))
              dy (range -1 (inc leaves-radius))
              dz (range (- leaves-radius) (inc leaves-radius))]
        (when (<= (+ (* dx dx) (* dy dy) (* dz dz))
                  (* leaves-radius leaves-radius))
          (let [leaves-pos (.offset leaves-center dx dy dz)
                current-state (.getBlockState level leaves-pos)]
            (when (.isAir current-state)
              (.setBlock level leaves-pos (->block-state leaves-block) 3))))))
    true))

(comment
  ;; 使用示例

  ;; ========== 矿石生成 ==========

  ;; 1. 简单矿石
  (def my-ore
    (create-ore-feature-data
     "mymod:magic_ore"
     Blocks/MAGIC_ORE
     [Blocks/STONE Blocks/DEEPSLATE]
     9
     16
     [0 64]))

  ;; 2. 使用宏定义
  (defore copper-ore
    :id "mymod:copper_ore"
    :ore Blocks/COPPER_ORE
    :targets [Blocks/STONE]
    :size 10
    :count 16
    :height [0 112]
    :triangle true)

  ;; 3. 稀有矿石
  (defore diamond-ore
    :id "mymod:diamond_ore"
    :ore Blocks/DIAMOND_ORE
    :targets [Blocks/STONE Blocks/DEEPSLATE]
    :size 8
    :height [-64 16]
    :rarity 32)  ; 使用稀有度而不是 count

  ;; 4. 使用标准配置
  (def standard-ore-config
    (standard-ore "mymod:magic_ore" :diamond
                  Blocks/MAGIC_ORE
                  [Blocks/STONE]))

  ;; ========== 树木生成 ==========

  ;; 5. 简单树木
  (deftree magic-tree
    :id "mymod:magic_tree"
    :trunk Blocks/MAGIC_LOG
    :leaves Blocks/MAGIC_LEAVES
    :height [5 8]
    :count 2)

  ;; 6. 手动放置树木
  (place-simple-tree! level (BlockPos. 100 64 200)
                      Blocks/OAK_LOG
                      Blocks/OAK_LEAVES
                      6
                      :leaves-radius 2)

  ;; ========== 生物群系修改 ==========

  ;; 7. 添加特征到生物群系
  (def biome-mod
    (biome-modification-data
     [:plains :sunflower_plains]
     [[:add-feature :underground_ores my-ore]
      [:add-spawn EntityType/COW 10 4 4]]))

  ;; ========== 实用工具 ==========

  ;; 8. 查找地表
  (def surface-pos (find-surface-pos level 100 200))
  (println "Surface at:" surface-pos)

  ;; 9. 检查是否可以放置
  (when (can-place-tree? level pos 6)
    (place-simple-tree! level pos
                        Blocks/OAK_LOG Blocks/OAK_LEAVES 6)))
