# fabric-language-clojure 故障排查

本文档收集了常见问题及其解决方案。

## 开发环境问题

### Q: 语言适配器在 Architectury 开发环境中无法加载

**症状：**
```
Failed to instantiate language adapter: clojure
ClassNotFoundException: can't load class com.fabriclj.fabric.ClojureLanguageAdapter
```

**原因：** 这是 Architectury Transformer 与 Fabric 语言适配器系统的兼容性问题。在开发环境中，类加载器配置可能不正确。

**解决方案：**

1. **在构建的 JAR 中测试**：语言适配器在打包后的 JAR 中应该正常工作
   ```bash
   ./gradlew build
   # 将 JAR 放入 mods 文件夹测试
   ```

2. **开发时使用 Java 入口类**：在开发期间，使用传统的 Java 入口类：
   ```java
   public class MyMod implements ModInitializer {
       @Override
       public void onInitialize() {
           ClojureRuntime.ensureInitialized(MyMod.class);
           ClojureRuntime.requireNamespace("com.mymod.core");
           ClojureRuntime.invoke("com.mymod.core", "init");
       }
   }
   ```

3. **使用纯 Fabric Loom**：如果不需要 Forge 支持，可以使用纯 Fabric Loom 而不是 Architectury Loom

---

## 构建问题

### Q: Gradle 构建失败，找不到 fabric-language-clojure

**症状：**
```
Could not resolve com.fabriclj:fabric-language-clojure:1.0.0
```

**解决方案：**

1. 确保添加了正确的 Maven 仓库：
   ```groovy
   repositories {
       maven { url = 'https://maven.example.com/releases' }
   }
   ```

2. 如果是本地开发，先构建语言库：
   ```bash
   cd fabric-language-clojure
   ./gradlew publishToMavenLocal
   ```
   然后使用 `mavenLocal()` 仓库。

### Q: Clojure 编译错误

**症状：**
```
Execution failed for task ':compileClojure'.
```

**解决方案：**

禁用 Clojure AOT 编译（运行时加载）：

```groovy
tasks.named('compileClojure') { enabled = false }
tasks.named('checkClojure') { enabled = false }
```

### Q: Java 版本不兼容

**症状：**
```
Dependency requires at least JVM runtime version 21
```

**解决方案：**

确保使用 Java 17 或更高版本：

```bash
java -version
```

