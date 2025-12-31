# 瑞士军刀 (Swiss Knife)

> 快速封装 Architectury API 和 Fabric API 常见功能的 Clojure 工具库

## 📦 简介

瑞士军刀是一个为 fabric-language-clojure 项目设计的工具库，提供对 Minecraft Mod 开发常用功能的快速封装。它基于 Architectury API，为跨平台模组开发提供统一、简洁的 Clojure 接口。

## 🎯 设计目标

- **简洁易用**：用 Clojure 风格的 API 替代冗长的 Java 调用
- **功能完整**：覆盖 MC Mod 开发的常见场景
- **跨平台**：基于 Architectury API，支持 Fabric 和 Forge
- **宏驱动**：利用 Clojure 宏简化重复代码
- **文档完善**：每个函数都有详细的文档字符串和示例

## 📚 模块结构

### Common 模块（服务端+客户端通用）

#### 基础系统
| 模块 | 命名空间 | 功能 |
|------|---------|------|
| **核心工具** | `common.core` | 平台检测、资源定位、日志、基础配置 |
| **配置文件** | `common.config-file` | ⭐ EDN 配置文件、热重载、配置验证、Mod 隔离 |
| **注册系统** | `common.registry` | 物品/方块注册、属性构建器、批量注册 |
| **事件系统** | `common.events` | 80+ 游戏事件钩子，生命周期、Tick、玩家、实体、方块等 |
| **物品工具** | `common.items` | 物品栈管理、NBT/数据组件、物品操作 |
| **方块工具** | `common.blocks` | 方块操作、状态管理、区域填充、向量坐标支持 |
| **实体工具** | `common.entities` | 实体生成、属性修改、药水效果 |
| **网络通信** | `common.network` | 客户端-服务端数据包、EDN 序列化 |
| **创造标签** | `common.creative-tabs` | 创造模式物品栏标签页 |
| **燃料系统** | `common.fuel` | 燃料注册、燃烧时间管理 |
| **资源重载** | `common.reload-listeners` | 响应 `/reload` 命令的监听器 |

#### 游戏系统
| 模块 | 命名空间 | 功能 |
|------|---------|------|
| **物理系统** | `common.physics` | 射线追踪、碰撞检测、速度计算、抛物线运动 |
| **音效系统** | `common.sounds` | 音效播放、注册、序列播放、音效构建器 |
| **标签系统** | `common.tags` | 方块/物品/实体标签查询和创建 |
| **命令系统** | `common.commands` | 简化的命令注册、参数解析、DSL |
| **数据持久化** | `common.data` | 玩家数据、世界数据、NBT 转换 |
| **伤害系统** | `common.damage` | 伤害计算、伤害类型、护甲计算 |
| **附魔系统** | `common.enchantments` | 附魔查询、添加、效果计算 |
| **容器系统** | `common.containers` | 方块 GUI、菜单类型、槽位布局 |
| **背包系统** | `common.inventories` | 自定义背包、物品操作、NBT 存储 |

#### 高级 DSL
| 模块 | 命名空间 | 功能 |
|------|---------|------|
| **配方系统** | `common.recipes` | 有序/无序/熔炼配方、战利品表、数据生成 |
| **实用工具** | `common.utils` | 文本/时间/数学/NBT/调试工具集 |
| **世界生成** | `common.worldgen` | 矿石/树木配置、结构放置、生物群系修改 |
| **AI 系统** | `common.ai` | AI 目标、行为树、寻路、黑板系统 |
| **增强宏** | `common.dsl` | `defitem+`/`defblock+` 增强注册宏 |
| **链式构建器** | `common.builders` | 流畅的属性构建器 API |
| **事件链** | `common.event-chain` | 事件组合、条件执行、错误处理 |

#### 进阶功能
| 模块 | 命名空间 | 功能 |
|------|---------|------|
| **药水系统** | `common.potions` | ⭐ 效果管理、30+效果、**自定义效果**、预设组合 |
| **村民系统** | `common.villagers` | ⭐ 15种职业、**改进交易签名**、**自定义职业** |
| **进度系统** | `common.advancements` | ⭐ 进度管理、触发条件、**defadvancement 宏** |

