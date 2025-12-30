# Forge 平台状态说明

## 当前状态：⚠️ **已知问题**

Forge 平台目前由于 Architectury Loom 的 TinyRemapper 兼容性问题暂时无法构建。

## 问题描述

### 错误信息
```
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring project ':forge'.
> Failed to setup Minecraft, java.lang.RuntimeException: Failed to remap 5 mods

Caused by: java.lang.RuntimeException: Failed to remap: ModDependency{group='dev.architectury', name='architectury-forge', version='9.2.14', classifier='null'}

Caused by: java.util.concurrent.ExecutionException: java.lang.ArrayIndexOutOfBoundsException
```

### 根本原因

TinyRemapper（Loom 的依赖重映射工具）在处理以下依赖时崩溃：
- `dev.architectury:architectury-forge:9.2.14`

这是一个已知的 Architectury Loom 1.11-SNAPSHOT 与 Forge 1.20.1 的兼容性问题。

## 已尝试的解决方案

### ❌ 方案 1：清理 Loom 缓存
```bash
Remove-Item -Path "$env:USERPROFILE\.gradle\caches\fabric-loom" -Recurse -Force
.\gradlew.bat :forge:build
```
**结果：** 无效，问题持续存在

### ❌ 方案 2：移除 Clojure 包重命名
修改 `forge/build.gradle`，移除：
```groovy
relocate 'clojure', 'com.arclojure.shaded.clojure'
relocate 'nrepl', 'com.arclojure.shaded.nrepl'
```
**结果：** 无效，问题仍然存在

### ❌ 方案 3：禁用依赖传递
```groovy
common(project(path: ':common', configuration: 'namedElements')) { transitive = false }
```
**结果：** 无效

## 可能的解决方案

### 🔄 方案 A：降级 Loom 版本（未测试）

修改 `build.gradle`：
```diff
plugins {
-    id 'dev.architectury.loom' version '1.11-SNAPSHOT' apply false
+    id 'dev.architectury.loom' version '1.7' apply false
}
```

**风险：** Loom 1.7 可能不支持某些新特性或需要 Java 17

### 🔄 方案 B：降级 Architectury API（未测试）

修改 `gradle.properties`：
```diff
- architectury_api_version = 9.2.14
+ architectury_api_version = 9.0.0
```

**风险：** 可能缺少某些 API

### 🔄 方案 C：升级到 Minecraft 1.20.4（重大更改）

等待 Architectury 1.20.4 版本，该版本可能修复了这个问题。

**工作量：** 高，需要更新所有依赖和 Mixin

### ✅ 方案 D：暂时使用 Fabric 开发（推荐）

**优势：**
- Fabric 平台完全可用
- 90% 的代码是跨平台的（在 `common` 模块）
- 可以在 Fabric 上完成大部分开发工作
- 等待 Loom 或 Architectury 修复问题后再启用 Forge

**如何切换回 Forge：**
1. 编辑 `settings.gradle`：
```groovy
include 'forge'  // 取消注释
```

2. 清理并重新构建：
```bash
.\gradlew.bat clean :forge:build
```

## 当前推荐方案

**使用 Fabric 进行开发**，原因：

1. **代码复用率高**：
   - 所有 Clojure 代码在 `common` 模块
   - Mixin 代码在 `common` 模块
   - 仅平台特定的 Java 引导代码在各自模块

2. **功能完整**：
   - Clojure 运行时 ✅
   - nREPL 服务器 ✅
   - 热重载 ✅
   - Mixin 支持 ✅
   - 内容注册 ✅

3. **迁移成本低**：
   - 一旦 Forge 问题解决，只需取消注释 `settings.gradle` 即可

## Forge 特有功能的处理

如果需要使用 Forge 特有 API：

### 策略 1：条件编译
```java
// common/src/main/java/com/arclojure/ModMain.java
public static void init() {
    if (Platform.isForge()) {
        // 调用 Forge 特有逻辑
        ForgeSpecific.init();
    }
}
```

### 策略 2：使用 Architectury API
大多数常用功能 Architectury 已经抽象：
- 事件系统
- 网络通信
- 配置管理
- GUI/菜单

参考：[Architectury API 文档](https://docs.architectury.dev/)

### 策略 3：平台特定实现

在 `forge` 模块添加 Forge 特有代码：
```java
// forge/src/main/java/com/arclojure/forge/ForgeSpecific.java
public class ForgeSpecific {
    public static void init() {
        // Forge 专属逻辑
    }
}
```

使用 Architectury 的 `ExpectPlatform` 注解进行跨平台调用。

## 监控上游修复

- **Architectury Loom**: https://github.com/architectury/architectury-loom/issues
- **Architectury API**: https://github.com/architectury/architectury-api/issues
- **Forge**: https://github.com/MinecraftForge/MinecraftForge/issues

## 社区报告

如果你想帮助解决这个问题，可以：

1. 在 Architectury Loom 仓库搜索相关 Issue
2. 如果没有，创建新 Issue 并附上：
   - 完整的错误堆栈
   - Gradle 配置文件
   - `.\gradlew.bat :forge:build --stacktrace` 的输出

## 临时禁用 Forge

在 `settings.gradle` 中：
```groovy
rootProject.name = 'arclojure'

include 'common'
include 'fabric'
// include 'forge'  // 临时禁用
```

**什么时候重新启用：**
- Loom 或 Architectury 发布修复版本
- 切换到支持的 Minecraft 版本
- 找到可靠的 workaround

## 结论

**当前最佳实践：** 使用 Fabric 开发，因为：
- ✅ 完全可用
- ✅ 包含所有核心功能
- ✅ Clojure 热重载工作正常
- ✅ 90% 代码可重用到 Forge
- ⏳ 等待上游修复 Forge 兼容性问题

Forge 支持将在上游问题解决后立即恢复。
