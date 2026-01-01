# 魔法宝石 (Magic Gem) - Example Mod

> 一个完整的示例项目，展示 fabric-language-clojure 和 Swiss Knife 工具库的常用功能

## 🎯 本示例演示的技术特性

### Swiss Knife 功能展示

| 功能模块 | 演示内容 | 文件位置 |
|---------|---------|---------|
| **配置系统** ✅ | EDN 配置文件 + 配置验证器 | `core.clj` L36-58 |
| **注册系统** ✅ | 物品/方块/实体注册、属性构建器 | `core.clj` L64-178 |
| **事件系统** ✅ | 玩家加入、Tick、击杀、方块破坏等 8+ 事件 | `core.clj` L198-300 |
| **玩家工具** ✅ | 物品操作、消息发送、传送 | `core.clj` L229-345 |
| **网络通信** ✅ | 客户端-服务端数据包、EDN 序列化 | `core.clj` L306-341 |
| **音效系统** ✅ | 播放音效、音效配置 | `core.clj` L131, L261 |
| **文本工具** ✅ | 彩色消息、组件创建 | `core.clj` L235 |
| **物理系统** ✅ | 弹道计算、射线追踪 | `core.clj` L114-119 |
| **客户端渲染** ✅ | 按键绑定、HUD 渲染、粒子效果 | `client.clj` |
| **生命周期管理** ✅ | 统一初始化、资源管理 | `core.clj` L362-364 |
| **DataGen** ✨ | 自动生成模型、方块状态、语言文件、占位纹理 | `datagen.clj` |
| **配置验证器** ✨ | 30+ 验证器、组合验证 | `core.clj` L49-58 |

### 最佳实践演示

| 实践 | 说明 | 示例位置 |
|------|------|---------|
| **类型提示** | 避免反射，提升性能 | 所有 `.clj` 文件 |
| **错误处理** | try-catch、nil 检查、友好错误信息 | 遍布代码 |
| **客户端分离** | 延迟加载客户端类 | `core.clj` L317 |
| **性能优化** | Tick 节流、避免临时对象 | `core.clj` L293-300 |
| **文档规范** | 详细的 docstring、comment 示例 | 所有函数 |
| **命名规范** | kebab-case、?/! 后缀 | 所有文件 |
| **代码组织** | 功能分块、清晰注释 | 所有文件 |

## 📖 Mod 介绍

这是一个轻量级的魔法主题 mod，为玩家带来魔法宝石系统。玩家可以使用魔法宝石施放魔法、传送、并从怪物身上获取魔法碎片。

**同时也是学习 Clojure Minecraft Mod 开发的最佳模板。**

## ⚠️ 重要注意事项（Minecraft 1.21+）

在基于本示例开发时，请注意以下 Minecraft 1.21 API 变更：

### 1. 实体属性注册（必需）
所有自定义生物实体必须注册属性（AttributeSupplier），否则会抛出 `NullPointerException`：

```clojure
;; 使用 Fabric API 注册实体属性
(defn register-entity-attributes! []
  (let [fabric-registry (Class/forName "net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry")
        register-method (.getMethod fabric-registry "register" ...)
        attributes (.invoke (.getMethod YourEntityClass "createAttributes" ...) ...)]
    (.invoke register-method nil (into-array Object [entity-type attributes]))))

;; 在 init 函数中调用
(register-entity-attributes!)
```

参考：`core.clj` L209-233

### 2. ItemStack.hurtAndBreak 方法签名
需要额外的 `ServerLevel` 参数：

```clojure
;; 错误（旧版）
(.hurtAndBreak item-stack amount player callback)

;; 正确（1.21+）
(.hurtAndBreak item-stack amount level player callback)
```

参考：`core.clj` L158、`items.clj` L197

### 3. Level.playSound 需要 Holder<SoundEvent>
不能直接传递 `SoundEvent`，需要包装：

```clojure
(let [sound-holder (net.minecraft.core.Holder/direct sound-event)]
  (.playSound level nil x y z sound-holder source volume pitch))
```

参考：`sounds.clj` L150-156

### 4. 事件接口类型
Architectury 事件需要使用正确的接口类型，不能用 `Consumer`：

```clojure
;; 错误
(reify java.util.function.Consumer
  (accept [_ context] ...))

;; 正确
(reify dev.architectury.event.events.common.EntityEvent$LivingDeath
  (die [_ entity source] ...))
```

参考：`events/core.clj` L272-286

### 🎮 游戏内容

#### 物品

| 物品 | ID | 稀有度 | 获取方式 | 用途 |
|------|----|----|---------|------|
| **魔法宝石** | `example:magic_gem` | 稀有 | 挖掘魔法水晶矿获得 | 右键发射魔法弹，按 R 键传送，手持跳跃获得跳跃提升 II |
| **魔法碎片** | `example:magic_shard` | 罕见 | 击杀怪物掉落（20% 概率） | 未来版本用于合成 |
| **森林之魂药水** | `example:forest_soul_potion` | 史诗 | 击杀森林守卫 100% 掉落 | 饮用获得速度、跳跃提升和**森林祝福**（持续治疗）效果 |
| **自然亲和附魔书** | `example:nature_affinity_book` | 史诗 | 击杀森林守卫 100% 掉落 | 包含**爆裂打击 III** 附魔 |

#### 创造模式标签页

- **魔法宝石** 标签页 - 包含所有 mod 物品，方便创造模式获取

#### 自定义效果

| 效果 | ID | 类型 | 效果 |
|------|----|----|------|
| **森林祝福** | `example:forest_blessing` | 有益 | 每2秒治疗 0.5 点生命值（等级越高治疗越多） |

#### 自定义附魔

