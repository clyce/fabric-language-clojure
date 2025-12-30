# Arclojure

> 基于 Architectury API 与 Clojure 联动的 Minecraft 模组开发模板

Arclojure 是一个创新的 Minecraft 模组开发模板，它将 Clojure 的函数式编程优势与 Architectury 的跨平台能力相结合，为模组开发者提供了一种全新的开发体验。

## 特性

- **跨平台支持**：基于 Architectury API，一套代码同时支持 Fabric 和 Forge
- **REPL 驱动开发**：内置 nREPL 服务，支持运行时代码热替换
- **数据驱动设计**：利用 Clojure 的持久化数据结构描述游戏内容
- **宏编程抽象**：通过 DSL 简化繁琐的注册样板代码
- **类型安全桥接**：Java 壳处理静态契约，Clojure 核处理动态逻辑

## 项目结构

```
arclojure/
├── build.gradle              # 根构建配置
├── settings.gradle           # 项目设置
├── gradle.properties         # 版本变量
├── common/                   # 通用模块（核心逻辑）
│   ├── build.gradle
│   └── src/main/
│       ├── java/             # Java 适配层（入口、Mixin、桥接）
│       │   └── com/arclojure/
│       │       ├── ModMain.java      # Clojure 引导器
│       │       ├── ClojureHooks.java    # Mixin 钩子桥接
│       │       └── mixin/               # Mixin 类
│       ├── clojure/          # Clojure 源码（业务逻辑）
│       │   └── com/arclojure/
│       │       ├── core.clj             # 核心初始化
│       │       ├── client.clj           # 客户端逻辑
│       │       ├── registry.clj         # 内容注册 DSL
│       │       ├── nrepl.clj            # nREPL 服务
│       │       └── hooks.clj            # Mixin 钩子实现
│       └── resources/
├── fabric/                   # Fabric 平台模块
│   ├── build.gradle
│   └── src/main/
│       ├── java/             # Fabric 入口点
│       └── resources/
│           └── fabric.mod.json
└── forge/                    # Forge 平台模块
    ├── build.gradle
    └── src/main/
        ├── java/             # Forge 入口点
        └── resources/
            └── META-INF/mods.toml
```

## 架构设计

Arclojure 采用「**Java 壳，Clojure 核**」的混合架构模式：

```
┌─────────────────────────────────────────────────────────────────┐
│                       Minecraft / Mod Loader                    │
├─────────────────────────────────────────────────────────────────┤
│  Java Layer (静态契约)                                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Entrypoints │  │   Mixins    │  │    ClojureHooks         │  │
│  │ (入口点)     │  │ (字节码注入)  │  │    (桥接层)              │  │
│  └──────┬──────┘  └──────┬──────┘  └───────────┬─────────────┘  │
│         │                │                     │                │
│         └────────────────┼─────────────────────┘                │
│                          ↓                                      │
├─────────────────────────────────────────────────────────────────┤
│  Clojure Layer (动态逻辑)                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │    core     │  │  registry   │  │        hooks            │  │
│  │  (初始化)    │  │ (注册 DSL)   │  │     (钩子实现)           │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│                          ↑                                      │
│                     ┌────┴────┐                                 │
│                     │  nREPL  │  ← 开发时热替换                   │
│                     └─────────┘                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 为什么采用这种架构？

| 层级 | 语言 | 职责 | 原因 |
|------|------|------|------|
| 入口点 | Java | 模组加载器发现 | 加载器扫描 Java 注解定位入口 |
| Mixin | Java | 字节码注入 | Mixin 需要编译期静态类结构 |
| 钩子桥接 | Java | 静态方法调用 | 为 Mixin 提供稳定的调用接口 |
| 业务逻辑 | Clojure | 游戏内容实现 | 利用 FP 优势和 REPL 热替换 |

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 21 | JVM 运行环境 |
| Minecraft | 1.20.1 | 目标游戏版本 |
| Architectury API | 9.2.14 | 跨平台抽象层 |
| Fabric Loader | 0.18.4 | Fabric 平台加载器 |
| Forge | 47.4.10 | Forge 平台 |
| Clojure | 1.11.1 | 函数式编程语言 |
| nREPL | 1.3.0 | 远程 REPL 服务 |
| Clojurephant | 0.8.0-beta.7 | Gradle Clojure 插件 |

## 快速开始

请参阅 [快速开始指南](docs/quick-start.md) 获取详细的项目设置和开发指南。

## 开发指南

- [快速开始](docs/quick-start.md) - 环境设置、基本使用
- [开发者指南](docs/dev-guide.md) - 深入开发、最佳实践
- [调试指南](docs/debug-guide.md) - REPL 连接、调试技巧
- [故障排查](docs/troubleshooting.md) - 常见问题解决方案
- [架构分析](docs/Clojure%20MC%20Mod%20开发%20ArchAPI%20分析.md) - 架构设计原理
- [变更记录](CHANGELOG.md) - 项目变更历史

## 许可证

MIT License
