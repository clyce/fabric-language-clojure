(ns com.fabriclj.swiss-knife.common.utils
  "实用工具集

   提供各种便捷的实用函数，补充其他模块未覆盖的功能。

   核心功能：
   - 文本和翻译
   - 时间和计时
   - 数学和随机
   - 集合操作
   - 方块和区域
   - 玩家工具
   - NBT 工具
   - 调试工具"
  (:require [clojure.string :as str]
            [clojure.walk :as walk])
  (:import [net.minecraft.network.chat Component TextColor Style
            ClickEvent HoverEvent
            MutableComponent]
           [net.minecraft.world.entity Entity LivingEntity]
           [net.minecraft.world.entity.player Player]
           [net.minecraft.world.level Level ServerLevel]
           [net.minecraft.core BlockPos Direction]
           [net.minecraft.world.item ItemStack Item]
           [net.minecraft.world.level.block Block Blocks]
           [net.minecraft.world.level.block.state BlockState]
           [net.minecraft.nbt CompoundTag Tag ListTag StringTag
            IntTag DoubleTag FloatTag LongTag
            ByteTag ShortTag]
           [net.minecraft.world.phys Vec3 AABB]
           [net.minecraft.server.level ServerPlayer]
           [net.minecraft.resources ResourceLocation ResourceKey]
           [net.minecraft.world.level.dimension Level]
           [java.util UUID Random]
           [java.util.function Consumer]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 文本和翻译
;; ============================================================================

(defn translate
  "翻译键转文本

   参数:
   - key: 翻译键
   - args: 翻译参数（可选）

   返回：Component

   示例:
   ```clojure
   (translate \"item.minecraft.diamond\")
   (translate \"commands.kill.success\" player-name)
   ```"
  [key & args]
  (if (seq args)
    (Component/translatable key (into-array Object args))
    (Component/translatable key)))

(defn literal-text
  "创建字面文本"
  [text]
  (Component/literal text))

(defn colored-text
  "创建带颜色的文本

   参数:
   - text: 文本内容
   - color: 颜色（关键字或 RGB 整数）
     关键字: :black/:dark_blue/:dark_green/:dark_aqua/:dark_red
             :dark_purple/:gold/:gray/:dark_gray/:blue/:green
             :aqua/:red/:light_purple/:yellow/:white

   示例:
   ```clojure
   (colored-text \"Hello\" :red)
   (colored-text \"World\" 0xFF0000)
   ```"
  [text color]
  (let [^MutableComponent comp (Component/literal text)
        text-color (if (keyword? color)
                     (TextColor/fromLegacyFormat
                      (case color
                        :black (net.minecraft.ChatFormatting/BLACK)
                        :dark_blue (net.minecraft.ChatFormatting/DARK_BLUE)
                        :dark_green (net.minecraft.ChatFormatting/DARK_GREEN)
                        :dark_aqua (net.minecraft.ChatFormatting/DARK_AQUA)
                        :dark_red (net.minecraft.ChatFormatting/DARK_RED)
                        :dark_purple (net.minecraft.ChatFormatting/DARK_PURPLE)
                        :gold (net.minecraft.ChatFormatting/GOLD)
                        :gray (net.minecraft.ChatFormatting/GRAY)
                        :dark_gray (net.minecraft.ChatFormatting/DARK_GRAY)
                        :blue (net.minecraft.ChatFormatting/BLUE)
                        :green (net.minecraft.ChatFormatting/GREEN)
                        :aqua (net.minecraft.ChatFormatting/AQUA)
                        :red (net.minecraft.ChatFormatting/RED)
                        :light_purple (net.minecraft.ChatFormatting/LIGHT_PURPLE)
                        :yellow (net.minecraft.ChatFormatting/YELLOW)
                        :white (net.minecraft.ChatFormatting/WHITE)
                        (net.minecraft.ChatFormatting/WHITE)))
                     (TextColor/fromRgb (int color)))]
    (.withStyle comp (-> (Style/EMPTY) (.withColor text-color)))))

(defn formatted-text
  "创建格式化文本

   参数:
   - text: 文本内容
   - opts: 格式选项
     - :color - 颜色
     - :bold - 粗体
     - :italic - 斜体
     - :underlined - 下划线
     - :strikethrough - 删除线
     - :obfuscated - 混淆

   示例:
   ```clojure
   (formatted-text \"Important!\" :color :red :bold true :underlined true)
   ```"
  [text & {:keys [color bold italic underlined strikethrough obfuscated]}]
  (let [^MutableComponent comp (Component/literal text)
        style (cond-> (Style/EMPTY)
                color (.withColor (if (keyword? color)
                                    (TextColor/fromLegacyFormat
                                     (eval (symbol "net.minecraft.ChatFormatting"
                                                   (str/upper-case (name color)))))
                                    (TextColor/fromRgb (int color))))
                bold (.withBold (boolean bold))
                italic (.withItalic (boolean italic))
                underlined (.withUnderlined (boolean underlined))
                strikethrough (.withStrikethrough (boolean strikethrough))
                obfuscated (.withObfuscated (boolean obfuscated)))]
    (.withStyle comp style)))

(defn clickable-text
  "创建可点击文本

   参数:
   - text: 文本内容
   - action: 点击动作
     - [:open-url url] - 打开 URL
     - [:run-command cmd] - 运行命令
     - [:suggest-command cmd] - 建议命令
     - [:change-page page] - 改变书页
     - [:copy-to-clipboard text] - 复制到剪贴板

   示例:
   ```clojure
   (clickable-text \"Click me!\" [:open-url \"https://minecraft.net\"])
   (clickable-text \"Run command\" [:run-command \"/kill @s\"])
   ```"
  [text [action-type action-value]]
  (let [^MutableComponent comp (Component/literal text)
        click-event (case action-type
                      :open-url (ClickEvent. ClickEvent$Action/OPEN_URL action-value)
                      :run-command (ClickEvent. ClickEvent$Action/RUN_COMMAND action-value)
                      :suggest-command (ClickEvent. ClickEvent$Action/SUGGEST_COMMAND action-value)
                      :change-page (ClickEvent. ClickEvent$Action/CHANGE_PAGE (str action-value))
                      :copy-to-clipboard (ClickEvent. ClickEvent$Action/COPY_TO_CLIPBOARD action-value))]
    (.withStyle comp (-> (Style/EMPTY) (.withClickEvent click-event)))))

(defn hoverable-text
  "创建悬停提示文本

   参数:
   - text: 文本内容
   - hover: 悬停内容（Component 或字符串）

   示例:
   ```clojure
   (hoverable-text \"Hover me\" \"This is a tooltip\")
   ```"
  [text hover]
  (let [^MutableComponent comp (Component/literal text)
        hover-comp (if (instance? Component hover)
                     hover
                     (Component/literal hover))
        hover-event (HoverEvent. HoverEvent$Action/SHOW_TEXT hover-comp)]
    (.withStyle comp (-> (Style/EMPTY) (.withHoverEvent hover-event)))))

;; ============================================================================
;; 时间和计时
;; ============================================================================

(defn ticks->seconds
  "Tick 转秒（20 ticks = 1 second）"
  [ticks]
  (/ ticks 20.0))

(defn seconds->ticks
  "秒转 Tick"
  [seconds]
  (int (* seconds 20)))

(defn ticks->minutes
  "Tick 转分钟"
  [ticks]
  (/ ticks 1200.0))

(defn minutes->ticks
  "分钟转 Tick"
  [minutes]
  (int (* minutes 1200)))

(defonce ^:private scheduled-tasks (atom []))

(defn schedule-task
  "延迟执行任务（需要在游戏循环中调用 tick-scheduled-tasks）

   参数:
   - delay-ticks: 延迟 Tick 数
   - task: 任务函数

   返回：任务 ID"
  [delay-ticks task]
  (let [task-id (str (UUID/randomUUID))
        execute-at (+ (System/currentTimeMillis) (* delay-ticks 50))]
    (swap! scheduled-tasks conj {:id task-id
                                 :execute-at execute-at
                                 :task task})
    task-id))

(defn cancel-task
  "取消计划任务"
  [task-id]
  (swap! scheduled-tasks
         (fn [tasks]
           (remove #(= (:id %) task-id) tasks))))

(defn tick-scheduled-tasks
  "执行到期的计划任务（应在游戏循环中调用）"
  []
  (let [now (System/currentTimeMillis)
        {ready true not-ready false} (group-by #(<= (:execute-at %) now) @scheduled-tasks)]
    (doseq [{:keys [task]} ready]
      (try
        (task)
        (catch Exception e
          (println "Error executing scheduled task:" (.getMessage e)))))
    (reset! scheduled-tasks (or not-ready []))))

;; ============================================================================
;; 数学和随机
;; ============================================================================

(defonce ^:private rng (Random.))

(defn random-int
  "随机整数 [min, max)"
  [min max]
  (+ min (.nextInt ^Random rng (- max min))))

(defn random-float
  "随机浮点数 [min, max)"
  [min max]
  (+ min (* (.nextFloat ^Random rng) (- max min))))

(defn random-chance
  "随机概率（返回 boolean）

   参数:
   - probability: 概率（0.0-1.0）

   示例:
   ```clojure
   (when (random-chance 0.3)  ; 30% 概率
     (println \"Lucky!\"))
   ```"
  [probability]
  (< (.nextFloat ^Random rng) probability))

(defn weighted-random
  "权重随机选择

   参数:
   - weighted-map: 权重映射 {item weight ...}

   返回：随机选中的 item

   示例:
   ```clojure
   (weighted-random {:diamond 1 :gold 3 :iron 10})
   ; 铁的概率最高
   ```"
  [weighted-map]
  (let [total-weight (reduce + (vals weighted-map))
        rand-val (.nextFloat ^Random rng)
        target (* rand-val total-weight)]
    (loop [items (seq weighted-map)
           accumulated 0]
      (if-let [[item weight] (first items)]
        (let [new-accumulated (+ accumulated weight)]
          (if (< target new-accumulated)
            item
            (recur (rest items) new-accumulated)))
        (first (keys weighted-map))))))

(defn lerp
  "线性插值

   参数:
   - a: 起始值
   - b: 结束值
   - t: 插值参数（0.0-1.0）

   示例:
   ```clojure
   (lerp 0 100 0.5)  ; => 50.0
   ```"
  [a b t]
  (+ a (* t (- b a))))

(defn clamp
  "限制值在范围内

   示例:
   ```clojure
   (clamp 150 0 100)  ; => 100
   (clamp -10 0 100)  ; => 0
   ```"
  [value min-val max-val]
  (max min-val (min max-val value)))

(defn distance-2d
  "2D 距离（忽略 Y 轴）"
  [x1 z1 x2 z2]
  (Math/sqrt (+ (Math/pow (- x2 x1) 2)
                (Math/pow (- z2 z1) 2))))

(defn distance-3d
  "3D 距离"
  [x1 y1 z1 x2 y2 z2]
  (Math/sqrt (+ (Math/pow (- x2 x1) 2)
                (Math/pow (- y2 y1) 2)
                (Math/pow (- z2 z1) 2))))

(defn normalize-angle
  "归一化角度到 [-180, 180]"
  [angle]
  (let [a (mod (+ angle 180) 360)]
    (- a 180)))

;; ============================================================================
;; 集合操作
;; ============================================================================

(defn find-nearest
  "查找最近的元素

   参数:
   - pos: 参考位置 [x y z] 或 Vec3 或 BlockPos
   - entities: 实体集合

   返回：最近的实体或 nil"
  [pos entities]
  (let [[x y z] (cond
                  (vector? pos) pos
                  (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)]
                  (instance? BlockPos pos) [(.getX ^BlockPos pos)
                                            (.getY ^BlockPos pos)
                                            (.getZ ^BlockPos pos)])]
    (when (seq entities)
      (apply min-key
             (fn [entity]
               (let [epos (.position ^Entity entity)]
                 (distance-3d x y z (.x epos) (.y epos) (.z epos))))
             entities))))

(defn find-in-radius
  "查找半径内的元素

   参数:
   - pos: 中心位置
   - entities: 实体集合
   - radius: 半径

   返回：半径内的实体列表"
  [pos entities radius]
  (let [[x y z] (cond
                  (vector? pos) pos
                  (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)]
                  (instance? BlockPos pos) [(.getX ^BlockPos pos)
                                            (.getY ^BlockPos pos)
                                            (.getZ ^BlockPos pos)])]
    (filter (fn [entity]
              (let [epos (.position ^Entity entity)
                    dist (distance-3d x y z (.x epos) (.y epos) (.z epos))]
                (< dist radius)))
            entities)))

(defn sort-by-distance
  "按距离排序

   参数:
   - pos: 参考位置
   - entities: 实体集合

   返回：按距离从近到远排序的实体列表"
  [pos entities]
  (let [[x y z] (cond
                  (vector? pos) pos
                  (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)]
                  (instance? BlockPos pos) [(.getX ^BlockPos pos)
                                            (.getY ^BlockPos pos)
                                            (.getZ ^BlockPos pos)])]
    (sort-by (fn [entity]
               (let [epos (.position ^Entity entity)]
                 (distance-3d x y z (.x epos) (.y epos) (.z epos))))
             entities)))

;; ============================================================================
;; 方块和区域
;; ============================================================================

(defn parse-pos
  "解析位置参数为 BlockPos

   支持格式:
   - [x y z]
   - {:x x :y y :z z}
   - BlockPos
   - Vec3

   返回：BlockPos"
  [pos]
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

(defn set-blocks-in-region
  "在区域内批量设置方块

   参数:
   - level: Level
   - from: 起始位置
   - to: 结束位置
   - block: 方块或方块状态

   示例:
   ```clojure
   (set-blocks-in-region level [0 60 0] [10 70 10] Blocks/STONE)
   ```"
  [^Level level from to block]
  (let [from-pos (parse-pos from)
        to-pos (parse-pos to)
        min-x (min (.getX from-pos) (.getX to-pos))
        max-x (max (.getX from-pos) (.getX to-pos))
        min-y (min (.getY from-pos) (.getY to-pos))
        max-y (max (.getY from-pos) (.getY to-pos))
        min-z (min (.getZ from-pos) (.getZ to-pos))
        max-z (max (.getZ from-pos) (.getZ to-pos))
        block-state (if (instance? BlockState block)
                      block
                      (.defaultBlockState ^Block block))]
    (doseq [x (range min-x (inc max-x))
            y (range min-y (inc max-y))
            z (range min-z (inc max-z))]
      (.setBlock level (BlockPos. x y z) block-state 3))))

(defn fill-sphere
  "填充球体

   参数:
   - level: Level
   - center: 中心位置
   - radius: 半径
   - block: 方块
   - hollow?: 是否空心（默认 false）

   示例:
   ```clojure
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
   - hollow?: 是否空心

   示例:
   ```clojure
   (fill-cylinder level [100 64 200] 3 10 Blocks/STONE)
   ```"
  [^Level level center radius height block & {:keys [hollow?] :or {hollow? false}}]
  (let [center-pos (parse-pos center)
        cx (.getX center-pos)
        cy (.getY center-pos)
        cz (.getZ center-pos)
        r-squared (* radius radius)
        block-state (if (instance? BlockState block)
                      block
                      (.defaultBlockState ^Block block))]
    (doseq [x (range (- cx radius) (+ cx radius 1))
            y (range cy (+ cy height))
            z (range (- cz radius) (+ cz radius 1))]
      (let [dist-squared (+ (Math/pow (- x cx) 2)
                            (Math/pow (- z cz) 2))]
        (when (and (<= dist-squared r-squared)
                   (or (not hollow?)
                       (> dist-squared (* (dec radius) (dec radius)))))
          (.setBlock level (BlockPos. x y z) block-state 3))))))

;; ============================================================================
;; 玩家工具
;; ============================================================================

(defn teleport-player
  "传送玩家

   参数:
   - player: Player
   - pos: 目标位置
   - opts: 可选参数
     - :dimension - 目标维度（ResourceKey）
     - :yaw - 水平朝向
     - :pitch - 垂直朝向

   示例:
   ```clojure
   (teleport-player player [100 64 200])
   (teleport-player player [0 100 0] :yaw 90.0 :pitch 0.0)
   ```"
  [^Player player pos & {:keys [dimension yaw pitch]}]
  (let [target-pos (parse-pos pos)
        x (+ (.getX target-pos) 0.5)
        y (.getY target-pos)
        z (+ (.getZ target-pos) 0.5)
        final-yaw (float (or yaw (.getYRot player)))
        final-pitch (float (or pitch (.getXRot player)))]
    (if dimension
      (.teleportTo ^ServerPlayer player
                   ^ServerLevel (-> player .getServer (.getLevel dimension))
                   x y z final-yaw final-pitch)
      (.teleportTo player x y z))))

(defn give-item-to-player
  "给予玩家物品（自动处理满背包）

   参数:
   - player: Player
   - item-stack: ItemStack

   返回：是否成功添加

   示例:
   ```clojure
   (give-item-to-player player (ItemStack. Items/DIAMOND 64))
   ```"
  [^Player player ^ItemStack item-stack]
  (let [inventory (.getInventory player)]
    (.add inventory item-stack)))

(defn remove-item-from-player
  "从玩家移除物品

   参数:
   - player: Player
   - item: Item
   - count: 数量

   返回：实际移除的数量"
  [^Player player ^Item item count]
  (let [inventory (.getInventory player)]
    (loop [remaining count
           slot 0]
      (if (or (<= remaining 0) (>= slot (.getContainerSize inventory)))
        (- count remaining)
        (let [stack (.getItem inventory slot)]
          (if (and (not (.isEmpty stack))
                   (= (.getItem stack) item))
            (let [stack-count (.getCount stack)
                  to-remove (min remaining stack-count)]
              (.shrink stack to-remove)
              (recur (- remaining to-remove) (inc slot)))
            (recur remaining (inc slot))))))))

(defn has-item?
  "检查玩家是否拥有物品

   参数:
   - player: Player
   - item: Item
   - count: 数量（默认 1）

   返回：boolean"
  ([^Player player ^Item item]
   (has-item? player item 1))
  ([^Player player ^Item item count]
   (let [inventory (.getInventory player)]
     (>= (reduce (fn [total slot]
                   (let [stack (.getItem inventory slot)]
                     (if (and (not (.isEmpty stack))
                              (= (.getItem stack) item))
                       (+ total (.getCount stack))
                       total)))
                 0
                 (range (.getContainerSize inventory)))
         count))))

(defn get-player-facing-direction
  "获取玩家朝向

   返回：Direction"
  [^Player player]
  (.getDirection player))

(defn get-player-by-name
  "通过名称获取玩家

   参数:
   - level: Level
   - player-name: 玩家名称

   返回：Player 或 nil"
  [^Level level player-name]
  (.getPlayerByName (.getServer ^ServerLevel level) player-name))

(defn get-player-by-uuid
  "通过 UUID 获取玩家

   参数:
   - level: Level
   - uuid: UUID（字符串或 UUID 对象）

   返回：Player 或 nil"
  [^Level level uuid]
  (let [uuid-obj (if (instance? UUID uuid) uuid (UUID/fromString uuid))]
    (.getPlayerByUUID (.getServer ^ServerLevel level) uuid-obj)))

;; ============================================================================
;; NBT 工具
;; ============================================================================

(defn parse-nbt-string
  "解析 NBT 字符串

   参数:
   - nbt-string: NBT 格式字符串

   返回：CompoundTag

   示例:
   ```clojure
   (parse-nbt-string \"{Health:20.0f,OnGround:1b}\")
   ```"
  [^String nbt-string]
  (net.minecraft.nbt.TagParser/parseTag nbt-string))

(defn nbt->pretty-string
  "NBT 转美化字符串"
  [^Tag nbt-tag]
  (.toString nbt-tag))

(defn nbt->map
  "NBT 转 Clojure Map（简化版）

   注意：仅支持基本类型"
  [^CompoundTag nbt]
  (into {}
        (map (fn [key]
               (let [tag (.get nbt key)]
                 [key (cond
                        (instance? CompoundTag tag) (nbt->map tag)
                        (instance? IntTag tag) (.getAsInt ^IntTag tag)
                        (instance? DoubleTag tag) (.getAsDouble ^DoubleTag tag)
                        (instance? FloatTag tag) (.getAsFloat ^FloatTag tag)
                        (instance? StringTag tag) (.getAsString ^StringTag tag)
                        (instance? ByteTag tag) (.getAsByte ^ByteTag tag)
                        :else (.toString tag))]))
             (.getAllKeys nbt))))

(defn map->nbt
  "Clojure Map 转 NBT（简化版）"
  [data]
  (let [nbt (CompoundTag.)]
    (doseq [[k v] data]
      (let [key-str (name k)]
        (cond
          (integer? v) (.putInt nbt key-str (int v))
          (float? v) (.putFloat nbt key-str (float v))
          (double? v) (.putDouble nbt key-str (double v))
          (string? v) (.putString nbt key-str v)
          (boolean? v) (.putBoolean nbt key-str v)
          (map? v) (.put nbt key-str (map->nbt v))
          :else (.putString nbt key-str (str v)))))
    nbt))

;; ============================================================================
;; 调试工具
;; ============================================================================

(defn debug-log
  "调试日志（带级别和颜色）

   参数:
   - level: 日志级别（:info/:warn/:error/:debug）
   - message: 消息
   - args: 消息参数"
  [level message & args]
  (let [prefix (case level
                 :info "[INFO]"
                 :warn "[WARN]"
                 :error "[ERROR]"
                 :debug "[DEBUG]"
                 "[LOG]")
        formatted (apply format message args)]
    (println prefix formatted)))

(defn debug-particle
  "调试粒子（显示位置/路径）

   参数:
   - level: ServerLevel
   - pos: 位置
   - opts: 可选参数
     - :particle - 粒子类型（默认 :flame）
     - :count - 粒子数量
     - :spread - 扩散范围"
  [^ServerLevel level pos & {:keys [particle count spread]
                             :or {particle :flame count 1 spread 0.0}}]
  (let [[x y z] (cond
                  (vector? pos) pos
                  (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)]
                  (instance? BlockPos pos) [(double (.getX ^BlockPos pos))
                                            (double (.getY ^BlockPos pos))
                                            (double (.getZ ^BlockPos pos))])]
    ;; 实际实现需要使用 Minecraft 的粒子系统
    (debug-log :debug "Debug particle at [%.2f %.2f %.2f]" x y z)))

(defn profile-fn
  "性能分析函数

   参数:
   - f: 函数
   - args: 函数参数

   返回：{:result 结果 :time-ms 耗时（毫秒）}"
  [f & args]
  (let [start (System/nanoTime)
        result (apply f args)
        end (System/nanoTime)
        time-ms (/ (- end start) 1000000.0)]
    {:result result
     :time-ms time-ms}))

(comment
  ;; 使用示例

  ;; ========== 文本 ==========

  ;; 1. 彩色文本
  (def red-text (colored-text "Danger!" :red))
  (def custom-color (colored-text "Custom" 0xFF69B4))

  ;; 2. 格式化文本
  (def fancy-text
    (formatted-text "Important!"
                    :color :gold
                    :bold true
                    :underlined true))

  ;; 3. 可点击文本
  (def link-text
    (clickable-text "Click here"
                    [:open-url "https://minecraft.net"]))

  ;; 4. 悬停文本
  (def hover-text
    (hoverable-text "Hover me"
                    "This is a tooltip"))

  ;; ========== 时间 ==========

  ;; 5. 时间转换
  (ticks->seconds 100)  ; => 5.0
  (seconds->ticks 10)   ; => 200

  ;; 6. 延迟任务
  (schedule-task 100
                 (fn [] (println "Executed after 5 seconds")))

  ;; ========== 数学 ==========

  ;; 7. 随机
  (random-int 1 10)
  (random-chance 0.3)
  (weighted-random {:diamond 1 :gold 5 :iron 20})

  ;; 8. 插值和限制
  (lerp 0 100 0.5)     ; => 50.0
  (clamp 150 0 100)    ; => 100

  ;; ========== 方块 ==========

  ;; 9. 区域填充
  (set-blocks-in-region level [0 60 0] [10 70 10] Blocks/STONE)

  ;; 10. 球体
  (fill-sphere level [100 64 200] 5 Blocks/GLASS :hollow? true)

  ;; 11. 圆柱
  (fill-cylinder level [100 64 200] 3 10 Blocks/STONE)

  ;; ========== 玩家 ==========

  ;; 12. 传送
  (teleport-player player [100 64 200] :yaw 90.0)

  ;; 13. 物品操作
  (give-item-to-player player (ItemStack. Items/DIAMOND 64))
  (has-item? player Items/DIAMOND 10)
  (remove-item-from-player player Items/DIAMOND 5)

  ;; ========== NBT ==========

  ;; 14. NBT 解析
  (def nbt (parse-nbt-string "{Health:20.0f,OnGround:1b}"))

  ;; 15. Map <-> NBT
  (def nbt-from-map (map->nbt {:health 20 :name "Steve"}))
  (def map-from-nbt (nbt->map nbt))

  ;; ========== 调试 ==========

  ;; 16. 性能分析
  (def result (profile-fn expensive-function arg1 arg2))
  (println "Took" (:time-ms result) "ms"))
