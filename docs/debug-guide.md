# 调试指南

本文档详细介绍 Arclojure 模组的调试方法，包括 nREPL 连接、Java 调试器使用以及常见问题排查。

## 调试方案概览

| 方案 | 适用场景 | 优势 | 劣势 |
|------|----------|------|------|
| nREPL + Calva | Clojure 业务逻辑调试 | 热重载、即时反馈 | 无法调试 Java 层 |
| Java Debugger | Mixin/入口点调试 | 断点、变量检查 | 需要重启生效 |
| 混合调试 | 复杂问题定位 | 全栈覆盖 | 配置复杂 |

## 方案一：nREPL 驱动开发（推荐）

nREPL 是 Clojure 开发的核心工具，允许在游戏运行时实时修改代码。

### 启动游戏

```bash
# Fabric
.\gradlew.bat :fabric:runClient

# Forge
.\gradlew.bat :forge:runClient
```

等待控制台输出：

```
[Arclojure/nREPL] Server started on 127.0.0.1:7888
```

### 使用 VS Code + Calva 连接

1. 按 `Ctrl+Shift+P` 打开命令面板
2. 输入并选择 `Calva: Connect to a running REPL in the project`
3. 选择项目类型：`Generic`
4. 输入连接地址：`localhost:7888`
5. 等待连接成功提示

### 基本操作

| 操作 | 快捷键 | 说明 |
|------|--------|------|
| 求值当前表达式 | `Alt+Enter` | 执行光标所在的表达式 |
| 求值整个文件 | `Ctrl+Alt+C Enter` | 重新加载整个命名空间 |
| 求值选中区域 | `Ctrl+Enter` | 执行选中的代码 |
| 打开 REPL 窗口 | `Ctrl+Alt+C O` | 打开交互式 REPL |
| 查看文档 | `Ctrl+Alt+C D` | 显示函数文档 |
| 查找定义 | `Ctrl+Alt+C .` | 跳转到定义 |

### 热重载工作流

```clojure
;; 1. 修改函数
(defn on-player-jump
  [^Player player ^CallbackInfo ci]
  (println "玩家跳跃了！"))  ; 添加新逻辑

;; 2. 按 Alt+Enter 求值
;; 3. 在游戏中测试 - 立即生效！
```

### 在 REPL 中直接操作游戏

连接 nREPL 后，你可以在 REPL 窗口直接与游戏交互：

```clojure
;; 切换到客户端命名空间
(in-ns 'com.arclojure.client)

;; 获取玩家实例
(def player (get-player))

;; 查看玩家位置
(.position player)

;; 发送聊天消息
(send-chat-message "Hello from REPL!")

;; 获取玩家生命值
(.getHealth player)
```

### 调试技巧

#### 添加临时日志

```clojure
(defn some-function [arg]
  (println "Debug: arg =" arg)  ; 临时日志
  (let [result (process arg)]
    (println "Debug: result =" result)
    result))
```

#### 使用 tap> 进行非侵入式调试

```clojure
;; 设置 tap 处理器
(add-tap println)

;; 在代码中使用 tap>
(defn on-player-tick [player]
  (tap> {:event :tick :player (.getName player)})
  ;; ... 其他逻辑
  )
```

#### 检查运行时状态

```clojure
;; 查看已注册的物品
(in-ns 'com.arclojure.registry)
items  ; 查看物品注册表

;; 查看 nREPL 状态
(in-ns 'com.arclojure.nrepl)
(server-running?)
(get-port)
```

## 方案二：Java 调试器

用于调试 Java 层代码，如 Mixin 注入、入口点初始化等。

### 配置 VS Code