#### 系统增强（第七批）
| 模块 | 命名空间 | 功能 |
|------|---------|------|
| **配置同步** | `common.config-sync` | ⭐ 客户端-服务端配置同步、冲突处理、同步策略 |
| **性能分析** | `common.profiler` | ⭐ 时间/内存/TPS监控、实体性能、报告生成 |

#### 高级功能（第八批）
| 模块 | 命名空间 | 功能 |
|------|---------|------|
| **配置 GUI** | `client.config-screen` | ⭐ 游戏内配置界面、5种组件、自动生成、验证 |
| **调试可视化** | `client.debug-visualizer` | ⭐ 网络流量、区块加载、实体密度、TPS图表、性能热点 |
| **数据包支持** | `common.datapack` | ⭐ 完整数据包生成、标签、战利品表、函数、谓词、修饰器 |

### Client 模块（客户端专用）

| 模块 | 命名空间 | 功能 |
|------|---------|------|
| **客户端核心** | `client.core` | 客户端访问器、窗口信息、性能数据 |
| **客户端事件** | `client.events` | 渲染事件、输入事件、GUI 事件 |
| **按键绑定** | `client.keybindings` | 按键注册、状态查询、键码映射 |
| **渲染工具** | `client.rendering` | 基础渲染、颜色工具、矩阵变换 |
| **HUD 系统** | `client.hud` | 自定义 HUD 元素、屏幕位置 |
| **GUI/菜单** | `client.menus` | 自定义屏幕、组件创建（按钮/复选框/滑块/文本框/标签页） |
| **粒子系统** | `client.particles` | 80+ 粒子类型、几何图形、预设效果 |
| **调试渲染** | `client.debug-render` | ⭐ AI路径/导航目标/碰撞箱/区域可视化 |

## 🚀 快速开始

### 基本使用

```clojure
(ns com.mymod.core
  (:require [com.fabriclj.swiss-knife :as mb]))

;; 打印信息
(mb/print-info)

;; 平台检测
(when (mb/fabric?)
  (mb/log-info "Running on Fabric!"))

;; 创建注册表
(def items (mb/create-registry "mymod" :item))
(def blocks (mb/create-registry "mymod" :block))

;; 注册事件
(mb/on-player-join
  (fn [player]
    (mb/log-info (.getName player) "joined the game!")))
```

### 注册物品和方块

```clojure
(require '[com.fabriclj.swiss-knife.common.registry :as reg])

;; 使用宏定义物品
(reg/defitem items magic-sword
  (reg/simple-item :stack-size 1 :rarity :epic))

;; 同时注册方块和物品
(reg/defblock-item blocks items magic-ore
  (reg/block-properties :strength 3.0 :sound-type :stone)
  (reg/item-properties :rarity :rare))

;; 执行注册
(reg/register-all! items blocks)
```

### 事件处理

```clojure
(require '[com.fabriclj.swiss-knife.common.events :as events])

;; 服务器 Tick
(events/on-server-tick
  (fn [server]
    (when (zero? (mod (.getTickCount server) 20))
      (mb/log-debug "One second passed"))))

;; 方块破坏
(events/on-block-break
  (fn [level pos state player]
    (if (= state (mb/get-block :minecraft:bedrock))
      (events/event-interrupt)  ; 禁止破坏基岩
      (events/event-pass))))

;; 实体生成（禁止苦力怕）
(events/on-entity-spawn
  (fn [entity level]
    (if (instance? net.minecraft.world.entity.monster.Creeper entity)
      (events/event-interrupt)
      (events/event-pass))))
```

### 网络通信

```clojure
(require '[com.fabriclj.swiss-knife.common.network :as net])

;; 定义数据包（使用 EDN 自动序列化）
(net/defpacket-edn chat-packet "mymod:chat"
  :server (fn [data player]
            (println (.getName player) "says:" (:message data)))
  :client (fn [data player]
            (println "Server:" (:message data))))

;; 客户端发送
(net/send-to-server! chat-packet {:message "Hello!"})

;; 服务端发送
(net/send-to-player! player chat-packet {:message "Welcome!"})
```