| 附魔 | ID | 适用物品 | 最大等级 | 效果 |
|------|----|----|---------|------|
| **爆裂打击** | `example:explosive_strike` | 剑、斧 | III | 被攻击的实体将在 3 秒后爆炸（等级越高威力越大），同时被标记发光 |

#### 方块

| 方块 | ID | 特性 | 获取方式 | 用途 |
|------|----|----|---------|------|
| **魔法水晶矿** | `example:magic_crystal_ore` | 发光（等级7），需要正确工具 | 玩家首次加入时赠送 | 挖掘后掉落魔法宝石 |

#### 实体

| 实体 | ID | 类型 | 生成方式 | 特性 | 掉落物 |
|------|----|----|---------|------|------|
| **森林守卫** | `example:forest_guardian` | 敌对生物 | 魔法弹命中树叶时召唤 | 僵尸模型，**远程攻击**（发射雪球），距离过近时**自动后退** | 森林之魂药水 + 自然亲和附魔书（100%）|

#### 功能特性

1. **魔法水晶矿**
   - 发光方块（光照等级 7），在黑暗中易于发现
   - 需要正确工具挖掘（镐）
   - 挖掘后掉落 1 个魔法宝石，播放紫水晶破碎音效

2. **魔法宝石系统**
   - 耐久度 100，每次使用消耗 1 点耐久
   - 右键使用: 发射魔法弹（雪球弹道）
   - 按 R 键: 向前传送 10 格（需手持魔法宝石）
   - 手持跳跃: 获得跳跃提升 II 效果（3秒）
   - 魔法弹命中树叶: 召唤森林守卫
   - **HUD 显示**: 左下角显示能量条，颜色随耐久度变化

3. **森林守卫战斗**
   - 敌对生物，基于僵尸模型
   - **远程攻击**: 每 3 秒发射一次雪球
   - **智能 AI**: 距离玩家小于 5 格时自动后退，保持远程攻击距离
   - 100% 掉落珍贵物品: 森林之魂药水 + 自然亲和附魔书（爆裂打击 III）
   - 击败时播放升级音效

4. **自定义附魔 - 爆裂打击**
   - 适用于剑和斧
   - 最大等级: III
   - 效果: 被攻击的实体将在 3 秒后爆炸，等级越高威力越大
   - 附加效果: 被攻击者获得发光效果（便于追踪）

5. **自定义效果 - 森林祝福**
   - 类型: 有益效果
   - 效果: 每 2 秒治疗 0.5 点生命值（等级越高治疗越多）
   - 获取方式: 饮用森林之魂药水

6. **HUD 显示**
   - 手持魔法宝石时，左下角显示能量条
   - 能量条颜色根据耐久度变化: 绿色（高）→ 黄色（中）→ 红色（低）

7. **怪物掉落**
   - 击杀普通怪物有 20% 概率掉落 1-3 个魔法碎片
   - 击杀森林守卫 100% 掉落森林之魂药水和附魔书

6. **配置系统**
   - 配置文件: `config/example/config.edn`
   - 可调整宝石威力、耐久度、粒子数量等参数

7. **欢迎系统**
   - 新玩家加入时自动赠送 3 个魔法水晶矿
   - 显示欢迎消息和游戏提示

8. **创造模式支持**
   - 所有物品在创造模式的"魔法宝石"标签页中可获取

## 📂 代码结构

### Clojure 代码

| 文件 | 功能 | 展示的技术 |
|------|------|-----------|
| **`core.clj`** | 主入口、注册、事件 | 配置系统、注册系统、事件系统、网络通信、音效、玩家工具、类型提示 |
| **`client.clj`** | 客户端专用功能 | 按键绑定、HUD 渲染、粒子效果、客户端访问器 |
| **`hooks.clj`** | Mixin 钩子函数 | Java-Clojure 互操作、Mixin 集成 |
| **`datagen.clj`** ✨ | 资源文件生成 | DataGen、模型生成、方块状态、语言文件 |

### Java 代码

| 文件 | 功能 | 作用 |
|------|------|------|
| **`ExampleMod.java`** | Java 入口点 | 调用 Clojure 初始化函数 |
| **`ExampleModClient.java`** | 客户端 Java 入口 | 调用 Clojure 客户端初始化 |
| **`mixin/ExampleMixin.java`** | Mixin 示例 | 展示如何从 Java Mixin 调用 Clojure |

## 🎯 展示的功能

### Swiss Knife 核心功能（已实现）

#### 服务端功能
| 功能 | 演示代码位置 | 说明 |
|------|------------|------|
| **物品注册** | `core.clj` L96-159 | 4 个物品: 魔法宝石（带交互）、碎片、药水、附魔书 |
| **方块注册** | `core.clj` L76-89 | 魔法水晶矿（发光、需要工具、自定义掉落） |
| **实体注册** | `core.clj` L166-178 | 森林守卫（敌对 mob，自定义掉落） |
| **配置系统** | `core.clj` L36-61 | EDN 文件 + **配置验证器** ✨ |
| **事件系统** | `core.clj` L198-300 | 8+ 事件: 加入、Tick、击杀、破坏、弹道 |
| **玩家工具** | `core.clj` L229-345 | 物品操作、传送、消息发送 |
| **音效系统** | 多处 | 发射音效、召唤音效、掉落音效 |
| **网络通信** | `core.clj` L306-341 | 双向数据包、EDN 序列化 |
| **生命周期管理** | `core.clj` L362-364 | 统一初始化入口 |

#### 客户端功能
| 功能 | 演示代码位置 | 说明 |
|------|------------|------|
| **按键绑定** | `client.clj` L21-39 | R 键触发传送 |
| **HUD 渲染** | `client.clj` L45-81 | 能量条、文本、动态颜色 |
| **粒子效果** | `client.clj` L87-107 | 魔法弹发射粒子、环形粒子 |
| **客户端访问器** | `client.clj` | 获取玩家、世界、窗口信息 |

