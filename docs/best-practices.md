# Clojure Minecraft Mod 开发最佳实践

> 综合指南：性能优化、代码组织、常见陷阱及解决方案

---

## 📋 目录

1. [性能优化](#性能优化)
2. [代码组织](#代码组织)
3. [命名规范](#命名规范)
4. [类型提示](#类型提示)
5. [客户端/服务端分离](#客户端服务端分离)
6. [配置管理](#配置管理)
7. [事件处理](#事件处理)
8. [网络通信](#网络通信)
9. [错误处理](#错误处理)
10. [常见陷阱](#常见陷阱)
11. [调试技巧](#调试技巧)
12. [部署清单](#部署清单)

---

## 性能优化

### ✅ 1. 始终启用反射警告

```clojure
(ns com.mymod.core)

;; 在每个命名空间开头添加
(set! *warn-on-reflection* true)

;; 编译时会警告任何反射调用
;; Reflection warning, com/mymod/core.clj:10:1 - call to method getHealth...
```

**为什么重要：** 反射调用比直接方法调用慢 10-100 倍。

### ✅ 2. 使用类型提示

```clojure
;; ❌ 不好：会产生反射
(defn get-health [entity]
  (.getHealth entity))

;; ✅ 好：使用类型提示
(defn get-health [^LivingEntity entity]
  (.getHealth entity))

;; ✅ 对返回值也可以添加类型提示
(defn get-player ^Player [server name]
  (.getPlayerByName server name))
```

**关键类型提示：**
```clojure
^MinecraftServer, ^Player, ^ServerPlayer, ^LivingEntity
^Level, ^ClientLevel, ^ServerLevel
^BlockPos, ^Vec3, ^ItemStack
^Block, ^Item, ^Entity
```

### ✅ 3. 避免高频事件中的性能陷阱

```clojure
;; ❌ 不好：每 tick 创建临时对象
(events/on-server-tick
  (fn [server]
    (doseq [player (players/get-all-players server)]
      (let [pos {:x 1 :y 2 :z 3}]  ; 每 tick 创建新 map
        (process-player player pos)))))

;; ✅ 好：复用对象，使用类型提示
(events/on-server-tick
  (fn [^MinecraftServer server]
    (doseq [^ServerPlayer player (.getPlayerList (.getPlayerManager server))]
      (.teleportTo player 1.0 2.0 3.0))))  ; 直接调用 Java 方法

;; ✅ 更好：只在需要时执行
(events/on-server-tick
  (fn [^MinecraftServer server]
    (when (zero? (mod (.getTickCount server) 20))  ; 每秒执行一次
      (process-something))))
```

### ✅ 4. 使用原始类型避免装箱

```clojure
;; ❌ 不好：大量装箱操作
(defn calculate-distance [x1 y1 z1 x2 y2 z2]
  (Math/sqrt (+ (* (- x2 x1) (- x2 x1))
                (* (- y2 y1) (- y2 y1))
                (* (- z2 z1) (- z2 z1)))))

;; ✅ 好：使用原始类型
(defn calculate-distance ^double [^double x1 ^double y1 ^double z1
                                   ^double x2 ^double y2 ^double z2]
  (Math/sqrt (+ (* (- x2 x1) (- x2 x1))
                (* (- y2 y1) (- y2 y1))
                (* (- z2 z1) (- z2 z1)))))
```

### ✅ 5. 预计算和缓存

```clojure
;; ✅ 使用 memoize 缓存计算结果
(def calculate-damage
  (memoize
    (fn [base-damage armor-value enchant-level]
      ;; 复杂计算
      (* base-damage (- 1.0 (* 0.04 armor-value)) (+ 1.0 (* 0.1 enchant-level))))))

;; ✅ 使用 delay 延迟初始化
(def expensive-resource
  (delay
    (load-expensive-resource)))

;; 使用时才加载
(when needed
  @expensive-resource)
```

### ⚡ 性能对比参考

| 场景 | Java 性能 | Clojure (无优化) | Clojure (优化后) |
|------|----------|------------------|------------------|
| 事件处理 | 100% | 95% | 98-99% |
| Tick 逻辑 | 100% | 80% | 95-98% |
| 物理计算 | 100% | 70% | 90-95% |
| 注册系统 | 100% | 100% | 100% |
| 启动时间 | 3s | 5s | 4-5s |

**结论：** 正确使用类型提示后，性能差异 <5%，完全可以忽略。

---

## 代码组织

### ✅ 1. 命名空间组织

```clojure
;; 推荐的命名空间结构
com.mymod/
├── core.clj              ; 主入口、注册、初始化
├── client.clj            ; 客户端入口
├── config.clj            ; 配置管理
├── hooks.clj             ; Mixin 钩子函数
├── content/              ; 游戏内容
│   ├── items.clj         ; 物品定义
│   ├── blocks.clj        ; 方块定义
│   └── entities.clj      ; 实体定义
├── features/             ; 游戏功能
│   ├── magic_system.clj  ; 魔法系统
│   ├── teleport.clj      ; 传送系统
│   └── quests.clj        ; 任务系统
└── util/                 ; 工具函数
    ├── helpers.clj
    └── debug.clj
```

### ✅ 2. 主入口模块（core.clj）

```clojure
(ns com.mymod.core
  (:require [com.fabriclj.nrepl :as nrepl]
            [com.fabriclj.swiss-knife :as mb]
            [com.fabriclj.swiss-knife.common.lifecycle :as lifecycle]
            [com.mymod.config :as cfg]
            [com.mymod.content.items :as items]
            [com.mymod.content.blocks :as blocks]
            [com.mymod.features.magic-system :as magic]))

(defn init
  "Mod 初始化函数"
  []
  (mb/log-info "[MyMod] Initializing on" (mb/platform-name))

  ;; 1. 加载配置（第一步）
  (cfg/load-config!)

  ;; 2. 统一初始化（第二步）
  (lifecycle/init-common! "mymod"
    {:enable-generic-packets? true
     :enable-config-sync? true})

  ;; 3. 注册内容（第三步）
  (items/register-items!)
  (blocks/register-blocks!)

  ;; 4. 注册事件（第四步）
  (magic/register-events!)

  ;; 5. 开发工具（最后）
  (when (mb/development?)
    (nrepl/start-server!)
    (mb/log-info "[MyMod] nREPL server started on port 7888"))

  (mb/log-info "[MyMod] Initialization complete!"))
```

### ✅ 3. 客户端模块（client.clj）

```clojure
(ns com.mymod.client
  (:require [com.fabriclj.swiss-knife :as mb]
            [com.fabriclj.swiss-knife.common.lifecycle :as lifecycle]
            [com.fabriclj.swiss-knife.client.ui.keybindings :as keys]
            [com.fabriclj.swiss-knife.client.rendering.hud :as hud]))

(defn init-client
  "客户端初始化"
  []
  (mb/log-info "[MyMod/Client] Initializing client")

  ;; 1. 统一初始化
  (lifecycle/init-client! "mymod"
    {:enable-hud? true
     :enable-debug? (mb/development?)})

  ;; 2. 注册按键
  (setup-keybindings!)

  ;; 3. 注册 HUD
  (setup-hud!)

  (mb/log-info "[MyMod/Client] Client ready!"))
```

---

## 命名规范

### ✅ 1. Clojure 标准命名

```clojure
;; 函数和变量：kebab-case
(defn get-player-health [player] ...)
(def magic-gem-power 10)

;; 常量：kebab-case（不使用 SCREAMING_CASE）
(def max-players 100)
(def default-config {:enabled true})

;; 命名空间：kebab-case
(ns com.mymod.magic-system)

;; Protocols 和 Records：PascalCase
(defprotocol MagicCaster
  (cast-spell [this spell]))

(defrecord MagicWand [power durability])
```

### ✅ 2. 函数命名约定

```clojure
;; 谓词函数：? 后缀
(defn enabled? [feature] ...)
(defn has-permission? [player] ...)
(defn in-game? [] ...)

;; 副作用函数：! 后缀
(defn teleport! [player pos] ...)
(defn give-item! [player item] ...)
(defn save-config! [] ...)

;; 转换函数：-> 前缀
(defn ->resource-location [obj] ...)
(defn ->vec3 [pos] ...)

;; 解构函数：<- 前缀（可选）
(defn <-nbt [compound-tag] ...)
```

### ✅ 3. 命名空间别名

```clojure
;; 推荐的统一别名
(require '[com.fabriclj.swiss-knife :as mb]
         '[com.fabriclj.swiss-knife.common.platform.core :as platform]
         '[com.fabriclj.swiss-knife.common.registry.core :as reg]
         '[com.fabriclj.swiss-knife.common.events.core :as events]
         '[com.fabriclj.swiss-knife.common.game-objects.items :as items]
         '[com.fabriclj.swiss-knife.common.game-objects.blocks :as blocks]
         '[com.fabriclj.swiss-knife.common.game-objects.players :as players]
         '[com.fabriclj.swiss-knife.common.network.core :as net]
         '[com.fabriclj.swiss-knife.common.config.core :as config])

;; ❌ 避免使用 :refer :all
(require '[com.fabriclj.swiss-knife.common.events.core :refer :all])  ; 不推荐

;; ❌ 避免过长的别名
(require '[com.fabriclj.swiss-knife.common.platform.core :as swiss-knife-platform])
```

---

## 类型提示

### ✅ 1. 关键位置的类型提示

```clojure
;; 函数参数
(defn damage-entity [^LivingEntity entity ^double amount]
  (.hurt entity DamageSource/GENERIC amount))

;; 局部变量
(defn process-entities [level]
  (let [^List entities (.getEntities level)]
    (doseq [^Entity entity entities]
      (.tick entity))))

;; 字段访问
(defn get-position [^Entity entity]
  (let [^Vec3 pos (.position entity)]
    {:x (.x pos) :y (.y pos) :z (.z pos)}))
```

### ✅ 2. 常用 Minecraft 类型

```clojure
;; 服务端
^MinecraftServer, ^ServerLevel, ^ServerPlayer

;; 客户端
^Minecraft, ^ClientLevel, ^LocalPlayer

;; 通用
^Level, ^Player, ^LivingEntity, ^Entity
^BlockPos, ^Vec3, ^AABB
^ItemStack, ^Item, ^Block, ^BlockState
^Component, ^ResourceLocation

;; 集合
^List, ^Map, ^Set
^Collection, ^Iterable
```

### ⚠️ 3. 何时可以省略类型提示

```clojure
;; 简单的纯 Clojure 代码
(defn calculate [a b]
  (+ a b))  ; 无需类型提示

;; 数据转换
(defn parse-config [config-map]
  (update config-map :power #(* % 2)))  ; 无需类型提示

;; 只在调用 Java 互操作时需要类型提示
(defn damage-player [^Player player amount]  ; 需要
  (let [health (.getHealth player)]  ; player 已有类型提示
    (- health amount)))  ; 纯 Clojure，无需提示
```

---

## 客户端/服务端分离

### ✅ 1. 分离命名空间

```clojure
;; core.clj - 服务端+客户端通用
(ns com.mymod.core
  (:require [com.fabriclj.swiss-knife :as mb]))

(defn init []
  ;; 通用初始化
  (register-items!)
  (register-events!))

;; client.clj - 仅客户端
(ns com.mymod.client
  (:require [com.fabriclj.swiss-knife :as mb]
            [com.fabriclj.swiss-knife.client.platform.core :as client]))

(defn init-client []
  (when (mb/client-side?)  ; 双重保险
    (register-keybindings!)
    (register-renderers!)))
```

### ✅ 2. 使用平台检测

```clojure
;; ✅ 在运行时检测
(defn conditional-feature []
  (if (mb/client-side?)
    (client-specific-code)
    (server-specific-code)))

;; ✅ 使用便捷宏
(require '[com.fabriclj.swiss-knife.common.platform.core :as platform])

(platform/client-only
  (register-renderers))

(platform/server-only
  (schedule-autosave))

(platform/dev-only
  (enable-debug-tools))
```

### ⚠️ 3. 避免在服务端加载客户端类

```clojure
;; ❌ 不好：会在服务端崩溃
(ns com.mymod.core
  (:require [com.mymod.client :as client])  ; 客户端命名空间
  (:import [net.minecraft.client Minecraft]))  ; 客户端类

;; ✅ 好：延迟加载
(defn setup-client-features []
  (when (mb/client-side?)
    (require '[com.mymod.client :as client])
    (client/init)))

;; ✅ 或使用 lifecycle 管理
(lifecycle/init-client! "mymod" {...})  ; 自动处理
```

---

## 配置管理

### ✅ 1. 使用配置验证器

```clojure
(require '[com.fabriclj.swiss-knife.common.config.core :as config]
         '[com.fabriclj.swiss-knife.common.config.validators :as v])

(config/register-config! "mymod" "default"
  {:difficulty :normal
   :spawn-rate 0.5
   :max-players 100
   :server-name "My Server"}
  :validator (v/all-of
               ;; 必需的键
               (v/has-keys? :difficulty :spawn-rate :max-players :server-name)
               ;; 具体验证
               (v/validate-key :difficulty (v/one-of? :easy :normal :hard))
               (v/validate-key :spawn-rate (v/probability?))
               (v/validate-key :max-players
                 (v/all-of (v/positive-integer?) (v/in-range? 1 1000)))
               (v/validate-key :server-name
                 (v/all-of (v/non-empty-string?) (v/max-length? 50)))))
```

### ✅ 2. 多配置文件组织

```clojure
;; 按功能分离配置文件
(config/register-config! "mymod" "gameplay"
  {:spawn-rate 0.5 :difficulty :normal}
  :file-name "gameplay.edn"
  :validator ...)

(config/register-config! "mymod" "rendering"
  {:particle-quality :high :view-distance 16}
  :file-name "rendering.edn"
  :validator ...)

;; 读取时指定配置文件
(config/get-config-value "mymod" "gameplay" :spawn-rate)
(config/get-config-value "mymod" "rendering" :particle-quality)
```

### ✅ 3. 配置热重载

```clojure
;; 监听配置变化
(config/watch-config! "mymod" :spawn-watcher
  (fn [old-cfg new-cfg]
    (when (not= (:spawn-rate old-cfg) (:spawn-rate new-cfg))
      (mb/log-info "Spawn rate changed:" (:spawn-rate new-cfg))
      (update-spawn-system!))))

;; 手动重载
(config/reload-config! "mymod")
```

---

## 事件处理

### ✅ 1. 事件优先级

```clojure
(require '[com.fabriclj.swiss-knife.common.events.priority :as priority])

;; 使用优先级控制执行顺序
(priority/register-handler! :server-starting :permission-check :highest
  (fn [server]
    (check-permissions server)))

(priority/register-handler! :server-starting :init-world :normal
  (fn [server]
    (init-world server)))

(priority/register-handler! :server-starting :logging :lowest
  (fn [server]
    (log-startup server)))
```

### ✅ 2. 事件结果处理

```clojure
(require '[com.fabriclj.swiss-knife.common.events.core :as events])

;; 使用正确的事件结果
(events/on-block-break
  (fn [level pos state player]
    (if (protected? pos)
      (events/event-interrupt)  ; 阻止破坏
      (events/event-pass))))    ; 允许破坏

;; 返回值给事件
(events/on-player-attack
  (fn [player target]
    (if (friendly? target)
      (events/event-interrupt false)  ; 阻止攻击
      (events/event-pass))))
```

### ✅ 3. 避免在事件中阻塞

```clojure
;; ❌ 不好：阻塞 Tick 事件
(events/on-server-tick
  (fn [server]
    (Thread/sleep 1000)  ; 会冻结游戏！
    (do-something)))

;; ✅ 好：使用异步或调度
(require '[com.fabriclj.swiss-knife.common.utils.time :as time])

(events/on-server-tick
  (fn [server]
    (time/schedule-task 20  ; 1 秒后执行
      #(do-something-async))))
```

---

## 网络通信

### ✅ 1. 使用通用数据包系统

```clojure
(require '[com.fabriclj.swiss-knife.common.network.core :as net])

;; 1. 初始化（在 lifecycle 中自动完成）
(lifecycle/init-common! "mymod" {:enable-generic-packets? true})

;; 2. 注册处理器
(net/register-generic-handler! "mymod" :teleport :server
  (fn [data player]
    (let [{:keys [x y z]} data]
      (players/teleport! player [x y z]))))

;; 3. 发送数据包
(net/send-generic! "mymod" :teleport {:x 100 :y 64 :z 200})
```

### ✅ 2. 数据包大小优化

```clojure
;; ❌ 不好：发送大量数据
(net/send-to-player! player packet
  {:all-players (map player-data (get-all-players))
   :world-data (get-full-world-data)})  ; 可能几 MB

;; ✅ 好：只发送必要数据
(net/send-to-player! player packet
  {:player-count (count (get-all-players))
   :time (get-world-time)})  ; 几十字节
```

### ✅ 3. 避免高频同步

```clojure
;; ❌ 不好：每 tick 同步
(events/on-server-tick
  (fn [server]
    (doseq [player (get-all-players)]
      (sync-data-to-client player))))  ; 会卡服务器

;; ✅ 好：按需同步
(events/on-server-tick
  (fn [server]
    (when (zero? (mod (.getTickCount server) 20))  ; 每秒一次
      (sync-data-to-clients))))

;; ✅ 更好：事件驱动同步
(events/on-player-join
  (fn [player]
    (sync-initial-data player)))  ; 只在加入时同步
```

---

## 错误处理

### ✅ 1. 防御性编程

```clojure
;; ✅ 检查 nil
(defn damage-player [player amount]
  (when player  ; 防止 nil
    (when (pos? amount)  ; 验证输入
      (.hurt player DamageSource/GENERIC amount))))

;; ✅ 使用 try-catch
(defn load-custom-data [file]
  (try
    (-> file slurp read-string)
    (catch Exception e
      (mb/log-error "Failed to load data:" (.getMessage e))
      {})))  ; 返回默认值
```

### ✅ 2. 友好的错误消息

```clojure
;; ❌ 不好：模糊的错误
(defn get-item [id]
  (or (core/get-item id)
      (throw (Exception. "Item not found"))))

;; ✅ 好：详细的错误信息
(defn get-item [id]
  (or (core/get-item id)
      (throw (IllegalArgumentException.
               (str "Unknown item: " id "\n"
                    "Make sure the item is registered.\n"
                    "Example: (get-item :minecraft:diamond)")))))
```

### ✅ 3. 事件中的异常处理

```clojure
;; ✅ 包装事件处理器
(defn safe-event-handler [handler]
  (fn [& args]
    (try
      (apply handler args)
      (catch Exception e
        (mb/log-error "Event handler error:" (.getMessage e))
        (events/event-pass)))))  ; 不影响其他 mod

(events/on-player-join
  (safe-event-handler
    (fn [player]
      (potentially-failing-operation player))))
```

---

## 常见陷阱

### 🔴 陷阱 1：在错误的线程执行代码

```clojure
;; ❌ 不好：在异步线程修改世界
(future
  (set-block! level pos Blocks/STONE))  ; 会崩溃！

;; ✅ 好：使用 defer 或 schedule-task
(require '[com.fabriclj.swiss-knife.common.utils.time :as time])

(time/schedule-task 1
  #(set-block! level pos Blocks/STONE))  ; 在主线程执行
```

### 🔴 陷阱 2：忘记注册表初始化

```clojure
;; ❌ 不好：忘记调用 register-all!
(def items (reg/create-registry "mymod" :item))
(reg/defitem items my-item ...)

(defn init []
  (println "Done"))  ; 物品不会被注册！

;; ✅ 好：始终调用 register-all!
(defn init []
  (reg/register-all! items blocks entities)
  (println "Done"))
```

### 🔴 陷阱 3：在客户端访问服务端对象

```clojure
;; ❌ 不好：客户端访问 ServerPlayer
(defn client-function []
  (let [players (players/get-all-players server)]  ; server 在客户端是 nil
    ...))

;; ✅ 好：使用正确的客户端 API
(require '[com.fabriclj.swiss-knife.client.platform.core :as client])

(defn client-function []
  (when-let [player (client/get-player)]  ; LocalPlayer
    ...))
```

### 🔴 陷阱 4：配置文件路径冲突

```clojure
;; ❌ 不好：多个功能使用同一个配置文件
(config/register-config! "mymod" "default" {...})  ; gameplay 配置
(config/register-config! "mymod" "default" {...})  ; rendering 配置（会覆盖！）

;; ✅ 好：使用不同的配置 ID 或文件名
(config/register-config! "mymod" "gameplay" {...} :file-name "gameplay.edn")
(config/register-config! "mymod" "rendering" {...} :file-name "rendering.edn")
```

### 🔴 陷阱 5：忘记初始化 lifecycle

```clojure
;; ❌ 不好：手动初始化各个系统
(defn init []
  (net/init-generic-packet-system! "mymod")
  (config-sync/register-config-sync-packets! "mymod")
  ;; 容易忘记某个系统...
  )

;; ✅ 好：使用 lifecycle 统一管理
(defn init []
  (lifecycle/init-common! "mymod"
    {:enable-generic-packets? true
     :enable-config-sync? true}))
```

---

## 调试技巧

### ✅ 1. 使用 nREPL 实时调试

```clojure
;; 在 core.clj 中启动 nREPL
(when (mb/development?)
  (nrepl/start-server!))

;; 连接后可以在 REPL 中：
;; 1. 查看当前状态
(def server (mb/get-server))
(players/get-all-players server)

;; 2. 测试功能
(give-item! (first (get-all-players)) Items/DIAMOND 64)

;; 3. 重新定义函数（热重载）
(defn my-function []
  (println "New implementation"))
```

### ✅ 2. 使用日志函数

```clojure
;; ✅ 使用分级日志
(mb/log-info "Server started")
(mb/log-warn "Config value out of range, using default")
(mb/log-error "Failed to load data:" error-msg)
(mb/log-debug "Processing entity:" entity-id)  ; 仅开发环境

;; ✅ 添加上下文信息
(mb/log-info "[MagicSystem]" "Casting spell:" spell-name "by" player-name)
```

### ✅ 3. 使用性能分析器

```clojure
(require '[com.fabriclj.swiss-knife.common.data.profiler :as prof])

;; 包装需要分析的代码
(prof/profile :my-expensive-function
  (expensive-calculation))

;; 查看统计
(let [stats (prof/get-timing-stats :com.mymod.core/my-expensive-function)]
  (mb/log-info "Average:" (:avg-ms stats) "ms"
               "Total calls:" (:count stats)))

;; 生成完整报告
(prof/print-performance-report
  (prof/generate-performance-report :top-n 10))
```

---

## 代码质量

### ✅ 1. 添加文档字符串

```clojure
;; ✅ 详细的文档字符串
(defn teleport-player
  "传送玩家到指定位置

   参数：
   - player: ServerPlayer 实例
   - pos: 位置向量 [x y z] 或 Vec3
   - opts: 可选参数
     - :yaw - 视角水平角度（默认保持不变）
     - :pitch - 视角俯仰角度（默认保持不变）
     - :dimension - 目标维度（默认当前维度）

   返回：boolean（是否成功）

   示例：
   ```clojure
   (teleport-player player [100 64 200])
   (teleport-player player [100 64 200] {:yaw 90.0 :pitch 0.0})
   ```

   注意：跨维度传送需要指定 :dimension 选项"
  [player pos & {:as opts}]
  ...)
```

### ✅ 2. 使用 comment 块提供示例

```clojure
(comment
  ;; 使用示例（不会被编译）

  ;; 1. 基本用法
  (teleport-player player [100 64 200])

  ;; 2. 高级用法
  (teleport-player player [100 64 200]
    {:yaw 90.0
     :pitch 0.0
     :dimension :minecraft:the_nether})

  ;; 3. 测试代码
  (def test-player (first (get-all-players)))
  (teleport-player test-player [0 100 0]))
```

### ✅ 3. 代码分层

```clojure
;; 底层：直接 Java 互操作（私有）
(defn- ^Vec3 create-vec3 [x y z]
  (Vec3. x y z))

;; 中层：基础封装（公开）
(defn teleport-vec3! [^Player player ^Vec3 pos]
  (.teleportTo player (.x pos) (.y pos) (.z pos)))

;; 高层：便捷 API（公开，推荐使用）
(defn teleport! [player pos & opts]
  (let [vec3 (parse-position pos)]
    (teleport-vec3! player vec3)))
```

---

## 命名空间管理

### ✅ 1. 按需导入

```clojure
;; ✅ 只导入需要的模块
(ns com.mymod.items
  (:require [com.fabriclj.swiss-knife.common.registry.core :as reg]
            [com.fabriclj.swiss-knife.common.platform.core :as platform])
  (:import [net.minecraft.world.item Item Item$Properties]))

;; ❌ 避免导入整个工具库
(require '[com.fabriclj.swiss-knife :as mb])  ; 700+ 行，会加载所有模块
```

### ✅ 2. 延迟加载客户端模块

```clojure
;; ✅ 使用函数延迟加载
(defn setup-client []
  (when (mb/client-side?)
    (require '[com.mymod.client :as client])
    (client/init)))

;; ✅ 或使用 lifecycle
(lifecycle/init-client! "mymod" {...})
```

---

## 内存管理

### ✅ 1. 避免内存泄漏

```clojure
;; ❌ 不好：无限增长的集合
(defonce player-data (atom {}))

(events/on-player-join
  (fn [player]
    (swap! player-data assoc player {:join-time (System/currentTimeMillis)})))
;; 玩家离开后数据仍然存在！

;; ✅ 好：清理离开的玩家
(events/on-player-quit
  (fn [player]
    (swap! player-data dissoc player)))

;; ✅ 更好：使用 WeakHashMap
(import '[java.util WeakHashMap])
(defonce player-data (atom (WeakHashMap.)))
;; 玩家对象被 GC 时自动清理
```

### ✅ 2. 合理使用缓存

```clojure
;; ✅ 对不变的数据使用缓存
(def expensive-calculation
  (memoize
    (fn [x y]
      ;; 昂贵的纯计算
      )))

;; ⚠️ 不要缓存会变化的数据
(def get-player-health  ; ❌ 不要这样做
  (memoize
    (fn [player]
      (.getHealth player))))  ; 玩家血量会变化！
```

---

## 开发工作流

### ✅ 1. 推荐的开发流程

```
1. 设计 → 2. 实现 → 3. 测试 → 4. 优化
   ↓         ↓         ↓         ↓
配置      注册      REPL      性能分析
验证器    事件      测试      类型提示
```

### ✅ 2. 使用 DataGen 自动化

```clojure
(ns com.mymod.datagen
  (:require [com.fabriclj.swiss-knife.common.datagen.models :as models]
            [com.fabriclj.swiss-knife.common.datagen.blockstates :as bs]
            [com.fabriclj.swiss-knife.common.datagen.lang :as lang]))

(defn generate-all-assets []
  (let [base "./src/main/resources"
        items ["ruby" "sapphire" "emerald"]
        blocks ["ruby_ore" "sapphire_ore"]]
    ;; 一键生成所有资源文件
    (models/generate-simple-items! base "mymod" items)
    (models/generate-simple-blocks! base "mymod" blocks)
    (bs/generate-simple-blockstates! base "mymod" blocks)
    (lang/create-complete-lang-file! base "mymod" "en_us" items blocks {})))

;; 在 REPL 中运行
(generate-all-assets)  ; 几秒内生成所有文件
```

### ✅ 3. 版本控制

```
.gitignore 应包含：
─────────────────
build/
.gradle/
run/
logs/
.idea/
*.iml
config/  # 开发时的配置文件
```

---

## 生产环境检查清单

### ✅ 发布前必查项

```clojure
;; 1. 禁用开发工具
(defn init []
  ;; ✅ 只在开发环境启用 nREPL
  (when (mb/development?)
    (nrepl/start-server!))

  ;; ✅ 只在开发环境启用调试
  (lifecycle/init-client! "mymod"
    {:enable-debug? (mb/development?)}))

;; 2. 移除调试日志
;; ❌ 不要留下大量 println
(println "Debug:" player-data)  ; 在生产环境删除

;; ✅ 使用 log-debug（自动在生产环境禁用）
(mb/log-debug "Debug:" player-data)

;; 3. 验证配置文件
;; 确保所有配置都有验证器
(config/register-config! "mymod" "default" {...}
  :validator (v/all-of ...))

;; 4. 测试所有平台
;; - Fabric 客户端 ✓
;; - Fabric 服务端 ✓
;; - Forge 客户端 ✓
;; - Forge 服务端 ✓

;; 5. 性能测试
;; 使用 profiler 检查热点
(prof/profile :critical-path
  (critical-operation))
```

---

## 项目结构示例

```
mymod/
├── src/main/
│   ├── clojure/
│   │   └── com/mymod/
│   │       ├── core.clj           # 主入口
│   │       ├── client.clj         # 客户端入口
│   │       ├── config.clj         # 配置管理
│   │       ├── hooks.clj          # Mixin 钩子
│   │       ├── content/           # 游戏内容
│   │       │   ├── items.clj
│   │       │   ├── blocks.clj
│   │       │   └── entities.clj
│   │       ├── features/          # 游戏功能
│   │       │   ├── magic.clj
│   │       │   └── teleport.clj
│   │       └── util/              # 工具函数
│   │           └── helpers.clj
│   ├── java/                      # Mixin 和 Java 代码
│   │   └── com/mymod/
│   │       ├── ExampleMod.java
│   │       └── mixin/
│   │           └── PlayerMixin.java
│   └── resources/
│       ├── fabric.mod.json
│       ├── mymod.mixins.json
│       └── assets/mymod/
│           ├── models/
│           ├── textures/
│           └── lang/
└── build.gradle
```

---

## 快速参考卡

### 性能优化

| 操作 | 推荐做法 |
|------|---------|
| Java 互操作 | 始终添加类型提示 |
| Tick 事件 | 避免耗时操作，使用节流 |
| 数据结构 | 高频场景直接用 Java 对象 |
| 计算密集 | 使用 memoize 缓存 |

### 命名规范

| 类型 | 格式 | 示例 |
|------|------|------|
| 函数/变量 | kebab-case | `get-player-health` |
| 谓词 | ?后缀 | `enabled?`, `in-game?` |
| 副作用 | !后缀 | `teleport!`, `save!` |
| 转换 | ->前缀 | `->vec3`, `->nbt` |
| 常量 | kebab-case | `default-port` |

### 模块导入

| 模块 | 推荐别名 |
|------|---------|
| swiss-knife | `mb` |
| platform.core | `platform` |
| registry.core | `reg` |
| events.core | `events` |
| game-objects.players | `players` |
| network.core | `net` |
| config.core | `config` |

### 常用模式

```clojure
;; 安全的客户端代码
(when (mb/client-side?)
  (require '[com.mymod.client :as client])
  (client/init))

;; 错误处理
(try
  (risky-operation)
  (catch Exception e
    (mb/log-error "Error:" (.getMessage e))
    (fallback-value)))

;; 类型提示模板
(defn my-function [^Type arg1 ^Type arg2]
  ^ReturnType
  (body))

;; 配置验证模板
(config/register-config! "mymod" "default" {...}
  :validator (v/all-of
               (v/has-keys? :key1 :key2)
               (v/validate-key :key1 validator1)
               (v/validate-key :key2 validator2)))
```

---

## 完整示例：遵循所有最佳实践

```clojure
(ns com.mymod.core
  "My Awesome Mod - 主入口模块

   功能：
   - 魔法系统
   - 传送系统
   - 自定义物品"
  (:require [com.fabriclj.core :as lib]
            [com.fabriclj.nrepl :as nrepl]
            [com.fabriclj.swiss-knife :as mb]
            [com.fabriclj.swiss-knife.common.lifecycle :as lifecycle]
            [com.fabriclj.swiss-knife.common.registry.core :as reg]
            [com.fabriclj.swiss-knife.common.events.core :as events]
            [com.fabriclj.swiss-knife.common.game-objects.players :as players]
            [com.fabriclj.swiss-knife.common.config.core :as config]
            [com.fabriclj.swiss-knife.common.config.validators :as v])
  (:import [net.minecraft.world.item Item Item$Properties Rarity]
           [net.minecraft.server.level ServerPlayer]))

;; 启用反射警告（必须！）
(set! *warn-on-reflection* true)

;; ============================================================================
;; 配置系统
;; ============================================================================

(defn load-config!
  "加载配置文件"
  []
  (config/register-config! "mymod" "default"
    {:magic {:power 10.0
             :cooldown-ticks 20}
     :teleport {:max-distance 100.0
                :enabled true}}
    :validator (v/all-of
                 (v/has-keys? :magic :teleport)
                 (v/validate-key [:magic :power] (v/positive-number?))
                 (v/validate-key [:magic :cooldown-ticks] (v/positive-integer?))
                 (v/validate-key [:teleport :max-distance] (v/positive-number?))
                 (v/validate-key [:teleport :enabled] boolean?))))

;; ============================================================================
;; 注册系统
;; ============================================================================

(def items-registry (reg/create-registry "mymod" :item))

;; 使用类型提示和属性构建器
(reg/defitem items-registry magic-gem
  (Item. (reg/item-properties
           :stack-size 1
           :durability 100
           :rarity :rare
           :fire-resistant true)))

;; ============================================================================
;; 事件处理
;; ============================================================================

(defn register-events!
  "注册所有事件处理器"
  []
  ;; 使用类型提示提升性能
  (events/on-player-join
    (fn [^ServerPlayer player]
      (let [welcome (config/get-config-value "mymod" [:messages :welcome])]
        (players/send-message! player welcome))))

  ;; 节流 Tick 事件
  (events/on-server-tick
    (fn [^MinecraftServer server]
      (when (zero? (mod (.getTickCount server) 20))  ; 每秒一次
        (update-magic-systems server)))))

;; ============================================================================
;; 主初始化
;; ============================================================================

(defn init
  "Mod 初始化函数"
  []
  (mb/log-info "[MyMod] Initializing on" (mb/platform-name))

  (try
    ;; 1. 配置
    (load-config!)

    ;; 2. Lifecycle
    (lifecycle/init-common! "mymod"
      {:enable-generic-packets? true
       :enable-config-sync? true})

    ;; 3. 注册
    (reg/register-all! items-registry)

    ;; 4. 事件
    (register-events!)

    ;; 5. 开发工具
    (when (mb/development?)
      (nrepl/start-server!)
      (mb/log-info "[MyMod] nREPL started on :7888"))

    (mb/log-info "[MyMod] Done!")

    (catch Exception e
      (mb/log-error "[MyMod] Initialization failed:" (.getMessage e))
      (throw e))))  ; 重新抛出，让 Fabric 知道初始化失败

(comment
  ;; REPL 测试代码

  ;; 重新加载配置
  (config/reload-config! "mymod")

  ;; 测试功能
  (def server (mb/get-server))
  (def player (first (players/get-all-players server)))
  (players/give-item! player Items/DIAMOND 64)

  ;; 性能分析
  (require '[com.fabriclj.swiss-knife.common.data.profiler :as prof])
  (prof/print-performance-report
    (prof/generate-performance-report :top-n 10)))
```

---

## 🎯 核心原则总结

### 1. **性能第一**
- ✅ 启用反射警告
- ✅ 使用类型提示
- ✅ 避免高频操作中的临时对象

### 2. **安全第一**
- ✅ 验证所有配置
- ✅ 处理所有异常
- ✅ 检查 nil 值

### 3. **可维护性**
- ✅ 详细的文档字符串
- ✅ 清晰的代码组织
- ✅ 使用 comment 块提供示例

### 4. **客户端/服务端分离**
- ✅ 分离命名空间
- ✅ 使用平台检测
- ✅ 延迟加载客户端类

### 5. **利用工具**
- ✅ Swiss Knife 提供的便捷 API
- ✅ nREPL 实时调试
- ✅ Profiler 性能分析
- ✅ DataGen 自动化资源生成

---

## 🚀 下一步

1. **阅读示例项目** - [example/](../example/) 展示了所有最佳实践
2. **使用模板** - 复制示例项目作为起点
3. **启用工具** - nREPL + Profiler + 配置验证器
4. **持续优化** - 用 profiler 找瓶颈，用类型提示优化

---

## 📚 相关文档

- [快速开始](quick-start.md) - 创建第一个 mod
- [开发者指南](dev-guide.md) - 深入开发
- [调试指南](debug-guide.md) - nREPL 使用
- [性能分析](#性能优化) - 本文档性能章节
- [示例项目](../example/README.md) - 完整的实践示例

---

**记住：先让它工作，再让它快。Clojure 提供的开发效率远超微小的性能差异。** 🎉
