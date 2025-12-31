(ns com.example.core
  "示例 Clojure mod 主入口 - 魔法宝石 (Magic Gem) mod

  本示例展示 fabric-language-clojure 和 Swiss Knife 工具库的常用功能:
  - 物品注册( 魔法宝石、魔法碎片)
  - 配置系统( 宝石威力配置)
  - 事件系统( 玩家加入、击杀怪物)
  - 玩家工具( 物品操作、状态查询)
  - 音效系统( 使用宝石时播放音效)
  - 网络通信( 客户端-服务端数据包) "
  (:require [com.fabriclj.core :as lib]
            [com.fabriclj.nrepl :as nrepl]
            [com.fabriclj.swiss-knife :as mb]
            [com.fabriclj.swiss-knife.common.lifecycle :as lifecycle]
            [com.fabriclj.swiss-knife.common.registry.core :as reg]
            [com.fabriclj.swiss-knife.common.events.core :as events]
            [com.fabriclj.swiss-knife.common.game-objects.players :as players]
            [com.fabriclj.swiss-knife.common.game-objects.items :as items]
            [com.fabriclj.swiss-knife.common.config.core :as config]
            [com.fabriclj.swiss-knife.common.config.validators :as v]
            [com.fabriclj.swiss-knife.common.gameplay.sounds :as sounds]
            [com.fabriclj.swiss-knife.common.network.core :as net]
            [com.fabriclj.swiss-knife.common.utils.text :as text])
  (:import (net.minecraft.world.item Item Item$Properties Rarity BlockItem)
           (net.minecraft.world.level.block Block Block$Properties Blocks SoundType)
           (net.minecraft.world.level.material MapColor)
           (net.minecraft.world InteractionResultHolder)
           (net.minecraft.world.entity.monster Monster)
           (net.minecraft.world.entity EntityType$Builder EntityType$EntityFactory MobCategory)
           (net.minecraft.world.entity.projectile Snowball)
           (net.minecraft.world.phys HitResult$Type BlockHitResult)))

;; ============================================================================
;; 配置系统 - 使用 EDN 配置文件 + 验证器
;; ============================================================================

(defn load-config!
  "加载或创建配置文件

   演示功能:
   - 配置文件自动创建
   - 使用验证器确保配置有效性
   - 配置值的类型和范围验证"
  []
  (config/register-config! "example" "default"
                           {:magic-gem {:power 10.0
                                        :durability 100
                                        :particle-count 20
                                        :cooldown-ticks 20}
                            :messages {:welcome "欢迎来到魔法世界！"
                                       :gem-activated "魔法宝石已激活！"}}
                           ;; ✨ 新功能: 使用配置验证器
                           :validator (v/all-of
                                       ;; 验证必需的键
                                       (v/has-keys? :magic-gem :messages)
                                       ;; 验证 magic-gem 配置
                                       (v/validate-key [:magic-gem :power]
                                                       (v/all-of (v/positive-number?) (v/in-range? 1.0 100.0)))
                                       (v/validate-key [:magic-gem :durability]
                                                       (v/all-of (v/positive-integer?) (v/in-range? 1 1000)))
                                       (v/validate-key [:magic-gem :particle-count]
                                                       (v/all-of (v/positive-integer?) (v/in-range? 1 100)))
                                       (v/validate-key [:magic-gem :cooldown-ticks]
                                                       (v/positive-integer?))
                                       ;; 验证消息
                                       (v/validate-key [:messages :welcome] (v/non-empty-string?))
                                       (v/validate-key [:messages :gem-activated] (v/non-empty-string?)))))

(defn get-gem-power
  "获取宝石威力配置"
  []
  (config/get-config-value "example" [:magic-gem :power]))

(defn get-welcome-message
  "获取欢迎消息"
  []
  (config/get-config-value "example" [:messages :welcome]))

;; ============================================================================
;; 注册表创建
;; ============================================================================

(def blocks-registry (reg/create-registry "example" :block))
(def items-registry (reg/create-registry "example" :item))
(def entities-registry (reg/create-registry "example" :entity-type))

;; ============================================================================
;; 方块注册 - 魔法水晶矿
;; ============================================================================

;; 魔法水晶矿 - 发光的矿石，挖掘后掉落魔法宝石
(reg/defblock blocks-registry magic-crystal-ore
  (proxy [Block] [(-> (Block$Properties/of)
                      (.mapColor MapColor/COLOR_PURPLE)
                      (.requiresCorrectToolForDrops)
                      (.strength 3.0 3.0)
                      (.sound SoundType/AMETHYST)
                      (.lightLevel (fn [_] 7)))]  ; 发光等级 7
    ))

