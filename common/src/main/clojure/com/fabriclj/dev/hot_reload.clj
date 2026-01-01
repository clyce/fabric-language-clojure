(ns com.fabriclj.dev.hot-reload
  "开发模式下的文件监控和自动热重载

   **功能**:
   - 监控 .clj 文件变化
   - 自动重新加载修改的命名空间
   - 清除 ClojureBridge 缓存
   - 可配置的监控路径和排除规则

   **使用示例**:
   ```clojure
   (require '[com.fabriclj.dev.hot-reload :as reload])
   
   ;; 启动文件监控
   (reload/start! {:watch-paths [\"example/src/main/clojure\"]
                   :on-reload (fn [ns] (println \"Reloaded:\" ns))})
   
   ;; 停止监控
   (reload/stop!)
   ```

   **注意**: 仅在开发模式下使用！"
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.nio.file FileSystems
                          Path
                          Paths
                          StandardWatchEventKinds
                          WatchEvent
                          WatchKey
                          WatchService)
           (java.io File)
           (java.util.concurrent TimeUnit)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 状态管理
;; ============================================================================

(defonce ^:private watch-state
  (atom {:running? false
         :watch-service nil
         :watch-thread nil
         :watched-dirs #{}
         :last-reload-times {}
         :config {}}))

;; ============================================================================
;; 工具函数
;; ============================================================================

