(ns com.fabriclj.swiss-knife.common.world.effects
  "瑞士军刀 - 世界效果模块

   提供天气、时间、世界边界、游戏规则等世界状态控制功能。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.server MinecraftServer)
           (net.minecraft.server.level ServerLevel)
           (net.minecraft.world.level Level GameRules GameRules$Key)
           (net.minecraft.world.level.border WorldBorder)
           (net.minecraft.world.phys Vec3)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 天气控制
;; ============================================================================

(defn set-weather!
  "设置天气

   参数:
   - level: ServerLevel
   - weather: 天气类型
     - :clear - 晴天
     - :rain - 雨天
     - :thunder - 雷雨
   - duration: 持续时间（tick，可选，默认 6000）

   示例:
   ```clojure
   (set-weather! level :clear)
   (set-weather! level :thunder 12000)  ; 10分钟雷雨
   ```"
  ([^ServerLevel level weather]
   (set-weather! level weather 6000))
  ([^ServerLevel level weather duration]
   (case weather
     :clear (do
              (.setWeatherParameters level duration 0 false false))
     :rain (do
             (.setWeatherParameters level 0 duration true false))
     :thunder (do
                (.setWeatherParameters level 0 duration true true))
     (throw (IllegalArgumentException.
            (str "Invalid weather type: " weather ". Use :clear, :rain, or :thunder"))))))

(defn set-rain!
  "设置是否下雨

   参数:
   - level: ServerLevel
   - raining?: 是否下雨
   - duration: 持续时间（可选）

   示例:
   ```clojure
   (set-rain! level true)
   (set-rain! level false 6000)
   ```"
  ([^ServerLevel level raining?]
   (if raining?
     (set-weather! level :rain)
     (set-weather! level :clear)))
  ([^ServerLevel level raining? duration]
   (if raining?
     (set-weather! level :rain duration)
     (set-weather! level :clear duration))))

(defn set-thunder!
  "设置是否打雷

   参数:
   - level: ServerLevel
   - thundering?: 是否打雷
   - duration: 持续时间（可选）

   示例:
   ```clojure
   (set-thunder! level true)
   (set-thunder! level false 6000)
   ```"
  ([^ServerLevel level thundering?]
   (if thundering?
     (set-weather! level :thunder)
     (set-weather! level :clear)))
  ([^ServerLevel level thundering? duration]
   (if thundering?
     (set-weather! level :thunder duration)
     (set-weather! level :clear duration))))

(defn get-rain-level
  "获取雨量等级（0.0-1.0）"
  [^Level level]
  (.getRainLevel level 1.0))

(defn get-thunder-level
  "获取雷电强度（0.0-1.0）"
  [^Level level]
  (.getThunderLevel level 1.0))

(defn is-raining?
  "检查是否下雨"
  [^Level level]
  (.isRaining level))

(defn is-thundering?
  "检查是否打雷"
  [^Level level]
  (.isThundering level))

;; ============================================================================
;; 时间控制
;; ============================================================================

(defn get-day-time
  "获取游戏时间（0-24000）

   时间对应:
   - 0 (6:00) - 日出
   - 6000 (12:00) - 正午
   - 12000 (18:00) - 日落
   - 18000 (0:00) - 午夜"
  [^ServerLevel level]
  (.getDayTime level))

(defn set-day-time!
  "设置游戏时间

   参数:
   - level: ServerLevel
   - time: 时间值或关键字
     - 数字: 0-24000
     - :sunrise / :dawn - 日出（0）
     - :noon / :day - 正午（6000）
     - :sunset / :dusk - 日落（12000）
     - :midnight / :night - 午夜（18000）

   示例:
   ```clojure
   (set-day-time! level :noon)
   (set-day-time! level 18000)
   ```"
  [^ServerLevel level time]
  (let [time-value (if (keyword? time)
                    (case time
                      (:sunrise :dawn) 0
                      (:noon :day) 6000
                      (:sunset :dusk) 12000
                      (:midnight :night) 18000
                      (throw (IllegalArgumentException.
                             (str "Invalid time keyword: " time))))
                    time)]
    (.setDayTime level (long time-value))))