### 客户端功能

```clojure
(when (mb/client-side?)
  (require '[com.fabriclj.swiss-knife.client.core :as client]
           '[com.fabriclj.swiss-knife.client.keybindings :as keys]
           '[com.fabriclj.swiss-knife.client.hud :as hud]
           '[com.fabriclj.swiss-knife.client.particles :as particles])

  ;; 注册按键
  (keys/defkey! :special-ability
    "key.mymod.special"
    :r
    :gameplay
    (fn []
      (println "Special ability activated!")))

  ;; 注册 HUD
  (hud/init-hud-system!)

  (hud/register-hud-renderer!
    (fn [graphics delta]
      (when-let [player (client/get-player)]
        (let [health (.getHealth player)
              pos (hud/get-top-left 10)]
          (hud/draw-bar-hud graphics
                            (:x pos) (:y pos)
                            100 10
                            health 20.0
                            0xFF00FF00))))
    0
    :health-bar)

  ;; 粒子效果
  (particles/magic-effect! [100 64 200])
  (particles/circle-particles! :flame [100 64 200] 2.0 20))
```

### 物理系统

```clojure
(require '[com.fabriclj.swiss-knife.common.physics :as physics])

;; 射线追踪
(def hit (physics/raycast-from-eyes player 5.0))

;; 碰撞检测
(def box (physics/aabb 0 0 0 1 1 1))
(def entities (physics/get-entities-in-aabb level box))

;; 速度操作
(physics/push-towards! entity [100 64 200] 0.5)
(physics/launch-upward! entity 1.0)

;; 抛物线计算
(def velocity (physics/calculate-projectile-velocity
                [100 64 200] [110 70 210] 40))
```

### 音效系统

```clojure
(require '[com.fabriclj.swiss-knife.common.sounds :as sounds])

;; 播放音效
(sounds/play-sound! level [100 64 200] :minecraft:entity.player.levelup
  {:source :player :volume 1.0 :pitch 1.2})

;; 快捷音效
(sounds/quick-sound! level [100 64 200] :success)

;; 音效序列
(sounds/play-sound-sequence! level
  [{:sound :pling :delay 0 :pos pos :opts {:pitch 1.0}}
   {:sound :pling :delay 5 :pos pos :opts {:pitch 1.5}}])
```

### 伤害系统

```clojure
(require '[com.fabriclj.swiss-knife.common.damage :as damage])

;; 造成伤害
(damage/deal-damage-from! target 10.0 player :player-attack)

;; 伤害计算
(def final-damage
  (-> 10.0
      (damage/calculate-armor-damage 15 2)
      (damage/calculate-resistance-damage 2)))
```

### 配置文件系统 ⭐

```clojure
(require '[com.fabriclj.swiss-knife.common.config-file :as config])

;; 注册配置（自动创建 config/mymod/config.edn）
(config/register-config! "mymod"
  {:features {:mining true
              :pvp false}
   :balance {:spawn-rate 0.5
             :damage-multiplier 1.0}})

;; 读取配置
(when (config/get-config-value "mymod" [:features :mining])
  (enable-mining!))

;; 修改并保存
(config/set-config-value! "mymod" [:balance :spawn-rate] 0.8 :save? true)

;; 配置验证
(config/register-config! "mymod" {...}
  :validator (fn [cfg]
               (and (pos? (:spawn-rate cfg))
                    (>= (:damage-multiplier cfg) 0.1))))

;; 配置热重载
(config/reload-config! "mymod")

;; 监听配置变化
(config/watch-config! "mymod" :my-watcher
  (fn [old new]
    (when (not= (:spawn-rate old) (:spawn-rate new))
      (update-spawn-system!))))
```

### 增强的 DSL

