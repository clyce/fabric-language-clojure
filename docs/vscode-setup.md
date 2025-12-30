# VSCode 开发环境配置指南

本指南将帮助你配置 VSCode 以便高效开发 Arclojure 项目。

## 必需扩展

### 1. Extension Pack for Java (by Microsoft)

包含以下核心组件：
- **Language Support for Java(TM) by Red Hat** - Java 语言服务器
- **Debugger for Java** - Java 调试器
- **Test Runner for Java** - 单元测试运行器
- **Maven for Java** / **Gradle for Java** - 构建工具支持

**安装：**
```
Ctrl+Shift+X -> 搜索 "Extension Pack for Java" -> Install
```

### 2. Calva (Clojure & ClojureScript)

提供 Clojure 语言支持和 REPL 集成。

**安装：**
```
Ctrl+Shift+X -> 搜索 "Calva" -> Install
```

## 配置步骤

### 步骤 1：导入 Gradle 项目

1. 打开项目文件夹：`File` -> `Open Folder` -> 选择 `arclojure` 目录
2. VSCode 会自动检测 Gradle 项目并开始导入
3. 等待右下角提示 "Importing Gradle Project..." 完成
4. 如果没有自动导入，按 `Ctrl+Shift+P`，输入 "Java: Import Java Projects"

**验证：**
- 在 Explorer 中，Java 文件应该有正确的包结构图标
- 终端运行 `.\gradlew.bat tasks` 应该能列出所有任务

### 步骤 2：配置 Java 环境

1. 按 `Ctrl+Shift+P`，输入 "Java: Configure Java Runtime"
2. 确认 JDK 21 被正确识别
3. 如果未识别，点击 "Reload and Edit in settings.json"，手动添加：

```json
{
  "java.configuration.runtimes": [
    {
      "name": "JavaSE-21",
      "path": "C:\\Program Files\\Microsoft\\jdk-21.0.9.10-hotspot",
      "default": true
    }
  ]
}
```

**验证：**
- 按 `Ctrl+Shift+P` -> "Java: Show Build Job Status"
- 应该显示 "Ready"

### 步骤 3：解决 F12 跳转问题

如果 F12（跳转到定义）不工作，通常是因为：

#### 问题 A：Gradle 项目未完全导入

**解决方案：**
1. 按 `Ctrl+Shift+P` -> "Java: Clean Java Language Server Workspace"
2. 选择 "Restart and delete"
3. 等待重新索引完成（右下角会显示进度）

#### 问题 B：源码未附加

**解决方案：**
1. 运行 Gradle 任务下载源码：
```bash
.\gradlew.bat build --refresh-dependencies
```

2. 或手动配置 Maven 仓库（让 VSCode 下载源码）：
   - `Ctrl+Shift+P` -> "Preferences: Open User Settings (JSON)"
   - 添加：
```json
{
  "java.project.referencedLibraries": [
    "lib/**/*.jar",
    ".gradle/caches/**/*.jar"
  ]
}
```

#### 问题 C：Clojure 代码无法跳转

**原因：** Clojure 是动态语言，静态分析有限。

**解决方案（使用 Calva）：**
1. 启动游戏客户端（nREPL 会在端口 7888 启动）：
```bash
.\gradlew.bat :fabric:runClient
```

2. 等待游戏窗口出现后，按 `Ctrl+Alt+C Ctrl+Alt+C` (Calva: Connect to a Running REPL)
3. 选择 "Arclojure nREPL" 或手动输入：
   - Host: `localhost`
   - Port: `7888`

4. 连接后，Calva 可以：
   - **跳转到定义** (`F12` 或 `Ctrl+Click`)
   - **查找引用** (`Shift+F12`)
   - **内联求值** (`Ctrl+Alt+C E`)
   - **加载文件到 REPL** (`Ctrl+Alt+C Enter`)

**验证：**
- 打开 `common/src/main/clojure/com/arclojure/core.clj`
- 右键点击 `registry/register-all!`
- 选择 "Go to Definition" (F12)
- 应该跳转到 `registry.clj` 的对应函数

### 步骤 4：配置 Calva REPL

已自动配置在 `.vscode/settings.json` 中：

```json
{
  "calva.replConnectSequences": [
    {
      "name": "Arclojure nREPL",
      "projectType": "generic",
      "host": "localhost",
      "port": 7888
    }
  ]
}
```

**使用流程：**
1. 启动游戏：`.\gradlew.bat :fabric:runClient`
2. 等待日志显示：`[Arclojure/nREPL] Server started on 127.0.0.1:7888`
3. VSCode 中按 `Ctrl+Alt+C Ctrl+Alt+C` 连接 REPL
4. 选择 "Arclojure nREPL"

**REPL 常用快捷键：**
- `Ctrl+Alt+C E` - 求值当前表达式
- `Ctrl+Alt+C Enter` - 加载当前文件
- `Ctrl+Alt+C Ctrl+Alt+N` - 切换命名空间到当前文件
- `Ctrl+Alt+C P` - 在 REPL 中打印最后结果
- `Ctrl+Alt+C Ctrl+Alt+D` - 断开 REPL 连接