(defn add-time!
  "增加游戏时间

   参数:
   - level: ServerLevel
   - ticks: 增加的 tick 数

   示例:
   ```clojure
   (add-time! level 6000)  ; 前进 5 分钟
   ```"
  [^ServerLevel level ticks]
  (set-day-time! level (+ (get-day-time level) ticks)))

(defn get-game-time
  "获取总游戏时间（从世界创建开始）"
  [^ServerLevel level]
  (.getGameTime level))

(defn is-day?
  "检查是否为白天"
  [^Level level]
  (.isDay level))

(defn is-night?
  "检查是否为夜晚"
  [^Level level]
  (.isNight level))

;; ============================================================================
;; 世界边界
;; ============================================================================

(defn get-world-border
  "获取世界边界对象"
  ^WorldBorder [^Level level]
  (.getWorldBorder level))

(defn set-world-border!
  "设置世界边界

   参数:
   - level: ServerLevel
   - center: 中心坐标 [x z] 或 Vec3
   - size: 边界大小（方形边长）

   示例:
   ```clojure
   (set-world-border! level [0 0] 1000)
   ```"
  [^ServerLevel level center size]
  (let [^WorldBorder border (get-world-border level)
        [cx cz] (cond
                 (vector? center) center
                 (instance? Vec3 center) [(.x ^Vec3 center) (.z ^Vec3 center)]
                 :else (throw (IllegalArgumentException.
                              "center must be [x z] or Vec3")))]
    (.setCenter border cx cz)
    (.setSize border (double size))))

(defn animate-world-border!
  "动画调整世界边界大小

   参数:
   - level: ServerLevel
   - from-size: 起始大小
   - to-size: 目标大小
   - time-ms: 动画时间（毫秒）

   示例:
   ```clojure
   ;; 5秒内从 1000 缩小到 100
   (animate-world-border! level 1000 100 5000)
   ```"
  [^ServerLevel level from-size to-size time-ms]
  (let [^WorldBorder border (get-world-border level)]
    (.setSize border (double from-size))
    (.lerpSizeBetween border (double from-size) (double to-size) (long time-ms))))

(defn set-world-border-damage!
  "设置世界边界伤害

   参数:
   - level: ServerLevel
   - damage-per-block: 每方块伤害
   - buffer: 安全缓冲距离

   示例:
   ```clojure
   (set-world-border-damage! level 0.2 5.0)
   ```"
  [^ServerLevel level damage-per-block buffer]
  (let [^WorldBorder border (get-world-border level)]
    (.setDamagePerBlock border (double damage-per-block))
    (.setDamageSafeZone border (double buffer))))

(defn set-world-border-warning!
  "设置世界边界警告

   参数:
   - level: ServerLevel
   - distance: 警告距离
   - time: 警告时间（秒）

   示例:
   ```clojure
   (set-world-border-warning! level 10 60)
   ```"
  [^ServerLevel level distance time]
  (let [^WorldBorder border (get-world-border level)]
    (.setWarningBlocks border (int distance))
    (.setWarningTime border (int time))))

;; ============================================================================
;; 游戏规则
;; ============================================================================