#### 开发工具（全新）✨
| 功能 | 文件位置 | 说明 |
|------|---------|------|
| **配置验证器** | `core.clj` L49-58 | 30+ 验证器、范围验证、键验证、组合验证 |
| **DataGen - 模型** | `datagen.clj` L19-50 | 自动生成物品/方块模型 JSON |
| **DataGen - 方块状态** | `datagen.clj` L54-62 | 自动生成 blockstates JSON |
| **DataGen - 语言文件** | `datagen.clj` L66-96 | 多语言支持、自动翻译 |
| **nREPL 集成** | `core.clj` L399-419 | 运行时热重载、REPL 调试 |
| **🔥 自动文件监控** | `core.clj` L408-419 | 保存文件即自动重载，游戏内通知 |

## 🚀 使用方式

### 游戏内操作

1. **获取魔法水晶矿**
   - 首次加入游戏时自动获得 3 个
   - 或使用命令: `/give @p example:magic_crystal_ore 3`
   - 放置在地上作为装饰（会发光）

2. **挖掘魔法宝石**
   - 使用镐挖掘魔法水晶矿
   - 获得 1 个魔法宝石
   - 听到紫水晶破碎音效

3. **发射魔法弹**
   - 手持魔法宝石，右键使用
   - 发射魔法弹（雪球弹道）
   - 观察粒子效果和能量条

4. **召唤森林守卫**
   - 发射魔法弹，瞄准树叶方块
   - 命中后在树叶上方召唤森林守卫
   - 准备战斗！

5. **击败森林守卫**
   - 森林守卫会攻击你
   - 击败后 100% 掉落:
     - 森林之魂药水 x1
     - 自然亲和附魔书 x1

6. **传送能力**
   - 手持魔法宝石，按 R 键
   - 向前传送 10 格

7. **获取魔法碎片**
   - 击杀任意怪物
   - 20% 概率掉落 1-3 个碎片

8. **查看能量**
   - 手持魔法宝石时自动显示左下角能量条

## 🛠️ 开发工具使用

### DataGen - 自动生成资源文件 ✨

本示例展示如何使用 Swiss Knife 的 DataGen 功能自动生成资源文件。

#### 在 nREPL 中生成资源文件

1. **启动游戏并连接 nREPL**
   ```bash
   ./gradlew runClient
   # 等待 nREPL 启动消息
   # 使用 Calva 连接到 localhost:7888
   ```

2. **运行 DataGen**
   ```clojure
   ;; 在 REPL 中执行
   (require '[com.example.datagen :as datagen])
   (datagen/generate-all-assets!)
   ```

3. **查看生成的文件**
   ```
   src/main/resources/assets/example/
   ├── models/
   │   ├── item/
   │   │   ├── magic_gem.json         ✨ 自动生成
   │   │   ├── magic_shard.json       ✨ 自动生成
   │   │   └── ...
   │   └── block/
   │       └── magic_crystal_ore.json ✨ 自动生成
   ├── blockstates/
   │   └── magic_crystal_ore.json     ✨ 自动生成
   ├── lang/
   │   ├── en_us.json                 ✨ 自动生成
   │   └── zh_cn.json                 ✨ 自动生成
   └── textures/
       ├── item/
       │   ├── magic_gem.png          ✨ 占位纹理（自动生成）
       │   ├── magic_shard.png        ✨ 占位纹理（自动生成）
       │   └── ...
       └── block/
           └── magic_crystal_ore.png  ✨ 占位纹理（自动生成）
   ```

#### 占位纹理生成 ✨

`generate-all-assets!` 会自动生成简单的单色占位纹理（16x16 像素 PNG 文件）。这些纹理使用以下颜色：

| 物品/方块 | 颜色 | RGB 值 |
|----------|------|--------|
| 魔法宝石 | 品红色 | [255, 100, 255] |
| 魔法碎片 | 紫色 | [150, 50, 255] |
| 森林之魂药水 | 绿色 | [50, 200, 50] |
| 自然亲和书 | 浅绿色 | [100, 150, 100] |
| 魔法水晶矿 | 紫罗兰色 | [200, 100, 255] |

**重要提示**：这些占位纹理仅用于开发测试。在生产环境中，你应该替换为自己的纹理文件。

#### 单独生成某类资源

```clojure
;; 只生成物品模型
(datagen/generate-item-models!)

;; 只生成方块模型
(datagen/generate-block-models!)

;; 只生成方块状态
(datagen/generate-blockstates!)

;; 只生成语言文件
(datagen/generate-lang-files!)

;; 只生成占位纹理
(datagen/generate-placeholder-textures!)
```

### 在正式项目中使用自定义模型和纹理

#### 资源文件结构

在 Minecraft Fabric 中，模型和纹理通过资源包（Resource Pack）系统管理。资源文件需要放置在以下目录结构：

```
src/main/resources/assets/<mod-id>/
├── models/
│   ├── item/              # 物品模型
│   │   └── <item_name>.json
│   └── block/             # 方块模型
│       └── <block_name>.json
├── blockstates/           # 方块状态（仅方块需要）
│   └── <block_name>.json
└── textures/
    ├── item/              # 物品纹理（PNG 文件）
    │   └── <item_name>.png
    └── block/             # 方块纹理（PNG 文件）
        └── <block_name>.png
```

#### 1. 为物品指定自定义纹理和模型

**步骤 1：创建纹理文件**

将你的纹理 PNG 文件（推荐 16x16 像素）放置到：
```
src/main/resources/assets/<mod-id>/textures/item/<item_name>.png
```

**步骤 2：创建或生成模型文件**

使用 DataGen 工具生成模型，或手动创建 JSON 文件：
```
src/main/resources/assets/<mod-id>/models/item/<item_name>.json
```

**示例：为魔法宝石添加自定义纹理**