;; 魔法水晶矿的物品形式( 用于创造模式和 /give 命令)
(reg/defitem items-registry magic-crystal-ore-item
  (BlockItem. @magic-crystal-ore
              (-> (Item$Properties.)
                  (.rarity Rarity/RARE))))

;; ============================================================================
;; 物品注册 - 魔法宝石和魔法碎片
;; ============================================================================

;; 魔法碎片 - 从怪物掉落的材料
(reg/defitem items-registry magic-shard
  (Item. (-> (Item$Properties.)
             (.stacksTo 64)
             (.rarity Rarity/UNCOMMON))))

;; 魔法宝石 - 主要物品，可以发射魔法弹
(defn create-magic-gem
  "创建魔法宝石物品"
  []
  (proxy [Item] [(-> (Item$Properties.)
                     (.stacksTo 1)
                     (.durability (config/get-config-value "example" [:magic-gem :durability]))
                     (.rarity Rarity/RARE))]
    (use [level player hand]
      (if (.isClientSide level)
        (InteractionResultHolder/success (.getItemInHand player hand))
        (do
          ;; 服务端逻辑: 发射魔法弹
          (let [snowball (Snowball. level player)]
            ;; 设置弹道速度
            (.shootFromRotation snowball player
                                (.getXRot player)
                                (.getYRot player)
                                0.0 1.5 1.0)

            ;; 生成弹道实体
            (.addFreshEntity level snowball)

            ;; 在数据包中标记这是魔法弹( 用于客户端粒子效果)
            (net/send-to-player! player :gem-shoot
                                 {:pos [(.-x (.position player))
                                        (.-y (.position player))
                                        (.-z (.position player))]}))

          ;; 播放音效
          (sounds/play-sound! level (.position player) :minecraft:entity.ender_dragon.shoot
                              {:source :player :volume 0.8 :pitch 1.5})

          ;; 损坏物品
          (let [item-stack (.getItemInHand player hand)]
            (.hurtAndBreak item-stack 1 player
                           (reify java.util.function.Consumer
                             (accept [_ _] nil))))

          (InteractionResultHolder/success (.getItemInHand player hand)))))))

(reg/defitem items-registry magic-gem (create-magic-gem))

;; 森林之魂药水 - 由森林守卫掉落
(reg/defitem items-registry forest-soul-potion
  (Item. (-> (Item$Properties.)
             (.stacksTo 16)
             (.rarity Rarity/EPIC)
             ;; 使用 swiss-knife 的食物属性构建器
             (.food (items/food-properties
                     :nutrition 0
                     :saturation 0.0
                     :always-eat? true
                     :effects [{:effect :speed
                                :duration 400
                                :amplifier 1
                                :probability 1.0}
                               {:effect :jump-boost
                                :duration 400
                                :amplifier 1
                                :probability 1.0}])))))

;; 自然亲和附魔书 - 由森林守卫掉落
(reg/defitem items-registry nature-affinity-book
  (Item. (-> (Item$Properties.)
             (.stacksTo 1)
             (.rarity Rarity/EPIC))))

;; ============================================================================
;; 实体注册 - 森林守卫
;; ============================================================================

;; 简化的森林守卫 - 基于僵尸的敌对 mob
(defn create-forest-guardian-type
  "创建森林守卫实体类型"
  []
  (-> (EntityType$Builder/of
       (reify EntityType$EntityFactory
         (create [_ entity-type level]
           (net.minecraft.world.entity.monster.Zombie. entity-type level)))
       MobCategory/MONSTER)
      (.sized 0.6 1.95)  ; 尺寸与僵尸相同
      (.clientTrackingRange 8)
      (.build "forest_guardian")))

(reg/defentity entities-registry forest-guardian (create-forest-guardian-type))

;; ============================================================================
;; 事件系统 - 玩家加入、击杀怪物等
;; ============================================================================

(defn spawn-forest-guardian!
  "在指定位置生成森林守卫"
  [level pos]
  (when-let [guardian-type @forest-guardian]
    (let [guardian (.create guardian-type level)]
      (.moveTo guardian (.-x pos) (.-y pos) (.-z pos) 0.0 0.0)
      (.addFreshEntity level guardian)

      ;; 播放生成音效
      (sounds/play-sound! level pos :minecraft:entity.zombie.ambient
                          {:source :hostile :volume 1.0 :pitch 0.8})

      (log-info "森林守卫已生成"))))

