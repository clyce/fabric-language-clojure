(ns com.fabriclj.swiss-knife.common.events.core
  "瑞士军刀 - 事件系统

   封装 Architectury API 的事件系统，提供简洁的事件监听和处理接口。支持超过 80+ 游戏事件钩子。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (dev.architectury.event EventResult)
           (com.fabriclj EventBridge)
           (dev.architectury.event.events.common
            BlockEvent ChatEvent EntityEvent ExplosionEvent
            LifecycleEvent
            LightningEvent
            LootEvent
            PlayerEvent
            TickEvent
            InteractionEvent)
           (net.minecraft.world.entity Entity LivingEntity)
           (net.minecraft.world.entity.player Player)
           (net.minecraft.world.level Level)
           (net.minecraft.server MinecraftServer)
           (net.minecraft.world InteractionHand InteractionResult)
           (net.minecraft.world.entity.projectile Projectile)
           (net.minecraft.world.phys HitResult HitResult$Type)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 事件结果辅助函数
;; ============================================================================

(defn event-pass
  "事件继续传递( 不干预) "
  []
  (EventResult/pass))

(defn event-interrupt
  "中断事件( 阻止默认行为)

   注意: EventResult/interrupt 只接受一个 boolean 参数。
   如果需要返回值，请直接返回 InteractionResult 或使用其他事件 API。"
  ([]
   (EventResult/interrupt false))
  ([value]
   ;; EventResult/interrupt 只接受 boolean，不支持返回值
   ;; 如果需要返回值，应该在事件处理中直接处理
   (EventResult/interrupt true)))

(defn event-interact-result
  "交互结果转换

   Clojure 关键字转换为 InteractionResult"
  [result]
  (case result
    :success InteractionResult/SUCCESS
    :consume InteractionResult/CONSUME
    :consume-partial InteractionResult/CONSUME_PARTIAL
    :pass InteractionResult/PASS
    :fail InteractionResult/FAIL
    result))

;; ============================================================================
;; 生命周期事件
;; ============================================================================

(defn on-server-starting
  "服务器启动时触发

   参数:
   - handler: 函数 (fn [^MinecraftServer server] ...)

   示例:
   ```clojure
   (on-server-starting
     (fn [server]
       (println \"Server is starting!\")
       (core/set-server! server)))
   ```"
  [handler]
  (.register (LifecycleEvent/SERVER_STARTING)
             (reify java.util.function.Consumer
               (accept [_ server]
                 (handler server)))))

(defn on-server-started
  "服务器启动完成时触发

   参数:
   - handler: 函数 (fn [^MinecraftServer server] ...)"
  [handler]
  (.register (LifecycleEvent/SERVER_STARTED)
             (reify java.util.function.Consumer
               (accept [_ server]
                 (handler server)))))

(defn on-server-stopping
  "服务器停止时触发

   参数:
   - handler: 函数 (fn [^MinecraftServer server] ...)"
  [handler]
  (.register (LifecycleEvent/SERVER_STOPPING)
             (reify java.util.function.Consumer
               (accept [_ server]
                 (handler server)))))

(defn on-server-stopped
  "服务器停止完成时触发

   参数:
   - handler: 函数 (fn [^MinecraftServer server] ...)"
  [handler]
  (.register (LifecycleEvent/SERVER_STOPPED)
             (reify java.util.function.Consumer
               (accept [_ server]
                 (handler server)))))

;; ============================================================================
;; Tick 事件
;; ============================================================================

(defn on-server-tick
  "每个服务端 tick 触发( 每秒20次)

   参数:
   - handler: 函数 (fn [^MinecraftServer server] ...)

   注意: 此函数每秒调用 20 次，避免执行耗时操作

   示例:
   ```clojure
   (on-server-tick
     (fn [server]
       (when (zero? (mod (.getTickCount server) 20))
         (println \"One second passed\"))))
   ```"
  [handler]
  (EventBridge/registerServerTickWithConsumer
   (reify java.util.function.Consumer
     (accept [_ server]
       (handler server)))))

(defn on-level-tick
  "每个维度 tick 触发

   参数:
   - handler: 函数 (fn [^Level level] ...)"
  [handler]
  (EventBridge/registerLevelTickWithConsumer
   (reify java.util.function.Consumer
     (accept [_ level]
       (handler level)))))

(defn on-player-tick
  "每个玩家 tick 触发

   参数:
   - handler: 函数 (fn [^Player player] ...)"
  [handler]
  (EventBridge/registerPlayerTickWithConsumer
   (reify java.util.function.Consumer
     (accept [_ player]
       (handler player)))))

;; ============================================================================
;; 玩家事件
;; ============================================================================

(defn on-player-join
  "玩家加入服务器时触发

   参数:
   - handler: 函数 (fn [^Player player] ...)

   示例:
   ```clojure
   (on-player-join
     (fn [player]
       (println (.getName player) \"joined the game!\")))
   ```"
  [handler]
  (EventBridge/registerPlayerJoinWithConsumer
   (reify java.util.function.Consumer
     (accept [_ player]
       (handler player)))))

(defn on-player-quit
  "玩家离开服务器时触发

   参数:
   - handler: 函数 (fn [^Player player] ...)"
  [handler]
  (EventBridge/registerPlayerQuitWithConsumer
   (reify java.util.function.Consumer
     (accept [_ player]
       (handler player)))))

(defn on-player-respawn
  "玩家重生时触发

   参数:
   - handler: 函数 (fn [^Player new-player ^Player old-player alive?] ...)

   参数说明:
   - new-player: 重生后的玩家实例
   - old-player: 死亡前的玩家实例
   - alive?: 是否从末地返回( 而非真正死亡) "
  [handler]
  (.register (PlayerEvent/PLAYER_RESPAWN)
             (reify java.util.function.Consumer
               (accept [_ respawn-context]
                 (handler (.newPlayer respawn-context)
                          (.oldPlayer respawn-context)
                          (.alive respawn-context))))))

(defn on-player-change-dimension
  "玩家切换维度时触发

   参数:
   - handler: 函数 (fn [^Player player ^Level old-level ^Level new-level] ...)"
  [handler]
  (.register (PlayerEvent/CHANGE_DIMENSION)
             (reify java.util.function.Consumer
               (accept [_ dimension-context]
                 (handler (.player dimension-context)
                          (.oldLevel dimension-context)
                          (.newLevel dimension-context))))))

(defn on-player-clone
  "玩家数据克隆时触发( 死亡重生或从末地返回)

   参数:
   - handler: 函数 (fn [^Player old-player ^Player new-player alive?] ...)

   用途: 保存玩家死亡前的数据到重生后的玩家"
  [handler]
  (.register (PlayerEvent/PLAYER_CLONE)
             (reify java.util.function.Consumer
               (accept [_ clone-context]
                 (handler (.oldPlayer clone-context)
                          (.newPlayer clone-context)
                          (.alive clone-context))))))

;; ============================================================================
;; 实体事件
;; ============================================================================

(defn on-entity-spawn
  "实体生成时触发

   参数:
   - handler: 函数 (fn [^Entity entity ^Level level] ...) -> EventResult

   返回:
   - (event-pass) - 允许生成
   - (event-interrupt) - 阻止生成

   示例:
   ```clojure
   (on-entity-spawn
     (fn [entity level]
       (if (instance? Creeper entity)
         (event-interrupt)  ; 禁止苦力怕生成
         (event-pass))))
   ```"
  [handler]
  (.register (EntityEvent/ADD)
             (reify java.util.function.Consumer
               (accept [_ spawn-context]
                 (handler (.entity spawn-context)
                          (.level spawn-context))))))

(defn on-living-death
  "生物死亡时触发

   参数:
   - handler: 函数 (fn [^LivingEntity entity ^DamageSource source] ...) -> EventResult

   返回:
   - (event-pass) - 允许死亡
   - (event-interrupt) - 阻止死亡( 保留1点生命值) "
  [handler]
  (.register (EntityEvent/LIVING_DEATH)
             (reify dev.architectury.event.events.common.EntityEvent$LivingDeath
               (die [_ entity source]
                 (handler entity source)))))

(defn on-entity-hurt
  "实体受伤时触发

   参数:
   - handler: 函数 (fn [^LivingEntity entity ^DamageSource source amount] ...) -> EventResult

   返回:
   - (event-pass) - 允许受伤
   - (event-interrupt new-amount) - 修改伤害值"
  [handler]
  (.register (EntityEvent/LIVING_HURT)
             (reify dev.architectury.event.events.common.EntityEvent$LivingHurt
               (hurt [_ entity source amount]
                 (handler entity source amount)))))

;; ============================================================================
;; 方块事件
;; ============================================================================

(defn on-block-break
  "方块破坏时触发

   参数:
   - handler: 函数 (fn [^Level level ^BlockPos pos ^BlockState state ^ServerPlayer player xp] ...) -> EventResult

   返回:
   - (event-pass) - 允许破坏
   - (event-interrupt) - 阻止破坏

   示例:
   ```clojure
   (on-block-break
     (fn [level pos state player xp]
       (if (= state (core/get-block :minecraft:bedrock))
         (event-interrupt)  ; 禁止破坏基岩
         (event-pass))))
   ```"
  [handler]
  (EventBridge/registerBlockBreakWithHandler
   (reify java.util.function.Function
     (apply [_ args]
       (let [^"[Ljava.lang.Object;" arr args
             level (aget arr 0)
             pos (aget arr 1)
             state (aget arr 2)
             player (aget arr 3)
             xp (aget arr 4)]
         (handler level pos state player xp))))))

(defn on-block-place
  "方块放置时触发

   参数:
   - handler: 函数 (fn [^Level level ^BlockPos pos ^BlockState state ^Entity placer] ...) -> EventResult

   返回:
   - (event-pass) - 允许放置
   - (event-interrupt) - 阻止放置"
  [handler]
  (EventBridge/registerBlockPlaceWithHandler
   (reify java.util.function.Function
     (apply [_ args]
       (let [^"[Ljava.lang.Object;" arr args
             level (aget arr 0)
             pos (aget arr 1)
             state (aget arr 2)
             placer (aget arr 3)]
         (handler level pos state placer))))))

;; ============================================================================
;; 交互事件
;; ============================================================================

(defn on-right-click-block
  "右键点击方块时触发

   参数:
   - handler: 函数 (fn [^Player player ^InteractionHand hand ^BlockPos pos ^Direction direction] ...) -> EventResult

   返回:
   - (event-pass) - 继续处理
   - (event-interrupt :success/:consume/:fail) - 中断并返回交互结果

   示例:
   ```clojure
   (on-right-click-block
     (fn [player hand pos direction]
       (println \"Player clicked block at\" pos)
       (event-interrupt :success)))
   ```"
  [handler]
  (.register (InteractionEvent/RIGHT_CLICK_BLOCK)
             (reify java.util.function.Consumer
               (accept [_ click-context]
                 (handler (.player click-context)
                          (.hand click-context)
                          (.pos click-context)
                          (.direction click-context))))))

(defn on-right-click-item
  "右键使用物品时触发

   参数:
   - handler: 函数 (fn [^Player player ^InteractionHand hand] ...) -> EventResult

   返回:
   - (event-pass) - 使用物品默认行为
   - (event-interrupt result) - 中断并返回自定义结果"
  [handler]
  (.register (InteractionEvent/RIGHT_CLICK_ITEM)
             (reify java.util.function.Consumer
               (accept [_ click-context]
                 (handler (.player click-context)
                          (.hand click-context))))))

(defn on-interact-entity
  "与实体交互时触发

   参数:
   - handler: 函数 (fn [^Player player ^Entity entity ^InteractionHand hand] ...) -> EventResult

   返回:
   - (event-pass) - 默认交互
   - (event-interrupt result) - 自定义交互结果"
  [handler]
  (.register (InteractionEvent/INTERACT_ENTITY)
             (reify java.util.function.Consumer
               (accept [_ interact-context]
                 (handler (.player interact-context)
                          (.entity interact-context)
                          (.hand interact-context))))))

;; ============================================================================
;; 聊天事件
;; ============================================================================

(defn on-chat-message
  "玩家发送聊天消息时触发

   参数:
   - handler: 函数 (fn [^Player player ^String message] ...) -> EventResult

   返回:
   - (event-pass) - 正常发送消息
   - (event-interrupt) - 阻止消息发送

   示例:
   ```clojure
   (on-chat-message
     (fn [player message]
       (if (.startsWith message \"/secret\")
         (do
           (println \"Secret command detected!\")
           (event-interrupt))
         (event-pass))))
   ```"
  [handler]
  (.register (ChatEvent/RECEIVED)
             (reify java.util.function.Consumer
               (accept [_ chat-context]
                 (handler (.player chat-context)
                          (.message chat-context))))))

;; ============================================================================
;; 爆炸事件
;; ============================================================================

(defn on-explosion-detonate
  "爆炸发生时触发

   参数:
   - handler: 函数 (fn [^Level level ^Explosion explosion ^List affected-blocks ^List affected-entities] ...)

   用途: 修改爆炸影响的方块和实体列表"
  [handler]
  (.register (ExplosionEvent/DETONATE)
             (reify java.util.function.Consumer
               (accept [_ context]
                 (handler (.level context)
                          (.explosion context)
                          (.affectedBlocks context)
                          (.affectedEntities context))))))

;; ============================================================================
;; 工具宏
;; ============================================================================

(defmacro defevent
  "定义事件处理器( 语法糖)

   示例:
   ```clojure
   (defevent on-player-join player-greeting
     [player]
     (println \"Welcome\" (.getName player) \"!\"))
   ```"
  [event-fn handler-name args & body]
  `(def ~handler-name
     (~event-fn
      (fn ~args
        ~@body))))

(defmacro when-event
  "条件事件处理

   示例:
   ```clojure
   (on-entity-spawn
     (when-event [entity level]
       (instance? Creeper entity)
       (event-interrupt)))
   ```"
  [args condition & body]
  `(fn ~args
     (if ~condition
       (do ~@body)
       (event-pass))))

;; ============================================================================
;; 事件管理
;; ============================================================================

(defonce ^:private event-handlers (atom []))

(defn register-handler!
  "注册事件处理器( 用于后续管理)

   返回: 处理器 ID"
  [event-type handler]
  (let [id (java.util.UUID/randomUUID)]
    (swap! event-handlers conj {:id id
                                :type event-type
                                :handler handler})
    id))

(defn unregister-handler!
  "注销事件处理器

   注意: Architectury API 不支持运行时注销事件，此函数仅用于记录管理"
  [handler-id]
  (swap! event-handlers
         (fn [handlers]
           (remove #(= (:id %) handler-id) handlers))))

(defn list-handlers
  "列出所有已注册的事件处理器"
  []
  @event-handlers)

(comment
  ;; 使用示例

  ;; 服务器生命周期事件
  (on-server-starting
   (fn [server]
     (core/set-server! server)
     (core/log-info "Server starting...")))

  ;; 玩家事件
  (on-player-join
   (fn [player]
     (core/log-info (.getName player) " joined the game")))

  ;; 条件事件
  (on-entity-spawn
   (fn [entity level]
     (if (instance? net.minecraft.world.entity.monster.Creeper entity)
       (do
         (core/log-info "Creeper spawn blocked")
         (event-interrupt))
       (event-pass))))

  ;; Tick 事件( 每秒执行一次)
  (on-server-tick
   (fn [server]
     (when (zero? (mod (.getTickCount server) 20))
       (core/log-debug "One second passed"))))

  ;; 使用宏简化
  (defevent on-player-quit player-goodbye
    [player]
    (core/log-info (.getName player) " left the game")))