```clojure
(require '[com.fabriclj.swiss-knife.common.dsl :as dsl]
         '[com.fabriclj.swiss-knife.common.builders :as builders])

;; 简洁的物品注册
(dsl/defitem+ items magic-wand "magic_wand"
  :stack-size 1
  :durability 500
  :rarity :rare
  :fireproof? true
  :on-use (fn [level player hand]
            (println "Magic!")
            InteractionResult/SUCCESS))

;; 链式构建器
(-> (builders/item-properties)
    (builders/with-stack-size 16)
    (builders/with-rarity :rare)
    builders/fireproof)
```

## 📖 详细文档

每个模块都有完整的文档字符串和使用示例。建议通过以下方式查看：

1. **查看命名空间文档**：
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.events :as events])
   (doc events/on-player-join)
   ```

2. **查看示例代码**：每个文件末尾都有 `(comment ...)` 块，包含详细的使用示例

3. **查看内联注释**：所有函数参数、返回值都有详细说明

## 🎨 设计理念

### 1. Clojure First

所有 API 都遵循 Clojure 的设计哲学：

- **数据优先**：使用 Map 和 Vector 而非 Java 对象
- **函数式**：纯函数、不可变数据、函数组合
- **宏驱动**：用宏消除样板代码

### 2. 简洁优雅

```clojure
;; Java 风格
Item myItem = Registry.register(
    Registries.ITEM,
    new ResourceLocation("mymod", "my_item"),
    new Item(new Item.Properties().stacksTo(64).rarity(Rarity.RARE))
);

;; Swiss Knife 风格
(reg/defitem items my-item
  (reg/simple-item :stack-size 64 :rarity :rare))
```

### 3. 类型安全

虽然是动态语言，但通过类型提示避免反射：

```clojure
(set! *warn-on-reflection* true)  ; 所有模块都启用

(defn get-health [^LivingEntity entity]
  (.getHealth entity))  ; 无反射调用
```

### 4. 错误处理

提供友好的错误信息和安全的默认值：

```clojure
(defn item-stack
  ([item]
   (item-stack item 1))
  ([item count]
   (if-let [item-obj (core/get-item item)]
     (ItemStack. item-obj count)
     (throw (IllegalArgumentException.
              (str "Unknown item: " item))))))
```

## 🔧 高级功能

### 数据驱动注册

```clojure
(reg/register-from-edn items :item
  {:magic-sword {:stack-size 1 :rarity :epic}
   :magic-gem {:stack-size 64 :rarity :rare}
   :magic-ore {:stack-size 64}})
```

### 通用数据包系统

```clojure
;; 无需预定义数据包类型
(net/init-generic-packet-system!)

(net/register-generic-handler! :buy-item :server
  (fn [data player]
    (println "Player wants to buy" (:item data))))

