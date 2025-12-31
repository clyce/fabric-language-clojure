# fabric-language-clojure

[![Maven](https://img.shields.io/badge/Maven-1.0.0-blue)](https://maven.example.com)
[![Modrinth](https://img.shields.io/badge/Modrinth-fabric--language--clojure-green)](https://modrinth.com)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> Fabric language module for Clojure. Adds support for Clojure entrypoints and bundles the Clojure runtime.

fabric-language-clojure 是一个 Fabric 语言支持模组，允许你使用 Clojure 编写 Minecraft mod。它提供了 Clojure 语言适配器，并捆绑了 Clojure 运行时和常用库。

## 特性

- **语言适配器**：在 `fabric.mod.json` 中使用 `"adapter": "clojure"` 声明 Clojure 入口点
- **Clojure 运行时捆绑**：无需单独安装 Clojure，所有依赖都包含在 mod 中
- **nREPL 支持**：内置 nREPL 服务器，支持运行时代码热替换
- **注册表 DSL**：提供简洁的 Clojure DSL 用于注册游戏内容
- **Mixin 桥接**：提供 `ClojureBridge` 工具类，方便从 Mixin 调用 Clojure 代码
- **跨平台工具**：基于 Architectury API 的平台检测工具

## 使用方法

### 1. 添加依赖

在你的 `build.gradle` 中添加：

```groovy
dependencies {
    modImplementation "com.fabriclj:fabric-language-clojure:1.0.0+clojure.1.11.1"
}
```

### 2. 配置入口点

**方式 A：使用 Java 入口类（推荐，兼容性最好）**

```java
// MyMod.java
public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // 使用 ClojureRuntime 加载 Clojure 代码
        ClojureRuntime.ensureInitialized(MyMod.class);
        ClojureRuntime.requireNamespace("com.mymod.core");
        ClojureRuntime.invoke("com.mymod.core", "init");
    }
}
```

```json
{
  "entrypoints": {
    "main": ["com.mymod.MyMod"]
  },
  "depends": {
    "fabric-language-clojure": ">=1.0.0"
  }
}
```

**方式 B：使用 Clojure 适配器（纯 Fabric Loom 环境）**

> ⚠️ 注意：语言适配器在 Architectury 开发环境中可能有兼容性问题

```json
{
  "schemaVersion": 1,
  "id": "mymod",
  "version": "1.0.0",
  "entrypoints": {
    "main": [
      {
        "adapter": "clojure",
        "value": "com.mymod.core/init"
      }
    ],
    "client": [
      {
        "adapter": "clojure",
        "value": "com.mymod.client/init-client"
      }
    ]
  },
  "depends": {
    "fabricloader": ">=0.15.0",
    "fabric-language-clojure": ">=1.0.0"
  }
}
```

### 3. 编写 Clojure 代码

创建入口点函数：

```clojure
;; src/main/clojure/com/mymod/core.clj
(ns com.mymod.core
  (:require [com.fabriclj.core :as lib]
            [com.fabriclj.registry :as reg]
            [com.fabriclj.nrepl :as nrepl]))

(def items (reg/create-registry "mymod" :item))

(defn init
  "Mod 初始化函数 - 由语言适配器自动调用"
  []
  (println "[MyMod] Initializing...")

  ;; 注册游戏内容
  (reg/register-all! items)

  ;; 开发模式下启动 nREPL
  (when (lib/dev-mode?)
    (nrepl/start-server!))

  (println "[MyMod] Done!"))
```

### 4. Mixin 支持

Mixin 必须用 Java 编写，使用 `ClojureBridge` 调用 Clojure 代码：

```java
@Mixin(Player.class)
public class MyMixin {
    @Inject(method = "jump", at = @At("HEAD"))
    private void onJump(CallbackInfo ci) {
        ClojureBridge.invoke("com.mymod.hooks", "on-player-jump",
                             (Player)(Object)this, ci);
    }
}
```

对应的 Clojure 实现：

```clojure
(ns com.mymod.hooks)

(defn on-player-jump [player ci]
  (println "Player jumped!"))
```

## 入口点格式

支持以下入口点格式：

| 格式 | 示例 | 说明 |
|------|------|------|
| 函数引用 | `"com.mymod.core/init"` | 调用命名空间中的函数 |
| 命名空间引用 | `"com.mymod.core"` | 自动调用 `-main` 函数 |
| 变量引用 | `"com.mymod.core/initializer"` | 使用变量的值（必须实现对应接口） |

## 捆绑库

| 库 | 版本 | 说明 |
|----|------|------|
| `org.clojure:clojure` | 1.11.1 | Clojure 核心库 |
| `nrepl:nrepl` | 1.3.0 | nREPL 服务器 |

## 项目结构

```
fabric-language-clojure/
├── common/                   # 通用模块（核心逻辑）
│   └── src/main/
│       ├── java/             # Java 代码
│       │   └── com/fabriclj/
│       │       ├── ModMain.java        # 主入口
│       │       ├── ClojureRuntime.java # 运行时管理
│       │       └── ClojureBridge.java  # Mixin 桥接工具
│       └── clojure/          # Clojure 工具库
│           └── com/fabriclj/
│               ├── core.clj      # 核心工具
│               ├── registry.clj  # 注册表 DSL
│               ├── nrepl.clj     # nREPL 服务
│               └── client.clj    # 客户端工具
├── fabric/                   # Fabric 平台模块
│   └── src/main/
│       ├── java/             # Fabric 入口点
│       │   └── com/fabriclj/fabric/
│       │       └── ClojureLanguageAdapter.java
│       └── resources/
│           └── fabric.mod.json
└── examples/                 # 示例项目
    └── example-mod/
```

## 开发指南

详细的开发指南请参阅 [docs/](docs/) 目录：

- [快速开始](docs/quick-start.md) - 环境设置、基本使用
- [开发者指南](docs/dev-guide.md) - 深入开发、最佳实践
- [调试指南](docs/debug-guide.md) - REPL 连接、调试技巧
- [测试指南](docs/testing.md) - 编写和运行测试
- [部署指南](docs/deploy-guide.md) - 构建发布版本、发布到 Maven/模组平台
- [故障排查](docs/troubleshooting.md) - 常见问题解决方案

## 与 fabric-language-kotlin 的对比

本项目的设计参考了 [fabric-language-kotlin](https://github.com/FabricMC/fabric-language-kotlin)：

| 特性 | fabric-language-kotlin | fabric-language-clojure |
|------|------------------------|-------------------------|
| 语言适配器 | ✅ 支持 | ✅ 支持 |
| 运行时捆绑 | ✅ Kotlin stdlib | ✅ Clojure 核心库 |
| REPL 支持 | ❌ 无 | ✅ nREPL |
| 热重载 | ❌ 需要重启 | ✅ 运行时热替换 |
| 编译模式 | AOT 编译 | 运行时加载 |

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | JVM 运行环境 |
| Minecraft | 1.20+ | 目标游戏版本 |
| Fabric Loader | 0.15+ | 模组加载器 |
| Clojure | 1.11.1 | 函数式编程语言 |
| nREPL | 1.3.0 | 远程 REPL 服务 |
| Architectury API | 9.2.14 | 跨平台抽象层 |

## 许可证

MIT License

## 致谢

- [Fabric](https://fabricmc.net/) - 优秀的 Minecraft 模组加载器
- [fabric-language-kotlin](https://github.com/FabricMC/fabric-language-kotlin) - 设计参考
- [Clojure](https://clojure.org/) - 强大的函数式编程语言
- [Architectury](https://architectury.dev/) - 跨平台模组开发框架
