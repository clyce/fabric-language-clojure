# 快速开始

本文档将指导你使用 fabric-language-clojure 创建你的第一个 Clojure mod。

## 环境要求

| 工具 | 最低版本 | 推荐版本 | 备注 |
|------|----------|----------|------|
| JDK | 17 | **21** | 推荐使用 Java 21 |
| Gradle | 8.x | 8.10+ | 项目自带 Wrapper |
| VS Code | 最新 | 最新 | 或其他支持 Clojure 的 IDE |

### 必需的 VS Code 插件

| 插件名称 | 用途 |
|----------|------|
| [Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva) | Clojure 开发支持 |
| [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) | Java 开发支持 |
| [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) | Gradle 集成 |

## 创建新项目

### 1. 项目结构

创建以下目录结构：

```
mymod/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── src/main/
│   ├── clojure/
│   │   └── com/mymod/
│   │       ├── core.clj
│   │       └── client.clj
│   ├── java/
│   │   └── com/mymod/mixin/
│   │       └── (如需要 Mixin)
│   └── resources/
│       ├── fabric.mod.json
│       └── mymod.mixins.json (如需要 Mixin)
```

### 2. 配置 build.gradle

```groovy
plugins {
    id 'fabric-loom' version '1.6-SNAPSHOT'
    id 'dev.clojurephant.clojure' version '0.8.0-beta.7'
}

version = project.mod_version
group = project.maven_group

repositories {
    maven { url = 'https://clojars.org/repo' }
    // fabric-language-clojure 发布仓库
    maven { url = 'https://maven.example.com/releases' }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    // 【关键】添加 fabric-language-clojure 依赖
    modImplementation "com.fabriclj:fabric-language-clojure:1.0.0+clojure.1.11.1"
}

// 配置 Clojure 源码
sourceSets {
    main {
        clojure {
            srcDirs = ['src/main/clojure']
        }
    }
}

// 禁用 Clojure AOT 编译
tasks.named('compileClojure') { enabled = false }
tasks.named('checkClojure') { enabled = false }

// 将 Clojure 源文件打包进 JAR
processResources {
    from(sourceSets.main.clojure) {
        into 'clojure'
    }
}
```

### 3. 配置 fabric.mod.json

```json
{
  "schemaVersion": 1,
  "id": "mymod",
  "version": "1.0.0",
  "name": "My Clojure Mod",
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
    "minecraft": ">=1.20",
    "fabric-language-clojure": ">=1.0.0"
  }
}
```

### 4. 编写入口点

**core.clj** - 主入口：

```clojure
(ns com.mymod.core
  (:require [com.fabriclj.core :as lib]
            [com.fabriclj.nrepl :as nrepl]))

(defn init
  "Mod 初始化函数"
  []
  (println "[MyMod] Initializing...")

  ;; 开发模式下启动 nREPL
  (when (lib/dev-mode?)
    (nrepl/start-server!))

  (println "[MyMod] Done!"))
```

**client.clj** - 客户端入口：

```clojure
(ns com.mymod.client)

(defn init-client
  "客户端初始化函数"
  []
  (println "[MyMod/Client] Client initialized!"))
```

## 运行游戏

```bash
# Windows
.\gradlew.bat runClient

# Linux/macOS
./gradlew runClient
```

启动成功后，你会在控制台看到：

```
[fabric-language-clojure] Core initialized (v1.0.0)
[MyMod] Initializing...
[nREPL] Server started on 127.0.0.1:7888
[MyMod] Done!
```

## 连接 nREPL

1. 确保游戏已启动且看到 nREPL 启动消息
2. 在 VS Code 中按 `Ctrl+Shift+P`
3. 输入 `Calva: Connect to a running REPL`
4. 选择 `Generic`
5. 输入 `localhost:7888`

现在你可以在 REPL 中实时修改和测试代码！

## 注册游戏内容

使用 `com.fabriclj.registry` 命名空间：

```clojure
(ns com.mymod.content
  (:require [com.fabriclj.registry :as reg])
  (:import [net.minecraft.world.item Item Item$Properties]))

;; 创建物品注册表
(def items (reg/create-registry "mymod" :item))

;; 注册物品
(reg/defitem items my-item
  (Item. (-> (Item$Properties.)
             (.stacksTo 64))))

;; 在 init 中注册
(defn register-content! []
  (reg/register-all! items))
```

## 添加 Mixin

Mixin 必须用 Java 编写：

```java
// src/main/java/com/mymod/mixin/MyMixin.java
package com.mymod.mixin;

import com.fabriclj.ClojureBridge;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class MyMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void onJump(CallbackInfo ci) {
        ClojureBridge.invoke("com.mymod.hooks", "on-jump",
                             (Player)(Object)this, ci);
    }
}
```

对应的 Clojure 钩子：

```clojure
;; src/main/clojure/com/mymod/hooks.clj
(ns com.mymod.hooks)

(defn on-jump [player ci]
  (println "Player jumped!"))
```

## 构建发布版本

```bash
.\gradlew.bat build
```

构建产物位于 `build/libs/` 目录。

## 下一步

- 阅读 [开发者指南](dev-guide.md) 了解最佳实践
- 查看 [调试指南](debug-guide.md) 学习 REPL 调试技巧
- 参考 [examples/](../examples/) 目录的示例代码
