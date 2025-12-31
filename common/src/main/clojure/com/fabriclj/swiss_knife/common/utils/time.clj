(ns com.fabriclj.swiss-knife.common.utils.time
  "时间和计时工具

   提供 Minecraft Tick 系统的时间转换和任务调度功能:
   - Tick 与时间单位转换
   - 延迟任务调度
   - 任务管理

   注意: Minecraft 的 Tick 速率是 20 ticks/秒"
  (:import (java.util UUID)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 时间转换
;; ============================================================================

(defn ticks->seconds
  "Tick 转秒

   Minecraft 默认 20 ticks = 1 second

   示例:
   ```clojure
   (ticks->seconds 20)  ; => 1.0
   (ticks->seconds 100) ; => 5.0
   ```"
  [ticks]
  (/ ticks 20.0))

(defn seconds->ticks
  "秒转 Tick

   示例:
   ```clojure
   (seconds->ticks 1)  ; => 20
   (seconds->ticks 5)  ; => 100
   ```"
  [seconds]
  (int (* seconds 20)))

(defn ticks->minutes
  "Tick 转分钟

   示例:
   ```clojure
   (ticks->minutes 1200) ; => 1.0
   (ticks->minutes 2400) ; => 2.0
   ```"
  [ticks]
  (/ ticks 1200.0))

(defn minutes->ticks
  "分钟转 Tick

   示例:
   ```clojure
   (minutes->ticks 1) ; => 1200
   (minutes->ticks 5) ; => 6000
   ```"
  [minutes]
  (int (* minutes 1200)))

;; ============================================================================
;; 任务调度
;; ============================================================================

(defonce ^:private scheduled-tasks
  "全局任务队列"
  (atom []))

(defn schedule-task
  "延迟执行任务( 需要在游戏循环中调用 tick-scheduled-tasks)

   参数:
   - delay-ticks: 延迟 Tick 数
   - task: 任务函数

   返回: 任务 ID( 用于取消)

   注意:
   - 此函数仅调度任务，不自动执行
   - 必须在游戏循环( 如 on-server-tick) 中调用 tick-scheduled-tasks
   - 延迟基于实际时间而非游戏 Tick( 避免暂停/lag 影响)

   示例:
   ```clojure
   ;; 延迟 100 ticks( 5秒) 执行
   (def task-id (schedule-task 100 #(println \"Task executed!\")))

   ;; 在游戏循环中执行
   (events/on-server-tick
     (fn [server]
       (tick-scheduled-tasks)))
   ```"
  [delay-ticks task]
  (let [task-id (str (UUID/randomUUID))
        execute-at (+ (System/currentTimeMillis) (* delay-ticks 50))]
    (swap! scheduled-tasks conj {:id task-id
                                  :execute-at execute-at
                                  :task task})
    task-id))

(defn cancel-task
  "取消计划任务

   参数:
   - task-id: 由 schedule-task 返回的任务 ID

   示例:
   ```clojure
   (def task-id (schedule-task 100 #(println \"Hello\")))
   (cancel-task task-id) ; 任务不会执行
   ```"
  [task-id]
  (swap! scheduled-tasks
         (fn [tasks]
           (remove #(= (:id %) task-id) tasks))))

(defn tick-scheduled-tasks
  "执行到期的计划任务

   应在游戏循环中调用( 如 on-server-tick 事件)

   功能:
   - 检查所有计划任务
   - 执行到期的任务
   - 捕获任务执行中的异常
   - 从队列中移除已执行的任务

   示例:
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.events.core :as events])

   ;; 在服务器 Tick 中执行调度任务
   (events/on-server-tick
     (fn [server]
       (tick-scheduled-tasks)))

   ;; 现在可以使用 schedule-task
   (schedule-task 20 #(println \"1 second later\"))
   (schedule-task 100 #(println \"5 seconds later\"))
   ```"
  []
  (let [now (System/currentTimeMillis)
        {ready true not-ready false} (group-by #(<= (:execute-at %) now) @scheduled-tasks)]
    (doseq [{:keys [task]} ready]
      (try
        (task)
        (catch Exception e
          (println "Error executing scheduled task:" (.getMessage e)))))
    (reset! scheduled-tasks (or not-ready []))))

(defn clear-all-tasks
  "清除所有计划任务

   用于清理或重置

   示例:
   ```clojure
   (clear-all-tasks)
   ```"
  []
  (reset! scheduled-tasks []))

(defn get-pending-tasks-count
  "获取待执行任务数量

   返回: 待执行任务数

   示例:
   ```clojure
   (get-pending-tasks-count) ; => 3
   ```"
  []
  (count @scheduled-tasks))

(comment
  ;; 使用示例

  ;; ========== 时间转换 ==========

  ;; 1. Tick 转换
  (ticks->seconds 20)   ; => 1.0
  (ticks->seconds 100)  ; => 5.0
  (seconds->ticks 1)    ; => 20
  (seconds->ticks 5)    ; => 100

  (ticks->minutes 1200) ; => 1.0
  (minutes->ticks 1)    ; => 1200

  ;; ========== 任务调度 ==========

  ;; 2. 设置游戏循环( 必须先执行)
  (require '[com.fabriclj.swiss-knife.common.events.core :as events])

  (events/on-server-tick
    (fn [server]
      (tick-scheduled-tasks)))

  ;; 3. 调度任务
  (schedule-task 20 #(println "1 second later"))
  (schedule-task 100 #(println "5 seconds later"))
  (schedule-task 1200 #(println "1 minute later"))

  ;; 4. 取消任务
  (def my-task (schedule-task 200 #(println "This won't run")))
  (cancel-task my-task)

  ;; 5. 查询任务数
  (get-pending-tasks-count) ; => 2

  ;; 6. 清除所有任务
  (clear-all-tasks)

  ;; ========== 实际应用 ==========

  ;; 7. 延迟给玩家物品
  (defn give-reward-later [player item delay-seconds]
    (schedule-task
      (seconds->ticks delay-seconds)
      #(.give (.getInventory player) item)))

  ;; 8. 定时提醒
  (defn remind-every-minute [message times]
    (doseq [i (range times)]
      (schedule-task
        (minutes->ticks (inc i))
        #(println message))))

  ;; 9. 倒计时
  (defn countdown [start-seconds on-complete]
    (doseq [i (range start-seconds 0 -1)]
      (schedule-task
        (seconds->ticks (- start-seconds i))
        #(println "Countdown:" i)))
    (schedule-task
      (seconds->ticks start-seconds)
      on-complete)))
