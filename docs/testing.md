# 测试指南

本文档介绍如何在发布前测试 fabric-language-clojure 语言支持库。

## 测试环境

### 方式 1: 使用 example 模块（推荐）

项目包含一个完整的测试 mod:

```bash
# 启动测试客户端
.\gradlew.bat :example:runClient

# 查看日志确认加载成功
# 应该看到:
# [fabric-language-clojure] Language support library initialized
# [ExampleMod] Initializing...
# [ExampleMod] Items registered!
# [nREPL] Server started on 127.0.0.1:7888
```

详细说明参见 [example/README.md](../example/README.md)

### 方式 2: 创建独立测试项目

1. 先发布到本地 Maven:
   ```bash
   .\gradlew.bat publishToMavenLocal
   ```

2. 创建新项目，在 `build.gradle` 中添加:
   ```groovy
   repositories {
       mavenLocal()
       maven { url = 'https://clojars.org/repo' }
   }

   dependencies {
       modImplementation "com.fabriclj:fabric-language-clojure:1.0.0"
   }
   ```

3. 按照 [快速开始指南](quick-start.md) 配置项目

## 测试检查清单

### 核心功能测试

- [ ] Clojure 运行时正常初始化
  ```
  [ClojureRuntime] Clojure runtime bootstrapped successfully
  ```

- [ ] Clojure 代码正常加载和执行
  ```clojure
  (println "Test from Clojure!")
  ```

- [ ] Java 可以调用 Clojure 函数
  ```java
  ClojureRuntime.invoke("com.example.core", "init");
  ```

### ClojureBridge 测试

- [ ] Mixin 可以调用 Clojure 钩子
  ```java
  ClojureBridge.invoke("com.example.hooks", "on-jump", player, ci);
  ```

- [ ] 钩子函数正常执行
- [ ] 可以取消事件（`ci.cancel()`）
- [ ] 可以修改返回值

### 注册表测试

- [ ] 物品注册成功
  ```clojure
  (reg/defitem items test-item ...)
  (reg/register-all! items)
  ```

- [ ] 物品在游戏中可见（/give 命令）
- [ ] 方块注册成功
- [ ] 实体注册成功

### nREPL 测试

- [ ] nREPL 服务器启动成功
- [ ] 可以从 REPL 客户端连接
- [ ] 可以在 REPL 中求值代码
- [ ] 可以热重载函数定义
- [ ] ClojureBridge 缓存清除功能正常

### 平台兼容性测试

- [ ] 在 Fabric 平台运行正常
- [ ] 平台检测函数正确
  ```clojure
  (com.fabriclj.core/fabric?) ;; => true
  (com.fabriclj.core/forge?)  ;; => false
  ```

### 客户端/服务器测试

- [ ] 客户端初始化正常
- [ ] 专用服务器运行正常
- [ ] 客户端工具函数正常
  ```clojure
  (com.fabriclj.client/minecraft)
  (com.fabriclj.client/player)
  ```

## 性能测试

### 启动时间

测量 Clojure 运行时初始化时间:

```clojure
;; 在 ClojureRuntime.java 中添加时间记录
long start = System.currentTimeMillis();
// ... 初始化代码 ...
long elapsed = System.currentTimeMillis() - start;
LOGGER.info("Clojure runtime initialized in {}ms", elapsed);
```

**预期值**: < 1000ms

### 运行时性能

测试热路径中的 ClojureBridge 调用:

```java
long start = System.nanoTime();
for (int i = 0; i < 1000; i++) {
    ClojureBridge.invoke("test", "noop");
}
long elapsed = System.nanoTime() - start;
System.out.println("1000 calls: " + elapsed / 1000000.0 + "ms");
```

**预期值**: < 10ms/1000 次调用

### 内存占用

查看 Clojure 运行时内存占用:

```bash
# 在游戏中按 F3 查看内存使用
# 或使用 JVM 监控工具
```

**预期值**: Clojure 运行时 < 50MB

## 兼容性测试

### Mod 兼容性

测试与其他常见 mod 的兼容性:

- [ ] Fabric API
- [ ] Architectury API
- [ ] 其他使用 Mixin 的 mod

### Minecraft 版本

测试不同的 Minecraft 版本:

- [ ] 1.20.1
- [ ] 1.20.4
- [ ] 1.21（如果适用）

## 回归测试

在修改代码后运行完整测试:

```bash
# 1. 清理构建
.\gradlew.bat clean

# 2. 重新构建
.\gradlew.bat build

# 3. 运行测试 mod
.\gradlew.bat :example:runClient

# 4. 验证所有功能正常
```

## 已知问题

### 语言适配器在 Architectury 开发环境中无法使用

**状态**: 已知限制

**影响**: 无法在开发环境使用 `"adapter": "clojure"`

**解决方案**: 使用 Java 入口类 + `ClojureRuntime`

**测试**: 在打包后的 JAR 中验证语言适配器功能

## 自动化测试（未来）

计划添加的自动化测试:

- [ ] 单元测试（JUnit + Clojure test）
- [ ] 集成测试（模拟游戏环境）
- [ ] 性能基准测试
- [ ] CI/CD 流水线

## 报告问题

如果发现问题:

1. 收集信息:
   - 日志文件（`logs/latest.log`）
   - Minecraft 版本
   - fabric-language-clojure 版本
   - 复现步骤

2. 提交 Issue 到 GitHub

3. 提供最小复现示例

## 下一步

- [快速开始](quick-start.md) - 基本使用
- [开发者指南](dev-guide.md) - 深入开发
- [故障排查](troubleshooting.md) - 问题解决