```clojure
;; 在 datagen.clj 中
(require '[com.fabriclj.swiss-knife.common.datagen.models :as models])

;; 生成物品模型（指定纹理路径）
(models/save-item-model! "./src/main/resources" "example" "magic_gem"
  {:parent "minecraft:item/generated"
   :textures {:layer0 "example:item/magic_gem"}})
```

然后手动将你的纹理文件复制到：
```
src/main/resources/assets/example/textures/item/magic_gem.png
```

**常用物品模型类型：**

- **`minecraft:item/generated`** - 标准物品（材料、食物等）
  ```json
  {
    "parent": "minecraft:item/generated",
    "textures": {
      "layer0": "example:item/magic_gem"
    }
  }
  ```

- **`minecraft:item/handheld`** - 手持物品（工具、武器）
  ```json
  {
    "parent": "minecraft:item/handheld",
    "textures": {
      "layer0": "example:item/magic_sword"
    }
  }
  ```

- **多层纹理**（如药水）
  ```json
  {
    "parent": "minecraft:item/generated",
    "textures": {
      "layer0": "example:item/potion_bottle",
      "layer1": "example:item/potion_overlay"
    }
  }
  ```

#### 2. 为方块指定自定义纹理和模型

**步骤 1：创建纹理文件**

将你的纹理 PNG 文件放置到：
```
src/main/resources/assets/<mod-id>/textures/block/<block_name>.png
```

**步骤 2：生成方块模型**

```clojure
;; 在 datagen.clj 中
(models/save-block-model! "./src/main/resources" "example" "magic_crystal_ore"
  {:parent "minecraft:block/cube_all"
   :textures {:all "example:block/magic_crystal_ore"}})
```

**步骤 3：生成方块状态文件（如果方块有多个状态）**

```clojure
;; 在 datagen.clj 中
(require '[com.fabriclj.swiss-knife.common.datagen.blockstates :as bs])
(bs/save-simple-blockstate! "./src/main/resources" "example" "magic_crystal_ore")
```

**常用方块模型类型：**

- **`minecraft:block/cube_all`** - 六面同纹理
  ```json
  {
    "parent": "minecraft:block/cube_all",
    "textures": {
      "all": "example:block/magic_ore"
    }
  }
  ```

- **`minecraft:block/cube`** - 六面不同纹理
  ```json
  {
    "parent": "minecraft:block/cube",
    "textures": {
      "down": "example:block/ore_bottom",
      "up": "example:block/ore_top",
      "north": "example:block/ore_side",
      "south": "example:block/ore_side",
      "west": "example:block/ore_side",
      "east": "example:block/ore_side",
      "particle": "example:block/ore_side"
    }
  }
  ```

- **`minecraft:block/cube_column`** - 柱状（如原木）
  ```json
  {
    "parent": "minecraft:block/cube_column",
    "textures": {
      "end": "example:block/log_top",
      "side": "example:block/log_side"
    }
  }
  ```

#### 3. 为实体指定自定义模型和纹理

实体的模型和纹理需要使用客户端渲染器（Entity Renderer）。基本流程如下：

**步骤 1：创建实体模型文件**
```
src/main/resources/assets/<mod-id>/models/entity/<entity_name>.json
```

**步骤 2：创建实体纹理文件**
```
src/main/resources/assets/<mod-id>/textures/entity/<entity_name>.png
```

**步骤 3：在客户端代码中注册渲染器**

```clojure
;; 在 client.clj 中
(require '[com.fabriclj.swiss-knife.client.rendering.entities :as entity-render])

;; 注册实体渲染器（需要根据实际 API 调整）
(entity-render/register-renderer! entity-type
  {:model "example:entity/forest_guardian"
   :texture "example:textures/entity/forest_guardian.png"
   :shadow-size 0.5})
```

**注意**：本示例 mod 中的 `forest_guardian` 使用了默认的僵尸模型。要实现自定义实体模型，需要：

1. 使用建模工具（如 Blockbench）创建实体模型
2. 导出为 JSON 格式
3. 在客户端注册自定义渲染器

#### 4. 使用 DataGen 工具生成模型文件

Swiss Knife 提供了便捷的 DataGen 工具来自动生成模型文件：

```clojure
(require '[com.fabriclj.swiss-knife.common.datagen.models :as models])

;; 生成简单物品模型
(models/save-item-model! "./src/main/resources" "mymod" "my_item"
  (models/generated-item-model "mymod:item/my_item"))

;; 生成手持物品模型
(models/save-item-model! "./src/main/resources" "mymod" "my_sword"
  (models/handheld-item-model "mymod:item/my_sword"))

;; 生成方块模型
(models/save-block-model! "./src/main/resources" "mymod" "my_block"
  (models/cube-all-block-model "mymod:block/my_block"))
```

#### 5. 纹理文件要求

- **格式**：PNG
- **尺寸**：推荐 16x16 像素（物品和方块），可按需使用 32x32、64x64 等
- **透明度**：支持 Alpha 通道
- **命名**：使用小写字母、数字和下划线（snake_case）

#### 6. 资源文件命名规则

Minecraft 使用资源位置（ResourceLocation）来引用资源：

- **格式**：`<namespace>:<path>`
- **示例**：`example:item/magic_gem` 对应文件 `assets/example/textures/item/magic_gem.png`
- **Namespace**：通常是你的 mod ID
- **路径**：相对于 `assets/<namespace>/` 目录

#### 7. 热重载资源文件

在开发模式下，可以使用资源包重新加载功能：

1. 在游戏中按 `F3 + T` 重新加载资源包
2. 或使用命令 `/reload` 重新加载资源

**提示**：修改纹理文件后，重新加载资源包即可看到效果，无需重启游戏。

### 配置验证器使用 ✨

配置验证器确保配置文件的值有效且在合理范围内。

