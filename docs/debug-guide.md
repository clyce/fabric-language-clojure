# fabric-language-clojure 调试指南

本文档介绍如何使用 nREPL 进行运行时调试和代码热替换。

## nREPL 概述

nREPL（Network REPL）允许你在游戏运行时连接到 Clojure 运行时，实现:

- **代码热替换**: 修改函数后立即生效，无需重启游戏
- **状态检查**: 查看和修改运行时状态
- **交互式开发**: 在真实游戏环境中测试代码

## 启动 nREPL

### 在 mod 初始化时启动

```clojure
(ns com.mymod.core
  (:require [com.fabriclj.core :as lib]
            [com.fabriclj.nrepl :as nrepl]))

(defn init []
  ;; 仅在开发模式启动 nREPL
  (when (lib/dev-mode?)
    (nrepl/start-server!))  ;; 默认端口 7888

  ;; 或指定端口
  ;; (nrepl/start-server! 9999)

  ;; 或同时指定绑定地址（危险！）
  ;; (nrepl/start-server! 9999 "0.0.0.0")
  )
```

### 验证启动成功

控制台应显示:

```
[nREPL] Server started on 127.0.0.1:7888
[nREPL] Connect with: lein repl :connect 7888
[nREPL] Or use Calva: 'Connect to a running REPL' -> localhost:7888
```

## 连接 nREPL

### VS Code + Calva（推荐）

1. 安装 [Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva) 插件
2. 启动游戏，确认 nREPL 已启动
3. 按 `Ctrl+Shift+P`
4. 输入 `Calva: Connect to a running REPL`
5. 选择 `Generic`
6. 输入连接信息: `localhost:7888`

### 命令行

```bash
# 使用 Leiningen
lein repl :connect 7888

# 使用 Clojure CLI
clj -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.3.0"}}}' \
    -M -m nrepl.cmdline --connect --host localhost --port 7888
```

### IntelliJ IDEA + Cursive

1. 安装 Cursive 插件
2. `Run` → `Edit Configurations...`
3. 添加 `Clojure REPL` → `Remote`
4. 设置 Host: `localhost`, Port: `7888`
5. 运行配置

## 热替换工作流

### 1. 修改函数

```clojure
;; 在 REPL 中切换到目标命名空间
(in-ns 'com.mymod.hooks)

;; 重新定义函数
(defn on-jump [player ci]
  ;; 新逻辑
  (println "New behavior!"))
```

### 2. 清除 ClojureBridge 缓存

如果你的函数通过 `ClojureBridge` 调用，需要清除缓存:

```clojure
;; 清除特定命名空间的缓存
(com.fabriclj.ClojureBridge/clearCache "com.mymod.hooks")

;; 或清除所有缓存
(com.fabriclj.ClojureBridge/clearCache nil)
```

### 3. 测试新代码

在游戏中触发相关逻辑，查看效果。

## 常用调试命令

### 查看命名空间

```clojure
;; 列出所有已加载的命名空间
(all-ns)

;; 查看命名空间中的公开符号
(keys (ns-publics 'com.mymod.core))

;; 查看函数的源码（如果有）
(clojure.repl/source my-function)
```

### 检查状态

```clojure
;; 查看 atom 内容
@player-data

;; 查看 mod 是否加载
(com.fabriclj.core/mod-loaded? "mymod")

;; 查看当前平台
(com.fabriclj.core/platform-name)
```

### 重新加载代码

```clojure
;; 重新加载命名空间
(require 'com.mymod.hooks :reload)

;; 强制重新加载（包括依赖）
(require 'com.mymod.hooks :reload-all)
```

### 游戏交互

```clojure
;; 获取客户端实例
(def mc (com.fabriclj.client/minecraft))

;; 获取当前玩家
(def player (com.fabriclj.client/player))

;; 获取玩家位置
(when player
  [(.getX player) (.getY player) (.getZ player)])

;; 发送聊天消息（仅客户端）
(when-let [p (com.fabriclj.client/player)]
  (.displayClientMessage p
    (net.minecraft.network.chat.Component/literal "Hello from REPL!")
    false))
```

## 调试技巧

### 使用 comment 块

在源文件中添加 `comment` 块存放调试代码:

```clojure
(ns com.mymod.hooks)

(defn on-jump [player ci]
  ;; 实际逻辑
  )

(comment
  ;; 调试代码，不会被执行
  ;; 但可以在 REPL 中逐个求值

  ;; 测试函数
  (on-jump nil nil)

  ;; 清除缓存
  (com.fabriclj.ClojureBridge/clearCache "com.mymod.hooks")

  ;; 检查状态
  @some-atom
  )
```

### 条件日志

```clojure
(defn debug [& args]
  (when (com.fabriclj.core/dev-mode?)
    (apply println "[DEBUG]" args)))

(defn on-jump [player ci]
  (debug "Player jump:" (.getName (.getGameProfile player)))
  ;; ...
  )
```

### 异常捕获

```clojure
(defn safe-invoke [f & args]
  (try
    (apply f args)
    (catch Exception e
      (println "Error:" (.getMessage e))
      (.printStackTrace e))))
```

## 常见问题

### Q: 连接失败

**检查项: **
1. 游戏是否已完全启动？
2. 控制台是否显示 nREPL 启动消息？
3. 端口是否被占用？

```clojure
;; 检查 nREPL 状态
(com.fabriclj.nrepl/server-running?)

;; 获取当前端口
(com.fabriclj.nrepl/get-port)
```

### Q: 函数修改后无效

**可能原因: **
1. 使用了 `defonce`（只执行一次）
2. ClojureBridge 缓存未清除
3. 命名空间未重新加载

**解决: **
```clojure
;; 清除 ClojureBridge 缓存
(com.fabriclj.ClojureBridge/clearCache nil)

;; 强制重新加载
(require 'com.mymod.hooks :reload-all)
```

### Q: 游戏崩溃

如果在 REPL 中执行代码导致游戏崩溃:

1. 检查是否在错误的线程执行了渲染代码
2. 确保不在 tick 循环中执行耗时操作
3. 避免修改游戏核心状态

```clojure
;; 安全地在主线程执行
(let [mc (com.fabriclj.client/minecraft)]
  (.execute mc
    #(println "Running on main thread")))
```

## 生产环境

**【重要】** 在发布 mod 时禁用 nREPL:

```clojure
(defn init []
  ;; dev-mode? 在生产环境返回 false
  (when (com.fabriclj.core/dev-mode?)
    (nrepl/start-server!)))
```

或完全移除 nREPL 代码。

## 下一步

- [故障排查](troubleshooting.md) - 更多问题解决方案
- [开发者指南](dev-guide.md) - 最佳实践
