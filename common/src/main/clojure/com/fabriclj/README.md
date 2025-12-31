# fabriclj - Fabric Language Clojure 核心库

> Clojure 语言支持库的核心 API 层

## 📦 介绍

`com.fabriclj` 是 fabric-language-clojure 项目的核心命名空间，提供 Minecraft Mod 开发的最基础 API。它是语言支持库的一部分，所有使用 fabric-language-clojure 的 mod 都可以直接使用。

## 🎯 设计定位

**fabriclj = 最小 API 层**

- ✅ 提供语言适配器和运行时管理
- ✅ 提供最基础的平台抽象
- ✅ 提供最基础的注册系统 DSL
- ✅ 提供 nREPL 支持
- ❌ 不包含高级游戏功能封装
- ❌ 不包含复杂的 DSL 和 builders

如果你需要更丰富的功能和更高级的封装，请使用 **[Swiss Knife](swiss-knife/README.md)** 工具库。

## 📚 核心模块

### 1. `com.fabriclj.core` - 核心工具

提供平台检测、版本查询等基础功能。

```clojure
(ns com.mymod.core
  (:require [com.fabriclj.core :as lib]))

;; 平台检测
(lib/fabric?)        ; => true/false
(lib/forge?)         ; => true/false
(lib/dev-mode?)      ; => true/false

;; Mod 管理
(lib/mod-loaded? "jei")           ; => true/false
(lib/get-mod-version "mymod")     ; => "1.0.0"

;; 版本信息
(lib/version)        ; => "1.0.0"
```

**核心功能: **
- 平台检测（Fabric/Forge/NeoForge）
- 开发模式检测
- Mod 加载状态查询
- 语言库版本信息

### 2. `com.fabriclj.registry` - 注册系统

基于 Architectury API 的 DeferredRegister 封装，提供简洁的注册 DSL。

```clojure
(ns com.mymod.content
  (:require [com.fabriclj.registry :as reg])
  (:import [net.minecraft.world.item Item Item$Properties]
           [net.minecraft.world.level.block Block]))

;; 创建注册表
(def items (reg/create-registry "mymod" :item))
(def blocks (reg/create-registry "mymod" :block))

;; 注册物品
(reg/defitem items my-sword
  (Item. (-> (Item$Properties.)
             (.stacksTo 1))))

;; 注册方块
(reg/defblock blocks my-ore
  (Block. (Block$Properties/of)))

;; 执行注册（在 mod 初始化时调用）
(defn init []
  (reg/register-all! items blocks))
```

**核心功能: **
- 创建 DeferredRegister
- 注册游戏对象
- 便捷宏（`defitem`, `defblock`）
- 支持的注册表类型: `:item`, `:block`, `:entity`, `:block-entity`, `:menu`, `:recipe`, `:sound`, `:particle`, `:creative-tab`

### 3. `com.fabriclj.nrepl` - nREPL 服务

内置 nREPL 服务器，支持运行时代码热替换和调试。

```clojure
(ns com.mymod.core
  (:require [com.fabriclj.nrepl :as nrepl]
            [com.fabriclj.core :as lib]))

;; 仅在开发模式下启动 nREPL
(when (lib/dev-mode?)
  (nrepl/start-server!)              ; 默认端口 7888
  (nrepl/start-server! 9999)         ; 自定义端口
  (nrepl/start-server! 9999 "0.0.0.0")) ; 允许远程连接（危险！）

;; 停止服务器
(nrepl/stop-server!)

;; 检查状态
(nrepl/server-running?)  ; => true/false
(nrepl/get-port)         ; => 7888
```

**核心功能: **
- 启动/停止 nREPL 服务器
- 默认端口 7888，仅本地访问（127.0.0.1）
- 服务器状态查询

**连接方式: **
```bash
# Calva (VS Code)
Ctrl+Shift+P -> "Calva: Connect to a running REPL" -> Generic -> localhost:7888

# Leiningen
lein repl :connect 7888

# 命令行
clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.3.0"}}}' \
        -M -m nrepl.cmdline --connect --host localhost --port 7888
```

### 4. `com.fabriclj.client` - 客户端工具

提供客户端环境的基础访问器。

```clojure
(ns com.mymod.client
  (:require [com.fabriclj.client :as client]))

;; 获取客户端实例
(def mc (client/minecraft))

;; 获取当前玩家
(when-let [player (client/player)]
  (println "Player:" (.getName player)))

;; 获取当前世界
(when-let [level (client/level)]
  (println "Level:" (.dimension level)))

;; 检查是否在游戏中
(client/in-game?)  ; => true/false
```

