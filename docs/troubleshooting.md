# 故障排查指南

## 构建问题

### 问题 1：Java 版本不匹配

**错误信息：**
```
Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.
```

**解决方案：**
1. 下载并安装 [Java 21](https://learn.microsoft.com/zh-cn/java/openjdk/download#openjdk-21)
2. 设置 `JAVA_HOME` 环境变量
3. 重新打开终端
4. 验证：`java -version` 应显示 21.x

### 问题 2：ClassNotFoundException: clojure.lang.IFn

**错误信息：**
```
Exception in thread "main" java.lang.TypeNotPresentException: Type clojure/lang/IFn not present
Caused by: java.lang.ClassNotFoundException: clojure.lang.IFn
```

**原因：** Clojure 运行时未正确添加到类路径

**解决方案：**
确保 `common/build.gradle` 使用 `modImplementation`：
```groovy
dependencies {
    modImplementation "org.clojure:clojure:$rootProject.clojure_version"
    modImplementation "nrepl:nrepl:$rootProject.nrepl_version"
}
```

### 问题 3：checkClojure 编译错误

**错误信息：**
```
Syntax error compiling at (com\arclojure\core.clj:44:3).
No such var: registry/register-all!
```

**原因：** Clojurephant 在构建时尝试编译 Clojure 代码，但 Minecraft 类不可用

**解决方案：**
禁用编译时检查：
```groovy
tasks.named('checkClojure') {
    enabled = false
}
tasks.named('compileClojure') {
    enabled = false
}
```

### 问题 4：remapJar 失败

**错误信息：**
```
Execution failed for task ':fabric:remapJar'.
ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 2
```

**状态：** 已知问题，可能是 Loom 与 Clojure 依赖的兼容性问题

**临时解决方案：**
使用 `runClient` 直接运行开发环境，跳过打包：
```bash
.\gradlew.bat :fabric:runClient
```

### 问题 5：Loom 缓存损坏

**错误信息：**
```
Failed to setup Minecraft, java.lang.RuntimeException: Failed to remap 5 mods
Previous process has disowned the lock due to abrupt termination.
```

**原因：** Gradle 进程异常终止导致 Loom 缓存锁文件损坏

**解决方案：**
```bash
# 删除 Loom 缓存（Windows PowerShell）
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\fabric-loom"

# 停止所有守护进程
.\gradlew.bat --stop

# 重新运行
.\gradlew.bat :fabric:runClient
```

### 问题 6：clean 任务失败（文件占用）

**错误信息：**
```
Unable to delete directory '...\common\build'
Failed to delete some children.
```

**原因：** 之前的进程还在占用文件

**解决方案：**
1. 关闭所有 Java 进程
2. 或者直接运行而不 clean：
```bash
.\gradlew.bat :fabric:runClient
```

## 运行时问题

### 问题 7：nREPL 无法连接

**症状：** Calva 连接超时

**解决方案：**
1. 确认游戏已完全启动
2. 查看控制台是否显示：
   ```
   [Arclojure/nREPL] Server started on 127.0.0.1:7888
   ```
3. 检查端口是否被占用：
   ```bash
   netstat -an | findstr 7888
   ```
4. 确认防火墙没有阻止本地连接

### 问题 8：Clojure 代码修改不生效

**症状：** REPL 中求值成功，但游戏行为未改变

**可能原因：**
1. 函数被缓存（`defonce` 定义的变量）
2. Java 层缓存了函数引用

**解决方案：**
1. 对于 `defonce` 变量，需要重启游戏
2. 对于其他函数，使用：
   ```clojure
   (require 'namespace :reload)
   ```
3. 检查 `ClojureHooks.java` 是否缓存了函数引用

### 问题 9：反射警告

**症状：**
```
Reflection warning, NO_SOURCE_PATH:18:1 - reference to field flush can't be resolved.
```

**解决方案：**
添加类型提示：
```clojure
;; ❌ 有反射
(.someMethod object)

;; ✅ 无反射
(.someMethod ^ClassName object)
```

## Gradle 守护进程问题

### 问题 10：守护进程不兼容

**错误信息：**
```
Starting a Gradle Daemon, 3 incompatible Daemons could not be reused
```

**解决方案：**
停止所有守护进程并重新启动：
```bash
.\gradlew.bat --stop
.\gradlew.bat :fabric:runClient
```

### 问题 11：内存不足

**症状：** 构建缓慢或失败

**解决方案：**
增加 Gradle 内存，编辑 `gradle.properties`：
```properties
org.gradle.jvmargs=-Xmx4G
```

## IDE 问题

### 问题 12：VS Code 无法识别 Clojure 文件

**解决方案：**
1. 安装 [Calva 插件](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva)
2. 重新加载 VS Code
3. 打开 `.clj` 文件时选择 Clojure 语言模式

### 问题 13：Java 类找不到

**症状：** IDE 提示 `Cannot resolve symbol`

**解决方案：**
1. 运行 `.\gradlew.bat genSources` 生成 Minecraft 源码
2. 刷新 IDE 项目
3. 确保 Java Extension Pack 已安装

## 调试技巧

### 启用详细日志

```bash
.\gradlew.bat :fabric:runClient --info
```

### 查看完整堆栈跟踪

```bash
.\gradlew.bat :fabric:runClient --stacktrace
```

### 查看 Gradle 扫描报告

```bash
.\gradlew.bat :fabric:runClient --scan
```

### 问题 8：Clojure 编译错误 - defonce 参数错误

**错误信息：**
```
Caused by: clojure.lang.ArityException: Wrong number of args (3) passed to: clojure.core/defonce
        at knot//clojure.lang.Compiler.macroexpand1(Compiler.java:7016)
```

**原因：** `defonce` 宏不支持文档字符串，只接受 2 个参数（符号和初始化表达式）

**错误代码示例：**
```clojure
(defonce ^:private server-atom
  "这是文档字符串"  ;; ❌ 会被当作第三个参数
  (atom nil))
```

**解决方案：**

1. **移除文档字符串（推荐）**
```clojure
;; 使用注释代替文档字符串
(defonce ^:private server-atom (atom nil))
```

2. **或使用 `def` 代替**
```clojure
;; def 支持文档字符串，但不保证单次初始化
(def ^:private server-atom
  "这是文档字符串"
  (atom nil))
```

**技术说明：**
- `defonce` 保证变量只初始化一次（即使重新加载命名空间）
- 适用于有状态的原子（atom/agent/ref）
- 不支持文档字符串，只能用注释

## 获取帮助

如果以上方案都无法解决问题：

1. 查看 [问题报告](file:///F:/Projects/mc-mods/arclojure/build/reports/problems/problems-report.html)
2. 检查 [Architectury 文档](https://docs.architectury.dev/)
3. 查阅 [Clojurephant 文档](https://clojurephant.dev/)
4. 搜索相关错误信息
