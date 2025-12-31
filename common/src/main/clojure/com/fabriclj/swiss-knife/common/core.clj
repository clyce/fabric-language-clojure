(ns com.fabriclj.swiss-knife.common.core
  "瑞士军刀 - 核心工具模块

   提供平台抽象、环境检测、资源定位等基础功能。
   基于 Architectury API 的 Platform Abstraction。"
  (:import [dev.architectury.platform Platform]
           [net.minecraft.resources ResourceLocation]
           [net.minecraft.core.registries BuiltInRegistries]
           [net.minecraft.server MinecraftServer]
           [net.minecraft.world.level Level]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 平台检测
;; ============================================================================

(defn platform-name
  "获取当前平台名称

   返回: \"fabric\" 或 \"forge\" 或 \"neoforge\""
  ^String []
  (.getName (Platform/getName)))

(defn fabric?
  "是否在 Fabric 平台运行"
  []
  (.isFabric (Platform/getName)))

(defn forge?
  "是否在 Forge 平台运行"
  []
  (.isForge (Platform/getName)))

(defn neoforge?
  "是否在 NeoForge 平台运行"
  []
  (= "neoforge" (platform-name)))

(defn mod-loaded?
  "检查指定 mod 是否已加载

   参数:
   - mod-id: mod ID 字符串

   示例:
   ```clojure
   (when (mod-loaded? \"jei\")
     (println \"JEI is installed!\"))
   ```"
  [^String mod-id]
  (Platform/isModLoaded mod-id))

;; ============================================================================
;; 环境检测
;; ============================================================================

(defn client-side?
  "是否在物理客户端运行（包括单人游戏）"
  []
  (-> (Platform/getEnv) (.isClient)))

(defn server-side?
  "是否在物理服务端运行（包括单人游戏的集成服务器）"
  []
  (-> (Platform/getEnv) (.isServer)))

(defn development?
  "是否在开发环境运行"
  []
  (Platform/isDevelopmentEnvironment))

;; ============================================================================
;; 资源定位
;; ============================================================================

(defn resource-location
  "创建资源定位符

   参数:
   - namespace: 命名空间（通常是 mod id）
   - path: 资源路径

   或单参数形式:
   - full-path: 完整路径，格式为 \"namespace:path\"

   示例:
   ```clojure
   (resource-location \"mymod\" \"items/sword\")
   (resource-location \"minecraft:stone\")
   ```"
  (^ResourceLocation [^String namespace ^String path]
   (ResourceLocation/fromNamespaceAndPath namespace path))
  (^ResourceLocation [^String full-path]
   (ResourceLocation/parse full-path)))

(defn ->resource-location
  "将对象转换为 ResourceLocation

   接受:
   - ResourceLocation（直接返回）
   - String（解析为资源定位符）
   - Keyword（转换为字符串后解析）

   示例:
   ```clojure
   (->resource-location :minecraft:stone)
   (->resource-location \"mymod:custom_item\")
   ```"
  ^ResourceLocation [obj]
  (cond
    (instance? ResourceLocation obj) obj
    (string? obj) (ResourceLocation/parse obj)
    (keyword? obj) (ResourceLocation/parse (name obj))
    :else (throw (IllegalArgumentException.
                  (str "Cannot convert to ResourceLocation: " obj)))))

;; ============================================================================
;; 游戏对象查询
;; ============================================================================

(defn get-item
  "根据资源定位符获取物品

   参数:
   - id: 资源定位符（ResourceLocation、String 或 Keyword）

   返回: Item 对象或 nil（如果不存在）"
  [id]
  (let [^ResourceLocation loc (->resource-location id)]
    (when (.containsKey BuiltInRegistries/ITEM loc)
      (.get BuiltInRegistries/ITEM loc))))

(defn get-block
  "根据资源定位符获取方块

   参数:
   - id: 资源定位符（ResourceLocation、String 或 Keyword）

   返回: Block 对象或 nil（如果不存在）"
  [id]
  (let [^ResourceLocation loc (->resource-location id)]
    (when (.containsKey BuiltInRegistries/BLOCK loc)
      (.get BuiltInRegistries/BLOCK loc))))

(defn get-entity-type
  "根据资源定位符获取实体类型

   参数:
   - id: 资源定位符（ResourceLocation、String 或 Keyword）

   返回: EntityType 对象或 nil（如果不存在）"
  [id]
  (let [^ResourceLocation loc (->resource-location id)]
    (when (.containsKey BuiltInRegistries/ENTITY_TYPE loc)
      (.get BuiltInRegistries/ENTITY_TYPE loc))))

;; ============================================================================
;; 服务器访问
;; ============================================================================

(defonce ^:private current-server (atom nil))

(defn set-server!
  "设置当前服务器实例（内部使用）

   注意：通常不需要手动调用，由框架自动管理"
  [^MinecraftServer server]
  (reset! current-server server))

(defn get-server
  "获取当前服务器实例

   返回: MinecraftServer 或 nil（如果不在服务器环境）"
  ^MinecraftServer []
  @current-server)

(defn level-from-dimension
  "根据维度 ID 获取 Level

   参数:
   - dimension: 维度资源定位符（支持 :minecraft:overworld 等）

   返回: Level 对象或 nil"
  ^Level [dimension]
  (when-let [^MinecraftServer server (get-server)]
    (let [^ResourceLocation dim-loc (->resource-location dimension)]
      (.getLevel server (.dimension dim-loc)))))

;; ============================================================================
;; 日志工具
;; ============================================================================

(def ^:private log-prefix "[Swiss Knife]")

(defn log-info
  "记录信息日志

   示例:
   ```clojure
   (log-info \"Mod initialized successfully\")
   ```"
  [& msgs]
  (println log-prefix (apply str msgs)))

(defn log-warn
  "记录警告日志"
  [& msgs]
  (println log-prefix "WARN:" (apply str msgs)))

(defn log-error
  "记录错误日志"
  [& msgs]
  (println log-prefix "ERROR:" (apply str msgs)))

(defn log-debug
  "记录调试日志（仅在开发环境）"
  [& msgs]
  (when (development?)
    (println log-prefix "DEBUG:" (apply str msgs))))

;; ============================================================================
;; 延迟执行
;; ============================================================================

(defn defer
  "将函数推迟到下一个游戏 tick 执行

   参数:
   - f: 要执行的函数

   注意：需要配合事件系统使用"
  [f]
  (future (f)))

;; ============================================================================
;; 配置管理
;; ============================================================================

(defonce ^:private config-store (atom {}))

(defn config-set!
  "设置配置项

   参数:
   - key: 配置键（支持嵌套路径，如 [:items :sword :damage]）
   - value: 配置值

   示例:
   ```clojure
   (config-set! [:combat :damage-multiplier] 1.5)
   ```"
  [key value]
  (swap! config-store assoc-in (if (vector? key) key [key]) value))

(defn config-get
  "获取配置项

   参数:
   - key: 配置键
   - default: 默认值（可选）

   示例:
   ```clojure
   (config-get [:combat :damage-multiplier] 1.0)
   ```"
  ([key]
   (config-get key nil))
  ([key default]
   (get-in @config-store (if (vector? key) key [key]) default)))

(defn config-update!
  "更新配置项（使用函数）

   参数:
   - key: 配置键
   - f: 更新函数

   示例:
   ```clojure
   (config-update! [:counter] inc)
   ```"
  [key f & args]
  (swap! config-store update-in (if (vector? key) key [key]) #(apply f % args)))

(defn config-reset!
  "清空所有配置"
  []
  (reset! config-store {}))

;; ============================================================================
;; 工具宏
;; ============================================================================

(defmacro when-mod-loaded
  "当指定 mod 加载时执行代码

   示例:
   ```clojure
   (when-mod-loaded \"jei\"
     (register-jei-integration))
   ```"
  [mod-id & body]
  `(when (mod-loaded? ~mod-id)
     ~@body))

(defmacro client-only
  "仅在客户端环境执行代码

   示例:
   ```clojure
   (client-only
     (register-renderers))
   ```"
  [& body]
  `(when (client-side?)
     ~@body))

(defmacro server-only
  "仅在服务端环境执行代码

   示例:
   ```clojure
   (server-only
     (schedule-autosave))
   ```"
  [& body]
  `(when (server-side?)
     ~@body))

(defmacro dev-only
  "仅在开发环境执行代码

   示例:
   ```clojure
   (dev-only
     (enable-debug-overlay))
   ```"
  [& body]
  `(when (development?)
     ~@body))