1. 确保安装了 Java Extension Pack
2. 运行 `.\gradlew.bat genVsCodeRuns`（如果可用）
3. 或手动创建 `.vscode/launch.json`：

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Minecraft Client (Fabric)",
      "request": "launch",
      "mainClass": "net.fabricmc.devlaunchinjector.Main",
      "projectName": "fabric",
      "args": "",
      "vmArgs": "-Dfabric.development=true"
    }
  ]
}
```

### 使用 Gradle 任务调试

更简单的方法是使用 Gradle 的调试任务：

```bash
# 启动可调试的客户端
.\gradlew.bat :fabric:runClient --debug-jvm
```

然后在 VS Code 中附加调试器：
1. 创建 `Attach` 类型的调试配置
2. 端口设为 5005

### 断点调试

1. 在 Java 文件中设置断点
2. 启动调试会话
3. 触发相应代码路径
4. 检查变量、调用栈

## 方案三：混合调试

同时使用 Java 调试器和 nREPL。

### 配置步骤

1. 使用 `--debug-jvm` 启动游戏
2. 附加 Java 调试器
3. 连接 nREPL

### 跨语言调试流程

1. 在 Java Mixin 中设置断点
2. 断点触发后，检查 Java 层状态
3. 在 nREPL 中检查 Clojure 层状态
4. 继续执行或修改 Clojure 代码

## 性能调试

### 反射警告

Clojure 的反射调用会严重影响性能。启用警告：

```clojure
(set! *warn-on-reflection* true)
```

修复方法 - 添加类型提示：

```clojure
;; 有反射（慢）
(.setPos entity x y z)

;; 无反射（快）
(.setPos ^net.minecraft.world.entity.Entity entity x y z)
```

### 热路径优化

在每 Tick 调用的代码中：

```clojure
;; 避免：惰性序列
(defn on-player-tick [player]
  (doseq [item (filter some-pred? (get-items player))]
    (process item)))

;; 推荐：loop/recur
(defn on-player-tick [player]
  (loop [items (get-items player)]
    (when-let [item (first items)]
      (when (some-pred? item)
        (process item))
      (recur (rest items)))))
```

### 使用 time 宏测量

```clojure
(time
  (dotimes [_ 1000]
    (your-function)))
```

## 常见调试场景

### 场景1：模组加载失败

**症状**：游戏启动时崩溃，日志显示 Clojure 相关错误

**排查步骤**：
1. 检查日志中的具体错误信息
2. 确认 `setContextClassLoader` 调用正确
3. 验证 Clojure 命名空间路径与文件路径匹配
4. 检查 ShadowJar 重定位配置

### 场景2：nREPL 无法连接

**症状**：Calva 连接超时

**排查步骤**：
1. 确认游戏已完全启动
2. 检查端口是否被占用：`netstat -an | findstr 7888`
3. 确认在开发模式下运行
4. 检查防火墙设置

### 场景3：代码修改不生效

**症状**：REPL 中求值成功，但游戏行为未改变

**可能原因**：
1. 函数被缓存 - 使用 `defonce` 定义的需要重启
2. 命名空间未正确加载 - 使用 `(require 'ns :reload)`
3. Java 层缓存了函数引用 - 检查 ClojureHooks.java

### 场景4：Mixin 不工作

**症状**：Mixin 钩子未被调用

**排查步骤**：
1. 确认 Mixin 类已添加到 `arclojure.mixins.json`
2. 检查方法签名是否正确
3. 使用 Java 调试器在 Mixin 方法中设断点
4. 查看 Mixin 应用日志

## nREPL 配置进阶

### 修改端口

编辑 `common/src/main/clojure/com/arclojure/nrepl.clj`：

```clojure
(def default-port 7889)  ; 修改默认端口
```

### 禁用 nREPL（生产环境）

nREPL 仅在开发模式下启动，生产构建不会包含此功能。如需在开发时也禁用：

```clojure
(defn init []
  ;; 注释掉或删除此行
  ;; (when (Platform/isDevelopmentEnvironment)
  ;;   (nrepl/start-server!))
  )
```

### 自定义 nREPL 中间件

```clojure
(defn start-server! []
  (nrepl/start-server
    :port 7888
    :bind "127.0.0.1"
    :handler (nrepl/default-handler
               ;; 添加自定义中间件
               )))
```

## 日志配置

### 调整日志级别

在运行参数中添加：

```bash
-Dlog4j.configurationFile=log4j2-debug.xml
```

### 创建调试日志配置

创建 `run/log4j2-debug.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] [%logger]: %msg%n"/>
        </Console>
    </Appenders>
    <Loggers>
        <Logger name="com.arclojure" level="DEBUG"/>
        <Root level="INFO">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

## 资源链接

| 资源 | 链接 |
|------|------|
| Calva 用户指南 | https://calva.io/ |
| nREPL 文档 | https://nrepl.org/ |
| Architectury 文档 | https://docs.architectury.dev/ |
| Mixin 文档 | https://github.com/SpongePowered/Mixin/wiki |
