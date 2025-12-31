(ns com.fabriclj.swiss-knife
  "瑞士军刀 - 主入口模块
   提供便捷的命名空间导入和快速访问。

   这是一个用于快速封装 Architectury API 和 Fabric API 常见功能的工具库，
   为其他 API 提供简洁的 Clojure 接口。"
  (:require ;; 平台和核心系统
   [com.fabriclj.swiss-knife.common.platform.core :as core]
   [com.fabriclj.swiss-knife.common.registry.core :as registry]
   [com.fabriclj.swiss-knife.common.lifecycle :as lifecycle]

   ;; 事件系统
   [com.fabriclj.swiss-knife.common.events.core :as events]
   [com.fabriclj.swiss-knife.common.events.chain :as event-chain]
   [com.fabriclj.swiss-knife.common.events.priority :as event-priority]

   ;; 游戏对象
   [com.fabriclj.swiss-knife.common.game-objects.items :as items]
   [com.fabriclj.swiss-knife.common.game-objects.blocks :as blocks]
   [com.fabriclj.swiss-knife.common.game-objects.entities :as entities]
   [com.fabriclj.swiss-knife.common.game-objects.players :as players]

   ;; 构建器系统
   [com.fabriclj.swiss-knife.common.builders.macros :as dsl]
   [com.fabriclj.swiss-knife.common.builders.runtime :as builders]

   ;; 网络和配置
   [com.fabriclj.swiss-knife.common.network.core :as network]
   [com.fabriclj.swiss-knife.common.config.core :as config-file]
   [com.fabriclj.swiss-knife.common.config.sync :as config-sync]
   [com.fabriclj.swiss-knife.common.config.validators :as config-validators]

   ;; 数据系统
   [com.fabriclj.swiss-knife.common.data.datapack :as datapack]
   [com.fabriclj.swiss-knife.common.data.persistence :as data]
   [com.fabriclj.swiss-knife.common.data.profiler :as profiler]
   [com.fabriclj.swiss-knife.common.data.reload-listeners :as reload]

   ;; DataGen 系统
   [com.fabriclj.swiss-knife.common.datagen.models :as datagen-models]
   [com.fabriclj.swiss-knife.common.datagen.blockstates :as datagen-blockstates]
   [com.fabriclj.swiss-knife.common.datagen.lang :as datagen-lang]

   ;; 游戏玩法系统
   [com.fabriclj.swiss-knife.common.gameplay.commands :as commands]
   [com.fabriclj.swiss-knife.common.gameplay.recipes :as recipes]
   [com.fabriclj.swiss-knife.common.gameplay.conditional-recipes :as conditional-recipes]
   [com.fabriclj.swiss-knife.common.gameplay.advancements :as advancements]
   [com.fabriclj.swiss-knife.common.gameplay.damage :as damage]
   [com.fabriclj.swiss-knife.common.gameplay.enchantments :as enchantments]
   [com.fabriclj.swiss-knife.common.gameplay.potions :as potions]
   [com.fabriclj.swiss-knife.common.gameplay.villagers :as villagers]
   [com.fabriclj.swiss-knife.common.gameplay.sounds :as sounds]
   [com.fabriclj.swiss-knife.common.gameplay.tags :as tags]
   [com.fabriclj.swiss-knife.common.gameplay.fuel :as fuel]
   [com.fabriclj.swiss-knife.common.gameplay.ai :as ai]

   ;; 物理和世界
   [com.fabriclj.swiss-knife.common.physics.core :as physics]
   [com.fabriclj.swiss-knife.common.world.regions :as regions]
   [com.fabriclj.swiss-knife.common.world.worldgen :as worldgen]

   ;; UI 系统
   [com.fabriclj.swiss-knife.common.ui.containers :as containers]
   [com.fabriclj.swiss-knife.common.ui.inventories :as inventories]
   [com.fabriclj.swiss-knife.common.ui.creative-tabs :as creative-tabs]

   ;; 工具函数
   [com.fabriclj.swiss-knife.common.utils.core :as utils]

   ;; 客户端系统( 延迟加载)
   [com.fabriclj.swiss-knife.client.ui.config-screen :as config-screen]
   [com.fabriclj.swiss-knife.client.debug.visualizer :as debug-viz]))

