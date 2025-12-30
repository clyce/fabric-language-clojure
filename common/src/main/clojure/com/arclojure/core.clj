(ns com.arclojure.core
  "Arclojure 模组核心命名空间

   此命名空间包含模组的主要初始化逻辑和通用功能。
   遵循「Java 壳，Clojure 核」的架构模式，所有动态逻辑都在这里实现。"
  (:require [com.arclojure.registry :as registry]
            [com.arclojure.nrepl :as nrepl])
  (:import [dev.architectury.platform Platform]
           [com.arclojure ModMain]))

;; 启用反射警告，避免性能问题
(set! *warn-on-reflection* true)

;; ============================================================================
;; 模组元数据
;; ============================================================================

(def mod-id
  "模组唯一标识符（引用 Java 层定义，保证单一数据源）"
  ModMain/MOD_ID)

(def mod-version
  "模组版本（从 Platform API 动态获取）"
  (delay
    (try
      (.getVersion (Platform/getMod mod-id))
      (catch Exception _ "0.0.0-dev"))))

;; ============================================================================
;; 初始化逻辑
;; ============================================================================

(defn init
  "模组主初始化函数
   此函数由 Java 引导类调用，负责：
   1. 注册游戏内容（方块、物品等）
   2. 设置事件监听器
   3. 在开发模式下启动 nREPL 服务"
  []
  (println "[Arclojure/Clojure] Mod core initializing...")
  (println (str "[Arclojure/Clojure] Version: " @mod-version))

  ;; 注册游戏内容
  (registry/register-all!)

  ;; 开发模式下启动 nREPL
  (when (Platform/isDevelopmentEnvironment)
    (nrepl/start-server!))

  (println "[Arclojure/Clojure] Mod core initialized!"))

;; ============================================================================
;; 实用工具函数
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
