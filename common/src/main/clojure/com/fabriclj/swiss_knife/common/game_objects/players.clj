(ns com.fabriclj.swiss-knife.common.game-objects.players
  "瑞士军刀 - 玩家工具模块

   **模块定位**: 专注于玩家相关的操作和查询

   **核心功能**:
   - 玩家查询( 按名称、UUID)
   - 玩家传送
   - 玩家物品操作
   - 玩家状态查询

   **使用示例**:
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.game-objects.players :as players])

   ;; 查询玩家
   (players/get-player-by-name server \"Steve\")
   (players/get-player-by-uuid server uuid)

   ;; 传送玩家
   (players/teleport! player [100 64 200])
   (players/teleport-to-player! player1 player2)

   ;; 物品操作
   (players/give-item! player Items/DIAMOND 5)
   (players/has-item? player Items/DIAMOND)
   (players/remove-item! player Items/DIAMOND 1)
   ```"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.world.entity.player Player)
           (net.minecraft.world.item ItemStack Item)
           (net.minecraft.world.phys Vec3)
           (net.minecraft.server MinecraftServer)
           (net.minecraft.server.level ServerPlayer ServerLevel)
           (java.util UUID)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 玩家查询
;; ============================================================================

(defn get-player-by-name
  "根据玩家名称获取玩家

   参数:
   - server: MinecraftServer
   - name: 玩家名称( 字符串)

   返回: ServerPlayer 或 nil

   示例:
   ```clojure
   (when-let [player (get-player-by-name server \"Steve\")]
     (println \"Found player:\" (.getName player)))
   ```"
  ^ServerPlayer [^MinecraftServer server ^String name]
  (.getPlayerByName (.getPlayerList server) name))

(defn get-player-by-uuid
  "根据 UUID 获取玩家

   参数:
   - server: MinecraftServer
   - uuid: UUID 对象或字符串

   返回: ServerPlayer 或 nil

   示例:
   ```clojure
   (def uuid (UUID/fromString \"123e4567-e89b-12d3-a456-426614174000\"))
   (when-let [player (get-player-by-uuid server uuid)]
     (println \"Found player:\" (.getName player)))
   ```"
  ^ServerPlayer [^MinecraftServer server uuid]
  (let [^UUID uuid-obj (if (instance? UUID uuid)
                         uuid
                         (UUID/fromString (str uuid)))]
    (.getPlayerByUUID (.getPlayerList server) uuid-obj)))

(defn get-all-players
  "获取所有在线玩家

   参数:
   - server: MinecraftServer

   返回: ServerPlayer 列表

   示例:
   ```clojure
   (doseq [player (get-all-players server)]
     (println (.getName player)))
   ```"
  [^MinecraftServer server]
  (vec (.getPlayers (.getPlayerList server))))

(defn player-online?
  "检查玩家是否在线

   参数:
   - server: MinecraftServer
   - name-or-uuid: 玩家名称或 UUID

   返回: boolean

   示例:
   ```clojure
   (when (player-online? server \"Steve\")
     (println \"Steve is online\"))
   ```"
  [^MinecraftServer server name-or-uuid]
  (boolean
   (if (instance? UUID name-or-uuid)
     (get-player-by-uuid server name-or-uuid)
     (get-player-by-name server (str name-or-uuid)))))

;; ============================================================================
;; 玩家传送
;; ============================================================================

(defn teleport!
  "传送玩家到指定位置

   参数:
   - player: ServerPlayer
   - pos: 位置，支持多种格式:
     - [x y z] - 向量
     - {:x x :y y :z z} - Map
     - Vec3 对象
   - opts: 可选参数( 可选)
     - :dimension - 维度 ResourceKey( 跨维度传送)
     - :yaw - 偏航角( 默认保持当前)
     - :pitch - 俯仰角( 默认保持当前)

   示例:
   ```clojure
   ;; 传送到坐标
   (teleport! player [100 64 200])

   ;; 传送并设置朝向
   (teleport! player [100 64 200] {:yaw 90.0 :pitch 0.0})

   ;; 跨维度传送( 需要 dimension)
   (teleport! player [0 64 0] {:dimension Level/NETHER})
   ```"
  ([player pos]
   (teleport! player pos {}))
  ([^ServerPlayer player pos opts]
   (let [[x y z] (cond
                   (vector? pos) pos
                   (map? pos) [(:x pos) (:y pos) (:z pos)]
                   (instance? Vec3 pos) [(.x ^Vec3 pos) (.y ^Vec3 pos) (.z ^Vec3 pos)])
         yaw (or (:yaw opts) (.getYRot player))
         pitch (or (:pitch opts) (.getXRot player))]
     (if-let [dimension (:dimension opts)]
       (.teleportTo player
                    (.getLevel (.server player) dimension)
                    (double x) (double y) (double z)
                    (float yaw) (float pitch))
       (.teleportTo player (double x) (double y) (double z))))))

(defn teleport-to-player!
  "传送玩家到另一个玩家的位置

   参数:
   - player: 要传送的玩家
   - target: 目标玩家

   示例:
   ```clojure
   (teleport-to-player! player1 player2)
   ```"
  [^ServerPlayer player ^Player target]
  (let [pos (.position target)]
    (teleport! player [(.x pos) (.y pos) (.z pos)]
               {:yaw (.getYRot target)
                :pitch (.getXRot target)})))

;; ============================================================================
;; 手持物品操作
;; ============================================================================

(defn get-main-hand-item
  "获取玩家主手物品

   参数:
   - player: Player 实例

   返回: ItemStack( 可能为空)

   示例:
   ```clojure
   (let [main-hand (get-main-hand-item player)]
     (when-not (.isEmpty main-hand)
       (println \"主手: \" (.getDescriptionId (.getItem main-hand)))))
   ```"
  ^ItemStack [^Player player]
  (.getMainHandItem player))

(defn get-off-hand-item
  "获取玩家副手物品

   参数:
   - player: Player 实例

   返回: ItemStack( 可能为空) "
  ^ItemStack [^Player player]
  (.getOffhandItem player))

(defn get-item-in-hand
  "获取玩家指定手持物品

   参数:
   - player: Player 实例
   - hand: InteractionHand( :main-hand 或 :off-hand)

   返回: ItemStack

   示例:
   ```clojure
   (get-item-in-hand player :main-hand)
   (get-item-in-hand player :off-hand)
   ```"
  ^ItemStack [^Player player hand]
  (let [interaction-hand (case hand
                           :main-hand net.minecraft.world.InteractionHand/MAIN_HAND
                           :off-hand net.minecraft.world.InteractionHand/OFF_HAND
                           hand)]
    (.getItemInHand player interaction-hand)))

(defn holding-item?
  "检查玩家是否手持指定物品

   参数:
   - player: Player 实例
   - item: Item 或资源定位符
   - hand: 可选，:main-hand( 默认) 、:off-hand 或 :either

   返回: boolean

   示例:
   ```clojure
   (holding-item? player Items/DIAMOND_SWORD)
   (holding-item? player Items/SHIELD :off-hand)
   (holding-item? player Items/TORCH :either)  ; 任一手持有
   ```"
  ([player item]
   (holding-item? player item :main-hand))
  ([^Player player item hand]
   (let [item-obj (if (instance? Item item)
                    item
                    (core/get-item item))
         check-hand (fn [^ItemStack stack]
                      (and (not (.isEmpty stack))
                           (= (.getItem stack) item-obj)))]
     (case hand
       :main-hand (check-hand (get-main-hand-item player))
       :off-hand (check-hand (get-off-hand-item player))
       :either (or (check-hand (get-main-hand-item player))
                   (check-hand (get-off-hand-item player)))
       false))))

(defn set-main-hand-item!
  "设置玩家主手物品

   参数:
   - player: Player 实例
   - stack: ItemStack

   示例:
   ```clojure
   (set-main-hand-item! player (ItemStack. Items/DIAMOND_SWORD))
   ```"
  [^Player player ^ItemStack stack]
  (.setItemInHand player net.minecraft.world.InteractionHand/MAIN_HAND stack))

(defn set-off-hand-item!
  "设置玩家副手物品

   参数:
   - player: Player 实例
   - stack: ItemStack"
  [^Player player ^ItemStack stack]
  (.setItemInHand player net.minecraft.world.InteractionHand/OFF_HAND stack))

;; ============================================================================
;; 玩家物品操作
;; ============================================================================

(defn give-item!
  "给予玩家物品

   参数:
   - player: Player
   - item: Item 或 ResourceLocation 或 关键字
   - count: 数量( 可选，默认 1)

   返回: boolean( 是否成功)

   示例:
   ```clojure
   (give-item! player Items/DIAMOND 5)
   (give-item! player :minecraft:diamond 5)
   (give-item! player \"minecraft:diamond\" 5)
   ```"
  ([player item]
   (give-item! player item 1))
  ([^Player player item count]
   (let [item-obj (if (instance? Item item)
                    item
                    (core/get-item-by-id item))
         stack (ItemStack. item-obj (int count))]
     (.add (.getInventory player) stack))))

(defn has-item?
  "检查玩家是否拥有指定物品

   参数:
   - player: Player
   - item: Item 或 ResourceLocation 或 关键字
   - count: 最小数量( 可选，默认 1)

   返回: boolean

   示例:
   ```clojure
   (when (has-item? player Items/DIAMOND 5)
     (println \"Player has at least 5 diamonds\"))
   ```"
  ([player item]
   (has-item? player item 1))
  ([^Player player item min-count]
   (let [item-obj (if (instance? Item item)
                    item
                    (core/get-item-by-id item))
         inventory (.getInventory player)
         total (atom 0)]
     (doseq [i (range (.getContainerSize inventory))]
       (let [^ItemStack stack (.getItem inventory i)]
         (when (and (not (.isEmpty stack))
                    (= (.getItem stack) item-obj))
           (swap! total + (.getCount stack)))))
     (>= @total min-count))))

(defn remove-item!
  "从玩家背包移除物品

   参数:
   - player: Player
   - item: Item 或 ResourceLocation 或 关键字
   - count: 数量( 可选，默认 1)

   返回: 实际移除的数量

   示例:
   ```clojure
   (remove-item! player Items/DIAMOND 1)
   ```"
  ([player item]
   (remove-item! player item 1))
  ([^Player player item count]
   (let [item-obj (if (instance? Item item)
                    item
                    (core/get-item-by-id item))]
     (.clearOrCountMatchingItems (.getInventory player)
                                 (fn [^ItemStack s]
                                   (= (.getItem s) item-obj))
                                 count
                                 (.inventoryMenu player)))))

(defn count-item
  "统计玩家拥有的指定物品数量

   参数:
   - player: Player
   - item: Item 或 ResourceLocation 或 关键字

   返回: int

   示例:
   ```clojure
   (let [diamond-count (count-item player Items/DIAMOND)]
     (println \"Player has\" diamond-count \"diamonds\"))
   ```"
  ^long [^Player player item]
  (let [item-obj (if (instance? Item item)
                   item
                   (core/get-item-by-id item))
        inventory (.getInventory player)
        total (atom 0)]
    (doseq [i (range (.getContainerSize inventory))]
      (let [^ItemStack stack (.getItem inventory i)]
        (when (and (not (.isEmpty stack))
                   (= (.getItem stack) item-obj))
          (swap! total + (.getCount stack)))))
    @total))

;; ============================================================================
;; 玩家状态查询
;; ============================================================================

(defn get-experience-level
  "获取玩家经验等级

   参数:
   - player: Player

   返回: int"
  ^long [^Player player]
  (.experienceLevel player))

(defn get-health
  "获取玩家生命值

   参数:
   - player: Player

   返回: float"
  ^double [^Player player]
  (.getHealth player))

(defn get-food-level
  "获取玩家饥饿值

   参数:
   - player: Player

   返回: int"
  ^long [^Player player]
  (.getFoodData (.getFoodLevel player)))

(defn is-creative?
  "检查玩家是否处于创造模式

   参数:
   - player: Player

   返回: boolean"
  [^Player player]
  (.isCreative (.getAbilities player)))

(defn is-flying?
  "检查玩家是否正在飞行

   参数:
   - player: Player

   返回: boolean"
  [^Player player]
  (.flying (.getAbilities player)))

(comment
  ;; 使用示例

  ;; 1. 查询玩家
  (def server (get-server))
  (def player (get-player-by-name server "Steve"))
  (def all-players (get-all-players server))

  ;; 2. 传送玩家
  (teleport! player [100 64 200])
  (teleport! player [100 64 200] {:yaw 90.0 :pitch 0.0})
  (teleport-to-player! player1 player2)

  ;; 3. 物品操作
  (give-item! player Items/DIAMOND 5)
  (has-item? player Items/DIAMOND 5)
  (remove-item! player Items/DIAMOND 1)
  (count-item player Items/DIAMOND)

  ;; 4. 状态查询
  (get-experience-level player)
  (get-health player)
  (is-creative? player)
  (is-flying? player))