### 步骤 5：Java 和 Clojure 混合调试

#### Java 调试

1. 在 Java 代码中设置断点（点击行号左侧）
2. 按 `F5` 启动调试
3. 如果没有启动配置，创建 `.vscode/launch.json`：

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug Fabric Client",
      "request": "launch",
      "mainClass": "net.fabricmc.devlaunchinjector.Main",
      "projectName": "fabric",
      "cwd": "${workspaceFolder}/fabric/run",
      "vmArgs": "-Dfabric.development=true",
      "args": "",
      "console": "integratedTerminal"
    }
  ]
}
```

**注意：** Gradle 启动的客户端比直接调试复杂，推荐使用日志调试。

#### Clojure 调试

使用 Calva 的 REPL 进行交互式调试：

1. 连接 REPL
2. 在代码中添加打印语句：
```clojure
(println "[DEBUG]" variable-name variable-value)
```

3. 重新加载文件：`Ctrl+Alt+C Enter`
4. 查看游戏控制台或 VSCode 的 Calva REPL 输出

## 常见问题

### Q1: "Cannot resolve symbol" 错误（Java）

**解决方案：**
```bash
# 清理并重新构建
.\gradlew.bat clean build --refresh-dependencies

# 清理 VSCode 缓存
Ctrl+Shift+P -> "Java: Clean Java Language Server Workspace"
```

### Q2: Clojure 文件没有语法高亮

**解决方案：**
1. 确认 Calva 已安装并启用
2. 检查文件扩展名是 `.clj`
3. 右下角点击语言模式 -> 选择 "Clojure"

### Q3: nREPL 连接失败

**原因：**
- 游戏未启动
- nREPL 端口被占用
- 防火墙阻止连接

**解决方案：**
```bash
# 1. 确认游戏正在运行
.\gradlew.bat :fabric:runClient

# 2. 检查端口占用
netstat -ano | findstr :7888

# 3. 如果被占用，修改端口（在 core.clj 中）
(nrepl/start-server! 7889)
```

### Q4: F12 跳转很慢或不准确

**原因：** 索引未完成或项目过大

**解决方案：**
1. 等待索引完成（右下角会显示进度条）
2. 增加 VSCode 的内存限制：
   - `Ctrl+Shift+P` -> "Preferences: Configure Runtime Arguments"
   - 添加：`-Xmx4G`（给 Java Language Server 4GB 内存）
3. 排除不必要的目录（已在 `.vscode/settings.json` 配置）

### Q5: Gradle 任务执行很慢

**解决方案：**
```bash
# 1. 启用 Gradle 守护进程（已默认启用）
# 2. 增加 Gradle 内存（在 gradle.properties）
org.gradle.jvmargs=-Xmx4G

# 3. 启用并行构建（已启用）
org.gradle.parallel=true

# 4. 使用本地缓存
.\gradlew.bat build --build-cache
```

## 推荐的 VSCode 扩展（可选）

- **GitLens** - Git 历史可视化
- **Error Lens** - 内联显示错误信息
- **Better Comments** - 高亮特殊注释
- **Gradle for Java** - 更好的 Gradle 支持

## 工作流示例

### 场景 1：添加新物品

1. 编辑 `common/src/main/clojure/com/arclojure/registry.clj`
2. 添加：
```clojure
(defitem example-item
  (Item. (-> (Item$Properties.)
             (.stacksTo 64))))
```

3. 保存文件（`Ctrl+S`）
4. 如果 REPL 已连接，按 `Ctrl+Alt+C Enter` 热重载
5. 否则，重启客户端：`.\gradlew.bat :fabric:runClient`

### 场景 2：调试 Mixin

1. 在 `ClojureHooks.java` 中添加日志：
```java
LOGGER.info("[DEBUG] onPlayerJump called for player: {}", player.getName().getString());
```

2. 在 `hooks.clj` 中添加逻辑：
```clojure
(defn on-player-jump [^Player player ^CallbackInfo ci]
  (println "[Clojure] Player jumped:" (.getName player)))
```

3. 重新构建并运行：
```bash
.\gradlew.bat :fabric:runClient
```

4. 在游戏中跳跃，查看日志输出

### 场景 3：性能分析

1. 连接 REPL
2. 在 REPL 窗口输入：
```clojure
(time (dotimes [_ 1000]
  (registry/register-all!)))
```

3. 查看执行时间
4. 使用 `clojure.java.jmx` 进行深度分析

## 额外资源

- [Calva 文档](https://calva.io/)
- [VSCode Java 文档](https://code.visualstudio.com/docs/languages/java)
- [Clojure Style Guide](https://guide.clojure.style/)
- [Architectury 文档](https://docs.architectury.dev/)

## 配置文件位置

- **VSCode 设置**: `.vscode/settings.json`
- **Calva REPL 配置**: `.vscode/settings.json` 中的 `calva.replConnectSequences`
- **Java 调试配置**: `.vscode/launch.json`（需要时创建）
- **Gradle 配置**: `gradle.properties`