```clojure
;; 查看当前配置（core.clj L36-58）
(config/get-config-value "example" [:magic-gem :power])  ; 必须在 1.0-100.0 范围内
(config/get-config-value "example" [:magic-gem :durability])  ; 必须在 1-1000 范围内

;; 手动编辑配置文件测试验证
;; 1. 打开 config/example/config.edn
;; 2. 修改 :power 为 -10（无效值）
;; 3. 运行 (config/reload-config! "example")
;; 4. 会看到验证失败的警告

;; 验证器会防止:
;; - 负数或零值
;; - 超出范围的值
;; - 缺失必需的键
;; - 类型不匹配
```

### 配置文件编辑

配置文件位于: `config/example/config.edn`

```clojure
{:magic-gem {:power 10.0              ; 魔法威力（未来版本使用）
             :durability 100          ; 最大耐久度
             :particle-count 20       ; 粒子数量
             :cooldown-ticks 20}      ; 冷却时间（ticks）
 :messages {:welcome "欢迎来到魔法世界！"
            :gem-activated "魔法宝石已激活！"}}
```

修改配置后使用 `/reload` 命令重新加载。

## 📁 项目结构

```
example/
├── build.gradle                    # Gradle 构建配置
├── src/main/
│   ├── java/com/example/
│   │   ├── ExampleMod.java         # 主入口（Java）- 调用 Clojure
│   │   ├── client/
│   │   │   └── ExampleModClient.java  # 客户端入口
│   │   └── mixin/
│   │       └── ExampleMixin.java   # 示例 Mixin（玩家跳跃钩子）
│   ├── clojure/com/example/
│   │   ├── core.clj                # 主逻辑: 物品注册、事件、配置
│   │   ├── client.clj              # 客户端: 按键、HUD、粒子
│   │   ├── hooks.clj               # Mixin 钩子实现
│   │   └── datagen.clj             # ✨ DataGen: 自动生成资源文件
│   └── resources/
│       ├── fabric.mod.json         # Mod 元数据配置
│       └── example.mixins.json     # Mixin 配置
└── config/                         # 运行时生成
    └── example/
        └── config.edn              # Mod 配置文件
```

## 🔧 开发指南

### 运行方式

#### 1. 启动游戏客户端

```bash
# Windows
.\gradlew.bat :example:runClient

# Linux/macOS
./gradlew :example:runClient
```

#### 2. 启动游戏服务器

```bash
.\gradlew.bat :example:runServer
```

#### 3. 构建 JAR 文件

```bash
.\gradlew.bat :example:build

# 产物位于
example/build/libs/example-clojure-mod-fabric-1.0.0.jar
```

### 调试方式

#### 方式 1: 使用 nREPL（强烈推荐）

**这是最快的开发方式！无需重启游戏即可测试代码修改。**

1. **启动游戏客户端**
   ```bash
   .\gradlew.bat :example:runClient
   ```

2. **确认 nREPL 已启动**
   查看控制台输出:
   ```
   [ExampleMod] 检测到开发模式，启动 nREPL 服务器...
   [nREPL] Server started on 127.0.0.1:7888
   ```

3. **连接 nREPL（VS Code + Calva）**
   - `Ctrl+Shift+P` → `Calva: Connect to a running REPL`
   - 选择 `Generic`
   - 输入 `localhost:7888`
   - 看到 "Connected to nREPL" 即成功

4. **热重载测试代码**
   ```clojure
   ;; 切换到 core 命名空间
   (in-ns 'com.example.core)

   ;; 查看配置
   (get-gem-power)           ; => 10.0
   (get-welcome-message)     ; => "欢迎来到魔法世界！"

   ;; 查看注册的物品
   @items-registry
   @magic-gem
   @magic-shard

   ;; 修改配置
   (com.fabriclj.swiss-knife.common.config.core/set-config-value!
     "example" [:magic-gem :power] 20.0)

   ;; 测试玩家工具（进入游戏后）
   (require '[com.fabriclj.swiss-knife.common.game-objects.players :as p])
   (def server (first (.getAllLevels (net.minecraft.server.MinecraftServer/getServer))))
   (def player (first (p/get-all-players (.getServer server))))
   (p/give-item! player @magic-shard 10)

   ;; 测试客户端功能
   (in-ns 'com.example.client)
   (require '[com.fabriclj.swiss-knife.client.platform.core :as c])
   (c/get-player)
   (when-let [player (c/get-player)]
     (spawn-gem-particles [(.getX player) (.getY player) (.getZ player)]))
   ```

5. **修改代码并重新求值**
   - 修改 `.clj` 文件
   - 光标放在函数上 → `Alt+Enter` 重新求值
   - 或重新加载整个文件: `Ctrl+Alt+C Enter`
   - 修改立即生效，无需重启游戏！

#### 方式 2: 日志调试

```clojure
;; 在代码中添加日志
(require '[com.fabriclj.swiss-knife :as mb])

(mb/log-info "调试信息: " some-value)
(mb/log-warn "警告信息")
(mb/log-error "错误信息")
```

日志输出到:
- 控制台
- `example/run/logs/latest.log`

#### 方式 3: 使用 IDE 调试器（高级）

1. 在 VS Code 中配置 Java 调试:
   - 打开 Run and Debug 面板
   - 添加配置: `Java: Attach`
   - Port: `5005`

2. 以调试模式启动游戏:
   ```bash
   .\gradlew.bat :example:runClient --debug-jvm
   ```

3. 在 Java 代码中设置断点（Clojure 代码断点支持有限）

### 热重载开发流程（推荐）

本示例提供两种热重载方式，可以同时使用：

#### 方式 A: 自动文件监控 🔥（最便捷）

**启动游戏后自动启用！** 修改代码保存即可，无需任何额外操作。

