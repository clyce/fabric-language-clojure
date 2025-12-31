(ns com.fabriclj.swiss-knife
  "瑞士军刀 - 主入口模块

   提供便捷的命名空间导入和快速访问。

   这是一个用于快速封装 Architectury API 和 Fabric API 常见功能的工具库，
   为其他 API 提供简洁的 Clojure 接口。"
  (:require [com.fabriclj.swiss-knife.common.core :as core]
            [com.fabriclj.swiss-knife.common.registry :as registry]
            [com.fabriclj.swiss-knife.common.events :as events]
            [com.fabriclj.swiss-knife.common.items :as items]
            [com.fabriclj.swiss-knife.common.blocks :as blocks]
            [com.fabriclj.swiss-knife.common.entities :as entities]
            [com.fabriclj.swiss-knife.common.network :as network]
            [com.fabriclj.swiss-knife.common.creative-tabs :as creative-tabs]
            [com.fabriclj.swiss-knife.common.fuel :as fuel]
            [com.fabriclj.swiss-knife.common.reload-listeners :as reload]
            [com.fabriclj.swiss-knife.common.containers :as containers]
            [com.fabriclj.swiss-knife.common.inventories :as inventories]
            ;; 第一批：基础系统
            [com.fabriclj.swiss-knife.common.physics :as physics]
            [com.fabriclj.swiss-knife.common.sounds :as sounds]
            [com.fabriclj.swiss-knife.common.tags :as tags]
            ;; 第二批：核心功能
            [com.fabriclj.swiss-knife.common.commands :as commands]
            [com.fabriclj.swiss-knife.common.data :as data]
            [com.fabriclj.swiss-knife.common.damage :as damage]
            [com.fabriclj.swiss-knife.common.enchantments :as enchantments]
            ;; 第三批：高级 DSL
            [com.fabriclj.swiss-knife.common.dsl :as dsl]
            [com.fabriclj.swiss-knife.common.builders :as builders]
            [com.fabriclj.swiss-knife.common.event-chain :as event-chain]
            ;; 第四批：扩展功能
            [com.fabriclj.swiss-knife.common.recipes :as recipes]
            [com.fabriclj.swiss-knife.common.utils :as utils]
            [com.fabriclj.swiss-knife.common.worldgen :as worldgen]
            [com.fabriclj.swiss-knife.common.ai :as ai]
            ;; 配置系统
            [com.fabriclj.swiss-knife.common.config-file :as config-file]
            ;; 第六批：进阶功能
            [com.fabriclj.swiss-knife.common.potions :as potions]
            [com.fabriclj.swiss-knife.common.villagers :as villagers]
            [com.fabriclj.swiss-knife.common.advancements :as advancements]
            ;; 第七批：系统增强
            [com.fabriclj.swiss-knife.common.config-sync :as config-sync]
            [com.fabriclj.swiss-knife.common.profiler :as profiler]
            ;; 第八批：高级功能
            [com.fabriclj.swiss-knife.client.config-screen :as config-screen]
            [com.fabriclj.swiss-knife.client.debug-visualizer :as debug-viz]
            [com.fabriclj.swiss-knife.common.datapack :as datapack]))

;; ============================================================================
;; 命名空间别名（便于使用）
;; ============================================================================

(def core core)
(def reg registry)
(def events events)
(def items items)
(def blocks blocks)
(def entities entities)
(def net network)
(def tabs creative-tabs)
(def fuel fuel)
(def reload reload)
(def containers containers)
(def inv inventories)

(def physics physics)
(def sounds sounds)
(def tags tags)

(def commands commands)
(def data data)
(def damage damage)
(def enchantments enchantments)

(def dsl dsl)
(def builders builders)
(def chain event-chain)
(def recipes recipes)
(def utils utils)
(def worldgen worldgen)
(def ai ai)

(def config config-file)

(def potions potions)
(def villagers villagers)
(def advancements advancements)

(def sync config-sync)
(def prof profiler)

(def cfg-screen config-screen)
(def debug-vis debug-viz)
(def dp datapack)


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
;; 客户端模块（延迟加载，避免在服务端加载客户端类）
;; ============================================================================

(defn client
  "获取客户端命名空间（仅在客户端环境可用）

   返回：包含所有客户端模块的映射

   示例:
   ```clojure
   (when (client-side?)
     (require '[com.fabriclj.swiss-knife :as mb])
     (let [client (mb/client)]
       ((:get-player (:core client)))))
   ```"
  []
  (when (client-side?)
    (require '[com.fabriclj.swiss-knife.client.core :as client-core]
             '[com.fabriclj.swiss-knife.client.events :as client-events]
             '[com.fabriclj.swiss-knife.client.keybindings :as keybindings]
             '[com.fabriclj.swiss-knife.client.rendering :as rendering]
             '[com.fabriclj.swiss-knife.client.hud :as hud]
             '[com.fabriclj.swiss-knife.client.menus :as menus]
             '[com.fabriclj.swiss-knife.client.particles :as particles]
             '[com.fabriclj.swiss-knife.client.debug-render :as debug-render])
    {:core (find-ns 'com.fabriclj.swiss-knife.client.core)
     :events (find-ns 'com.fabriclj.swiss-knife.client.events)
     :keys (find-ns 'com.fabriclj.swiss-knife.client.keybindings)
     :render (find-ns 'com.fabriclj.swiss-knife.client.rendering)
     :hud (find-ns 'com.fabriclj.swiss-knife.client.hud)
     :menus (find-ns 'com.fabriclj.swiss-knife.client.menus)
     :particles (find-ns 'com.fabriclj.swiss-knife.client.particles)
     :debug (find-ns 'com.fabriclj.swiss-knife.client.debug-render)}))

;; ============================================================================
;; 版本信息
;; ============================================================================

(comment
  ;; 快速开始示例

  ;; 1. 导入瑞士军刀
  (require '[com.fabriclj.swiss-knife :as mb])

  ;; 3. 使用核心功能
  (when (mb/fabric?)
    (mb/log-info "Running on Fabric!"))

  ;; 4. 创建注册表
  (def items (mb/create-registry "mymod" :item))

  ;; 5. 注册事件
  (mb/on-player-join
   (fn [player]
     (mb/log-info (.getName player) "joined!")))

  ;; 6. 客户端功能（仅在客户端）
  (when (mb/client-side?)
    (require '[com.fabriclj.swiss-knife.client.core :as client])
    (println "Player:" (client/get-player))))