(defn set-game-rule!
  "设置游戏规则

   参数:
   - level: ServerLevel
   - rule: 游戏规则名称（字符串或关键字）
   - value: 规则值（布尔值或整数）

   常用游戏规则:
   - :do-daylight-cycle - 日夜循环
   - :do-weather-cycle - 天气循环
   - :keep-inventory - 死亡不掉落
   - :mob-griefing - 生物破坏方块
   - :do-mob-spawning - 生物生成
   - :natural-regeneration - 自然回血
   - :announce-advancements - 成就公告
   - :show-death-messages - 死亡消息
   - :command-block-output - 命令方块输出
   - :max-command-chain-length - 命令链最大长度
   - :random-tick-speed - 随机刻速度
   - :spawn-radius - 出生点半径

   示例:
   ```clojure
   ;; 关闭日夜循环
   (set-game-rule! level :do-daylight-cycle false)

   ;; 设置随机刻速度
   (set-game-rule! level :random-tick-speed 10)

   ;; 使用字符串
   (set-game-rule! level \"keepInventory\" true)
   ```"
  [^ServerLevel level rule value]
  (let [^GameRules game-rules (.getGameRules level)
        rule-name (if (keyword? rule)
                   (name rule)
                   rule)
        ;; 转换 kebab-case 到 camelCase
        rule-name-camel (clojure.string/replace rule-name #"-(\w)"
                                                #(clojure.string/upper-case (second %)))
        ^GameRules$Key rule-key (try
                                  (.getField GameRules rule-name-camel)
                                  (catch Exception _
                                    ;; 尝试原始名称
                                    (.getField GameRules rule-name)))]
    (cond
      (boolean? value)
      (.getRule game-rules rule-key)

      (number? value)
      (.getRule game-rules rule-key)

      :else
      (throw (IllegalArgumentException.
             (str "Invalid game rule value type: " (type value)))))))

(defn get-game-rule
  "获取游戏规则值

   示例:
   ```clojure
   (get-game-rule level :do-daylight-cycle)
   ```"
  [^ServerLevel level rule]
  (let [^GameRules game-rules (.getGameRules level)
        rule-name (if (keyword? rule) (name rule) rule)]
    ;; 返回游戏规则对象（需要进一步处理）
    (.getRule game-rules rule-name)))

;; ============================================================================
;; 难度控制
;; ============================================================================

(defn set-difficulty!
  "设置游戏难度

   参数:
   - level: ServerLevel
   - difficulty: 难度
     - :peaceful / 0 - 和平
     - :easy / 1 - 简单
     - :normal / 2 - 普通
     - :hard / 3 - 困难

   示例:
   ```clojure
   (set-difficulty! level :hard)
   (set-difficulty! level 3)
   ```"
  [^ServerLevel level difficulty]
  (let [diff-level (if (keyword? difficulty)
                    (case difficulty
                      :peaceful 0
                      :easy 1
                      :normal 2
                      :hard 3
                      (throw (IllegalArgumentException.
                             (str "Invalid difficulty: " difficulty))))
                    difficulty)
        diff-obj (net.minecraft.world.Difficulty/byId diff-level)]
    (.setDifficulty (.getServer level) diff-obj true)))

(defn get-difficulty
  "获取当前难度

   返回: 难度关键字 (:peaceful, :easy, :normal, :hard)"
  [^ServerLevel level]
  (let [diff (.getDifficulty level)]
    (case (.getId diff)
      0 :peaceful
      1 :easy
      2 :normal
      3 :hard)))

;; ============================================================================
;; 组合效果
;; ============================================================================

(defn create-dramatic-storm!
  "创建戏剧性风暴（雷雨 + 夜晚）

   参数:
   - level: ServerLevel
   - duration: 持续时间（tick，默认 12000）

   示例:
   ```clojure
   (create-dramatic-storm! level)
   (create-dramatic-storm! level 24000)  ; 20分钟
   ```"
  ([^ServerLevel level]
   (create-dramatic-storm! level 12000))
  ([^ServerLevel level duration]
   (set-weather! level :thunder duration)
   (set-day-time! level :midnight)))

(defn create-peaceful-day!
  "创建和平白天（晴天 + 正午 + 和平难度）

   示例:
   ```clojure
   (create-peaceful-day! level)
   ```"
  [^ServerLevel level]
  (set-weather! level :clear 24000)
  (set-day-time! level :noon)
  (set-difficulty! level :peaceful))

(comment
  ;; 使用示例

  ;; 1. 天气控制
  (set-weather! level :clear)
  (set-weather! level :thunder 12000)
  (set-rain! level true)

  ;; 2. 时间控制
  (set-day-time! level :noon)
  (set-day-time! level 18000)
  (add-time! level 6000)

  ;; 3. 世界边界
  (set-world-border! level [0 0] 1000)
  (animate-world-border! level 1000 100 10000)

  ;; 4. 游戏规则
  (set-game-rule! level :do-daylight-cycle false)
  (set-game-rule! level :keep-inventory true)

  ;; 5. 难度
  (set-difficulty! level :hard)

  ;; 6. 组合效果
  (create-dramatic-storm! level)
  (create-peaceful-day! level))
