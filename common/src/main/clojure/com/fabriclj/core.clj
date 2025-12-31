(ns com.fabriclj.core
  "fabric-language-clojure 核心命名空间
   此命名空间是语言支持库的核心，提供:
   - 模组元数据访问
   - 平台检测工具
   - 常用工具函数

   用户 mod 可以依赖此命名空间获取通用功能。"
  (:import (dev.architectury.platform Platform)
           (com.fabriclj ModMain)))

;; 启用反射警告，避免性能问题
(set! *warn-on-reflection* true)

;; ============================================================================
;; 语言库元数据
;; ============================================================================

(def mod-id
  "语言支持库 ID"
  ModMain/MOD_ID)

(def ^:private lib-version
  "语言库版本( 延迟加载) "
  (delay
    (try
      (.getVersion (Platform/getMod mod-id))
      (catch Exception _ "0.0.0-dev"))))

(defn version
  "获取语言库版本"
  []
  @lib-version)

;; ============================================================================
;; 平台检测
;; ============================================================================

(defn dev-mode?
  "检查是否处于开发模式"
  []
  (Platform/isDevelopmentEnvironment))

(defn fabric?
  "检查当前是否运行在 Fabric 加载器上"
  []
  (Platform/isFabric))

(defn forge?
  "检查当前是否运行在 Forge 加载器上"
  []
  (Platform/isForge))

#_(defn neoforge?
  "检查当前是否运行在 NeoForge 加载器上
   注意: NeoForge 支持取决于 Architectury API 版本
   当前实现总是返回 false"
  []
  false)

(defn platform-name
  "获取当前平台名称"
  []
  (cond
    (fabric?) "Fabric"
    (forge?) "Forge"
    ;(neoforge?) "NeoForge"
    :else "Unknown"))

;; ============================================================================
;; 工具函数
;; ============================================================================

(defn get-mod-version
  "获取指定 mod 的版本
   参数:
   - mod-id: mod 的 ID

   返回版本字符串，如果 mod 不存在则返回 nil"
  [^String mod-id]
  (try
    (.getVersion (Platform/getMod mod-id))
    (catch Exception _ nil)))

(defn mod-loaded?
  "检查指定 mod 是否已加载
   参数:
   - mod-id: mod 的 ID"
  [^String mod-id]
  (Platform/isModLoaded mod-id))

;; ============================================================================
;; 初始化
;; ============================================================================

(defn init
  "语言库初始化函数
   此函数由语言库的 Fabric 入口点自动调用，
   通常不需要用户手动调用。"
  []
  (println (str "[fabric-language-clojure] Core initialized (v" (version) ")")))
