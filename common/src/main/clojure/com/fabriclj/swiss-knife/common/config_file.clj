(ns com.fabriclj.swiss-knife.common.config-file
  "EDN 配置文件支持

   提供运行时读写 EDN 配置文件的功能，支持：
   - 自动创建配置目录
   - 热重载配置
   - 配置验证
   - 默认值合并

   配置文件位置：
   - 服务端：./config/mod-id/config.edn
   - 客户端：./config/mod-id/client-config.edn"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.fabriclj.swiss-knife.common.core :as core])
  (:import [java.io File PushbackReader]
           [java.nio.file Files Paths StandardOpenOption]
           [java.nio.file.attribute FileAttribute]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 配置路径管理
;; ============================================================================

(defn- get-config-dir
  "获取配置目录

   返回：config/ 目录的 File 对象"
  []
  (let [config-dir (io/file "config")]
    (when-not (.exists config-dir)
      (.mkdirs config-dir))
    config-dir))

(defn get-mod-config-dir
  "获取 Mod 配置目录

   参数:
   - mod-id: Mod ID

   返回：config/mod-id/ 目录的 File 对象

   示例:
   ```clojure
   (get-mod-config-dir \"mymod\")
   ; => #object[java.io.File \"config/mymod/\"]
   ```"
  [mod-id]
  (let [mod-dir (io/file (get-config-dir) mod-id)]
    (when-not (.exists mod-dir)
      (.mkdirs mod-dir))
    mod-dir))

(defn get-config-file-path
  "获取配置文件路径

   参数:
   - mod-id: Mod ID
   - file-name: 文件名（默认 \"config.edn\"）
   - client?: 是否客户端配置（默认根据环境判断）

   返回：File 对象

   示例:
   ```clojure
   (get-config-file-path \"mymod\")
   (get-config-file-path \"mymod\" \"features.edn\")
   (get-config-file-path \"mymod\" \"client.edn\" :client? true)
   ```"
  [mod-id & {:keys [file-name client?]
             :or {file-name "config.edn"
                  client? (core/client-side?)}}]
  (let [mod-dir (get-mod-config-dir mod-id)
        final-name (if (and client? (not (str/includes? file-name "client")))
                     (str "client-" file-name)
                     file-name)]
    (io/file mod-dir final-name)))

;; ============================================================================
;; EDN 读写
;; ============================================================================

(defn read-edn-file
  "读取 EDN 文件

   参数:
   - file-path: 文件路径（File 或字符串）

   返回：解析后的 Clojure 数据，或 nil（文件不存在）

   示例:
   ```clojure
   (read-edn-file \"config/mymod/config.edn\")
   (read-edn-file (io/file \"config/mymod/config.edn\"))
   ```"
  [file-path]
  (let [file (if (instance? File file-path)
               file-path
               (io/file file-path))]
    (when (.exists file)
      (try
        (with-open [reader (PushbackReader. (io/reader file))]
          (edn/read reader))
        (catch Exception e
          (core/log-error "Failed to read EDN file:" (.getPath file))
          (core/log-error (.getMessage e))
          nil)))))

(defn write-edn-file!
  "写入 EDN 文件

   参数:
   - file-path: 文件路径
   - data: 要写入的数据
   - opts: 可选参数
     - :pretty? - 是否美化输出（默认 true）
     - :create-dirs? - 是否创建目录（默认 true）

   返回：是否成功

   示例:
   ```clojure
   (write-edn-file! \"config/mymod/config.edn\"
     {:feature-enabled true
      :spawn-rate 0.5})
   ```"
  [file-path data & {:keys [pretty? create-dirs?]
                     :or {pretty? true create-dirs? true}}]
  (let [file (if (instance? File file-path)
               file-path
               (io/file file-path))]
    (try
      ;; 创建父目录
      (when create-dirs?
        (when-let [parent (.getParentFile file)]
          (.mkdirs parent)))

      ;; 写入文件
      (with-open [writer (io/writer file)]
        (if pretty?
          (binding [*print-length* nil
                    *print-level* nil]
            (.write writer (with-out-str (clojure.pprint/pprint data))))
          (.write writer (pr-str data))))

      (core/log-info "Config file saved:" (.getPath file))
      true

      (catch Exception e
        (core/log-error "Failed to write EDN file:" (.getPath file))
        (core/log-error (.getMessage e))
        false))))

;; ============================================================================
;; 配置管理
;; ============================================================================

(defonce ^:private config-registry (atom {}))

(defn register-config!
  "注册配置

   参数:
   - mod-id: Mod ID
   - default-config: 默认配置
   - opts: 可选参数
     - :file-name - 配置文件名（默认 \"config.edn\"）
     - :auto-save? - 是否自动保存（默认 false）
     - :validator - 验证函数 (fn [config] -> boolean)

   返回：配置 atom

   示例:
   ```clojure
   (register-config! \"mymod\"
     {:feature-enabled true
      :spawn-rate 0.5
      :max-count 100}
     :validator (fn [cfg]
                  (and (number? (:spawn-rate cfg))
                       (pos? (:spawn-rate cfg)))))
   ```"
  [mod-id default-config & {:keys [file-name auto-save? validator]
                            :or {file-name "config.edn"
                                 auto-save? false}}]
  (if-let [existing (get @config-registry mod-id)]
    (do
      (core/log-warn "Config already registered for" mod-id)
      (:config-atom existing))
    (let [file-path (get-config-file-path mod-id :file-name file-name)
          ;; 尝试从文件加载
          loaded-config (read-edn-file file-path)
          ;; 合并默认值和加载的配置
          merged-config (merge default-config loaded-config)
          ;; 验证配置
          valid-config (if (and validator (not (validator merged-config)))
                         (do
                           (core/log-warn "Invalid config loaded, using defaults for" mod-id)
                           default-config)
                         merged-config)
          config-atom (atom valid-config)]

      ;; 如果配置文件不存在或配置无效，保存默认配置
      (when (or (not loaded-config) (not= merged-config valid-config))
        (write-edn-file! file-path valid-config))

      ;; 注册配置
      (swap! config-registry assoc mod-id
             {:config-atom config-atom
              :file-path file-path
              :default-config default-config
              :auto-save? auto-save?
              :validator validator})

      (core/log-info "Config registered for" mod-id)
      config-atom)))

(defn get-config-atom
  "获取配置 atom

   参数:
   - mod-id: Mod ID

   返回：配置 atom 或 nil"
  [mod-id]
  (when-let [entry (get @config-registry mod-id)]
    (:config-atom entry)))

(defn get-config-value
  "获取配置值

   参数:
   - mod-id: Mod ID
   - key-path: 配置键路径
   - default: 默认值（可选）

   示例:
   ```clojure
   (get-config-value \"mymod\" :feature-enabled)
   (get-config-value \"mymod\" [:spawning :rate] 0.5)
   ```"
  ([mod-id key-path]
   (get-config-value mod-id key-path nil))
  ([mod-id key-path default]
   (when-let [config-atom (get-config-atom mod-id)]
     (if (vector? key-path)
       (get-in @config-atom key-path default)
       (get @config-atom key-path default)))))

(defn set-config-value!
  "设置配置值

   参数:
   - mod-id: Mod ID
   - key-path: 配置键路径
   - value: 新值
   - opts: 可选参数
     - :save? - 是否立即保存到文件（默认根据 auto-save? 决定）

   示例:
   ```clojure
   (set-config-value! \"mymod\" :feature-enabled false)
   (set-config-value! \"mymod\" [:spawning :rate] 1.0 :save? true)
   ```"
  [mod-id key-path value & {:keys [save?]}]
  (when-let [entry (get @config-registry mod-id)]
    (let [{:keys [config-atom file-path auto-save? validator]} entry]
      ;; 更新配置
      (if (vector? key-path)
        (swap! config-atom assoc-in key-path value)
        (swap! config-atom assoc key-path value))

      ;; 验证配置
      (when (and validator (not (validator @config-atom)))
        (core/log-warn "Invalid config after update for" mod-id))

      ;; 保存配置
      (when (or save? auto-save?)
        (save-config! mod-id)))))

(defn reload-config!
  "重新加载配置

   参数:
   - mod-id: Mod ID

   返回：是否成功

   示例:
   ```clojure
   (reload-config! \"mymod\")
   ```"
  [mod-id]
  (when-let [entry (get @config-registry mod-id)]
    (let [{:keys [config-atom file-path default-config validator]} entry
          loaded-config (read-edn-file file-path)]
      (if loaded-config
        (let [merged-config (merge default-config loaded-config)]
          (if (and validator (not (validator merged-config)))
            (do
              (core/log-error "Invalid config file for" mod-id)
              false)
            (do
              (reset! config-atom merged-config)
              (core/log-info "Config reloaded for" mod-id)
              true)))
        (do
          (core/log-warn "Config file not found for" mod-id)
          false)))))

(defn save-config!
  "保存配置到文件

   参数:
   - mod-id: Mod ID

   返回：是否成功

   示例:
   ```clojure
   (save-config! \"mymod\")
   ```"
  [mod-id]
  (when-let [entry (get @config-registry mod-id)]
    (let [{:keys [config-atom file-path]} entry]
      (write-edn-file! file-path @config-atom))))

(defn reset-config!
  "重置配置为默认值

   参数:
   - mod-id: Mod ID
   - save?: 是否保存到文件（默认 true）

   示例:
   ```clojure
   (reset-config! \"mymod\")
   ```"
  [mod-id & {:keys [save?] :or {save? true}}]
  (when-let [entry (get @config-registry mod-id)]
    (let [{:keys [config-atom default-config file-path]} entry]
      (reset! config-atom default-config)
      (when save?
        (write-edn-file! file-path default-config))
      (core/log-info "Config reset to defaults for" mod-id))))

;; ============================================================================
;; 配置监听
;; ============================================================================

(defn watch-config!
  "监听配置变化

   参数:
   - mod-id: Mod ID
   - key: 监听器键（唯一标识）
   - callback: 回调函数 (fn [old-config new-config] -> void)

   示例:
   ```clojure
   (watch-config! \"mymod\" :my-watcher
     (fn [old new]
       (when (not= (:feature-enabled old)
                   (:feature-enabled new))
         (println \"Feature toggled!\"))))
   ```"
  [mod-id watch-key callback]
  (when-let [config-atom (get-config-atom mod-id)]
    (add-watch config-atom watch-key
               (fn [_ _ old-state new-state]
                 (callback old-state new-state)))))

(defn unwatch-config!
  "取消监听配置

   参数:
   - mod-id: Mod ID
   - key: 监听器键"
  [mod-id watch-key]
  (when-let [config-atom (get-config-atom mod-id)]
    (remove-watch config-atom watch-key)))

(comment
  ;; 使用示例

  ;; ========== 基础使用 ==========

  ;; 1. 注册配置
  (register-config! "mymod"
                    {:feature-enabled true
                     :spawn-rate 0.5
                     :max-count 100
                     :spawning {:enabled true
                                :frequency 10}})

  ;; 2. 读取配置
  (get-config-value "mymod" :feature-enabled)  ; => true
  (get-config-value "mymod" [:spawning :frequency])  ; => 10

  ;; 3. 修改配置
  (set-config-value! "mymod" :spawn-rate 0.8)
  (set-config-value! "mymod" [:spawning :enabled] false)

  ;; 4. 保存配置
  (save-config! "mymod")

  ;; 5. 重新加载配置
  (reload-config! "mymod")

  ;; 6. 重置为默认值
  (reset-config! "mymod")

  ;; ========== 高级使用 ==========

  ;; 7. 带验证的配置
  (register-config! "mymod"
                    {:spawn-rate 0.5
                     :max-count 100}
                    :validator (fn [cfg]
                                 (and (number? (:spawn-rate cfg))
                                      (pos? (:spawn-rate cfg))
                                      (>= (:max-count cfg) 1))))

  ;; 8. 自动保存配置
  (register-config! "mymod"
                    {:auto-save-enabled true}
                    :auto-save? true)

  ;; 9. 监听配置变化
  (watch-config! "mymod" :feature-watcher
                 (fn [old new]
                   (when (not= (:feature-enabled old)
                               (:feature-enabled new))
                     (if (:feature-enabled new)
                       (println "Feature enabled!")
                       (println "Feature disabled!")))))

  ;; 10. 多个配置文件
  (register-config! "mymod" {...} :file-name "features.edn")
  (register-config! "mymod" {...} :file-name "spawning.edn")

  ;; ========== 客户端配置 ==========

  ;; 11. 客户端专属配置
  (when (core/client-side?)
    (register-config! "mymod"
                      {:render-distance 128
                       :particle-quality :high}
                      :file-name "client-config.edn"))

  ;; ========== 直接文件操作 ==========

  ;; 12. 读取任意 EDN 文件
  (def custom-data (read-edn-file "config/mymod/custom.edn"))

  ;; 13. 写入任意 EDN 文件
  (write-edn-file! "config/mymod/custom.edn"
                   {:custom-data [1 2 3]
                    :metadata {:version "1.0"}}))