(defn setup-events!
  "设置游戏事件监听器"
  []
  ;; 弹道命中检测 - 命中树叶召唤森林守卫
  (events/on-projectile-hit
   (fn [projectile hit-result]
     (when (instance? Snowball projectile)
       (let [level (.level projectile)
             hit-type (.getType hit-result)]
         (when (= hit-type HitResult$Type/BLOCK)
           (let [block-hit ^BlockHitResult hit-result
                 pos (.getBlockPos block-hit)
                 state (.getBlockState level pos)
                 block (.getBlock state)]
             ;; 检查是否命中树叶
             (when (or (= block Blocks/OAK_LEAVES)
                       (= block Blocks/SPRUCE_LEAVES)
                       (= block Blocks/BIRCH_LEAVES)
                       (= block Blocks/JUNGLE_LEAVES)
                       (= block Blocks/ACACIA_LEAVES)
                       (= block Blocks/DARK_OAK_LEAVES)
                       (= block Blocks/AZALEA_LEAVES)
                       (= block Blocks/FLOWERING_AZALEA_LEAVES)
                       (= block Blocks/MANGROVE_LEAVES)
                       (= block Blocks/CHERRY_LEAVES))
               ;; 在命中位置上方生成森林守卫
               (let [spawn-pos (.above pos)]
                 (spawn-forest-guardian! level (.getCenter spawn-pos))))))))
     (events/event-pass)))

  ;; 玩家加入时发送欢迎消息和赠送物品
  (events/on-player-join
   (fn [player]
     (log-info "玩家加入:" (.getName (.getGameProfile player)))

     ;; 发送欢迎消息
     (players/send-message! player
                            (text/literal (get-welcome-message) :color :gold))

     ;; 赠送魔法水晶矿( 让玩家自己挖掘)
     (when-not (players/has-item? player @magic-crystal-ore-item)
       (players/give-item! player @magic-crystal-ore-item 3)
       (players/send-message! player
                              (text/literal "你获得了魔法水晶矿！挖掘它获取魔法宝石。"
                                            :color :aqua))
       (players/send-message! player
                              (text/literal "右键使用宝石发射魔法弹，命中树叶可以召唤森林守卫！"
                                            :color :yellow)))))

  ;; 玩家击杀怪物时掉落物品
  (events/on-living-death
   (fn [entity damage-source]
     (let [level (.level entity)
           pos (.position entity)]
       ;; 检查是否是森林守卫( 通过实体类型判断)
       (if (= (.getType entity) @forest-guardian)
         ;; 森林守卫 - 100% 掉落药水和附魔书
         (do
           (items/drop-item-at! level pos @forest-soul-potion 1)
           (items/drop-item-at! level pos @nature-affinity-book 1)
           (when-let [player (.getPlayer damage-source)]
             (players/send-message! player
                                    (text/literal "森林守卫掉落了珍贵物品！" :color :gold))
             (sounds/play-sound! level pos :minecraft:entity.player.levelup
                                 {:source :player :volume 1.0 :pitch 1.0})))

         ;; 普通怪物 - 20% 概率掉落 1-3 个魔法碎片
         (when (instance? Monster entity)
           (when-let [player (.getPlayer damage-source)]
             (let [drop-count (when (< (rand) 0.2)
                                (+ 1 (rand-int 3)))]
               (when drop-count
                 (items/drop-item-at! level pos @magic-shard drop-count)
                 (players/send-message! player
                                        (text/literal (str "魔法碎片 x" drop-count) :color :yellow))
                 (sounds/play-sound! level pos :minecraft:entity.item.pickup
                                     {:source :player :volume 0.5 :pitch 1.2})))))))
     (events/event-pass)))

  ;; 方块破坏事件 - 魔法水晶矿掉落魔法宝石
  (events/on-block-break
   (fn [level pos state player]
     (when (= (.getBlock state) @magic-crystal-ore)
       ;; 掉落 1 个魔法宝石
       (items/drop-item-at! level (.getCenter pos) @magic-gem 1)

       ;; 播放音效
       (sounds/play-sound! level (.getCenter pos) :minecraft:block.amethyst_block.break
                           {:source :block :volume 1.0 :pitch 1.2})

       ;; 提示消息
       (players/send-message! player
                              (text/literal "你获得了魔法宝石！" :color :light-purple)))
     (events/event-pass)))

  ;; 服务端 Tick 事件( 每秒执行一次，用于演示)
  (events/on-server-tick
   (fn [server]
     (let [tick-count (.getTickCount server)]
       ;; 每 20 秒( 400 ticks) 执行一次
       (when (zero? (mod tick-count 400))
         (log-debug "服务器运行正常，在线玩家数:"
                       (count (players/get-all-players server))))))))

;; ============================================================================
;; 网络通信 - 客户端与服务端数据包
;; ============================================================================