**核心功能: **
- 获取 Minecraft 客户端实例
- 获取当前玩家（LocalPlayer）
- 获取当前世界（ClientLevel）
- 游戏状态检查

**⚠️ 注意: ** 仅在客户端环境调用！在服务端调用会崩溃。

## 🚀 快速开始

### 最小示例

```clojure
(ns com.mymod.core
  (:require [com.fabriclj.core :as lib]
            [com.fabriclj.nrepl :as nrepl]))

(defn init
  "Mod 初始化函数 - 由语言适配器自动调用"
  []
  (println "[MyMod] Initializing on" (lib/platform-name))

  ;; 开发模式下启动 nREPL
  (when (lib/dev-mode?)
    (nrepl/start-server!))

  (println "[MyMod] Done!"))
```

### 完整示例（带注册）

```clojure
(ns com.mymod.core
  (:require [com.fabriclj.core :as lib]
            [com.fabriclj.registry :as reg]
            [com.fabriclj.nrepl :as nrepl])
  (:import [net.minecraft.world.item Item Item$Properties]))

;; 创建注册表
(def items (reg/create-registry "mymod" :item))

;; 注册物品
(reg/defitem items magic-gem
  (Item. (-> (Item$Properties.)
             (.stacksTo 64))))

;; 初始化
(defn init []
  (println "[MyMod] Initializing...")

  ;; 注册所有内容
  (reg/register-all! items)

  ;; 开发模式下启动 nREPL
  (when (lib/dev-mode?)
    (nrepl/start-server!))

  (println "[MyMod] Done!"))
```

## 🔧 高级功能

如果你需要更高级的功能，请使用 **[Swiss Knife](swiss-knife/README.md)** 工具库:

| fabriclj (基础层) | Swiss Knife (高级层) |
|------------------|---------------------|
| 平台检测 | ✅ 增强的平台工具 + 宏 |
| 基础注册 DSL | ✅ 高级注册系统 + 属性构建器 |
| - | ✅ 事件系统（80+ 事件） |
| - | ✅ 物品/方块/实体/玩家工具 |
| - | ✅ 网络通信（数据包系统） |
| - | ✅ 物理系统（射线追踪、碰撞检测） |
| - | ✅ 配置系统（EDN 配置文件） |
| - | ✅ 客户端渲染（HUD、粒子、调试可视化） |
| - | ✅ 游戏玩法系统（命令、配方、进度等） |

## 📖 相关文档

- [Swiss Knife README](swiss-knife/README.md) - 高级工具库文档
- [快速开始](../../../docs/quick-start.md) - 创建你的第一个 mod
- [开发者指南](../../../docs/dev-guide.md) - 深入开发指南
- [调试指南](../../../docs/debug-guide.md) - nREPL 连接和调试

## 🎨 设计哲学

### fabriclj 的职责

1. **语言支持** - 提供 Clojure 语言适配器
2. **运行时管理** - 管理 Clojure 运行时和类加载
3. **最小 API** - 提供最基础的 Minecraft API 封装
4. **开发工具** - 提供 nREPL 支持

### 为什么分为两层？

```
┌──────────────────────────────────┐
│    Swiss Knife (高级工具库)       │  ← 功能丰富，可选依赖
│    - 游戏玩法封装                  │
│    - DSL 和 Builders               │
│    - 客户端渲染工具                │
└──────────────────────────────────┘
           ↓ 依赖
┌──────────────────────────────────┐
│    fabriclj (核心 API 层)         │  ← 最小依赖，稳定 API
│    - 语言适配器                    │
│    - 基础平台抽象                  │
│    - 基础注册 DSL                  │
│    - nREPL 支持                    │
└──────────────────────────────────┘
           ↓ 依赖
┌──────────────────────────────────┐
│    Minecraft + Fabric/Forge       │
└──────────────────────────────────┘
```

**好处: **
- fabriclj 保持极简和稳定，API 变动少
- Swiss Knife 可以快速迭代和添加新功能
- 用户可以选择只使用 fabriclj 的最小功能
- 或者使用 Swiss Knife 获得完整的工具集

## 🤝 贡献

fabriclj 是 fabric-language-clojure 项目的一部分。

## 📜 许可证

MIT License

---

**Happy Coding with Clojure! 🎉**