;; ============================================================================
;; 命名空间别名( 便于使用)
;; ============================================================================

;; 核心系统
(def core (the-ns 'com.fabriclj.swiss-knife.common.platform.core))
(def reg (the-ns 'com.fabriclj.swiss-knife.common.registry.core))
(def lifecycle (the-ns 'com.fabriclj.swiss-knife.common.lifecycle))

;; 事件系统
(def events (the-ns 'com.fabriclj.swiss-knife.common.events.core))
(def chain (the-ns 'com.fabriclj.swiss-knife.common.events.chain))

;; 游戏对象
(def items (the-ns 'com.fabriclj.swiss-knife.common.game-objects.items))
(def blocks (the-ns 'com.fabriclj.swiss-knife.common.game-objects.blocks))
(def entities (the-ns 'com.fabriclj.swiss-knife.common.game-objects.entities))
(def players (the-ns 'com.fabriclj.swiss-knife.common.game-objects.players))

;; 构建器
(def dsl (the-ns 'com.fabriclj.swiss-knife.common.builders.macros))
(def builders (the-ns 'com.fabriclj.swiss-knife.common.builders.runtime))

;; 网络和配置
(def net (the-ns 'com.fabriclj.swiss-knife.common.network.core))
(def config (the-ns 'com.fabriclj.swiss-knife.common.config.core))
(def sync (the-ns 'com.fabriclj.swiss-knife.common.config.sync))
(def validators (the-ns 'com.fabriclj.swiss-knife.common.config.validators))

;; 数据系统
(def data (the-ns 'com.fabriclj.swiss-knife.common.data.persistence))
(def dp (the-ns 'com.fabriclj.swiss-knife.common.data.datapack))
(def prof (the-ns 'com.fabriclj.swiss-knife.common.data.profiler))
(def reload (the-ns 'com.fabriclj.swiss-knife.common.data.reload-listeners))

;; DataGen 系统
(def datagen-models (the-ns 'com.fabriclj.swiss-knife.common.datagen.models))
(def datagen-bs (the-ns 'com.fabriclj.swiss-knife.common.datagen.blockstates))
(def datagen-lang (the-ns 'com.fabriclj.swiss-knife.common.datagen.lang))

;; 游戏玩法
(def commands (the-ns 'com.fabriclj.swiss-knife.common.gameplay.commands))
(def recipes (the-ns 'com.fabriclj.swiss-knife.common.gameplay.recipes))
(def conditional-recipes (the-ns 'com.fabriclj.swiss-knife.common.gameplay.conditional-recipes))
(def advancements (the-ns 'com.fabriclj.swiss-knife.common.gameplay.advancements))
(def damage (the-ns 'com.fabriclj.swiss-knife.common.gameplay.damage))
(def enchantments (the-ns 'com.fabriclj.swiss-knife.common.gameplay.enchantments))
(def potions (the-ns 'com.fabriclj.swiss-knife.common.gameplay.potions))
(def villagers (the-ns 'com.fabriclj.swiss-knife.common.gameplay.villagers))
(def sounds (the-ns 'com.fabriclj.swiss-knife.common.gameplay.sounds))
(def tags (the-ns 'com.fabriclj.swiss-knife.common.gameplay.tags))
(def fuel (the-ns 'com.fabriclj.swiss-knife.common.gameplay.fuel))
(def ai (the-ns 'com.fabriclj.swiss-knife.common.gameplay.ai))

;; 物理和世界
(def physics (the-ns 'com.fabriclj.swiss-knife.common.physics.core))
(def regions (the-ns 'com.fabriclj.swiss-knife.common.world.regions))
(def worldgen (the-ns 'com.fabriclj.swiss-knife.common.world.worldgen))

;; UI 系统
(def containers (the-ns 'com.fabriclj.swiss-knife.common.ui.containers))
(def inv (the-ns 'com.fabriclj.swiss-knife.common.ui.inventories))
(def tabs (the-ns 'com.fabriclj.swiss-knife.common.ui.creative-tabs))

;; 工具函数
(def utils (the-ns 'com.fabriclj.swiss-knife.common.utils.core))

;; 客户端系统
(def cfg-screen (the-ns 'com.fabriclj.swiss-knife.client.ui.config-screen))
(def debug-vis (the-ns 'com.fabriclj.swiss-knife.client.debug.visualizer))


;; ============================================================================
;; 快速访问常用函数
;; ============================================================================

;; 平台检测
(def platform-name core/platform-name)
(def fabric? core/fabric?)
(def forge? core/forge?)
(def client-side? core/client-side?)
(def server-side? core/server-side?)
(def mod-loaded? core/mod-loaded?)

;; 注册
(def create-registry registry/create-registry)
(def register registry/register)
(def register-all! registry/register-all!)

;; 事件
(def on-server-starting events/on-server-starting)
(def on-player-join events/on-player-join)
(def on-server-tick events/on-server-tick)

;; 日志
(def log-info core/log-info)
(def log-warn core/log-warn)
(def log-error core/log-error)
(def log-debug core/log-debug)

;; ============================================================================
;; 客户端模块( 延迟加载，避免在服务端加载客户端类)
;; ============================================================================

(defn client
  "获取客户端命名空间( 仅在客户端环境可用)

   返回: 包含所有客户端模块的映射

   示例:
   ```clojure
   (when (client-side?)
     (require '[com.fabriclj.swiss-knife :as mb])
     (let [client (client)]
       ((:get-player (:core client)))))
   ```"
  []
  (when (client-side?)
    (require '[com.fabriclj.swiss-knife.client.platform.core :as client-core]
           '[com.fabriclj.swiss-knife.client.events.core :as client-events]
           '[com.fabriclj.swiss-knife.client.ui.keybindings :as keybindings]
           '[com.fabriclj.swiss-knife.client.rendering.core :as rendering]
           '[com.fabriclj.swiss-knife.client.rendering.hud :as hud]
           '[com.fabriclj.swiss-knife.client.ui.screens :as menus]
           '[com.fabriclj.swiss-knife.client.rendering.particles :as particles]
           '[com.fabriclj.swiss-knife.client.rendering.debug_render :as debug-render])
  {:core (find-ns 'com.fabriclj.swiss-knife.client.platform.core)
   :events (find-ns 'com.fabriclj.swiss-knife.client.events.core)
   :keys (find-ns 'com.fabriclj.swiss-knife.client.ui.keybindings)
   :render (find-ns 'com.fabriclj.swiss-knife.client.rendering.core)
   :hud (find-ns 'com.fabriclj.swiss-knife.client.rendering.hud)
   :menus (find-ns 'com.fabriclj.swiss-knife.client.ui.screens)
   :particles (find-ns 'com.fabriclj.swiss-knife.client.rendering.particles)
   :debug (find-ns 'com.fabriclj.swiss-knife.client.rendering.debug_render)}))

;; ============================================================================
;; 版本信息
;; ============================================================================

(comment
  ;; 快速开始示例

  ;; 1. 导入瑞士军刀
  (require '[com.fabriclj.swiss-knife :as mb])

  ;; 3. 使用核心功能
  (when (fabric?)
    (log-info "Running on Fabric!"))

  ;; 4. 创建注册表
  (def items (create-registry "mymod" :item))

  ;; 5. 注册事件
  (on-player-join
   (fn [player]
     (log-info (.getName player) "joined!")))

  ;; 6. 客户端功能( 仅在客户端)
  (when (client-side?)
    (require '[com.fabriclj.swiss-knife.client.core :as client])
    (println "Player:" (client/get-player))))