(net/send-generic! :buy-item {:item "sword" :count 1})
```

### 方块区域操作

```clojure
(require '[com.fabriclj.swiss-knife.common.blocks :as blocks])

;; 填充区域
(blocks/fill-blocks! level pos1 pos2 Blocks/STONE)

;; 查找钻石矿
(def diamonds
  (blocks/scan-blocks level pos1 pos2
    (fn [pos state]
      (blocks/is-block? level pos :minecraft:diamond_ore))))
```

### 第七批：自定义效果和配置同步

```clojure
;; 1. 创建自定义药水效果
(require '[com.fabriclj.swiss-knife.common.potions :as potions])

(def bleeding-effect
  (potions/create-custom-effect :bleeding
    :harmful 0xAA0000
    :on-tick (fn [entity amplifier]
               (.hurt entity (DamageSource. "bleeding") 0.5))
    :tick-rate 20))

(potions/register-custom-effect! "mymod" :bleeding bleeding-effect)
(potions/add-effect! player :bleeding 400)

;; 2. 改进的村民交易（向量参数）
(require '[com.fabriclj.swiss-knife.common.villagers :as villagers])

(villagers/create-trade
  [Items/WOODEN_SWORD Items/EMERALD]  ; 向量参数
  Items/IRON_SWORD
  :max-uses 8)

;; 3. 使用 defadvancement 宏
(require '[com.fabriclj.swiss-knife.common.advancements :as adv])

(adv/defadvancement my-first-diamond
  "mymod:first_diamond"
  Items/DIAMOND
  "获得钻石！"
  "挖到你的第一颗钻石"
  :parent "minecraft:story/mine_stone"
  :criteria {:has_diamond (adv/inventory-changed-criterion ["minecraft:diamond"])}
  :rewards {:experience 100})

;; 4. 配置同步系统
(require '[com.fabriclj.swiss-knife.common.config-sync :as sync])

(sync/register-syncable-config! :server-rules
  "config/server-rules.edn"
  :required? true
  :validator (fn [config] (pos? (:max-players config))))

(mb/events/on-player-join
  (fn [player]
    (sync/push-all-configs-to-client! player)))

;; 5. 性能分析工具
(require '[com.fabriclj.swiss-knife.common.profiler :as prof])

(prof/profile :my-expensive-function
  (expensive-operation))

(let [stats (prof/get-timing-stats :my-expensive-function)]
  (println "Average:" (:avg-ms stats) "ms"))

;; 生成性能报告
(-> (prof/generate-performance-report :top-n 10)
    prof/print-performance-report)
```

### 第八批：高级功能

```clojure
;; 1. 配置 GUI 系统
(require '[com.fabriclj.swiss-knife.client.config-screen :as cfg])

(cfg/register-config-screen! :my-mod-config
  "My Mod Configuration"
  "config/mymod.edn"
  [(cfg/create-config-entry :enabled "Enable Mod" :boolean :default true)
   (cfg/create-config-entry :power "Power Level" :slider :default 50 :min 1 :max 100)
   (cfg/create-config-entry :mode "Mode" :enum :options [:easy :normal :hard])])

(cfg/open-config-screen! :my-mod-config)

;; 2. 调试可视化
(require '[com.fabriclj.swiss-knife.client.debug-visualizer :as debug-vis])

;; 启用调试面板
(debug-vis/toggle-debug-panel!)

;; 显示区块边界
(debug-vis/show-chunk-borders! level player-pos 3 :color :cyan)

;; 显示实体密度热图
(debug-vis/show-entity-density-heatmap! level player-pos 5)

;; 渲染调试面板
(mb/events/on-render-hud
  (fn [graphics partial-tick]
    (debug-vis/render-debug-panel! graphics partial-tick)))

;; 3. 数据包生成
(require '[com.fabriclj.swiss-knife.common.datapack :as dp])

;; 创建数据包
(dp/create-datapack "./datapacks/mymod" "mymod"
  :description "My Datapack" :pack-format 10)

;; 生成标签
(dp/save-tag! "./datapacks/mymod" "mymod"
  :blocks "custom_ores"
  (dp/create-tag ["mymod:copper_ore" "mymod:tin_ore"]))

;; 生成战利品表
(dp/save-loot-table! "./datapacks/mymod" "mymod"
  :blocks "copper_ore"
  {:type "minecraft:block"
   :pools [(dp/create-loot-pool
             [{:type "minecraft:item"
               :name "mymod:raw_copper"
               :functions [(dp/loot-function :apply-bonus
                             :enchantment "minecraft:fortune"
                             :formula "minecraft:ore_drops")]}])]})

;; 生成函数
(dp/save-function! "./datapacks/mymod" "mymod" "init"
  ["say My Mod initialized!" "time set day"])
```

## 🎯 最佳实践

1. **使用命名空间别名**：统一使用短别名（如 `mb`, `reg`, `events`）
2. **启用反射警告**：在开发时捕获性能问题
3. **利用宏**：使用 `defitem`, `defblock`, `defevent`, `defadvancement` 等宏简化代码
4. **客户端检查**：始终用 `(when (client-side?) ...)` 包裹客户端代码
5. **错误处理**：使用 `try-catch` 和日志函数记录错误
6. **性能监控**：在开发时使用 `prof/profile` 宏监控性能
7. **配置同步**：多人游戏时使用配置同步确保客户端与服务端一致

## 🤝 贡献

瑞士军刀是 fabric-language-clojure 项目的一部分，欢迎贡献！

## 📜 许可证

MIT License

---

**Happy Coding with Clojure! 🎉**
