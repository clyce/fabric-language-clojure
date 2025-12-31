(ns com.fabriclj.swiss-knife.common.reload-listeners
  "瑞士军刀 - 资源重载监听器模块

   封装资源包/数据包重载监听，用于响应 /reload 命令。"
  (:require [com.fabriclj.swiss-knife.common.core :as core])
  (:import [dev.architectury.event.events.common LifecycleEvent]
           [net.minecraft.server.packs.resources ResourceManager PreparableReloadListener]
           [net.minecraft.util.profiling ProfilerFiller]
           [java.util.concurrent CompletableFuture Executor]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 重载监听器
;; ============================================================================

(defn create-reload-listener
  "创建资源重载监听器

   参数:
   - prepare-fn: 准备阶段函数 (fn [^ResourceManager manager] -> prepared-data)
   - apply-fn: 应用阶段函数 (fn [prepared-data ^ResourceManager manager] ...)

   说明：
   - prepare-fn 在后台线程执行，用于加载资源
   - apply-fn 在主线程执行，用于应用加载的资源

   示例:
   ```clojure
   (create-reload-listener
     (fn [manager]
       ;; 加载配置文件
       (load-config-files manager))
     (fn [config manager]
       ;; 应用配置
       (apply-config! config)))
   ```"
  [prepare-fn apply-fn]
  (reify PreparableReloadListener
    (reload [_ preparation-barrier resource-manager background-executor main-executor]
      (CompletableFuture/supplyAsync
       (reify java.util.function.Supplier
         (get [_]
           (try
             (prepare-fn resource-manager)
             (catch Exception e
               (core/log-error "Failed to prepare reload:" (.getMessage e))
               nil))))
       background-executor)
      (.thenCompose preparation-barrier)
      (.thenAcceptAsync
       (reify java.util.function.Consumer
         (accept [_ prepared-data]
           (try
             (apply-fn prepared-data resource-manager)
             (catch Exception e
               (core/log-error "Failed to apply reload:" (.getMessage e))))))
       main-executor))))

(defn create-simple-reload-listener
  "创建简单的重载监听器（仅在主线程执行）

   参数:
   - reload-fn: 重载函数 (fn [^ResourceManager manager] ...)

   示例:
   ```clojure
   (create-simple-reload-listener
     (fn [manager]
       (println \"Resources reloaded!\")
       (reinit-my-system!)))
   ```"
  [reload-fn]
  (create-reload-listener
   (fn [_] nil)
   (fn [_ manager]
     (reload-fn manager))))

;; ============================================================================
;; 注册监听器
;; ============================================================================

(defonce ^:private reload-listeners (atom []))

(defn register-reload-listener!
  "注册资源重载监听器

   参数:
   - listener: PreparableReloadListener 实例
   - id: 监听器 ID（可选，用于管理）

   返回：监听器 ID

   注意：必须在服务器启动前调用（通常在 mod 初始化阶段）

   示例:
   ```clojure
   (register-reload-listener!
     (create-simple-reload-listener
       (fn [manager]
         (reload-my-data!))))
   ```"
  ([listener]
   (register-reload-listener! listener (java.util.UUID/randomUUID)))
  ([listener id]
   (swap! reload-listeners conj {:id id :listener listener})
   (.register (LifecycleEvent/SERVER_BEFORE_START)
              (reify java.util.function.Consumer
                (accept [_ server]
                  (.addPackFinder (.getResourceManager server) listener))))
   id))

(defn on-reload!
  "注册简单的重载回调（语法糖）

   参数:
   - callback: 重载时执行的函数 (fn [^ResourceManager manager] ...)

   示例:
   ```clojure
   (on-reload!
     (fn [manager]
       (println \"Reloading custom data...\")
       (reload-custom-data!)))
   ```"
  [callback]
  (register-reload-listener!
   (create-simple-reload-listener callback)))

;; ============================================================================
;; 资源读取辅助
;; ============================================================================

(defn list-resources
  "列出资源包中的资源

   参数:
   - manager: ResourceManager
   - namespace: 命名空间（如 \"mymod\"）
   - path: 路径前缀（如 \"configs\"）
   - extension: 文件扩展名（如 \".json\"）

   返回：ResourceLocation 列表

   示例:
   ```clojure
   (list-resources manager \"mymod\" \"configs\" \".json\")
   ```"
  [^ResourceManager manager namespace path extension]
  (let [results (atom [])]
    (doseq [^net.minecraft.resources.ResourceLocation loc
            (.listResources manager namespace
                            (reify java.util.function.Predicate
                              (test [_ p]
                                (let [^String path-str (.getPath p)]
                                  (and (.startsWith path-str path)
                                       (.endsWith path-str extension))))))]
      (swap! results conj loc))
    @results))

(defn read-resource
  "读取资源文件内容

   参数:
   - manager: ResourceManager
   - location: ResourceLocation

   返回：字符串内容或 nil

   示例:
   ```clojure
   (when-let [content (read-resource manager
                        (core/resource-location \"mymod\" \"config.json\"))]
     (process-config content))
   ```"
  [^ResourceManager manager ^net.minecraft.resources.ResourceLocation location]
  (try
    (when-let [resource (.getResource manager location)]
      (with-open [stream (.open resource)]
        (slurp stream)))
    (catch Exception e
      (core/log-warn "Failed to read resource" location ":" (.getMessage e))
      nil)))

(defn read-json-resource
  "读取 JSON 资源文件

   参数:
   - manager: ResourceManager
   - location: ResourceLocation

   返回：解析后的 Clojure 数据或 nil"
  [^ResourceManager manager ^net.minecraft.resources.ResourceLocation location]
  (when-let [content (read-resource manager location)]
    (try
      (cheshire.core/parse-string content true)
      (catch Exception e
        (core/log-warn "Failed to parse JSON" location ":" (.getMessage e))
        nil))))

(defn read-edn-resource
  "读取 EDN 资源文件

   参数:
   - manager: ResourceManager
   - location: ResourceLocation

   返回：解析后的 Clojure 数据或 nil"
  [^ResourceManager manager ^net.minecraft.resources.ResourceLocation location]
  (when-let [content (read-resource manager location)]
    (try
      (clojure.edn/read-string content)
      (catch Exception e
        (core/log-warn "Failed to parse EDN" location ":" (.getMessage e))
        nil))))

;; ============================================================================
;; 宏
;; ============================================================================

(defmacro on-reload-do
  "重载时执行代码（语法糖）

   示例:
   ```clojure
   (on-reload-do
     (println \"Reloading...\")
     (reset! my-config (load-config))
     (println \"Reload complete!\"))
   ```"
  [& body]
  `(on-reload!
    (fn [_#]
      ~@body)))

(comment
  ;; 使用示例

  ;; 1. 简单重载回调
  (on-reload!
   (fn [manager]
     (println "Resources reloaded!")
     (reset! my-config (load-config))))

  ;; 2. 使用宏
  (on-reload-do
   (println "Reloading...")
   (reload-my-system!))

  ;; 3. 复杂重载（两阶段）
  (register-reload-listener!
   (create-reload-listener
    ;; 准备阶段（后台线程）
    (fn [manager]
      (println "Loading configs...")
      (let [configs (list-resources manager "mymod" "configs" ".json")]
        (map #(read-json-resource manager %) configs)))
    ;; 应用阶段（主线程）
    (fn [configs manager]
      (println "Applying" (count configs) "configs")
      (doseq [config configs]
        (apply-config! config)))))

  ;; 4. 读取自定义资源
  (on-reload!
   (fn [manager]
     (when-let [config (read-edn-resource manager
                                          (core/resource-location "mymod" "data.edn"))]
       (reset! my-data config)))))
