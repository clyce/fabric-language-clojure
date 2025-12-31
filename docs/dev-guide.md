# fabric-language-clojure 开发者指南

本文档面向希望深入了解 fabric-language-clojure 并进行高级开发的模组开发者。

## 目录

- [架构概述](#架构概述)
- [构建和开发工作流](#构建和开发工作流)
- [语言适配器详解](#语言适配器详解)
- [ClojureBridge 使用指南](#clojurebridge-使用指南)
- [注册表系统](#注册表系统)
- [nREPL 开发流程](#nrepl-开发流程)
- [最佳实践](#最佳实践)
- [性能优化](#性能优化)

---

## 架构概述

fabric-language-clojure 采用分层架构：

```
┌────────────────────────────────────────────────────────────┐
│  用户 Mod                                                   │
│  ├─ Clojure 业务逻辑 (*.clj)                                │
│  └─ Java Mixin 类（必需）                                   │
├────────────────────────────────────────────────────────────┤
│  fabric-language-clojure 语言支持库                         │
│  ├─ ClojureLanguageAdapter (语言适配器)                     │
│  ├─ ClojureBridge (Mixin 桥接工具)                          │
│  ├─ ClojureRuntime (运行时管理)                             │
│  └─ 工具命名空间 (core, registry, nrepl, client)            │
├────────────────────────────────────────────────────────────┤
│  Clojure 运行时 (捆绑)                                       │
│  ├─ clojure.core                                            │
│  └─ nrepl                                                   │
├────────────────────────────────────────────────────────────┤
│  Fabric Loader                                              │
│  └─ LanguageAdapter 接口                                    │
└────────────────────────────────────────────────────────────┘
```

### 为什么 Mixin 必须用 Java？

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| Mixin 注入 | 需要编译期确定的字节码结构 | Java Mixin 类 |
| 类加载顺序 | Mixin 在 Clojure 加载前应用 | Java 存根 + Clojure 实现 |
| ASM 兼容性 | Mixin 使用 ASM 操作字节码 | 避免动态类生成 |

---

## 构建和开发工作流

### 正常开发流程（90% 的情况）

在日常开发中，修改代码后只需要简单构建即可：

```powershell
# 构建整个项目
.\gradlew.bat build -x checkClojure -x compileClojure

# 或者只构建特定模块
.\gradlew.bat :fabric:build -x checkClojure -x compileClojure
.\gradlew.bat :example:build -x checkClojure -x compileClojure
```

**何时使用**：
- 修改 Clojure 代码（`.clj` 文件）
- 修改 Java 代码（`.java` 文件）
- 修改资源文件（`fabric.mod.json`、mixins 配置等）
- 更新依赖版本（`gradle.properties`）

**不需要**：
- ❌ 清理缓存
- ❌ 停止 Gradle Daemon
- ❌ 关闭 IDE
- ❌ 分阶段构建

### 需要清理缓存的情况（少见）

只有在以下**特殊情况**下才需要清理：

#### 1. 大规模重命名（包名、项目名）

```powershell
# 1. 完全退出 Cursor（右键任务栏图标 → 退出）
# 2. 清理所有缓存
Remove-Item -Recurse -Force .gradle, common\build, fabric\build, example\build
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\fabric-loom"

# 3. 重新构建
.\gradlew.bat build -x checkClojure -x compileClojure
```

**何时需要**：
- ✅ 包名从 `com.arclojure` 改为 `com.fabriclj`
- ✅ 项目名从 `arclojure` 改为 `fabric-language-clojure`
- ✅ 模组 ID 变化

#### 2. Gradle 配置重大变更

```powershell
# 停止 Daemon 并刷新依赖
.\gradlew.bat --stop
Remove-Item -Recurse -Force .gradle
.\gradlew.bat --refresh-dependencies build -x checkClojure -x compileClojure
```

**何时需要**：
- ✅ 修改 `settings.gradle` 中的项目包含（`include`）
- ✅ 修改 `enabled_platforms` 配置
- ✅ 更改 Shadow JAR 的 `relocate` 规则
- ✅ 添加/删除子项目

#### 3. Loom 映射损坏

```powershell
# 只清理 Loom 缓存
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\fabric-loom"
.\gradlew.bat build -x checkClojure -x compileClojure
```

**何时需要**：
- ✅ 看到 `Failed to setup mappings` 错误
- ✅ Minecraft 版本切换后映射错误
- ✅ 构建被异常中断（Ctrl+C、断电等）

### Cursor IDE 文件锁问题

**问题表现**：
```
java.nio.file.FileSystemException: mappings.jar: 另一个程序正在使用此文件
```

**原因**：Cursor 的 Java Language Server 会索引 Gradle 缓存中的 JAR 文件。

**解决方案（按推荐顺序）**：

#### 方案 1：使用独立终端（推荐）
- 不要在 Cursor 的集成终端中运行构建
- 使用 Windows PowerShell 或 CMD 独立窗口
- 这样 Cursor 的 Java 进程不会锁定文件

#### 方案 2：暂时禁用 Java 扩展
1. 在 Cursor 中按 `Ctrl+Shift+X`
2. 搜索"Java"
3. 禁用"Language Support for Java(TM) by Red Hat"
4. 运行构建
5. 构建完成后重新启用

#### 方案 3：完全退出 Cursor
- 右键任务栏的 Cursor 图标 → 退出（不是关闭窗口）
- 运行构建
- 重新打开 Cursor

**最佳实践**：在开发期间，使用方案 1（独立终端）来避免这个问题。

### example 模块的依赖问题

**问题表现**：
```
Failed to read metadata from fabric-language-clojure-fabric-1.0.0-dev.jar
NoSuchFileException
```

**原因**：`example` 项目依赖 `fabric` 模块的输出 JAR。在执行 `clean` 后，JAR 不存在。

**解决方案**：

```powershell
# 方案 1：不要同时 clean 所有模块
.\gradlew.bat :fabric:clean :fabric:build -x checkClojure -x compileClojure
.\gradlew.bat :example:build -x checkClojure -x compileClojure

# 方案 2：临时注释掉 example（如果需要完全清理）
# 1. 编辑 settings.gradle，注释掉 include 'example'
# 2. 构建基础模块
.\gradlew.bat build -x checkClojure -x compileClojure
# 3. 恢复 settings.gradle
# 4. 停止 Daemon 让配置生效
.\gradlew.bat --stop
# 5. 构建 example
.\gradlew.bat :example:build -x checkClojure -x compileClojure
```

**最佳实践**：避免对多项目同时执行 `clean`，除非真的需要完全重建。

### 快速参考表

| 场景 | 命令 | 需要清理？ | 需要关闭 Cursor？ |
|------|------|-----------|------------------|
| 修改 `.clj` 文件 | `.\gradlew build -x checkClojure -x compileClojure` | ❌ | ❌ |
| 修改 `.java` 文件 | `.\gradlew build -x checkClojure -x compileClojure` | ❌ | ❌ |
| 修改 `fabric.mod.json` | `.\gradlew build -x checkClojure -x compileClojure` | ❌ | ❌ |
| 更新依赖版本 | `.\gradlew --refresh-dependencies build` | ❌ | ❌ |
| 重命名包名 | 见"大规模重命名"章节 | ✅ | ✅ |
| 修改 `settings.gradle` | `.\gradlew --stop` + rebuild | ✅ `.gradle` | ❌ |
| 切换 Minecraft 版本 | 清理 Loom 缓存 | ✅ Loom | ❌ |
| 构建报文件锁错误 | 使用独立终端 | ❌ | ⚠️ 或禁用 Java 扩展 |

### 多项目构建顺序

本项目使用 Architectury 多项目结构：

```
fabric-language-clojure/
├── common/     ← 共享代码
├── fabric/     ← Fabric 平台实现（依赖 common）
└── example/    ← 示例 mod（依赖 fabric）
```

**依赖关系**：`example` → `fabric` → `common`

**构建顺序**（Gradle 自动处理）：
1. `common` 先编译
2. `fabric` 依赖 `common` 的输出
3. `example` 依赖 `fabric` 的输出

**正常情况下不需要手动指定顺序**，Gradle 会自动解析依赖图。

---

## 语言适配器详解

### 入口点格式

| 格式 | 示例 | 说明 |
|------|------|------|
| 函数引用 | `"com.mymod.core/init"` | 调用无参函数 |
| 命名空间 | `"com.mymod.core"` | 调用 `-main` 函数 |
| 变量引用 | `"com.mymod.core/initializer"` | 解引用变量 |

### 函数引用（推荐）

```json
{
  "adapter": "clojure",
  "value": "com.mymod.core/init"
}
```

```clojure
(ns com.mymod.core)

(defn init []
  (println "Mod initialized!"))
```

### 变量引用

```json
{
  "adapter": "clojure",
  "value": "com.mymod.core/initializer"
}
```

```clojure
(ns com.mymod.core)

(def initializer
  (reify net.fabricmc.api.ModInitializer
    (onInitialize [this]
      (println "Mod initialized!"))))
```

---

## ClojureBridge 使用指南

`ClojureBridge` 是从 Java Mixin 调用 Clojure 代码的桥梁。

### 基本用法

```java
// Java Mixin
ClojureBridge.invoke("com.mymod.hooks", "on-event", arg1, arg2);
```

```clojure
;; Clojure 实现
(ns com.mymod.hooks)

(defn on-event [arg1 arg2]
  ;; 处理逻辑
  )
```

### 方法签名

```java
// 无参数
Object invoke(String namespace, String function)

// 1-4 个参数
Object invoke(String namespace, String function, Object arg1)
Object invoke(String namespace, String function, Object arg1, Object arg2)
Object invoke(String namespace, String function, Object arg1, Object arg2, Object arg3)
Object invoke(String namespace, String function, Object arg1, Object arg2, Object arg3, Object arg4)

// 可变参数
Object invokeVarargs(String namespace, String function, Object... args)
```

### 取消事件

```java
@Inject(method = "jump", at = @At("HEAD"), cancellable = true)
private void onJump(CallbackInfo ci) {
    ClojureBridge.invoke("com.mymod.hooks", "on-jump",
                         (Player)(Object)this, ci);
}
```

```clojure
(defn on-jump [^Player player ^CallbackInfo ci]
  (when (should-cancel? player)
    (.cancel ci)))  ;; 取消跳跃
```

### 返回值修改

```java
@Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
private void modifyHealth(CallbackInfoReturnable<Float> cir) {
    Object result = ClojureBridge.invoke("com.mymod.hooks", "modify-health",
                                         (LivingEntity)(Object)this, cir);
    if (result instanceof Number) {
        cir.setReturnValue(((Number) result).floatValue());
    }
}
```

```clojure
(defn modify-health [entity cir]
  ;; 返回新的最大生命值
  40.0)
```

### 缓存和热重载

```clojure
;; 开发时清除缓存以重新加载函数
(com.fabriclj.ClojureBridge/clearCache "com.mymod.hooks")

;; 或清除所有缓存
(com.fabriclj.ClojureBridge/clearCache nil)
```

---

## 注册表系统

`com.fabriclj.registry` 提供简洁的注册 DSL。

### 创建注册表

```clojure
(require '[com.fabriclj.registry :as reg])

(def items (reg/create-registry "mymod" :item))
(def blocks (reg/create-registry "mymod" :block))
```

支持的注册表类型：
- `:item` - 物品
- `:block` - 方块
- `:entity` - 实体
- `:block-entity` - 方块实体
- `:menu` - GUI 菜单
- `:recipe` - 配方
- `:sound` - 音效
- `:particle` - 粒子
- `:creative-tab` - 创造模式标签

### 注册物品

```clojure
;; 使用宏
(reg/defitem items my-sword
  (Item. (-> (Item$Properties.)
             (.stacksTo 1))))

;; 使用函数
(def my-gem
  (reg/register items "my_gem"
    (fn [] (Item. (Item$Properties.)))))
```

### 执行注册

```clojure
(defn init []
  (reg/register-all! items blocks))
```

---

## nREPL 开发流程

### 启动 nREPL

```clojure
(require '[com.fabriclj.nrepl :as nrepl]
         '[com.fabriclj.core :as lib])

(when (lib/dev-mode?)
  (nrepl/start-server!))        ;; 默认端口 7888
  ;; 或指定端口
  ;; (nrepl/start-server! 9999)
```

### 连接方式

**VS Code + Calva：**
1. `Ctrl+Shift+P` → `Calva: Connect to a running REPL`
2. 选择 `Generic`
3. 输入 `localhost:7888`

**命令行：**
```bash
lein repl :connect 7888
# 或
clj -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.3.0"}}}' -M -m nrepl.cmdline --connect --host localhost --port 7888
```

### 热重载工作流

```clojure
;; 在 REPL 中修改函数
(in-ns 'com.mymod.hooks)

(defn on-jump [player ci]
  ;; 新逻辑立即生效
  (println "New jump behavior!"))

;; 清除缓存（如果使用 ClojureBridge）
(com.fabriclj.ClojureBridge/clearCache "com.mymod.hooks")
```

---

## 最佳实践

### 命名空间组织

```
com.mymod/
├── core.clj           # 主入口
├── client.clj         # 客户端入口
├── hooks.clj          # Mixin 钩子
├── content/
│   ├── items.clj      # 物品定义
│   ├── blocks.clj     # 方块定义
│   └── entities.clj   # 实体定义
└── util/
    ├── nbt.clj        # NBT 工具
    └── player.clj     # 玩家工具
```

### 类型提示

始终为 Java 互操作添加类型提示：

```clojure
;; ❌ 慢：产生反射调用
(defn get-name [player]
  (.getName player))

;; ✅ 快：无反射
(defn get-name [^Player player]
  (.getName player))

;; 启用反射警告
(set! *warn-on-reflection* true)
```

### 状态管理

```clojure
;; 使用 atom 管理状态
(defonce player-data (atom {}))

;; 不要存储实体对象，存储 UUID
(defn track-player! [^Player player]
  (swap! player-data assoc (.getStringUUID player) {:tracked-at (System/currentTimeMillis)}))
```

### 延迟初始化

```clojure
;; 使用 delay 推迟昂贵的初始化
(def ^:private config-data
  (delay
    (load-config-from-disk)))

(defn get-config []
  @config-data)  ;; 首次访问时才加载
```

---

## 性能优化

### 热路径优化

识别频繁调用的代码（tick、render 等）：

```clojure
;; ❌ 慢：每次创建序列
(defn process-entities [entities]
  (doseq [e (filter alive? entities)]
    (update-entity e)))

;; ✅ 快：使用 reduce
(defn process-entities [entities]
  (reduce (fn [_ e]
            (when (alive? e)
              (update-entity e)))
          nil entities))
```

### 避免装箱

```clojure
;; ❌ 慢：装箱开销
(defn calculate [base mult]
  (* base mult))

;; ✅ 快：原生类型
(defn calculate ^double [^double base ^double mult]
  (* base mult))
```

### 缓存常用数据

```clojure
(def ^:const TICK_RATE 20)

(defonce item-cache (atom {}))

(defn get-item [id]
  (or (@item-cache id)
      (let [item (expensive-lookup id)]
        (swap! item-cache assoc id item)
        item)))
```

---

## 调试技巧

### 使用 comment 块

```clojure
(comment
  ;; 这些代码不会执行，但可以在 REPL 中逐个求值

  ;; 测试函数
  (on-jump nil nil)

  ;; 查看状态
  @player-data

  ;; 重新加载命名空间
  (require 'com.mymod.hooks :reload)
  )
```

### 条件日志

```clojure
(defn debug-log [& args]
  (when (com.fabriclj.core/dev-mode?)
    (apply println "[DEBUG]" args)))
```

---

## 下一步

- [调试指南](debug-guide.md) - REPL 连接、调试技巧
- [故障排查](troubleshooting.md) - 常见问题解决
- [示例代码](../examples/) - 完整示例项目
