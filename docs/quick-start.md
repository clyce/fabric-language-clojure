# 快速开始

本文档将指导你完成 Arclojure 模组开发环境的搭建和基本使用。

## 环境要求

在开始之前，请确保你的系统满足以下要求：

| 工具 | 最低版本 | 推荐版本 | 备注 |
|------|----------|----------|------|
| JDK | 21 | **21** | 新版 Loom 需要 java 21 |
| Gradle | 8.x | 8.10+ | 项目自带 Wrapper |
| VS Code | 最新 | 最新 | 或其他支持 Clojure 的 IDE |

### 必需的 VS Code 插件

| 插件名称 | 用途 |
|----------|------|
| [Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva) | Clojure 开发支持 |
| [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) | Java 开发支持 |
| [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) | Gradle 集成 |

## 项目设置

### 1. 克隆项目

```bash
git clone https://github.com/your-username/arclojure.git
cd arclojure
```

### 2. 初始化 Gradle

首次运行会下载依赖，可能需要几分钟：

```bash
# Windows
.\gradlew.bat build

# Linux/macOS
./gradlew build
```

### 3. 生成 IDE 配置

```bash
.\gradlew.bat genSources
```

这将下载 Minecraft 源码并生成 IDE 可用的反编译代码。

### 4. 打开 VS Code

```bash
code .
```

VS Code 会自动检测 Java 和 Gradle 项目并进行配置。

## 运行游戏

### 启动 Fabric 客户端

```bash
.\gradlew.bat :fabric:runClient
```

### 启动 Forge 客户端

```bash
.\gradlew.bat :forge:runClient
```

启动成功后，你会在控制台看到：

```
[Arclojure/Clojure] Mod core initializing...
[Arclojure/Clojure] Version: 1.0.0
[Arclojure/Registry] Registering items...
[Arclojure/Registry] Registering blocks...
[Arclojure/Registry] All registrations complete!
[Arclojure/nREPL] Server started on 127.0.0.1:7888
[Arclojure/Clojure] Mod core initialized!
```

## 开发工作流

### 添加新物品

编辑 `common/src/main/clojure/com/arclojure/registry.clj`：

```clojure
;; 使用 defitem 宏定义物品
(defitem my-awesome-item
  (Item. (-> (Item$Properties.)
             (.stacksTo 64))))
```

### 添加新方块

```clojure
;; 使用 defblock 宏定义方块
(defblock my-cool-block
  (Block. (BlockBehaviour$Properties/of)))
```

### 添加事件处理

编辑 `common/src/main/clojure/com/arclojure/hooks.clj`：

```clojure
(defn on-player-jump
  [^Player player ^CallbackInfo ci]
  ;; 当玩家跳跃时打印消息
  (println (str "Player " (.getName player) " jumped!"))

  ;; 取消跳跃（可选）
  ;; (.cancel ci)
  )
```

### 热重载代码

1. 确保游戏已启动且 nREPL 服务器正在运行
2. 在 VS Code 中按 `Ctrl+Shift+P`
3. 输入 `Calva: Connect to a running REPL`
4. 选择 `Generic`
5. 输入 `localhost:7888`
6. 修改 `.clj` 文件后，按 `Alt+Enter` 重新求值

## 构建发布版本

### 构建所有平台

```bash
.\gradlew.bat build
```

构建产物位置：
- Fabric: `fabric/build/libs/arclojure-fabric-1.0.0.jar`
- Forge: `forge/build/libs/arclojure-forge-1.0.0.jar`

### 仅构建特定平台

```bash
# 仅 Fabric
.\gradlew.bat :fabric:build

# 仅 Forge
.\gradlew.bat :forge:build
```

## 项目定制

### 修改模组 ID

1. 编辑 `gradle.properties`：
   ```properties
   archives_name = your-mod-name
   maven_group = com.yourname
   ```

2. 重命名包结构：
   - `common/src/main/java/com/arclojure/` → `common/src/main/java/com/yourname/`
   - `common/src/main/clojure/com/arclojure/` → `common/src/main/clojure/com/yourname/`

3. 更新 Java 文件中的 `MOD_ID` 常量

4. 更新 Clojure 命名空间声明

5. 更新资源文件：
   - `fabric/src/main/resources/fabric.mod.json`
   - `forge/src/main/resources/META-INF/mods.toml`
   - `common/src/main/resources/arclojure.mixins.json`

### 添加新的 Mixin

1. 在 `common/src/main/java/com/arclojure/mixin/` 创建 Mixin 类
2. 在 `ClojureHooks.java` 添加桥接方法
3. 在 `hooks.clj` 实现逻辑
4. 在 `arclojure.mixins.json` 注册 Mixin 类名

## 常见问题

### Q: 错误 "Dependency requires at least JVM runtime version 21"

**症状：**
```
> Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.
```

**原因：** Architectury Loom 1.11+ 需要 Java 21

**解决方案 1（推荐）：** 升级到 Java 21
1. 下载 Java 21（见上方安装指南）
2. 配置 `JAVA_HOME` 环境变量
3. 重新打开终端
4. 验证：`java -version` 应显示 21.x

### Q: Gradle 构建失败，提示找不到 Clojure 依赖

确保 `settings.gradle` 包含 Clojars 仓库：

```groovy
maven { url = 'https://clojars.org/repo' }
```

### Q: nREPL 连接失败

1. 确认游戏已完全启动
2. 检查控制台是否显示 `nREPL server started on 127.0.0.1:7888`
3. 确认没有其他程序占用 7888 端口
4. 尝试手动连接：`lein repl :connect 7888`

### Q: Clojure 代码修改后无效

1. 确保已连接到 nREPL
2. 按 `Alt+Enter` 重新求值修改的代码
3. 对于 `defonce` 定义的变量，需要重启游戏

### Q: 类加载错误 (ClassNotFoundException)

这通常是类加载器上下文问题。确保：
1. `ModMain.java` 中正确设置了 `setContextClassLoader`
2. 没有在错误的时机加载 Clojure 代码

## 下一步

- 阅读 [调试指南](debug-guide.md) 学习高级调试技巧
- 查看 [Clojure MC Mod 开发 ArchAPI 分析.md](Clojure%20MC%20Mod%20开发%20ArchAPI%20分析.md) 了解架构设计原理
- 访问 [Architectury 文档](https://docs.architectury.dev/) 学习跨平台 API