(defn- file->namespace
  "将 .clj 文件路径转换为命名空间符号
   
   例如: 'com/example/core.clj' -> 'com.example.core'"
  [^File file]
  (when (.endsWith (.getName file) ".clj")
    (let [path (.getPath file)
          ;; 移除 .clj 后缀
          without-ext (subs path 0 (- (count path) 4))
          ;; 替换路径分隔符为点
          ns-str (-> without-ext
                     (str/replace #"[\\/]" ".")
                     (str/replace #"_" "-"))]
      ;; 提取命名空间部分（去掉路径前缀）
      (when-let [match (re-find #"([a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+)" ns-str)]
        (symbol (first match))))))

(defn- should-reload?
  "检查文件是否应该重新加载（避免频繁重载）"
  [ns-sym]
  (let [last-time (get-in @watch-state [:last-reload-times ns-sym] 0)
        current-time (System/currentTimeMillis)
        ;; 至少间隔 500ms
        min-interval 500]
    (> (- current-time last-time) min-interval)))

(defn- update-reload-time!
  "更新命名空间的重载时间"
  [ns-sym]
  (swap! watch-state assoc-in [:last-reload-times ns-sym] (System/currentTimeMillis)))

(defn- reload-namespace!
  "重新加载命名空间"
  [ns-sym]
  (when (and ns-sym (should-reload? ns-sym))
    (try
      (println (str "[HotReload] Reloading namespace: " ns-sym))
      
      ;; 重新加载命名空间
      (require ns-sym :reload)
      
      ;; 清除 ClojureBridge 缓存（如果使用了 Mixin）
      (try
        (when-let [bridge-class (Class/forName "com.fabriclj.ClojureBridge" false
                                               (.getContextClassLoader (Thread/currentThread)))]
          (when-let [clear-method (.getMethod bridge-class "clearCache" 
                                             (into-array Class [String]))]
            (.invoke clear-method nil (into-array Object [(str ns-sym)]))))
        (catch Exception _
          ;; ClojureBridge 可能不存在，忽略
          nil))
      
      ;; 调用用户回调
      (when-let [on-reload (get-in @watch-state [:config :on-reload])]
        (on-reload ns-sym))
      
      (update-reload-time! ns-sym)
      (println (str "[HotReload] Successfully reloaded: " ns-sym))
      
      (catch Exception e
        (println (str "[HotReload] Error reloading " ns-sym ": " (.getMessage e)))
        (.printStackTrace e)))
    nil))

(defn- get-clj-file
  "从 WatchEvent 获取 .clj 文件"
  [^Path dir ^WatchEvent event]
  (let [^Path filename (.context event)
        file-path (.resolve dir filename)
        file (.toFile file-path)]
    (when (and (.exists file) 
               (.isFile file)
               (.endsWith (.getName file) ".clj"))
      file)))

(defn- register-directory!
  "注册目录监控"
  [^WatchService watch-service ^Path dir]
  (try
    (.register dir 
               watch-service
               (into-array [StandardWatchEventKinds/ENTRY_MODIFY
                           StandardWatchEventKinds/ENTRY_CREATE]))
    (swap! watch-state update :watched-dirs conj dir)
    (catch Exception e
      (println (str "[HotReload] Failed to register directory: " dir " - " (.getMessage e))))))

(defn- register-directory-tree!
  "递归注册目录树"
  [^WatchService watch-service ^Path root-dir]
  (register-directory! watch-service root-dir)
  (doseq [^File file (file-seq (.toFile root-dir))
          :when (.isDirectory file)]
    (register-directory! watch-service (.toPath file))))

;; ============================================================================
;; 监控线程
;; ============================================================================

(defn- watch-loop
  "文件监控主循环"
  [^WatchService watch-service]
  (println "[HotReload] Watch loop started")
  (try
    (while (:running? @watch-state)
      (try
        ;; 等待文件变化事件（带超时避免阻塞）
        (when-let [^WatchKey key (.poll watch-service 1 TimeUnit/SECONDS)]
          (let [^Path dir (.watchable key)]
            (doseq [^WatchEvent event (.pollEvents key)]
              (when-let [file (get-clj-file dir event)]
                ;; 转换为命名空间并重载
                (when-let [ns-sym (file->namespace file)]
                  (reload-namespace! ns-sym))))
            ;; 重置 key
            (.reset key)))
        (catch Exception e
          (println (str "[HotReload] Error in watch loop: " (.getMessage e)))
          (.printStackTrace e))))
    (catch Exception e
      (println (str "[HotReload] Fatal error in watch loop: " (.getMessage e)))
      (.printStackTrace e))
    (finally
      (println "[HotReload] Watch loop stopped"))))

;; ============================================================================
;; 公共 API
;; ============================================================================

(defn start!
  "启动文件监控和自动重载
  
   选项:
   - :watch-paths - 要监控的路径列表（字符串或 File）
   - :on-reload - 重载后的回调函数 (fn [ns-sym] ...)
   - :recursive? - 是否递归监控子目录（默认 true）
   
   示例:
   ```clojure
   (start! {:watch-paths [\"example/src/main/clojure\"]
            :on-reload (fn [ns] (println \"Reloaded:\" ns))})
   ```"
  [& [opts]]
  (when (:running? @watch-state)
    (println "[HotReload] Already running!")
    (stop!))
  
  (let [config (merge {:watch-paths ["src"]
                       :recursive? true
                       :on-reload nil}
                      opts)
        watch-service (.. FileSystems getDefault newWatchService)]
    
    (println "[HotReload] Starting file watcher...")
    
    ;; 注册监控路径
    (doseq [path (:watch-paths config)]
      (let [dir-path (if (instance? Path path)
                       path
                       (.toPath (io/file path)))]
        (when (.exists (.toFile dir-path))
          (if (:recursive? config)
            (do
              (println (str "[HotReload] Watching directory tree: " dir-path))
              (register-directory-tree! watch-service dir-path))
            (do
              (println (str "[HotReload] Watching directory: " dir-path))
              (register-directory! watch-service dir-path)))
          (println (str "[HotReload] Registered " (count (:watched-dirs @watch-state)) " directories")))))
    
    ;; 启动监控线程
    (let [watch-thread (Thread. #(watch-loop watch-service))]
      (.setDaemon watch-thread true)
      (.setName watch-thread "HotReload-Watcher")
      (.start watch-thread)
      
      (swap! watch-state assoc
             :running? true
             :watch-service watch-service
             :watch-thread watch-thread
             :config config))
    
    (println "[HotReload] File watcher started successfully")
    (println "[HotReload] Monitored directories:" (count (:watched-dirs @watch-state)))
    true))

(defn stop!
  "停止文件监控"
  []
  (when (:running? @watch-state)
    (println "[HotReload] Stopping file watcher...")
    
    (swap! watch-state assoc :running? false)
    
    ;; 关闭 WatchService
    (when-let [^WatchService ws (:watch-service @watch-state)]
      (try
        (.close ws)
        (catch Exception e
          (println (str "[HotReload] Error closing watch service: " (.getMessage e))))))
    
    ;; 等待线程结束
    (when-let [^Thread t (:watch-thread @watch-state)]
      (try
        (.join t 5000)
        (catch InterruptedException _)))
    
    (swap! watch-state assoc
           :watch-service nil
           :watch-thread nil
           :watched-dirs #{})
    
    (println "[HotReload] File watcher stopped"))
  nil)

(defn restart!
  "重启文件监控"
  [& [opts]]
  (stop!)
  (Thread/sleep 500)
  (start! opts))

(defn running?
  "检查是否正在运行"
  []
  (:running? @watch-state))

(defn status
  "获取监控状态"
  []
  {:running? (:running? @watch-state)
   :watched-dirs (count (:watched-dirs @watch-state))
   :watched-paths (mapv str (:watched-dirs @watch-state))
   :config (:config @watch-state)})

;; ============================================================================
;; 使用示例
;; ============================================================================

(comment
  ;; 启动监控
  (start! {:watch-paths ["example/src/main/clojure"]})
  
  ;; 查看状态
  (status)
  
  ;; 停止监控
  (stop!)
  
  ;; 重启监控
  (restart! {:watch-paths ["example/src/main/clojure"
                          "common/src/main/clojure"]})
  
  ;; 带回调的监控
  (start! {:watch-paths ["example/src/main/clojure"]
           :on-reload (fn [ns]
                        (println "🔄 Reloaded:" ns)
                        ;; 可以在这里执行额外的操作
                        )})
  )
