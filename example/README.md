# Example Clojure Mod - 测试项目

这是一个完整的示例项目，用于测试和调试 fabric-language-clojure 语言支持库。

## 项目结构

```
example/
├── build.gradle                    # Gradle 构建配置
├── src/main/
│   ├── java/com/example/
│   │   ├── ExampleMod.java         # 主入口（Java）
│   │   ├── client/
│   │   │   └── ExampleModClient.java  # 客户端入口
│   │   └── mixin/
│   │       └── ExampleMixin.java   # 示例 Mixin
│   ├── clojure/com/example/
│   │   ├── core.clj                # 主逻辑（Clojure）
│   │   ├── client.clj              # 客户端逻辑
│   │   └── hooks.clj               # Mixin 钩子实现
│   └── resources/
│       ├── fabric.mod.json         # Mod 配置
│       └── example.mixins.json     # Mixin 配置
```

## 运行方式

### 1. 启动游戏客户端

```bash
# 在项目根目录执行
.\gradlew.bat :example:runClient

# 或在 Linux/macOS
./gradlew :example:runClient
```

### 2. 启动游戏服务器

```bash
.\gradlew.bat :example:runServer
```

### 3. 构建 JAR

```bash
.\gradlew.bat :example:build

# 产物位于
example/build/libs/example-clojure-mod-fabric-1.0.0.jar
```

## 调试方式

### 方式 1：使用 nREPL（推荐）

1. 启动游戏客户端
2. 查看控制台确认 nREPL 已启动：
   ```
   [nREPL] Server started on 127.0.0.1:7888
   ```
3. 在 VS Code 中连接 nREPL：
   - `Ctrl+Shift+P` → `Calva: Connect to a running REPL`
   - 选择 `Generic`
   - 输入 `localhost:7888`

4. 在 REPL 中测试代码：
   ```clojure
   ;; 切换到示例命名空间
   (in-ns 'com.example.core)

   ;; 查看平台信息
   (com.fabriclj.core/platform-name)

   ;; 查看注册的物品
   @items

   ;; 修改函数并测试
   (defn test-function []
     (println "Testing from REPL!"))

   (test-function)
   ```

### 方式 2：使用 IDE 调试器

1. 在 VS Code 中配置 Java 调试：
   - 打开 Run and Debug 面板
   - 添加配置：`Java: Attach`
   - Port: `5005`

2. 以调试模式启动游戏：
   ```bash
   .\gradlew.bat :example:runClient --debug-jvm
   ```

3. 在 Java 代码或 Clojure 代码中设置断点

### 方式 3：日志调试

在 Clojure 代码中添加调试日志：

```clojure
(defn init []
  (println "[DEBUG] Initializing example mod...")
  (println "[DEBUG] Platform:" (com.fabriclj.core/platform-name))
  ;; 更多逻辑...
  )
```

日志文件位置：`example/run/logs/latest.log`

## 热重载开发流程

1. 启动游戏并连接 nREPL
2. 修改 `.clj` 文件
3. 在 VS Code 中：
   - 光标放在函数上 → `Alt+Enter` 重新求值
   - 或重新加载整个文件：`Ctrl+Alt+C Enter`
4. 修改立即生效，无需重启游戏！

**注意**：如果函数被 `ClojureBridge` 调用（如 Mixin 钩子），需要清除缓存：

```clojure
(com.fabriclj.ClojureBridge/clearCache "com.example.hooks")
```

## 常见开发任务

### 添加新物品

```clojure
;; 在 core.clj 中添加
(reg/defitem items my-sword
  (Item. (-> (Item$Properties.)
             (.stacksTo 1))))

;; 重新求值代码
;; 调用注册
(reg/register-all! items)
```

### 添加新的 Mixin 钩子

1. 在 `ExampleMixin.java` 中添加注入：
   ```java
   @Inject(method = "tick", at = @At("HEAD"))
   private void onTick(CallbackInfo ci) {
       ClojureBridge.invoke("com.example.hooks", "on-tick",
                            (Player)(Object)this, ci);
   }
   ```

2. 在 `hooks.clj` 中实现：
   ```clojure
   (defn on-tick [player ci]
     (println "Player ticking..."))
   ```

3. 重新编译 Java 代码：
   ```bash
   .\gradlew.bat :example:classes
   ```

4. 重启游戏测试

### 测试不同的 Minecraft 版本

修改 `gradle.properties`：
```properties
minecraft_version = 1.20.4
```

然后重新构建。

## 故障排查

### Q: nREPL 未启动

检查是否在开发模式：
```clojure
(com.fabriclj.core/dev-mode?) ;; 应该返回 true
```

### Q: 代码修改不生效

1. 确认已连接 nREPL
2. 重新求值代码：`Alt+Enter`
3. 清除 ClojureBridge 缓存（如果适用）
4. 检查是否使用了 `defonce`（只执行一次）

### Q: ClassNotFoundException

确保在 `build.gradle` 中正确配置了依赖：
```groovy
common(project(path: ':common', configuration: 'namedElements'))
modApi project(path: ':fabric', configuration: 'namedElements')
```

## 下一步

- 参考 [开发者指南](../docs/dev-guide.md) 了解最佳实践
- 参考 [调试指南](../docs/debug-guide.md) 学习高级调试技巧
- 查看主项目文档了解更多 API