(defn setup-network!
  "设置网络数据包处理器"
  []
  ;; 初始化通用数据包系统
  (net/init-generic-packet-system! "example")

  ;; 注册客户端处理器 - 接收服务端的粒子生成请求
  (net/register-generic-handler! "example" :gem-shoot :client
                                 (fn [data player]
                                   ;; 在客户端生成魔法弹发射粒子效果
                                   (when (client-side?)
                                     ((requiring-resolve 'com.example.client/spawn-shoot-particles)
                                      (:pos data)))))

  ;; 注册服务端处理器 - 接收客户端的特殊能力请求( 传送)
  (net/register-generic-handler! "example" :special-ability :server
                                 (fn [data player]
                                   (let [level (.level player)
                                         current-pos (.position player)
                                         ;; 向前传送 10 格
                                         look-angle (.getYRot player)
                                         dx (* 10 (Math/sin (Math/toRadians look-angle)))
                                         dz (* -10 (Math/cos (Math/toRadians look-angle)))
                                         new-pos [(+ (.-x current-pos) dx)
                                                  (.-y current-pos)
                                                  (+ (.-z current-pos) dz)]]
                                     ;; 传送玩家
                                     (players/teleport! player new-pos)

                                     ;; 播放音效
                                     (sounds/play-sound! level (.position player) :minecraft:entity.enderman.teleport
                                                         {:source :player :volume 1.0 :pitch 1.0})

                                     ;; 提示消息
                                     (players/send-message! player
                                                            (text/literal "传送成功！" :color :light-purple))))))

;; ============================================================================
;; 主初始化函数
;; ============================================================================

(defn init
  "Mod 初始化函数 - 由 Java 入口点调用"
  []
  (println "[ExampleMod] ============================================")
(println "[ExampleMod] 魔法宝石 Mod 正在初始化...")
(println (str "[ExampleMod] 运行平台: " (lib/platform-name)))
(println (str "[ExampleMod] fabric-language-clojure 版本: " (lib/version)))

;; 1. 加载配置
(println "[ExampleMod] 加载配置文件...")
(load-config!)
(println "[ExampleMod] 配置加载完成，宝石威力:" (get-gem-power))

;; 2. 统一初始化 Swiss Knife 系统
(println "[ExampleMod] 初始化 Swiss Knife 系统...")
(lifecycle/init-common! "example"
                        {:enable-generic-packets? true
                         :enable-config-sync? false})  ; 单人游戏 mod，不需要配置同步

;; 3. 注册游戏内容
(println "[ExampleMod] 注册方块...")
(reg/register-all! blocks-registry)
(println "[ExampleMod] 方块注册完成:" (count @blocks-registry) "个方块")

(println "[ExampleMod] 注册物品...")
(reg/register-all! items-registry)
(println "[ExampleMod] 物品注册完成:" (count @items-registry) "个物品")

(println "[ExampleMod] 注册实体...")
(reg/register-all! entities-registry)
(println "[ExampleMod] 实体注册完成:" (count @entities-registry) "个实体")

;; 4. 设置网络通信
(println "[ExampleMod] 设置网络通信...")
(setup-network!)

;; 5. 设置事件监听器
(println "[ExampleMod] 注册事件监听器...")
(setup-events!)

  ;; 6. 开发模式下启动 nREPL
  (when (lib/dev-mode?)
    (println "[ExampleMod] 检测到开发模式，启动 nREPL 服务器...")
    (nrepl/start-server!))

  (println "[ExampleMod] 初始化完成！")
 (println "[ExampleMod] ============================================"))

;; ============================================================================
;; REPL 测试代码
;; ============================================================================

(comment
  ;; 在 nREPL 中测试配置系统
  (load-config!)
  (get-gem-power)
  (get-welcome-message)

  ;; 查看注册的内容
  @items-registry
  @blocks-registry
  @entities-registry
  @magic-gem
  @magic-shard
  @magic-crystal-ore
  @forest-guardian
  @forest-soul-potion
  @nature-affinity-book

  ;; 重新加载配置
  (config/reload-config! "example")

  ;; 测试玩家工具( 需要在游戏中测试)
  ;; 获取服务器和玩家
  ;; (require '[com.fabriclj.swiss-knife.client.platform.core :as c])
  ;; (def player (c/get-player))

  ;; 赠送物品测试
  ;; (players/give-item! player @magic-gem 1)
  ;; (players/give-item! player @magic-crystal-ore 3)
  ;; (players/give-item! player @forest-soul-potion 1)
  ;; (players/give-item! player @nature-affinity-book 1)

  ;; 生成森林守卫测试
  ;; (def level (.level player))
  ;; (def pos (.position player))
  ;; (spawn-forest-guardian! level (.offset pos 3.0 0.0 0.0))
  )
