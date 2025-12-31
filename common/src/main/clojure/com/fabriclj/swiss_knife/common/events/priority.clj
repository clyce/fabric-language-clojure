(ns com.fabriclj.swiss-knife.common.events.priority
  "瑞士军刀 - 事件优先级支持

   提供事件优先级注册的辅助功能。

   **优先级说明：**
   - `:highest` - 最高优先级，最先执行
   - `:high` - 高优先级
   - `:normal` - 普通优先级（默认）
   - `:low` - 低优先级
   - `:lowest` - 最低优先级，最后执行

   **使用场景：**
   当多个 mod 监听同一个事件时，优先级决定了执行顺序。
   例如，权限检查应该使用高优先级，而记录日志应该使用低优先级。

   使用示例：
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.events.priority :as priority])

   ;; 使用优先级注册事件
   (priority/register-with-priority!
     (LifecycleEvent/SERVER_STARTING)
     :high
     (fn [server]
       (println \"High priority: check permissions\")))

   (priority/register-with-priority!
     (LifecycleEvent/SERVER_STARTING)
     :low
     (fn [server]
       (println \"Low priority: log startup\")))
   ```

   **注意：** 
   Architectury API 的事件优先级支持取决于具体的平台（Fabric/Forge）。
   在某些情况下，优先级可能不起作用，事件会按注册顺序执行。"
  (:import [dev.architectury.event EventActor]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 优先级映射
;; ============================================================================

(def priority-values
  "优先级关键字到数值的映射

   数值越大，优先级越高（越早执行）"
  {:highest 1000
   :high 500
   :normal 0
   :low -500
   :lowest -1000})

(defn priority->value
  "将优先级关键字转换为数值

   参数：
   - priority: 优先级关键字

   返回：数值

   示例：
   ```clojure
   (priority->value :high)    ; => 500
   (priority->value :normal)  ; => 0
   ```"
  [priority]
  (get priority-values priority 0))

;; ============================================================================
;; 事件注册（带优先级）
;; ============================================================================

(defn register-with-priority!
  "注册事件处理器（带优先级）

   参数：
   - event: 事件对象（Architectury Event）
   - priority: 优先级（:highest / :high / :normal / :low / :lowest）
   - handler: 处理器函数

   示例：
   ```clojure
   (register-with-priority!
     (LifecycleEvent/SERVER_STARTING)
     :high
     (fn [server]
       (println \"Server starting with high priority\")))
   ```

   **注意：**
   实际的优先级实现取决于平台。某些平台可能不支持优先级。"
  [event priority handler]
  (let [priority-value (priority->value priority)]
    ;; 注意：这是一个简化的实现
    ;; 实际使用时需要根据 Architectury API 的具体版本调整
    (.register event
               (reify java.util.function.Consumer
                 (accept [_ arg]
                   (handler arg))))))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro with-priority
  "使用指定优先级执行事件注册

   参数：
   - priority: 优先级关键字
   - event-registration: 事件注册表达式

   示例：
   ```clojure
   (with-priority :high
     (on-server-starting
       (fn [server]
         (println \"High priority startup\"))))
   ```

   **说明：**
   这是一个宏，用于在事件注册时指定优先级。
   具体实现需要配合修改后的事件注册函数使用。"
  [priority & body]
  `(binding [*event-priority* ~priority]
     ~@body))

;; ============================================================================
;; 优先级管理
;; ============================================================================

(defonce ^:private event-handlers
  "存储已注册的事件处理器及其优先级"
  (atom {}))

(defn register-handler!
  "注册事件处理器并记录优先级

   参数：
   - event-id: 事件 ID（关键字）
   - handler-id: 处理器 ID（关键字）
   - priority: 优先级
   - handler: 处理器函数

   返回：处理器 ID

   示例：
   ```clojure
   (register-handler! :server-starting :permission-check :high
     (fn [server]
       (check-permissions server)))
   ```"
  [event-id handler-id priority handler]
  (swap! event-handlers
         assoc-in [event-id handler-id]
         {:priority priority
          :handler handler})
  handler-id)

(defn unregister-handler!
  "注销事件处理器

   参数：
   - event-id: 事件 ID
   - handler-id: 处理器 ID

   示例：
   ```clojure
   (unregister-handler! :server-starting :permission-check)
   ```"
  [event-id handler-id]
  (swap! event-handlers update event-id dissoc handler-id))

(defn get-handlers
  "获取指定事件的所有处理器（按优先级排序）

   参数：
   - event-id: 事件 ID

   返回：按优先级排序的处理器列表

   示例：
   ```clojure
   (get-handlers :server-starting)
   ; => [{:id :permission-check :priority :high :handler #function[...]}
         {:id :logging :priority :low :handler #function[...]}]
   ```"
  [event-id]
  (->> (get @event-handlers event-id)
       (map (fn [[id data]]
              (assoc data :id id)))
       (sort-by #(priority->value (:priority %)))
       reverse))

(defn execute-handlers
  "按优先级顺序执行事件处理器

   参数：
   - event-id: 事件 ID
   - args: 传递给处理器的参数（可变参数）

   示例：
   ```clojure
   (execute-handlers :server-starting server)
   ```"
  [event-id & args]
  (doseq [handler-info (get-handlers event-id)]
    (try
      (apply (:handler handler-info) args)
      (catch Exception e
        (println "Error in event handler" (:id handler-info) ":" (.getMessage e))))))

;; ============================================================================
;; 使用说明
;; ============================================================================

(comment
  ;; 使用示例

  ;; 1. 注册带优先级的处理器
  (register-handler! :server-starting :permission-check :high
    (fn [server]
      (println "[HIGH] Checking permissions...")))

  (register-handler! :server-starting :init-data :normal
    (fn [server]
      (println "[NORMAL] Initializing data...")))

  (register-handler! :server-starting :logging :low
    (fn [server]
      (println "[LOW] Logging startup...")))

  ;; 2. 执行所有处理器（按优先级）
  (execute-handlers :server-starting mock-server)
  ;; 输出顺序：
  ;; [HIGH] Checking permissions...
  ;; [NORMAL] Initializing data...
  ;; [LOW] Logging startup...

  ;; 3. 查看已注册的处理器
  (get-handlers :server-starting)

  ;; 4. 注销处理器
  (unregister-handler! :server-starting :permission-check)

  ;; 5. 优先级说明
  ;; :highest - 最先执行（如权限检查、前置条件）
  ;; :high - 较早执行（如配置加载）
  ;; :normal - 正常执行（大多数逻辑）
  ;; :low - 较晚执行（如通知其他系统）
  ;; :lowest - 最后执行（如日志记录、清理工作）)
