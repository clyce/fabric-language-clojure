(ns com.fabriclj.swiss-knife.common.profiler
  "性能分析工具

   提供全面的性能测量和分析功能：
   - 代码执行时间分析
   - 内存使用监控
   - TPS（每秒Tick数）监控
   - 实体/方块/区块性能统计
   - 性能热点识别
   - 性能报告生成"
  (:require [com.fabriclj.swiss-knife.common.core :as core])
  (:import [java.lang.management ManagementFactory MemoryMXBean]
           [java.util.concurrent ConcurrentHashMap]
           [net.minecraft.server MinecraftServer]
           [net.minecraft.world.level Level]
           [net.minecraft.world.entity Entity]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 性能数据存储
;; ============================================================================

(def ^:private performance-data (atom {}))
(def ^:private timing-stack (atom []))
(def ^ConcurrentHashMap timing-results (ConcurrentHashMap.))

;; ============================================================================
;; 时间测量
;; ============================================================================

(defmacro profile
  "性能分析宏：测量代码块执行时间

   参数:
   - name: 分析标识（字符串或关键字）
   - body: 要分析的代码

   返回：代码块的返回值

   示例:
   ```clojure
   (profile :my-function
     (expensive-operation)
     (another-operation))

   ;; 嵌套分析
   (profile :outer
     (some-work)
     (profile :inner
       (more-work)))
   ```"
  [name & body]
  `(let [start-time# (System/nanoTime)
         result# (do ~@body)
         end-time# (System/nanoTime)
         duration# (- end-time# start-time#)]
     (record-timing! ~name duration#)
     result#))

(defn profile-fn
  "分析函数执行时间

   参数:
   - name: 分析标识
   - f: 要分析的函数
   - args: 函数参数

   返回：函数返回值

   示例:
   ```clojure
   (profile-fn :complex-calculation
     complex-function
     arg1 arg2 arg3)
   ```"
  [name f & args]
  (let [start-time (System/nanoTime)
        result (apply f args)
        end-time (System/nanoTime)
        duration (- end-time start-time)]
    (record-timing! name duration)
    result))

(defn record-timing!
  "记录时间测量结果

   参数:
   - name: 标识
   - duration-ns: 持续时间（纳秒）

   内部使用"
  [name duration-ns]
  (let [name-key (if (keyword? name) name (keyword (str name)))
        current (.get timing-results (str name-key))
        new-data (if current
                   {:count (inc (:count current))
                    :total-ns (+ (:total-ns current) duration-ns)
                    :min-ns (min (:min-ns current) duration-ns)
                    :max-ns (max (:max-ns current) duration-ns)
                    :avg-ns (/ (+ (:total-ns current) duration-ns)
                               (inc (:count current)))}
                   {:count 1
                    :total-ns duration-ns
                    :min-ns duration-ns
                    :max-ns duration-ns
                    :avg-ns duration-ns})]
    (.put timing-results (str name-key) new-data)))

(defn get-timing-stats
  "获取时间统计数据

   参数:
   - name: 分析标识（可选，如果不提供则返回所有）

   返回：统计数据映射

   示例:
   ```clojure
   (get-timing-stats :my-function)
   ;; => {:count 100
   ;;     :total-ns 1000000000
   ;;     :min-ns 8000000
   ;;     :max-ns 15000000
   ;;     :avg-ns 10000000
   ;;     :total-ms 1000.0
   ;;     :avg-ms 10.0}
   ```"
  ([]
   (into {}
         (for [[k v] timing-results]
           [(keyword k)
            (assoc v
                   :total-ms (/ (:total-ns v) 1000000.0)
                   :avg-ms (/ (:avg-ns v) 1000000.0)
                   :min-ms (/ (:min-ns v) 1000000.0)
                   :max-ms (/ (:max-ns v) 1000000.0))])))
  ([name]
   (when-let [data (.get timing-results (str (if (keyword? name) name (keyword name))))]
     (assoc data
            :total-ms (/ (:total-ns data) 1000000.0)
            :avg-ms (/ (:avg-ns data) 1000000.0)
            :min-ms (/ (:min-ns data) 1000000.0)
            :max-ms (/ (:max-ns data) 1000000.0)))))

(defn clear-timing-stats!
  "清除所有时间统计数据

   示例:
   ```clojure
   (clear-timing-stats!)
   ```"
  []
  (.clear timing-results)
  (core/log-info \"Timing statistics cleared\"))

;; ============================================================================
;; 内存监控
;; ============================================================================

(defn get-memory-usage
  "获取当前内存使用情况

   返回：内存使用映射
   {:heap-used-mb float
    :heap-max-mb float
    :heap-usage-percent float
    :non-heap-used-mb float}

   示例:
   ```clojure
   (let [mem (get-memory-usage)]
     (println \"Heap used:\" (:heap-used-mb mem) \"MB\")
     (println \"Heap usage:\" (:heap-usage-percent mem) \"%\"))
   ```"
  []
  (let [^MemoryMXBean memory-bean (ManagementFactory/getMemoryMXBean)
        heap-usage (.getHeapMemoryUsage memory-bean)
        non-heap-usage (.getNonHeapMemoryUsage memory-bean)
        heap-used (/ (.getUsed heap-usage) 1048576.0)
        heap-max (/ (.getMax heap-usage) 1048576.0)
        non-heap-used (/ (.getUsed non-heap-usage) 1048576.0)]
    {:heap-used-mb heap-used
     :heap-max-mb heap-max
     :heap-usage-percent (* 100.0 (/ heap-used heap-max))
     :non-heap-used-mb non-heap-used}))

(defmacro profile-memory
  "分析代码块的内存使用

   参数:
   - name: 分析标识
   - body: 要分析的代码

   返回：{:result 代码返回值
          :memory-before 执行前内存
          :memory-after 执行后内存
          :memory-delta 内存变化}

   示例:
   ```clojure
   (let [{:keys [result memory-delta]}
         (profile-memory :load-world
           (load-large-world))]
     (println \"Memory used:\" (:heap-used-mb memory-delta) \"MB\"))
   ```"
  [name & body]
  `(let [memory-before# (get-memory-usage)
         result# (do ~@body)
         memory-after# (get-memory-usage)
         delta# {:heap-used-mb (- (:heap-used-mb memory-after#)
                                  (:heap-used-mb memory-before#))}]
     {:result result#
      :memory-before memory-before#
      :memory-after memory-after#
      :memory-delta delta#}))

;; ============================================================================
;; TPS 监控
;; ============================================================================

(def ^:private tps-history (atom []))
(def ^:private max-tps-history 100)

(defn record-tps!
  "记录 TPS（每秒Tick数）

   参数:
   - tps: 当前 TPS 值

   内部使用，通常在服务器 tick 事件中调用"
  [tps]
  (swap! tps-history
         (fn [history]
           (let [new-history (conj history {:tps tps
                                            :timestamp (System/currentTimeMillis)})]
             (if (> (count new-history) max-tps-history)
               (vec (drop (- (count new-history) max-tps-history) new-history))
               new-history)))))

(defn get-tps-stats
  "获取 TPS 统计

   返回：{:current float
          :average float
          :min float
          :max float
          :history [{:tps float :timestamp long}]}

   示例:
   ```clojure
   (let [tps-stats (get-tps-stats)]
     (println \"Current TPS:\" (:current tps-stats))
     (println \"Average TPS:\" (:average tps-stats)))
   ```"
  []
  (let [history @tps-history
        tps-values (map :tps history)]
    (when (seq tps-values)
      {:current (last tps-values)
       :average (/ (reduce + tps-values) (count tps-values))
       :min (apply min tps-values)
       :max (apply max tps-values)
       :history history})))

(defn get-server-tps
  "获取服务器当前 TPS（通过平均 tick 时间计算）

   参数:
   - server: MinecraftServer

   返回：TPS 值（理想值为 20.0）"
  [^MinecraftServer server]
  (let [tick-times (.tickTimes server)
        avg-tick-time (/ (reduce + tick-times) (count tick-times))
        tps (min 20.0 (/ 1000000000.0 avg-tick-time))]
    tps))

;; ============================================================================
;; 实体/方块性能统计
;; ============================================================================

(def ^:private entity-stats (atom {}))
(def ^:private block-stats (atom {}))

(defn record-entity-tick-time!
  "记录实体 tick 时间

   参数:
   - entity-type: 实体类型
   - duration-ns: 持续时间（纳秒）"
  [entity-type duration-ns]
  (swap! entity-stats update entity-type
         (fn [current]
           (let [cnt (inc (get current :count 0))
                 total (+ (get current :total-ns 0) duration-ns)]
             {:count cnt
              :total-ns total
              :avg-ns (/ total cnt)}))))

(defn get-entity-performance
  "获取实体性能统计

   返回：实体类型 -> 统计数据的映射

   示例:
   ```clojure
   (let [stats (get-entity-performance)]
     (doseq [[entity-type data] (take 10 (sort-by (comp :avg-ns second) > stats))]
       (println entity-type \":\" (:avg-ns data) \"ns avg\")))
   ```"
  []
  (into {}
        (for [[k v] @entity-stats]
          [k (assoc v :avg-ms (/ (:avg-ns v) 1000000.0))])))

(defn get-top-lag-entities
  "获取导致延迟最严重的实体类型

   参数:
   - n: 返回前 N 个（默认 10）

   返回：[{:type entity-type :avg-ms float :count int}]"
  [& [n]]
  (let [limit (or n 10)
        stats (get-entity-performance)]
    (take limit
          (sort-by :avg-ms >
                   (for [[entity-type data] stats]
                     {:type entity-type
                      :avg-ms (:avg-ms data)
                      :count (:count data)})))))

;; ============================================================================
;; 性能报告生成
;; ============================================================================

(defn generate-performance-report
  "生成完整的性能报告

   参数:
   - opts: 选项
     - :include-timing? - 包含时间统计（默认 true）
     - :include-memory? - 包含内存统计（默认 true）
     - :include-tps? - 包含 TPS 统计（默认 true）
     - :include-entities? - 包含实体统计（默认 true）
     - :top-n - 显示前 N 个热点（默认 10）

   返回：性能报告映射

   示例:
   ```clojure
   (let [report (generate-performance-report :top-n 5)]
     (println \"=== Performance Report ===\")
     (println \"Memory:\" (:memory report))
     (println \"TPS:\" (:tps report))
     (println \"Top timing hotspots:\" (:timing-hotspots report)))
   ```"
  [& {:keys [include-timing? include-memory? include-tps? include-entities? top-n]
      :or {include-timing? true
           include-memory? true
           include-tps? true
           include-entities? true
           top-n 10}}]
  (cond-> {}
    include-memory?
    (assoc :memory (get-memory-usage))

    include-tps?
    (assoc :tps (get-tps-stats))

    include-timing?
    (assoc :timing (get-timing-stats)
           :timing-hotspots (take top-n
                                  (sort-by (comp :avg-ms second) >
                                           (get-timing-stats))))

    include-entities?
    (assoc :entity-lag (get-top-lag-entities top-n))))

(defn print-performance-report
  "打印性能报告到控制台

   参数:
   - report: 性能报告（由 generate-performance-report 生成）

   示例:
   ```clojure
   (-> (generate-performance-report :top-n 5)
       print-performance-report)
   ```"
  [report]
  (println \"\\n=== Performance Report ===\")

  (when-let [memory (:memory report)]
    (println \"\\n[Memory Usage]\")
    (println (format \"  Heap: %.2f MB / %.2f MB (%.1f%%)\"
                     (:heap-used-mb memory)
                     (:heap-max-mb memory)
                     (:heap-usage-percent memory)))
    (println (format \"  Non-Heap: %.2f MB\" (:non-heap-used-mb memory))))

  (when-let [tps (:tps report)]
    (println \"\\n[TPS Statistics]\")
    (println (format \"  Current: %.2f\" (:current tps)))
    (println (format \"  Average: %.2f\" (:average tps)))
    (println (format \"  Min: %.2f  Max: %.2f\" (:min tps) (:max tps))))

  (when-let [hotspots (:timing-hotspots report)]
    (println \"\\n[Top Timing Hotspots]\")
    (doseq [[name stats] hotspots]
      (println (format \"  %s: avg %.2fms (count: %d, total: %.2fms)\"
                       name
                       (:avg-ms stats)
                       (:count stats)
                       (:total-ms stats)))))

  (when-let [entity-lag (:entity-lag report)]
    (println \"\\n[Top Lag-Causing Entities]\")
    (doseq [{:keys [type avg-ms count]} entity-lag]
      (println (format \"  %s: avg %.4fms (count: %d)\"
                       type avg-ms count))))

  (println \"\\n===========================\\n\"))

;; ============================================================================
;; 便捷函数
;; ============================================================================

(defn start-monitoring!
  "启动性能监控

   开始收集性能数据"
  []
  (core/log-info \"Performance monitoring started\"))

(defn stop-monitoring!
  "停止性能监控并生成报告

   返回：性能报告"
  []
  (let [report (generate-performance-report)]
    (print-performance-report report)
    (core/log-info \"Performance monitoring stopped\")
    report))

(defn reset-all-stats!
  "重置所有性能统计数据"
  []
  (clear-timing-stats!)
  (reset! tps-history [])
  (reset! entity-stats {})
  (reset! block-stats {})
  (core/log-info \"All performance statistics reset\"))

(comment
  ;; 使用示例

  ;; ========== 基础时间分析 ==========

  ;; 1. 使用 profile 宏
  (profile :my-expensive-function
    (Thread/sleep 100)
    (reduce + (range 1000000)))

  ;; 2. 嵌套分析
  (profile :outer-operation
    (profile :inner-operation-1
      (expensive-calc-1))
    (profile :inner-operation-2
      (expensive-calc-2)))

  ;; 3. 函数分析
  (profile-fn :calculation
    my-function
    arg1 arg2 arg3)

  ;; ========== 查看统计数据 ==========

  ;; 4. 获取特定函数的统计
  (get-timing-stats :my-expensive-function)
  ;; => {:count 5 :avg-ms 102.5 :min-ms 100.2 ...}

  ;; 5. 获取所有统计
  (get-timing-stats)

  ;; ========== 内存分析 ==========

  ;; 6. 获取当前内存使用
  (let [mem (get-memory-usage)]
    (println \"Heap:\" (:heap-used-mb mem) \"/\" (:heap-max-mb mem) \"MB\"))

  ;; 7. 分析内存使用
  (let [{:keys [result memory-delta]}
        (profile-memory :load-world
          (load-world-data))]
    (println \"Memory delta:\" (:heap-used-mb memory-delta) \"MB\"))

  ;; ========== TPS 监控 ==========

  ;; 8. 记录 TPS（在 tick 事件中）
  (events/on-server-tick
    (fn [server]
      (record-tps! (get-server-tps server))))

  ;; 9. 获取 TPS 统计
  (let [tps (get-tps-stats)]
    (println \"Average TPS:\" (:average tps)))

  ;; ========== 性能报告 ==========

  ;; 10. 生成并打印报告
  (-> (generate-performance-report :top-n 10)
      print-performance-report)

  ;; 11. 启动/停止监控
  (start-monitoring!)
  ;; ... 运行游戏 ...
  (stop-monitoring!)  ; 自动生成报告

  ;; ========== 实体性能 ==========

  ;; 12. 获取导致延迟的实体
  (let [lag-entities (get-top-lag-entities 5)]
    (doseq [{:keys [type avg-ms]} lag-entities]
      (println type \"causes\" avg-ms \"ms lag per tick\"))))