**工作流程**:
```
1. 启动游戏（自动监控已启动）
2. 在编辑器中修改 .clj 文件
3. 保存文件 (Ctrl+S)
4. ✅ 代码自动重载（< 1 秒）
5. 🎮 游戏中收到通知："🔄 代码已热重载: com.example.core"
6. 🔔 听到提示音效（经验球拾取音）
7. 立即测试新功能
```

**特性**:
- ✅ 完全自动化，无需手动操作
- ✅ 游戏内通知（彩色消息 + 音效）
- ✅ 支持监控多个目录
- ✅ 自动清除 ClojureBridge 缓存
- ✅ 防抖机制（避免频繁重载）

**监控的目录**:
- `example/src/main/clojure` - 示例 mod 代码

**查看状态**（在 nREPL 中）:
```clojure
(require '[com.fabriclj.dev.hot-reload :as reload])
(reload/status)  ; 查看监控状态
(reload/stop!)   ; 停止监控
(reload/restart! {:watch-paths ["example/src/main/clojure"]})  ; 重启监控
```

#### 方式 B: 手动 REPL 重载（精确控制）

**适合调试和实验代码片段**

典型工作流程:

```
1. 启动游戏 → 2. 连接 nREPL → 3. 修改代码 → 4. 重新求值 → 5. 游戏内测试 → 6. 重复 3-5
```

**优势**:
- ⚡ 极快（立即生效）
- 🎯 精确控制重载时机
- 🧪 可以在 REPL 中实验代码片段
- 🐛 实时调试和修复

**使用示例**:
```clojure
;; 在 REPL 中
(in-ns 'com.example.core)

;; 修改函数
(defn get-gem-power [] 20.0)  ; 立即生效

;; 重新加载整个命名空间
(require 'com.example.core :reload)
```

**注意**:
- 如果函数被 `ClojureBridge` 调用（如 Mixin 钩子），需要清除缓存:
  ```clojure
  (com.fabriclj.ClojureBridge/clearCache "com.example.hooks")
  ```
- 如果修改了 Java 代码或资源文件，需要重新编译或重启游戏

**推荐**：日常开发使用方式 A（自动监控），需要精确调试时使用方式 B（REPL）。

### 常见开发任务

#### 添加新物品

```clojure
;; 1. 在 core.clj 的物品注册区域添加
(reg/defitem items-registry my-new-item
  (Item. (-> (Item$Properties.)
             (.stacksTo 64)
             (.rarity Rarity/RARE))))

;; 2. 在 nREPL 中重新求值
;; 3. 重新注册
(reg/register-all! items-registry)

;; 4. 在游戏中使用命令获取:
;; /give @p example:my_new_item
```

#### 修改配置

```clojure
;; 1. 编辑 config/example/config.edn
;; 2. 在游戏中执行命令重新加载:
;; /reload

;; 或在 nREPL 中:
(config/reload-config! "example")
```

#### 添加新事件

```clojure
;; 在 core.clj 的 setup-events! 函数中添加
(events/on-block-break
  (fn [level pos state player]
    (println "方块被破坏: " state)
    (events/event-pass)))

;; 重新求值 setup-events! 函数
```

#### 测试客户端功能

```clojure
;; 连接 nREPL 后，切换到客户端命名空间
(in-ns 'com.example.client)

;; 测试粒子效果
(when-let [player (client/get-player)]
  (spawn-gem-particles [(.getX player) (.getY player) (.getZ player)]))

;; 重新注册 HUD
(setup-hud!)
```

## 📚 代码导览

### 核心代码结构

**core.clj** - 服务端逻辑（~360 行）
```
├── 配置系统 (load-config!, get-gem-power)
├── 注册表创建 (items, blocks, entities)
├── 方块注册 (magic-crystal-ore)
├── 物品注册 (magic-gem, magic-shard, forest-soul-potion, nature-affinity-book)
├── 实体注册 (forest-guardian)
├── 事件处理 (setup-events!)
│   ├── 弹道命中事件（召唤森林守卫）
│   ├── 玩家加入事件
│   ├── 击杀实体事件（掉落物品）
│   ├── 方块破坏事件（魔法水晶矿掉落）
│   └── 服务端 Tick 事件
├── 网络通信 (setup-network!)
└── 主初始化 (init)
```

**client.clj** - 客户端逻辑（~165 行）
```
├── 按键绑定 (setup-keybindings!)
├── HUD 渲染 (render-magic-energy-hud, setup-hud!)
├── 粒子效果 (spawn-shoot-particles)
└── 客户端初始化 (init-client)
```

**hooks.clj** - Mixin 钩子（~60 行）
```
└── 玩家跳跃钩子 (on-player-jump - 魔法跳跃增强)
```

### 关键技术点

1. **物品注册** - 使用 `reg/defitem` 和 proxy 创建自定义物品
2. **方块注册** - 使用 `reg/defblock` 创建发光方块（`lightLevel` 函数）
3. **实体注册** - 使用 `EntityType$Builder` 和 `reg/defentity` 注册自定义实体
4. **弹道检测** - 监听 `on-projectile-hit` 事件，检测命中方块类型
5. **掉落系统** - 使用 `items/drop-item-at!` 生成掉落物，100% 掉落率
6. **配置验证** - 使用 `:validator` 确保配置合法性
7. **事件优先级** - 使用 `event-pass` 和 `event-interrupt` 控制事件流
8. **网络数据包** - 使用 EDN 格式传输数据，自动序列化
9. **客户端检查** - 使用 `.isClientSide` 分离客户端/服务端逻辑
10. **HUD 渲染** - 使用能量条和文本显示实时信息

## ⚠️ 故障排查

### Q: nREPL 未启动

**原因**: 不在开发模式，或端口被占用

