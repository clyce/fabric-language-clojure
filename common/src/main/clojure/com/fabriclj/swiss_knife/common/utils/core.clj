(ns com.fabriclj.swiss-knife.common.utils.core
  "瑞士军刀 - 通用工具集

   **模块定位**：真正通用的辅助函数

   **重要提示**：
   许多原本在此模块的函数已移至专门的模块以提高组织性：
   - 文本和翻译 → `com.fabriclj.swiss-knife.common.utils.text`
   - 时间和计时 → `com.fabriclj.swiss-knife.common.utils.time`
   - 数学和随机 → `com.fabriclj.swiss-knife.common.utils.math`
   - NBT 工具 → `com.fabriclj.swiss-knife.common.utils.nbt`
   - 区域操作 → `com.fabriclj.swiss-knife.common.world.regions`
   - 玩家工具 → `com.fabriclj.swiss-knife.common.game-objects.players`

   **本模块保留**：
   - 空间查询辅助函数（基于距离的查找、排序）
   - 调试工具（日志、性能分析）

   **使用示例**：
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.utils.core :as utils])

   ;; 空间查询
   (utils/find-nearest player-pos entities)
   (utils/find-in-radius [0 64 0] entities 10.0)
   (utils/sort-by-distance player-pos entities)

   ;; 调试
   (utils/debug-log :warn \"Something went wrong!\")
   (utils/profile-fn expensive-function args)
   ```"
  (:require [com.fabriclj.swiss-knife.common.utils.math :as math])
  (:import [net.minecraft.world.entity Entity]
           [net.minecraft.world.phys Vec3]
           [net.minecraft.core BlockPos]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 空间查询辅助函数
;; ============================================================================

(defn find-nearest
  "查找最近的元素

   参数:
   - pos: 参考位置，支持多种格式：
     - [x y z] - 向量
     - Vec3 对象
     - BlockPos 对象
   - entities: 实体集合

   返回：最近的实体或 nil

   示例:
   ```clojure
   (def nearest-mob (find-nearest player-pos hostile-mobs))
   (when nearest-mob
     (println \"Nearest mob is\" (.getName nearest-mob)))
   ```"
  [pos entities]
  (let [[x y z] (cond
                  (vector? pos) pos
                  (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)]
                  (instance? BlockPos pos) [(double (.getX ^BlockPos pos))
                                            (double (.getY ^BlockPos pos))
                                            (double (.getZ ^BlockPos pos))])]
    (when (seq entities)
      (apply min-key
             (fn [entity]
               (let [epos (.position ^Entity entity)]
                 (math/distance-3d x y z (.x epos) (.y epos) (.z epos))))
             entities))))

(defn find-in-radius
  "查找半径内的元素

   参数:
   - pos: 中心位置（支持多种格式）
   - entities: 实体集合
   - radius: 半径（单位：方块）

   返回：半径内的实体列表

   示例:
   ```clojure
   ;; 查找10格范围内的所有玩家
   (def nearby-players
     (find-in-radius spawn-pos all-players 10.0))

   ;; 查找5格范围内的怪物
   (def nearby-mobs
     (filter is-hostile?
       (find-in-radius player-pos level-entities 5.0)))
   ```"
  [pos entities radius]
  (let [[x y z] (cond
                  (vector? pos) pos
                  (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)]
                  (instance? BlockPos pos) [(double (.getX ^BlockPos pos))
                                            (double (.getY ^BlockPos pos))
                                            (double (.getZ ^BlockPos pos))])]
    (filter (fn [entity]
              (let [epos (.position ^Entity entity)
                    dist (math/distance-3d x y z (.x epos) (.y epos) (.z epos))]
                (< dist radius)))
            entities)))

(defn sort-by-distance
  "按距离排序

   参数:
   - pos: 参考位置（支持多种格式）
   - entities: 实体集合

   返回：按距离从近到远排序的实体列表

   示例:
   ```clojure
   ;; 找出最近的3个玩家
   (def closest-3-players
     (->> all-players
          (sort-by-distance spawn-pos)
          (take 3)))

   ;; 为每个玩家显示距离
   (doseq [[idx player] (map-indexed vector
                          (sort-by-distance [0 64 0] players))]
     (println (str (inc idx) \". \" (.getName player))))
   ```"
  [pos entities]
  (let [[x y z] (cond
                  (vector? pos) pos
                  (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)]
                  (instance? BlockPos pos) [(double (.getX ^BlockPos pos))
                                            (double (.getY ^BlockPos pos))
                                            (double (.getZ ^BlockPos pos))])]
    (sort-by (fn [entity]
               (let [epos (.position ^Entity entity)]
                 (math/distance-3d x y z (.x epos) (.y epos) (.z epos))))
             entities)))

;; ============================================================================
;; 调试工具
;; ============================================================================

(defn debug-log
  "调试日志（带级别）

   参数:
   - level: 日志级别 (:info/:warn/:error/:debug)
   - message: 消息字符串（支持 format 占位符）
   - args: 消息参数（可选）

   示例:
   ```clojure
   (debug-log :info \"Server started\")
   (debug-log :warn \"Player %s tried invalid action\" player-name)
   (debug-log :error \"Failed to load config: %s\" error-msg)
   (debug-log :debug \"Position: [%.2f, %.2f, %.2f]\" x y z)
   ```"
  [level message & args]
  (let [prefix (case level
                 :info "[INFO]"
                 :warn "[WARN]"
                 :error "[ERROR]"
                 :debug "[DEBUG]"
                 "[LOG]")
        formatted (if (seq args)
                    (apply format message args)
                    message)]
    (println prefix formatted)))

(defn profile-fn
  "性能分析函数

   测量函数执行时间，用于性能调试。

   参数:
   - label: 标签（用于日志输出）
   - f: 要分析的函数
   - args: 函数参数

   返回：{:result 函数返回值 :time-ms 耗时（毫秒） :label 标签}

   示例:
   ```clojure
   ;; 简单测量
   (def result (profile-fn \"expensive-calc\" expensive-function arg1 arg2))
   (println \"Result:\" (:result result))
   (println \"Time:\" (:time-ms result) \"ms\")

   ;; 比较两种实现
   (def impl-a (profile-fn \"Implementation A\" calculate-a data))
   (def impl-b (profile-fn \"Implementation B\" calculate-b data))
   (println \"A:\" (:time-ms impl-a) \"ms\")
   (println \"B:\" (:time-ms impl-b) \"ms\")

   ;; 使用宏形式（更简洁）
   (let [{:keys [result time-ms]} (profile-fn \"complex-task\" #(do-complex-task))]
     (when (> time-ms 100)
       (debug-log :warn \"Task took %d ms (threshold: 100ms)\" time-ms)))
   ```"
  [label f & args]
  (let [start (System/nanoTime)
        result (apply f args)
        end (System/nanoTime)
        time-ms (/ (- end start) 1000000.0)]
    {:result result
     :time-ms time-ms
     :label label}))

(defmacro profile
  "性能分析宏（更简洁的语法）

   自动测量代码块的执行时间。

   参数:
   - label: 标签字符串
   - body: 要分析的代码

   返回：{:result 代码返回值 :time-ms 耗时}

   示例:
   ```clojure
   ;; 测量代码块
   (profile \"database-query\"
     (query-database connection sql))

   ;; 带条件的性能警告
   (let [{:keys [result time-ms]} (profile \"api-call\"
                                     (call-external-api data))]
     (when (> time-ms 500)
       (log-warn \"API call exceeded 500ms:\", time-ms))
     result)
   ```"
  [label & body]
  `(profile-fn ~label (fn [] ~@body)))

(comment
  ;; 使用示例

  ;; 1. 空间查询
  (def entities [...])  ; 实体列表
  (def player-pos [100 64 200])

  ;; 找最近的实体
  (def nearest (find-nearest player-pos entities))

  ;; 找半径内的实体
  (def nearby (find-in-radius player-pos entities 10.0))

  ;; 按距离排序
  (def sorted (sort-by-distance player-pos entities))

  ;; 2. 调试日志
  (debug-log :info "Application started")
  (debug-log :warn "Config value %s is deprecated" old-key)
  (debug-log :error "Failed to connect: %s" error-message)

  ;; 3. 性能分析
  (def result (profile-fn "heavy-computation" expensive-fn arg1 arg2))
  (println "Took" (:time-ms result) "ms")

  ;; 使用宏
  (profile "database-operation"
    (query-db "SELECT * FROM ...")))
