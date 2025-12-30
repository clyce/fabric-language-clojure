# Arclojure 开发者指南

本文档面向希望深入了解 Arclojure 架构并进行高级开发的模组开发者。

## 目录

- [架构深度解析](#架构深度解析)
- [Clojure 开发最佳实践](#clojure-开发最佳实践)
- [内容注册详解](#内容注册详解)
- [事件系统集成](#事件系统集成)
- [Mixin 开发模式](#mixin-开发模式)
- [性能优化指南](#性能优化指南)
- [跨平台兼容性](#跨平台兼容性)
- [常见开发模式](#常见开发模式)

---

## 架构深度解析

### 三层架构模型

Arclojure 采用分层架构，每层有明确的职责边界：

```
┌────────────────────────────────────────────────────────────┐
│  Layer 1: 加载器契约层 (Java)                               │
│  ├─ 入口点 (@Mod, ModInitializer)                          │
│  ├─ Mixin 类 (@Mixin, @Inject)                            │
│  └─ 注解处理 (Compile-time)                                │
├────────────────────────────────────────────────────────────┤
│  Layer 2: 桥接层 (Java ↔ Clojure)                          │
│  ├─ ModMain.java (Clojure 引导器)                       │
│  ├─ ClojureHooks.java (静态方法桥接)                       │
│  └─ 类加载器管理                                            │
├────────────────────────────────────────────────────────────┤
│  Layer 3: 业务逻辑层 (Clojure)                              │
│  ├─ core.clj (初始化流程)                                  │
│  ├─ registry.clj (注册 DSL)                                │
│  ├─ hooks.clj (游戏逻辑)                                   │
│  ├─ client.clj (渲染/GUI)                                  │
│  └─ nrepl.clj (开发工具)                                   │
└────────────────────────────────────────────────────────────┘
```

### 为什么不能全用 Clojure？

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 模组发现 | 加载器在类扫描阶段查找 `@Mod` 注解 | Java 入口类 |
| Mixin 注入 | 需要编译期确定的字节码结构 | Java Mixin 类 |
| AOT 编译 | Clojure 的 gen-class 不稳定 | Java 存根 + Clojure 实现 |
| 类加载顺序 | 模组加载器的隔离机制 | 手动配置 ClassLoader |

### 类加载器配置详解

```java
// ModMain.java 中的关键代码
Thread.currentThread().setContextClassLoader(callerClass.getClassLoader());
```

**为什么必须这样做？**

1. Fabric 使用 Knot ClassLoader
2. Forge 使用 ModLauncher
3. 两者都与默认的 AppClassLoader 隔离
4. Clojure 的 DynamicClassLoader 默认使用 AppClassLoader
5. 不设置会导致 `ClassNotFoundException`

---

## Clojure 开发最佳实践

### 命名空间组织

推荐按功能模块划分命名空间：

```
com.arclojure/
├── core.clj           # 核心初始化（必需）
├── client.clj         # 客户端入口（必需）
├── registry.clj       # 注册逻辑
├── hooks.clj          # Mixin 钩子
├── content/           # 游戏内容
│   ├── items.clj
│   ├── blocks.clj
│   ├── entities.clj
│   └── recipes.clj
├── systems/           # 游戏系统
│   ├── magic.clj
│   ├── economy.clj
│   └── quests.clj
└── util/              # 工具函数
    ├── nbt.clj
    ├── math.clj
    └── player.clj
```

### 单一数据源原则

**避免在多处重复定义相同的常量：**

```clojure
;; ❌ 错误：手动维护字面量，容易不一致
(ns com.arclojure.registry)
(def ^:const mod-id "arclojure")  ; 与 Java 层重复

;; ✅ 正确：直接引用 Java 常量
(ns com.arclojure.registry
  (:import [com.arclojure ModMain]))
(def ^:const mod-id ModMain/MOD_ID)  ; 单一数据源
```

**优势：**
- ✅ 修改一处，全局生效
- ✅ 避免拼写错误
- ✅ 编译期检查引用是否存在

### 类型提示规范

**始终为 Java 互操作添加类型提示：**

```clojure
;; ❌ 错误：会产生反射调用
(defn get-player-name [player]
  (.getName player))

;; ✅ 正确：无反射
(defn get-player-name [^net.minecraft.world.entity.player.Player player]
  (.getName player))
```

**检查反射警告：**

```clojure
;; 在文件顶部启用
(set! *warn-on-reflection* true)

;; 或在 REPL 中全局启用
(binding [*warn-on-reflection* true]
  (load-file "src/main/clojure/com/arclojure/core.clj"))
```

### 数据驱动开发

利用 Clojure 的数据结构定义游戏内容：

```clojure
(def items-config
  [{:id "iron_coin"
    :max-stack 64
    :rarity :common
    :properties {:fireproof true}}

   {:id "diamond_coin"
    :max-stack 16
    :rarity :rare
    :properties {:fireproof true :glow true}}])

(defn register-items! []
  (doseq [{:keys [id max-stack rarity properties]} items-config]
    (defitem (symbol id)
      (create-item max-stack rarity properties))))
```

### 状态管理

**使用 atom 管理可变状态：**

```clojure
(defonce player-data
  "玩家数据缓存"
  (atom {}))

(defn get-player-level [player-uuid]
  (get-in @player-data [player-uuid :level] 1))

(defn set-player-level! [player-uuid level]
  (swap! player-data assoc-in [player-uuid :level] level))
```

**避免在 atom 中存储游戏实体：**

```clojure
;; ❌ 错误：实体对象会被 GC 回收或失效
(def current-player (atom nil))

;; ✅ 正确：存储 UUID，需要时查询
(def current-player-uuid (atom nil))

(defn get-current-player []
  (when-let [uuid @current-player-uuid]
    (find-player-by-uuid uuid)))
```

---

## 内容注册详解

### 物品注册

#### 基础物品

```clojure
(ns com.arclojure.content.items
  (:require [com.arclojure.registry :refer [defitem]])
  (:import [net.minecraft.world.item Item Item$Properties]
           [net.minecraft.world.item.Rarity]))

;; 简单物品
(defitem copper-ingot
  (Item. (Item$Properties.)))

;; 带属性的物品
(defitem fire-gem
  (Item. (-> (Item$Properties.)
             (.stacksTo 16)
             (.fireResistant)
             (.rarity Rarity/RARE))))
```

#### 自定义物品类

```clojure
;; 定义 Java 类（使用 gen-class）
(ns com.arclojure.content.custom-item
  (:gen-class
    :name com.arclojure.CustomCoin
    :extends net.minecraft.world.item.Item
    :constructors {[net.minecraft.world.item.Item$Properties]
                   [net.minecraft.world.item.Item$Properties]}
    :init init
    :state state))

(defn -init [props]
  [[props] (atom {:uses 0})])

(defn -use [this ^net.minecraft.world.level.Level level
                 ^net.minecraft.world.entity.player.Player player
                 ^net.minecraft.world.InteractionHand hand]
  (swap! (.state this) update :uses inc)
  (println "Coin used" (get @(.state this) :uses) "times")
  (net.minecraft.world.InteractionResultHolder/success
    (.getItemInHand player hand)))

;; 注册
(defitem magic-coin
  (com.arclojure.CustomCoin. (Item$Properties.)))
```

### 方块注册

#### 基础方块

```clojure
(ns com.arclojure.content.blocks
  (:require [com.arclojure.registry :refer [defblock defitem]])
  (:import [net.minecraft.world.level.block Block]
           [net.minecraft.world.level.block.state BlockBehaviour$Properties]
           [net.minecraft.world.level.material MapColor]
           [net.minecraft.world.item BlockItem Item$Properties]))

;; 注册方块
(defblock example-block
  (Block. (-> (BlockBehaviour$Properties/of)
              (.mapColor MapColor/STONE)
              (.strength 3.0 3.0)
              (.requiresCorrectToolForDrops))))

;; 同时注册方块物品（BlockItem）
(defitem example-block-item
  (BlockItem. (.get example-block)
              (Item$Properties.)))
```

#### 自动注册 BlockItem

```clojure
(defmacro defblock-with-item
  "定义方块并自动创建对应的 BlockItem"
  [block-name block-form]
  `(do
     (defblock ~block-name ~block-form)
     (defitem ~(symbol (str block-name "-item"))
       (BlockItem. (.get ~block-name)
                   (Item$Properties.)))))

;; 使用
(defblock-with-item stone-altar
  (Block. (BlockBehaviour$Properties/of)))
```

### 创造模式标签页

```clojure
(ns com.arclojure.content.creative-tabs
  (:import [dev.architectury.registry CreativeTabRegistry]
           [net.minecraft.world.item CreativeModeTab ItemStack]
           [net.minecraft.network.chat Component]))

(def my-tab
  (CreativeTabRegistry/create
    (minecraft-resource-location "arclojure" "main")
    (reify java.util.function.Supplier
      (get [_]
        (-> (CreativeModeTab/builder)
            (.icon (reify java.util.function.Supplier
                     (get [_] (ItemStack. (.get example-item)))))
            (.title (Component/translatable "itemGroup.arclojure.main"))
            (.build))))))
```

---

## 事件系统集成

### Architectury 事件监听

```clojure
(ns com.arclojure.events
  (:import [dev.architectury.event.events.common
            PlayerEvent TickEvent EntityEvent]
           [dev.architectury.event EventResult]))

;; 玩家加入事件
(defn on-player-join [^net.minecraft.server.level.ServerPlayer player]
  (println (str "Player " (.getName player) " joined!")))

;; 注册事件
(defn register-events! []
  (-> (PlayerEvent/PLAYER_JOIN)
      (.register (reify java.util.function.Consumer
                   (accept [_ player]
                     (on-player-join player)))))

  ;; Tick 事件
  (-> (TickEvent/SERVER_PRE)
      (.register (reify java.util.function.Consumer
                   (accept [_ server]
                     (on-server-tick server))))))
```

### 可取消事件

```clojure
;; 玩家破坏方块事件
(defn on-block-break [^net.minecraft.world.level.Level level
                      ^net.minecraft.core.BlockPos pos
                      ^net.minecraft.world.level.block.state.BlockState state
                      ^net.minecraft.server.level.ServerPlayer player]
  (if (should-prevent-break? pos player)
    EventResult/interruptFalse  ; 取消事件
    EventResult/pass))          ; 继续传播

(-> (PlayerEvent/BREAK_BLOCK)
    (.register (reify dev.architectury.event.Event$BlockBreaker
                 (breakBlock [_ level pos state player equipment]
                   (on-block-break level pos state player)))))
```

---

## Mixin 开发模式

### 创建新 Mixin

**步骤 1：编写 Java Mixin 类**

```java
// common/src/main/java/com/arclojure/mixin/MixinItemStack.java
package com.arclojure.mixin;

import com.arclojure.ClojureHooks;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {

    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void arclojure$modifyMaxStackSize(CallbackInfoReturnable<Integer> cir) {
        ClojureHooks.onGetMaxStackSize((ItemStack)(Object)this, cir);
    }
}
```

**步骤 2：添加桥接方法**

```java
// ClojureHooks.java 中添加
public static void onGetMaxStackSize(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
    ensureInitialized();
    if (onGetMaxStackSizeFn != null) {
        try {
            Object result = onGetMaxStackSizeFn.invoke(stack, cir);
            // Clojure 函数可以返回新的堆叠大小
            if (result instanceof Long) {
                cir.setReturnValue(((Long) result).intValue());
            }
        } catch (Exception e) {
            ModMain.LOGGER.error("[ClojureHooks] Error in onGetMaxStackSize", e);
        }
    }
}
```

**步骤 3：实现 Clojure 逻辑**

```clojure
;; hooks.clj 中添加
(defn on-get-max-stack-size
  [^ItemStack stack ^CallbackInfoReturnable cir]
  ;; 让所有钻石工具堆叠到 16
  (when (is-diamond-tool? stack)
    (.setReturnValue cir 16)))
```

**步骤 4：注册 Mixin**

```json
// arclojure.mixins.json
{
  "mixins": [
    "MixinPlayer",
    "MixinItemStack"
  ]
}
```

### Mixin 注入点选择

| 注入点 | 用途 | 示例 |
|--------|------|------|
| `@At("HEAD")` | 方法开始 | 前置逻辑、事件触发 |
| `@At("TAIL")` | 方法结束 | 后置逻辑、清理 |
| `@At("RETURN")` | return 语句前 | 修改返回值 |
| `@At("INVOKE")` | 特定方法调用 | 拦截子方法调用 |

### 使用 @Shadow 访问私有字段

```java
@Mixin(Player.class)
public abstract class MixinPlayer {

    @Shadow
    @Final
    private Inventory inventory;

    @Inject(method = "tick", at = @At("HEAD"))
    private void arclojure$onTick(CallbackInfo ci) {
        // 可以访问 inventory 字段
        ClojureHooks.onPlayerTickWithInventory(
            (Player)(Object)this,
            this.inventory
        );
    }
}
```

---

## 性能优化指南

### 热路径优化

**识别热路径：**

- `tick()` 方法（每秒调用 20 次）
- 渲染相关方法（每帧调用）
- 事件处理器（频繁触发）

**优化技巧：**

```clojure
;; ❌ 慢：每次创建序列
(defn process-entities [entities]
  (doseq [e (filter alive? entities)]
    (update-entity e)))

;; ✅ 快：使用 loop/recur
(defn process-entities [entities]
  (loop [es entities]
    (when-let [e (first es)]
      (when (alive? e)
        (update-entity e))
      (recur (rest es)))))

;; ✅ 更快：使用 reduce
(defn process-entities [entities]
  (reduce (fn [_ e]
            (when (alive? e)
              (update-entity e)))
          nil
          entities))
```

### 避免装箱/拆箱

```clojure
;; ❌ 慢：大量装箱
(defn calculate-damage [base-damage multiplier]
  (* base-damage multiplier))

;; ✅ 快：使用原生类型
(defn calculate-damage ^double [^double base-damage ^double multiplier]
  (* base-damage multiplier))
```

### 缓存常用数据

```clojure
(def ^:const TICK_RATE 20)
(def ^:const MAX_HEALTH 20.0)

;; 缓存频繁访问的对象
(defonce item-cache
  (atom {}))

(defn get-cached-item [item-id]
  (or (@item-cache item-id)
      (let [item (expensive-lookup item-id)]
        (swap! item-cache assoc item-id item)
        item)))
```

### 延迟计算

```clojure
;; 使用 delay 推迟昂贵的初始化
(def ^:private registry-data
  (delay
    (println "Loading registry data...")
    (load-expensive-data)))

;; 首次访问时才执行
(defn get-registry []
  @registry-data)
```

---

## 跨平台兼容性

### 检测当前平台

```clojure
(ns com.arclojure.platform
  (:import [dev.architectury.platform Platform]))

(defn fabric? []
  (Platform/isFabric))

(defn forge? []
  (Platform/isForge))

(defn platform-name []
  (.getName (Platform/getName)))
```

### 平台特定代码

```clojure
(defn register-content! []
  ;; 通用注册
  (register-items!)
  (register-blocks!)

  ;; 平台特定
  (cond
    (fabric?) (do
                (println "Using Fabric-specific features")
                (register-fabric-features!))

    (forge?)  (do
                (println "Using Forge-specific features")
                (register-forge-features!))))
```

### 使用 Architectury API 抽象

**优先使用 Architectury 提供的跨平台 API：**

```clojure
;; ✅ 推荐：使用 Architectury 注册表
(def items (DeferredRegister/create Registries/ITEM "modid"))

;; ❌ 不推荐：直接使用平台 API
;; (def items (net.fabricmc.fabric.api.item.v1.FabricItemSettings.))
```

---

## 常见开发模式

### NBT 数据持久化

```clojure
(ns com.arclojure.util.nbt
  (:import [net.minecraft.nbt CompoundTag ListTag]
           [net.minecraft.world.item.ItemStack]))

(defn write-nbt
  "将 Clojure map 写入 NBT"
  [^CompoundTag nbt data]
  (doseq [[k v] data]
    (let [key (name k)]
      (cond
        (string? v) (.putString nbt key v)
        (number? v) (.putDouble nbt key (double v))
        (boolean? v) (.putBoolean nbt key v)
        (map? v) (let [compound (CompoundTag.)]
                   (write-nbt compound v)
                   (.put nbt key compound))))))

(defn read-nbt
  "从 NBT 读取为 Clojure map"
  [^CompoundTag nbt]
  (into {}
        (for [key (.getAllKeys nbt)]
          [(keyword key) (.get nbt key)])))

;; 使用示例
(defn save-player-data [^Player player data]
  (let [nbt (.getPersistentData player)]
    (write-nbt nbt {:level 5
                    :coins 100
                    :unlocked-skills ["fireball" "teleport"]})))
```

### 粒子效果

```clojure
(ns com.arclojure.client.particles
  (:import [net.minecraft.client.Minecraft]
           [net.minecraft.core.particles ParticleTypes]
           [net.minecraft.world.level.Level]))

(defn spawn-particle-ring
  "在位置周围生成粒子环"
  [^Level level x y z radius]
  (when (.isClientSide level)
    (let [mc (Minecraft/getInstance)
          particle-manager (.particleEngine mc)]
      (doseq [angle (range 0 (* 2 Math/PI) 0.2)]
        (let [px (+ x (* radius (Math/cos angle)))
              pz (+ z (* radius (Math/sin angle)))]
          (.createParticle particle-manager
                           ParticleTypes/FLAME
                           px y pz
                           0.0 0.0 0.0))))))
```

### 配置文件

```clojure
(ns com.arclojure.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def config-path "config/arclojure.edn")

(def default-config
  {:max-players 100
   :difficulty :normal
   :features {:magic true
              :economy false}})

(defn load-config []
  (if (.exists (io/file config-path))
    (with-open [r (io/reader config-path)]
      (merge default-config (edn/read (java.io.PushbackReader. r))))
    default-config))

(defn save-config [config]
  (io/make-parents config-path)
  (spit config-path (pr-str config)))

;; 使用
(defonce config (atom (load-config)))

(defn get-setting [& path]
  (get-in @config path))
```

### 国际化（i18n）

```clojure
(ns com.arclojure.i18n
  (:import [net.minecraft.network.chat Component]))

(defn translatable
  "创建可翻译的文本组件"
  ([key]
   (Component/translatable key))
  ([key & args]
   (Component/translatable key (into-array Object args))))

;; 在资源文件中定义翻译
;; assets/arclojure/lang/en_us.json:
;; {
;;   "item.arclojure.magic_coin": "Magic Coin",
;;   "message.arclojure.level_up": "Level up! Now level %s"
;; }

;; 使用
(defn send-level-up-message [player level]
  (.sendSystemMessage player
    (translatable "message.arclojure.level_up" level)))
```

---

## 开发技巧集锦

### 使用 comment 块进行 REPL 探索

```clojure
(comment
  ;; 这些代码不会被执行，但可以在 REPL 中逐个求值

  ;; 测试物品注册
  (require 'com.arclojure.registry :reload)

  ;; 查看注册表状态
  @com.arclojure.registry/items

  ;; 模拟事件
  (com.arclojure.hooks/on-player-jump nil nil)

  ;; 清空缓存
  (reset! com.arclojure.cache/item-cache {})
  )
```

### 条件编译

```clojure
;; 根据开发/生产环境执行不同代码
(defn init []
  (when (dev-mode?)
    (println "Development mode - starting nREPL")
    (nrepl/start-server!))

  #_:clj-kondo/ignore
  (when-not (dev-mode?)
    (println "Production mode - nREPL disabled")))
```

### 宏调试

```clojure
;; 使用 macroexpand 查看宏展开结果
(macroexpand-1
  '(defitem magic-sword
     (Item. (Item$Properties.))))

;; 使用 clojure.walk/macroexpand-all 完全展开
(require '[clojure.walk :as walk])
(walk/macroexpand-all
  '(defitem magic-sword
     (Item. (Item$Properties.))))
```

---

## 推荐阅读

- [Clojure 官方文档](https://clojure.org/)
- [Architectury API 文档](https://docs.architectury.dev/)
- [Fabric Wiki](https://fabricmc.net/wiki/)
- [Forge 文档](https://docs.minecraftforge.net/)
- [Mixin 规范](https://github.com/SpongePowered/Mixin/wiki)
- [Clojure 性能优化指南](https://clojure.org/reference/java_interop#_performance)

---

**下一步建议：**

1. 阅读 [快速开始](quick-start.md) 搭建开发环境
2. 参考本指南实现你的第一个功能
3. 使用 [调试指南](debug-guide.md) 配置 nREPL 调试
4. 查阅 [Clojure MC Mod 开发 ArchAPI 分析.md](Clojure%20MC%20Mod%20开发%20ArchAPI%20分析.md) 深入理解架构