**解决**:
```clojure
;; 检查开发模式
(com.fabriclj.core/dev-mode?) ;; 应该返回 true

;; 手动启动（使用不同端口）
(com.fabriclj.nrepl/start-server! 7889)
```

### Q: 魔法宝石使用后没有粒子效果

**原因**: 客户端网络处理器可能未正确注册

**解决**:
1. 检查控制台是否有错误信息
2. 在 nREPL 中验证:
   ```clojure
   (in-ns 'com.example.client)
   (spawn-gem-particles [0 64 0])  ; 应该看到粒子效果
   ```

### Q: HUD 不显示

**原因**: HUD 渲染器未注册，或不在客户端

**解决**:
```clojure
;; 重新注册 HUD
(in-ns 'com.example.client)
(setup-hud!)

;; 检查是否在客户端
(com.fabriclj.swiss-knife/client-side?) ;; 应该返回 true
```

### Q: 按 R 键没有反应

**原因**: 按键未绑定，或未手持魔法宝石

**解决**:
1. 确保手持魔法宝石
2. 检查按键是否冲突: `选项 → 控制 → 按键绑定`
3. 重新绑定按键

### Q: 配置修改不生效

**原因**: 配置未重新加载

**解决**:
```clojure
;; 在游戏中执行
/reload

;; 或在 nREPL 中
(com.fabriclj.swiss-knife.common.config.core/reload-config! "example")
```

### Q: ClassNotFoundException 或 NoClassDefFoundError

**原因**: 依赖配置错误

**解决**:
```bash
# 清理并重新构建
.\gradlew.bat clean :example:build
```

## 🎓 学习资源

### 相关文档
- [快速开始](../docs/quick-start.md) - 环境设置、创建第一个 mod
- [开发者指南](../docs/dev-guide.md) - 深入开发、最佳实践
- [调试指南](../docs/debug-guide.md) - nREPL 连接、调试技巧
- [fabriclj 核心 API](../common/src/main/clojure/com/fabriclj/README.md) - 最小 API 层
- [Swiss Knife 工具库](../common/src/main/clojure/com/fabriclj/swiss-knife/README.md) - 高级功能封装

### 代码示例
- 本项目所有代码都有详细注释
- 每个 `.clj` 文件末尾都有 `(comment ...)` 块，包含测试代码
- Swiss Knife 文档包含 80+ 个功能的示例

### 📚 学习路径

#### 第一步: 理解项目结构（30 分钟）

1. **阅读 `core.clj`** - 理解初始化流程
   - 配置加载（L36-61）
   - 注册系统（L64-178）
   - 事件处理（L198-300）
   - 网络通信（L306-341）

2. **阅读 `client.clj`** - 理解客户端功能
   - 按键绑定（L21-39）
   - HUD 渲染（L45-81）
   - 粒子效果（L87-107）

3. **阅读 `hooks.clj`** - 理解 Mixin 集成
   - Java-Clojure 互操作
   - 钩子函数实现

4. **阅读 `datagen.clj`** ✨ - 理解资源自动化
   - 模型生成
   - 方块状态生成
   - 多语言支持

#### 第二步: 运行和测试（1 小时）

1. **启动游戏**
   ```bash
   ./gradlew :example:runClient
   ```

2. **进入游戏测试**
   - 观察欢迎消息
   - 获得魔法水晶矿
   - 挖掘获得魔法宝石
   - 右键发射魔法弹
   - 按 R 键传送

3. **连接 nREPL 实时修改**
   - 连接到 `localhost:7888`
   - 运行 `(generate-all-assets!)` 生成资源
   - 修改配置值并重载
   - 修改函数并热重载

#### 第三步: 深入学习（2-3 小时）

1. **研究每个功能的实现**
   - 物品的 `use` 方法如何实现交互
   - 事件如何监听和处理
   - 网络数据包如何定义和发送
   - HUD 如何计算位置和颜色

2. **修改现有功能**
   - 调整魔法宝石的威力
   - 修改传送距离
   - 改变掉落概率
   - 添加新的粒子效果

3. **使用 REPL 实验**
   ```clojure
   ;; 生成资源文件
   (require '[com.example.datagen :as dg])
   (dg/generate-all-assets!)

   ;; 修改配置
   (require '[com.fabriclj.swiss-knife.common.config.core :as cfg])
   (cfg/set-config-value! "example" [:magic-gem :power] 50.0 :save? true)

   ;; 测试功能
   (require '[com.fabriclj.swiss-knife.client.platform.core :as c])
   (def p (c/get-player))
   (players/give-item! p Items/DIAMOND 64)
   ```

#### 第四步: 扩展功能（根据兴趣）

尝试添加以下功能来练习:

---

## 📌 重要技术注意事项

### Minecraft 1.21 API 变化

本项目已针对 Minecraft 1.21 进行适配。以下是主要的 API 变化：

#### 1. 附魔系统（数据驱动）
- **变化**: MC 1.21 附魔完全改为数据驱动，无法通过代码注册
- **解决方案**: 
  - 在 `data/example/enchantment/` 目录创建 JSON 定义
  - 在代码中通过事件监听附魔效果（`on-living-hurt`）
  - 使用 `DataComponents/ENCHANTMENTS` 检查物品附魔
- **示例**: `explosive_strike.json` + `core.clj` 中的爆炸逻辑

#### 2. 音效播放（需要 Holder）
- **变化**: `Level.playSound()` 现在要求 `Holder<SoundEvent>` 而不是 `SoundEvent`
- **解决方案**: 使用 `Holder/direct(soundEvent)` 包装
- **位置**: `common/gameplay/sounds.clj` L153-157

#### 3. 物品耐久度（需要 ServerLevel）
- **变化**: `ItemStack.hurtAndBreak()` 新增 `ServerLevel` 参数
- **解决方案**: `hurtAndBreak(amount, level, player, onBroken)`
- **位置**: `common/game_objects/items.clj` L197

