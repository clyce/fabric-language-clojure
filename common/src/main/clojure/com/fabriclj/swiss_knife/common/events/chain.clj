(ns com.fabriclj.swiss-knife.common.events.chain
  "瑞士军刀 - 事件链模块

   提供事件组合、条件执行和链式事件处理。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.swiss-knife.common.events.core :as events]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 事件链核心
;; ============================================================================

(defprotocol EventChain
  "事件链协议

  (execute [this context] \"执行事件链\")
  (then [this next-chain] \"添加下一个事件链\")
  (on-success [this success-fn] \"成功时执行\")
  (on-failure [this failure-fn] \"失败时执行\")")

(defrecord SimpleEventChain [handler next-chain success-fn failure-fn]
  EventChain
  (execute [this context]
    (try
      (let [result (handler context)]
        (when success-fn
          (success-fn result context))
        (when (and next-chain result)
          (execute next-chain (assoc context :previous-result result)))
        result)
      (catch Exception e
        (when failure-fn
          (failure-fn e context))
        nil)))

  (then [this next]
    (SimpleEventChain. handler next success-fn failure-fn))

  (on-success [this fn]
    (SimpleEventChain. handler next-chain fn failure-fn))

  (on-failure [this fn]
    (SimpleEventChain. handler next-chain success-fn fn)))

(defn create-chain
  "创建事件链

   参数:
   - handler: 处理函数 (fn [context] -> result)

   返回：EventChain

   示例:
   ```clojure
   (-> (create-chain
         (fn [ctx]
           (println \"Step 1\")
           (assoc ctx :step1 true)))
       (then
         (create-chain
           (fn [ctx]
             (println \"Step 2\")
             (assoc ctx :step2 true))))
       (on-success
         (fn [result ctx]
           (println \"Success!\" result)))
       (execute {}))
   ```"
  [handler]
  (SimpleEventChain. handler nil nil nil))

;; ============================================================================
;; 条件事件链
;; ============================================================================

(defn when-condition
  "条件事件链

   参数:
   - predicate: 条件函数 (fn [context] -> boolean)
   - then-chain: 满足条件时的链
   - else-chain: 不满足条件时的链（可选）

   示例:
   ```clojure
   (when-condition
     (fn [ctx] (> (:mana ctx) 50))
     (create-chain cast-spell)
     (create-chain show-error))
   ```"
  ([predicate then-chain]
   (when-condition predicate then-chain nil))
  ([predicate then-chain else-chain]
   (create-chain
    (fn [context]
      (if (predicate context)
        (execute then-chain context)
        (when else-chain
          (execute else-chain context)))))))

(defn repeat-chain
  "重复执行事件链

   参数:
   - times: 重复次数
   - chain: 事件链

   示例:
   ```clojure
   (repeat-chain 5
     (create-chain
       (fn [ctx]
         (println \"Repeat\" (:count ctx))
         (update ctx :count inc))))
   ```"
  [times chain]
  (create-chain
   (fn [context]
     (loop [n 0
            ctx context]
       (if (< n times)
         (recur (inc n) (execute chain (assoc ctx :iteration n)))
         ctx)))))

(defn parallel-chains
  "并行执行多个事件链

   参数:
   - chains: 事件链列表

   返回：所有结果的列表

   示例:
   ```clojure
   (parallel-chains
     [(create-chain task1)
      (create-chain task2)
      (create-chain task3)])
   ```"
  [chains]
  (create-chain
   (fn [context]
     (let [futures (mapv #(future (execute % context)) chains)]
       (mapv deref futures)))))

;; ============================================================================
;; 事件过滤和转换
;; ============================================================================

(defn filter-chain
  "过滤事件链结果

   参数:
   - predicate: 过滤函数 (fn [result] -> boolean)
   - chain: 事件链

   示例:
   ```clojure
   (filter-chain
     (fn [result] (not (nil? result)))
     some-chain)
   ```"
  [predicate chain]
  (create-chain
   (fn [context]
     (let [result (execute chain context)]
       (when (predicate result)
         result)))))

(defn transform-chain
  "转换事件链结果

   参数:
   - transform-fn: 转换函数 (fn [result] -> new-result)
   - chain: 事件链

   示例:
   ```clojure
   (transform-chain
     (fn [result] (* result 2))
     some-chain)
   ```"
  [transform-fn chain]
  (create-chain
   (fn [context]
     (let [result (execute chain context)]
       (transform-fn result)))))

;; ============================================================================
;; 延迟执行
;; ============================================================================

(defn delay-chain
  "延迟执行事件链

   参数:
   - delay-ticks: 延迟时间（tick）

   示例:
   ```clojure
   (delay-chain 20  ; 延迟1秒
     (create-chain some-action))
   ```"
  [delay-ticks chain]
  (create-chain
   (fn [context]
     (future
       (Thread/sleep (* delay-ticks 50))  ; 1 tick = 50ms
       (execute chain context)))))

(defn schedule-chain
  "计划执行事件链（带间隔重复）

   参数:
   - interval-ticks: 间隔时间
   - times: 重复次数（nil 表示无限）

   示例:
   ```clojure
   (schedule-chain 20 10  ; 每秒执行一次，共10次
     (create-chain periodic-task))
   ```"
  [interval-ticks times chain]
  (create-chain
   (fn [context]
     (future
       (loop [n 0]
         (when (or (nil? times) (< n times))
           (execute chain (assoc context :iteration n))
           (Thread/sleep (* interval-ticks 50))
           (recur (inc n))))))))

;; ============================================================================
;; 事件聚合
;; ============================================================================

(defn collect-chain
  "收集事件链结果

   参数:
   - chains: 事件链列表
   - collector-fn: 收集函数 (fn [results] -> final-result)

   示例:
   ```clojure
   (collect-chain
     [chain1 chain2 chain3]
     (fn [results] (reduce + results)))  ; 求和
   ```"
  [chains collector-fn]
  (create-chain
   (fn [context]
     (let [results (mapv #(execute % context) chains)]
       (collector-fn results)))))

;; ============================================================================
;; 错误处理链
;; ============================================================================

(defn retry-chain
  "重试事件链

   参数:
   - max-attempts: 最大尝试次数
   - chain: 事件链

   示例:
   ```clojure
   (retry-chain 3
     (create-chain risky-operation))
   ```"
  [max-attempts chain]
  (create-chain
   (fn [context]
     (loop [attempt 1]
       (try
         (execute chain (assoc context :attempt attempt))
         (catch Exception e
           (if (< attempt max-attempts)
             (do
               (println "Retry attempt" attempt)
               (recur (inc attempt)))
             (throw e))))))))

(defn fallback-chain
  "失败回退链

   参数:
   - primary-chain: 主事件链
   - fallback-chain: 回退事件链

   示例:
   ```clojure
   (fallback-chain
     (create-chain try-remote-api)
     (create-chain use-local-cache))
   ```"
  [primary-chain fallback-chain]
  (create-chain
   (fn [context]
     (try
       (execute primary-chain context)
       (catch Exception e
         (println "Primary failed, using fallback")
         (execute fallback-chain context))))))

;; ============================================================================
;; DSL 宏
;; ============================================================================

(defmacro chain->
  "链式执行宏

   示例:
   ```clojure
   (chain-> initial-context
     (step1)
     (step2)
     (step3))
   ```"
  [initial-context & steps]
  (reduce
   (fn [acc step]
     `(let [result# ~acc]
        (when result#
          (~(first step) result# ~@(rest step)))))
   initial-context
   steps))

(defmacro defchain
  "定义命名事件链

   示例:
   ```clojure
   (defchain magic-spell-chain
     (-> (create-chain check-mana)
         (then (create-chain cast-spell))
         (then (create-chain apply-effect))
         (on-success log-success)
         (on-failure log-failure)))
   ```"
  [name & body]
  `(def ~name
     ~@body))

(comment
  ;; 使用示例

  ;; 1. 简单链式执行
  (-> (create-chain
       (fn [ctx]
         (println "Step 1")
         (assoc ctx :step1 true)))
      (then
       (create-chain
        (fn [ctx]
          (println "Step 2")
          (assoc ctx :step2 true))))
      (on-success
       (fn [result ctx]
         (println "Complete!" result)))
      (execute {}))

  ;; 2. 条件执行
  (when-condition
   (fn [ctx] (> (:mana ctx) 50))
   (create-chain cast-spell)
   (create-chain show-insufficient-mana))

  ;; 3. 并行执行
  (parallel-chains
   [(create-chain load-data-1)
    (create-chain load-data-2)
    (create-chain load-data-3)])

  ;; 4. 延迟执行
  (delay-chain 20
               (create-chain spawn-particles))

  ;; 5. 重试机制
  (retry-chain 3
               (create-chain connect-to-server))

  ;; 6. 使用宏定义
  (defchain player-level-up-chain
    (-> (create-chain check-xp)
        (then (create-chain increase-level))
        (then (create-chain grant-rewards))
        (on-success notify-player))))