如需升级，下载 [Eclipse Temurin](https://adoptium.net/) 或 [Amazon Corretto](https://aws.amazon.com/corretto/)。

### Q: 文件被锁定，无法构建

**症状：**
```
java.nio.file.FileSystemException: mappings.jar: 另一个程序正在使用此文件
```

**原因：** Cursor/VSCode 的 Java Language Server 索引了 Gradle 缓存中的文件。

**解决方案（按推荐顺序）：**

1. **使用独立终端窗口**（推荐）：
   - 不要在 Cursor 的集成终端运行构建
   - 使用 Windows PowerShell 或 CMD 独立窗口

2. **暂时禁用 Java 扩展**：
   - `Ctrl+Shift+X` → 搜索 "Java" → 禁用
   - 运行构建后重新启用

3. **完全退出 IDE**：
   - 右键任务栏图标 → 退出（不是关闭窗口）
   - 运行构建
   - 重新打开 IDE

### Q: example 模块构建失败，找不到 fabric JAR

**症状：**
```
Failed to read metadata from fabric-language-clojure-fabric-1.0.0-dev.jar
NoSuchFileException
```

**原因：** `example` 依赖 `fabric` 模块的输出，但 JAR 尚未构建（通常发生在执行 `clean` 后）。

**解决方案：**

```powershell
# 方案 1：分阶段构建
.\gradlew.bat :common:build :fabric:build -x checkClojure -x compileClojure
.\gradlew.bat :example:build -x checkClojure -x compileClojure

# 方案 2：让 Gradle 自动处理依赖顺序（推荐）
# 如果 settings.gradle 正确配置，直接构建即可
.\gradlew.bat build -x checkClojure -x compileClojure
```

**注意**：如果刚修改了 `settings.gradle`，需要先停止 Gradle Daemon：
```powershell
.\gradlew.bat --stop
```

### Q: 修改代码后还需要清理缓存吗？

**不需要！** 在 90% 的情况下，直接运行构建即可：

```powershell
.\gradlew.bat build -x checkClojure -x compileClojure
```

**只在以下情况需要清理**：
- ✅ 重命名包名或项目名
- ✅ 修改 `settings.gradle` 或 `enabled_platforms`
- ✅ Loom 映射损坏（看到 "Failed to setup mappings" 错误）
- ✅ 切换 Minecraft 版本

详见 [开发指南 - 构建和开发工作流](dev-guide.md#构建和开发工作流)。

### Q: Loom 映射损坏

**症状：**
```
Failed to setup mappings: loom:mappings:layered+hash.40359
```

**原因：** Gradle 缓存损坏（通常由构建被异常中断导致）。

**解决方案：**

```powershell
# 只清理 Loom 缓存
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\fabric-loom"

# 重新构建
.\gradlew.bat build -x checkClojure -x compileClojure
```

### Q: 大规模重构后构建失败

**场景**：重命名包名、项目名、模组 ID 后出现各种奇怪错误。

**解决方案（完全清理）**：

```powershell
# 1. 完全退出 IDE
# 2. 清理所有缓存
Remove-Item -Recurse -Force .gradle
Remove-Item -Recurse -Force common\build, fabric\build, example\build
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\fabric-loom"

# 3. 停止所有 Gradle Daemon
.\gradlew.bat --stop

# 4. 重新构建
.\gradlew.bat build -x checkClojure -x compileClojure
```

---

## 运行时问题

### Q: ClassNotFoundException: clojure.java.api.Clojure

**症状：**
```
java.lang.ClassNotFoundException: clojure.java.api.Clojure
```

**原因：** Clojure 未正确打包或类加载器问题。

**解决方案：**

1. 确保 fabric-language-clojure 作为依赖：
   ```groovy
   modImplementation "com.fabriclj:fabric-language-clojure:1.0.0"
   ```

2. 确保 Clojure 源文件在 JAR 中：
   ```groovy
   processResources {
       from(sourceSets.main.clojure) {
           into 'clojure'
       }
   }
   ```

### Q: 入口点未找到

**症状：**
```
LanguageAdapterException: Failed to create Clojure entrypoint: com.mymod.core/init
```

**可能原因：**

1. 命名空间路径错误
2. 函数名拼写错误
3. Clojure 文件未打包

**检查项：**

```bash
# 检查 JAR 内容
jar tf build/libs/mymod-1.0.0.jar | grep clojure
```

应该看到类似：
```
clojure/com/mymod/core.clj
```

**确保文件路径正确：**
```
src/main/clojure/com/mymod/core.clj  →  命名空间 com.mymod.core
```

### Q: 命名空间加载失败

**症状：**
```
FileNotFoundException: Could not locate com/mymod/core__init.class or com/mymod/core.clj
```

**解决方案：**

1. 检查文件是否存在于正确路径
2. 检查命名空间声明与文件路径是否匹配：
   ```clojure
   ;; 文件: src/main/clojure/com/mymod/core.clj
   (ns com.mymod.core)  ;; 必须匹配
   ```

3. 检查 processResources 配置

### Q: Mixin 钩子不生效

**症状：** ClojureBridge.invoke 调用了但 Clojure 函数没执行。

**解决方案：**

1. 检查命名空间和函数名是否正确：
   ```java
   ClojureBridge.invoke("com.mymod.hooks", "on-jump", player, ci);
   ```

2. 确保命名空间已加载：
   ```clojure
   ;; 在 REPL 中测试
   (require 'com.mymod.hooks)
   (com.mymod.hooks/on-jump nil nil)
   ```

3. 清除缓存：
   ```clojure
   (com.fabriclj.ClojureBridge/clearCache nil)
   ```

---

## nREPL 问题

### Q: nREPL 连接失败

**症状：** Calva 或其他客户端无法连接。

**检查项：**

1. 游戏是否完全启动？
2. 控制台是否显示 nREPL 启动消息？
3. 是否使用了正确的端口？

**解决方案：**

```clojure
;; 在游戏控制台检查
(com.fabriclj.nrepl/server-running?)
(com.fabriclj.nrepl/get-port)
```

### Q: 端口被占用

**症状：**
```
[nREPL] Failed to start server: Address already in use
```

**解决方案：**

使用不同的端口：
```clojure
(nrepl/start-server! 7889)
```

或找出并结束占用端口的进程：
```bash
# Windows
netstat -ano | findstr :7888

# Linux/macOS
lsof -i :7888
```

### Q: 代码修改后不生效

**原因：**
1. 使用了 `defonce`
2. ClojureBridge 缓存
3. 命名空间未重新加载

**解决方案：**

```clojure
;; 1. 对于 defonce，需要手动重置
(reset! my-atom new-value)

;; 2. 清除 ClojureBridge 缓存
(com.fabriclj.ClojureBridge/clearCache "com.mymod.hooks")

;; 3. 重新加载命名空间
(require 'com.mymod.hooks :reload-all)
```

---

## 性能问题

### Q: 游戏卡顿

**可能原因：**
1. 反射调用过多
2. 在热路径中创建大量临时对象
3. 同步 I/O 操作

**解决方案：**

1. 添加类型提示：
   ```clojure
   (set! *warn-on-reflection* true)

   (defn get-name [^Player player]
     (.getName player))
   ```

2. 优化热路径代码：
   ```clojure
   ;; 避免在 tick 中创建序列
   (defn process-entities [entities]
     (reduce process-entity nil entities))
   ```

3. 使用异步 I/O：
   ```clojure
   (future (save-data-to-disk data))
   ```

### Q: 启动时间长

**原因：** Clojure 运行时初始化需要时间。

**这是正常现象。** Clojure 首次加载需要初始化运行时，后续加载会更快。

如果启动时间过长，检查是否在启动时加载了过多命名空间。

---

## 兼容性问题

### Q: 与其他 mod 冲突

**症状：** 安装某些 mod 后游戏崩溃。

**可能原因：**
1. 类加载器冲突
2. Mixin 冲突
3. 共享状态问题

**解决方案：**

1. 检查崩溃日志，确定冲突来源
2. 尝试使用不同的 Mixin 注入点
3. 报告问题到相关 mod 的 issue tracker

### Q: Forge 兼容性

fabric-language-clojure 主要针对 Fabric 平台。对于 Forge：

1. 需要编写 Java 入口类：
   ```java
   @Mod("mymod")
   public class MyMod {
       public MyMod() {
           ModMain.loadClojureMod(MyMod.class, "com.mymod.core", "init");
       }
   }
   ```

2. 参考 [开发者指南](dev-guide.md) 的 Forge 部分。

---

## 获取帮助

如果以上方案无法解决问题：

1. **检查日志**：查看 `logs/latest.log` 和 `crash-reports/` 目录
2. **搜索 Issues**：在 GitHub 仓库搜索类似问题
3. **提交 Issue**：提供以下信息：
   - Minecraft 版本
   - fabric-language-clojure 版本
   - 完整的错误日志
   - 最小复现步骤

## 相关文档

- [快速开始](quick-start.md)
- [开发者指南](dev-guide.md)
- [调试指南](debug-guide.md)