#### 4. 实体属性注册（必需）
- **变化**: MC 1.21 自定义实体必须显式注册属性
- **解决方案**: 使用 `FabricDefaultAttributeRegistry.register()`
- **封装**: `common/game_objects/entities.clj` 中的 `register-entity-attributes!`
- **位置**: `core.clj` 中的 `register-forest-guardian-attributes!`

#### 5. DamageSource API 变化
- **变化**: 移除了 `getPlayer()` 方法
- **解决方案**: 使用 `getEntity()` 然后检查是否为 `Player`
- **位置**: `core.clj` 中的 `on-living-death` 事件

#### 6. 实体渲染器注册（Fabric API）
- **变化**: 使用 Fabric API 而不是原版 `EntityRenderers`
- **解决方案**: `EntityRendererRegistry.register(entityType, rendererProvider)`
- **位置**: `client.clj` 中的 `setup-entity-renderers!`

### Swiss Knife 封装优先原则

本项目推荐优先使用 `com.fabriclj.swiss-knife` 中的封装，而不是直接调用 Minecraft 原生 API：

**物品操作**:
- ✅ **使用**: `(items/item-stack :diamond 64)` 
- ❌ **避免**: `(ItemStack. Items/DIAMOND 64)`

**玩家消息**:
- ✅ **使用**: `(players/send-message! player (text/colored-text "Hello" :green))`
- ❌ **避免**: 手动构建 `Component`

**音效播放**:
- ✅ **使用**: `(sounds/play-sound! level pos :minecraft:block.note_block.bell)`
- ❌ **避免**: 直接调用 `Level.playSound()`

**附魔操作**:
- ✅ **使用**: `(enchants/create-enchanted-book [["mymod" "explosive_strike" 3]])`
- ❌ **避免**: 手动操作 `ItemEnchantments$Mutable` 和 `STORED_ENCHANTMENTS`

**AI 系统**:
- ✅ **使用**: `(ai/add-goal! entity 1 (ai/ranged-attack-goal entity 1.0 60 16.0))`
- ❌ **避免**: 手动创建 `RangedAttackGoal` 和 `proxy Goal`

**实体操作**:
- ✅ **使用**: `(entities/set-velocity! entity dx dy dz)` 和 `(entities/distance-to e1 e2)`
- ❌ **避免**: `.setDeltaMovement` 和 `.distanceTo`

**原因**:
1. Swiss Knife 封装已处理 MC 1.21 API 变化
2. 代码更简洁易读
3. 类型转换和空检查已内置
4. 跨平台兼容（Fabric/Forge）

**附魔书创建示例**:
```clojure
;; 使用 Swiss Knife 封装（仅 1 行！）
(def book (enchants/create-enchanted-book
           [["example" "explosive_strike" 3]
            ["minecraft" "sharpness" 5]]))

;; ❌ 避免手动实现（20+ 行原生代码）
(let [book (ItemStack. Items/ENCHANTED_BOOK)
      registry (.lookupOrThrow ...)
      holder (.get registry ...)
      mutable (ItemEnchantments$Mutable. ...)]
  ;; ... 复杂的注册表查找和组件操作
  )
```

**森林守卫 AI 实现示例**:
```clojure
;; 使用 Swiss Knife AI 封装
(ai/add-goal! guardian 1
  (ai/create-goal 1
    :flags [:move]
    :can-use? (fn [entity]
                (when-let [target (.getTarget entity)]
                  (< (entities/distance-to entity target) 5.0)))
    :tick! (fn [entity]
             (when-let [target (.getTarget entity)]
               (let [dx (- (.getX entity) (.getX target))
                     dz (- (.getZ entity) (.getZ target))]
                 (entities/set-velocity! entity
                   (* dx 0.15)
                   (.getY (.getDeltaMovement entity))
                   (* dz 0.15)))))))

;; 远程攻击 AI
(ai/add-goal! guardian 2
  (ai/ranged-attack-goal guardian 1.0 60 16.0))

;; 寻找目标
(ai/add-target-goal! guardian 1
  (ai/nearest-attackable-target-goal guardian Player))
```

### 物品实例比较方法

由于物品通过 `DeferredRegister` 注册，比较物品时应使用实例而非字符串：

```clojure
;; ✅ 正确方式
(let [magic-gem-item (requiring-resolve 'com.example.core/magic-gem)]
  (when (= item (.get @magic-gem-item))
    ...))

;; ❌ 错误方式（不可靠）
(when (= (.getDescriptionId item) "item.example.magic_gem")
  ...)
```

**适用位置**: HUD 渲染、按键绑定、Mixin 钩子

---

#### 尝试添加以下功能来练习:

1. **合成配方** ✨
   - 使用魔法碎片合成魔法宝石
   - 使用条件配方（当某个 mod 加载时）

2. **新的魔法方块**
   - 魔法工作台
   - 魔法传送门

3. **更多魔法效果**
   - 治疗魔法
   - 范围伤害魔法
   - 飞行魔法

4. **进度系统**
   - 首次获得魔法宝石
   - 击败森林守卫
   - 收集所有魔法物品

5. **使用新工具** ✨
   - 添加配置验证器
   - 使用 DataGen 生成新资源
   - 添加事件优先级控制

### 下一步建议

1. **熟悉 nREPL** - 学习如何在 REPL 中交互式开发
2. **阅读 Swiss Knife 文档** - 了解更多可用功能
3. **阅读最佳实践** - [best-practices.md](../docs/best-practices.md) 性能优化和代码规范
4. **扩展 Example Mod** - 在这个项目基础上添加新功能
5. **创建自己的 Mod** - 使用本项目作为模板

## 📜 许可证

MIT License - 与 fabric-language-clojure 项目相同

---

**Happy Coding with Clojure! 🎉**
