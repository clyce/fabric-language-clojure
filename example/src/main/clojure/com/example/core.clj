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
            [com.fabriclj.swiss-knife :as sk]
            [com.fabriclj.swiss-knife.common.lifecycle :as lifecycle]
            [com.fabriclj.swiss-knife.common.registry.core :as reg]
            [com.fabriclj.swiss-knife.common.events.core :as events]
            [com.fabriclj.swiss-knife.common.game-objects.players :as players]
            [com.fabriclj.swiss-knife.common.game-objects.items :as items]
            [com.fabriclj.swiss-knife.common.game-objects.entities :as entities]
            [com.fabriclj.swiss-knife.common.gameplay.ai :as ai]
            [com.fabriclj.swiss-knife.common.gameplay.enchantments :as enchants]
            [com.fabriclj.swiss-knife.common.config.core :as config]
            [com.fabriclj.swiss-knife.common.config.validators :as v]
            [com.fabriclj.swiss-knife.common.gameplay.sounds :as sounds]
            [com.fabriclj.swiss-knife.common.gameplay.potions :as potions]
            [com.fabriclj.swiss-knife.common.network.core :as net]
            [com.fabriclj.swiss-knife.common.utils.text :as text]
            [com.fabriclj.swiss-knife.common.ui.creative-tabs :as tabs]
            [com.fabriclj.swiss-knife.common.physics.env-interaction :as env]
            [com.fabriclj.swiss-knife.client.rendering.particles :as particles])
  (:import (net.minecraft.world.item Item Item$Properties Rarity BlockItem)
           (net.minecraft.world.level.block Block Blocks SoundType)
           (net.minecraft.world.level.block.state BlockBehaviour$Properties)
           (net.minecraft.world.level.material MapColor)
           (net.minecraft.world InteractionResultHolder)
           (net.minecraft.world.entity.monster Monster Zombie)
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
(def entities-registry (reg/create-registry "example" :entity))
(def effects-registry (reg/create-registry "example" :mob-effect))
(def creative-tabs-registry (reg/create-registry "example" :creative-tab))

;; ============================================================================
;; 方块注册 - 魔法水晶矿
;; ============================================================================

;; 魔法水晶矿 - 发光的矿石，挖掘后掉落魔法宝石
(reg/defblock blocks-registry magic-crystal-ore
  (proxy [Block] [(-> (BlockBehaviour$Properties/of)
                      (.mapColor MapColor/COLOR_PURPLE)
                      (.requiresCorrectToolForDrops)
                      (.strength 1.5 3.0)  ; 硬度 1.5（类似石头），抗性 3.0（保持原值）
                      (.sound SoundType/AMETHYST)
                      (.lightLevel (reify java.util.function.ToIntFunction
                                     (applyAsInt [_ state] 7))))]  ; 发光等级 7
    ))

;; 魔法水晶矿的物品形式( 用于创造模式和 /give 命令)
;; 注意: 在 supplier 函数中调用 .get() 来获取实际的 Block
(def magic-crystal-ore-item
  (reg/register items-registry "magic_crystal_ore_item"
                (fn []
                  (BlockItem. (.get magic-crystal-ore)
                              (-> (Item$Properties.)
                                  (.rarity Rarity/RARE))))))

;; ============================================================================
;; 物品注册 - 魔法宝石和魔法碎片
;; ============================================================================

;; ============================================================================
;; 自定义效果 - 森林祝福
;; ============================================================================

(def forest-blessing-effect
  "森林祝福效果 - 持续治疗 + 自然亲和

   特性:
   - 每 2 秒恢复 0.5 生命值
   - 绿色粒子效果
   - 可与其他治疗效果叠加

   来源: 森林之魂药水"
  (potions/create-custom-effect :forest_blessing
    :beneficial 0x00FF88  ; 绿色
    :on-tick (fn [entity amplifier]
               ;; 每 2 秒治疗一次
               (when (instance? net.minecraft.world.entity.LivingEntity entity)
                 (let [^net.minecraft.world.entity.LivingEntity living entity
                       current-health (.getHealth living)
                       max-health (.getMaxHealth living)]
                   ;; 仅在未满血时治疗
                   (when (< current-health max-health)
                     (let [heal-amount (* (inc amplifier) 0.5)]
                       (.heal living (float heal-amount))

                       ;; 在服务端播放治疗粒子效果
                       (when-not (.isClientSide (.level living))
                         (let [level (.level living)
                               pos (.position living)]
                           ;; 播放心形粒子
                           (particles/spawn-particles!
                            level :heart
                            (.x pos) (+ (.y pos) 1.0) (.z pos)
                            5 {:velocity [0.0 0.1 0.0]
                               :spread [0.3 0.2 0.3]})))))))
               ;; 必须返回 true，表示效果已成功应用
               true)
    :tick-rate 40  ; 每 2 秒 (40 tick) 触发一次
    :on-added (fn [entity _amplifier]
                (when (instance? net.minecraft.world.entity.player.Player entity)
                  (players/send-message! entity
                    (text/colored-text "森林的力量在你体内涌动..." :green))))
    :on-removed (fn [entity _amplifier]
                  (when (instance? net.minecraft.world.entity.player.Player entity)
                    (players/send-message! entity
                      (text/colored-text "森林祝福已消退" :gray))))))

;; 注册自定义效果到注册表
(def forest-blessing
  (reg/register effects-registry "forest_blessing"
    (constantly forest-blessing-effect)))

;; ============================================================================
;; 创造模式标签页（必须在物品定义之前）
;; ============================================================================

;; 前向声明：物品将在后面定义
(declare magic-gem)

;; 创建标签页（注意：物品需要在创建时使用 with-tab 指定标签页）
(tabs/defcreative-tab creative-tabs-registry magic-items-tab "example" "magic_items"
  :title "魔法宝石"
  :icon magic-gem)

;; ============================================================================
;; 物品注册（实际注册）
;; ============================================================================

;; 魔法碎片 - 从怪物掉落的材料
(reg/defitem items-registry magic-shard
  (Item. (-> (Item$Properties.)
             (.stacksTo 64)
             (.rarity Rarity/UNCOMMON)
             (tabs/with-tab magic-items-tab))))

;; 魔法宝石 - 主要物品，可以发射魔法弹
(defn create-magic-gem
  "创建魔法宝石物品"
  []
  (proxy [Item] [(-> (Item$Properties.)
                     (.stacksTo 1)
                     (.durability (config/get-config-value "example" [:magic-gem :durability]))
                     (.rarity Rarity/RARE)
                     (tabs/with-tab magic-items-tab))]
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
            (net/send-generic! "example" :gem-shoot
                               {:pos [(.-x (.position player))
                                      (.-y (.position player))
                                      (.-z (.position player))]}
                               player))

          ;; 播放音效
          (sounds/play-sound! level (.position player) :minecraft:entity.ender_dragon.shoot
                              {:source :player :volume 0.8 :pitch 1.5})

          ;; 损坏物品( Minecraft 1.21+: hurtAndBreak(amount, level, player, onBroken))
          (let [item-stack (.getItemInHand player hand)]
            (.hurtAndBreak item-stack 1 level player
                           (reify java.util.function.Consumer
                             (accept [_ _] nil))))

          (InteractionResultHolder/success (.getItemInHand player hand)))))))

(reg/defitem items-registry magic-gem (create-magic-gem))

;; 森林之魂药水 - 由森林守卫掉落
;; 使用字符串 ID 引用自定义效果（延迟解析，避免注册顺序问题）
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
                                :probability 1.0}
                               ;; 使用字符串 ID 引用自定义效果
                               ;; 运行时从注册表解析，避免注册顺序问题
                               {:effect "example:forest_blessing"
                                :duration 400  ; 20秒
                                :amplifier 0
                                :probability 1.0}]))
             (tabs/with-tab magic-items-tab))))

;; 自然亲和附魔书 - 由森林守卫掉落（包含爆裂打击附魔）
;; 使用 Swiss Knife 封装简化实现
(defn create-nature-affinity-book
  "创建带有爆裂打击附魔的附魔书

   使用 Swiss Knife enchantments 模块简化实现"
  []
  (enchants/create-enchanted-book
   [["example" "explosive_strike" 3]]))  ; [mod-id enchantment-id level]

(reg/defitem items-registry nature-affinity-book
  (Item. (-> (Item$Properties.)
             (.stacksTo 1)
             (.rarity Rarity/EPIC)
             (tabs/with-tab magic-items-tab))))

;; ============================================================================
;; 实体注册 - 森林守卫
;; ============================================================================

;; 森林守卫 - 使用 Swiss Knife 实体构建器
(defn create-forest-guardian-type
  "创建森林守卫实体类型

   特点:
   - 基于僵尸模型
   - 远程攻击（发射雪球）
   - 智能后退（距离过近时）

   使用 Swiss Knife 实体构建器简化实现"
  []
  (entities/build-entity-type
   ;; 使用工厂函数创建自定义实体
   (fn [entity-type level]
     (proxy [net.minecraft.world.entity.monster.Zombie
             net.minecraft.world.entity.monster.RangedAttackMob]
       [entity-type level]

       ;; 实现远程攻击接口
       (performRangedAttack [target distance-factor]
         (let [^net.minecraft.world.entity.monster.Zombie this this
               snowball (net.minecraft.world.entity.projectile.Snowball.
                        (.level this) this)]
           (.setPos snowball (.getX this) (+ (.getY this) 1.5) (.getZ this))
           (let [dx (- (.getX target) (.getX this))
                 dy (- (+ (.getY target) (.getEyeHeight target) 0.5) (.getY snowball))
                 dz (- (.getZ target) (.getZ this))]
             (.shoot snowball dx dy dz 1.0 5.0))
           (.addFreshEntity (.level this) snowball)
           ;; 播放攻击音效
           (.playSound this net.minecraft.sounds.SoundEvents/SNOW_GOLEM_SHOOT 1.0 1.0)))

       ;; 重写 AI 注册 - 使用 Swiss Knife AI 封装
       (registerGoals []
         (let [^net.minecraft.world.entity.monster.Zombie this this]
           ;; 清除默认近战攻击
           (ai/clear-goals! this)

           ;; 添加自定义 AI（使用 Swiss Knife 封装）
           ;; 1. 后退 AI - 距离玩家过近时后退
           (ai/add-goal! this 1
             (ai/create-goal 1
               :flags [:move]
               :can-use? (fn [entity]
                          (when-let [target (.getTarget entity)]
                            (< (entities/distance-to entity target) 5.0)))
               :tick! (fn [entity]
                       (when-let [target (.getTarget entity)]
                         (let [dx (- (.getX entity) (.getX target))
                               dz (- (.getZ entity) (.getZ target))
                               dist (Math/sqrt (+ (* dx dx) (* dz dz)))]
                           (when (pos? dist)
                             ;; 使用 Swiss Knife 设置速度
                             (entities/set-velocity! entity
                               (* dx 0.15)
                               (.getY (.getDeltaMovement entity))
                               (* dz 0.15))))))))

           ;; 2. 远程攻击 AI - 使用 Swiss Knife 封装
           (ai/add-goal! this 2
             (ai/ranged-attack-goal this 1.0 60 16.0))

           ;; 3. 寻找目标 AI - 使用 Swiss Knife 封装
           (ai/add-target-goal! this 1
             (ai/nearest-attackable-target-goal this net.minecraft.world.entity.player.Player))

           ;; 4. 随机游荡 - 使用 Swiss Knife 封装
           (ai/add-goal! this 5
             (ai/wander-goal this 0.6))

           ;; 5. 看向玩家 - 使用 Swiss Knife 封装
           (ai/add-goal! this 6
             (ai/look-at-player-goal this 8.0))))))

   ;; 实体类别和配置
   :monster
   {:size [0.6 1.95]  ; 僵尸尺寸
    :name "forest_guardian"
    :tracking-range 8}))

(reg/defentity entities-registry forest-guardian (create-forest-guardian-type))

;; 注册森林守卫的属性（Minecraft 1.21 必需）
(defn register-forest-guardian-attributes!
  "注册森林守卫的实体属性

   使用 Swiss Knife 的辅助方法简化注册。"
  []
  (when-let [entity-type (.get forest-guardian)]
    (entities/register-entity-attributes! entity-type (Zombie/createAttributes))))

;; ============================================================================
;; 自定义附魔效果处理
;; ============================================================================

;; 存储被标记爆炸的实体
(def ^:private explosive-entities (atom {}))

(defn schedule-explosion!
  "标记实体在3秒后爆炸"
  [entity level amplifier]
  (let [explosion-time (+ (System/currentTimeMillis) 3000)
        entity-id (.getUUID entity)
        power (+ 1.0 (* amplifier 0.5))] ; 等级越高威力越大
    (swap! explosive-entities assoc entity-id
           {:time explosion-time
            :level level
            :pos (.position entity)
            :power power})
    (println "[ExampleMod] 实体" entity-id "将在3秒后爆炸，威力:" power)))

(defn check-explosions!
  "检查并触发已到时的爆炸"
  []
  (let [current-time (System/currentTimeMillis)
        ready-explosions (filter #(< (:time (val %)) current-time) @explosive-entities)]
    (doseq [[entity-id {:keys [level pos power]}] ready-explosions]
      (try
        ;; 使用 Swiss Knife 的 env-interaction 创建爆炸
        (when (instance? net.minecraft.server.level.ServerLevel level)
          (env/create-explosion-at! level pos power {:interaction :mob})
          (println "[ExampleMod] 爆炸已触发于:" pos "威力:" power))
        (catch Exception e
          (println "[ExampleMod] 触发爆炸时出错:" (.getMessage e))))
      ;; 移除已处理的爆炸
      (swap! explosive-entities dissoc entity-id))))

;; ============================================================================
;; 事件系统 - 玩家加入、击杀怪物等
;; ============================================================================

(defn spawn-forest-guardian!
  "在指定位置生成森林守卫

   使用 Swiss Knife entities/spawn-entity! 简化实体生成"
  [^net.minecraft.server.level.ServerLevel level pos]
  (println "[ExampleMod/Hooks] 尝试生成森林守卫于:" pos)
  (when-let [guardian-type (.get forest-guardian)]
    (try
      ;; 使用 Swiss Knife 的 spawn-entity! 简化生成逻辑
      (when-let [guardian (entities/spawn-entity! level guardian-type
                                                  (.-x pos) (.-y pos) (.-z pos))]
        (println "[ExampleMod/Hooks] 实体已创建，类型:" (class guardian))

        ;; 播放生成音效
        (sounds/play-sound! level pos :minecraft:entity.zombie.ambient
                            {:source :hostile :volume 1.0 :pitch 0.8})

        (sk/log-info "森林守卫已生成")
        guardian)
      (catch Exception e
        (println "[ExampleMod/Hooks] 生成森林守卫时出错:" (.getMessage e))
        (.printStackTrace e)))))

(defn setup-events!
  "设置游戏事件监听器"
  []
  ;; 注意: 弹道命中检测已通过 Mixin 实现 (ProjectileMixin)
  ;; 处理函数在 com.example.hooks/on-projectile-hit
  ;; 这种方式比事件系统更精确，因为直接 hook 了 Projectile.onHit() 方法

  ;; Server Tick - 检查爆炸附魔
  (events/on-server-tick
   (fn [server]
     (check-explosions!)))

  ;; 实体攻击事件 - 爆炸附魔效果
  (events/on-entity-hurt
   (fn [entity damage-source amount]
     (do
       (let [attacker-entity (.getEntity damage-source)]
         (when (instance? net.minecraft.world.entity.LivingEntity attacker-entity)
           ;; 检查攻击者是否持有爆炸附魔的武器
           (let [main-hand (players/get-main-hand-item attacker-entity)]
             (when-not (items/empty-stack? main-hand)
               ;; 检查是否有爆炸附魔
               (let [enchants (.get main-hand net.minecraft.core.component.DataComponents/ENCHANTMENTS)]
                 (when enchants
                   ;; 遍历附魔查找爆炸附魔
                   (doseq [enchant-entry (.entrySet (.enchantments enchants))]
                     (let [enchant-holder (.getKey enchant-entry)
                           level (.getValue enchant-entry)
                           enchant-key (str (.location (.getKey enchant-holder)))]
                       (when (= enchant-key "example:explosive_strike")
                         ;; 标记实体在3秒后爆炸
                         (schedule-explosion! entity (.level entity) level)
                         ;; 给被攻击者添加发光效果
                         (entities/add-effect! entity :minecraft:glowing 60 0 true)))))))))
       (events/event-pass)))))

  ;; 玩家加入时发送欢迎消息和赠送物品
  (events/on-player-join
   (fn [player]
     (sk/log-info "玩家加入:" (.getName (.getGameProfile player)))

     ;; 发送欢迎消息
     (players/send-message! player
                            (text/colored-text (get-welcome-message) :gold))

     ;; 赠送魔法水晶矿( 让玩家自己挖掘)
     (when-not (players/has-item? player (.get magic-crystal-ore-item))
       (players/give-item! player (.get magic-crystal-ore-item) 3)
       (players/send-message! player
                              (text/colored-text "你获得了魔法水晶矿！挖掘它获取魔法宝石。"
                                                 :aqua))
       (players/send-message! player
                              (text/colored-text "右键使用宝石发射魔法弹，命中树叶可以召唤森林守卫！"
                                                 :yellow)))))

  ;; 玩家击杀怪物时掉落物品
  (events/on-living-death
   (fn [entity damage-source]
     (let [level (.level entity)
           pos (.position entity)
           ;; Minecraft 1.21: 使用 getEntity 获取伤害来源
           damage-entity (.getEntity damage-source)
           player (when (instance? net.minecraft.world.entity.player.Player damage-entity)
                    damage-entity)]
       ;; 检查是否是森林守卫( 通过实体类型判断)
       (if (= (.getType entity) (.get forest-guardian))
         ;; 森林守卫 - 100% 掉落药水和附魔书
         (do
           (items/spawn-item-entity! level (.-x pos) (.-y pos) (.-z pos)
                                     (items/item-stack (.get forest-soul-potion) 1))
           (items/spawn-item-entity! level (.-x pos) (.-y pos) (.-z pos)
                                     (create-nature-affinity-book))
           (when player
             (players/send-message! player
                                    (text/colored-text "森林守卫掉落了珍贵物品！" :gold))
             (sounds/play-sound! level pos :minecraft:entity.player.levelup
                                 {:source :player :volume 1.0 :pitch 1.0})))

         ;; 普通怪物 - 20% 概率掉落 1-3 个魔法碎片
         (when (instance? Monster entity)
           (when player
             (let [drop-count (when (< (rand) 0.2)
                                (+ 1 (rand-int 3)))]
               (when drop-count
                 (items/spawn-item-entity! level (.-x pos) (.-y pos) (.-z pos)
                                           (items/item-stack (.get magic-shard) drop-count))
                 (players/send-message! player
                                        (text/colored-text (str "魔法碎片 x" drop-count) :yellow))
                 (sounds/play-sound! level pos :minecraft:entity.item.pickup
                                     {:source :player :volume 0.5 :pitch 1.2})))))))
     (events/event-pass)))

  ;; 方块破坏事件 - 魔法水晶矿掉落魔法宝石
  (events/on-block-break
   (fn [level pos state player xp]
     (when (= (.getBlock state) (.get magic-crystal-ore))
       ;; 掉落 1 个魔法宝石
       (let [center-pos (.getCenter pos)]
         (items/spawn-item-entity! level (.-x center-pos) (.-y center-pos) (.-z center-pos)
                                   (items/item-stack (.get magic-gem) 1)))

       ;; 播放音效
       (sounds/play-sound! level (.getCenter pos) :minecraft:block.amethyst_block.break
                           {:source :block :volume 1.0 :pitch 1.2})

       ;; 提示消息
       (players/send-message! player
                              (text/colored-text "你获得了魔法宝石！" :light-purple)))
     (events/event-pass)))

  ;; 服务端 Tick 事件( 每秒执行一次，用于演示)
  (events/on-server-tick
   (fn [server]
     (let [tick-count (.getTickCount server)]
       ;; 每 20 秒( 400 ticks) 执行一次
       (when (zero? (mod tick-count 400))
         (sk/log-debug "服务器运行正常，在线玩家数:"
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
  ;; 注意: 使用 requiring-resolve 避免服务端加载客户端代码
  ;; 原因: core.clj 是服务端和客户端共享的，而 client.clj 仅客户端
  ;; 如果直接 require，服务端加载时会尝试加载客户端命名空间，可能导致类加载错误
  (net/register-generic-handler! "example" :gem-shoot :client
                                 (fn [data player]
                                   ;; 在客户端生成魔法弹发射粒子效果
                                   (when (sk/client-side?)
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
                                                            (text/colored-text "传送成功！" :light-purple))))))

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
  (println "[ExampleMod] 方块注册完成")

  (println "[ExampleMod] 注册药水效果...")
  (reg/register-all! effects-registry)
  (println "[ExampleMod] 药水效果注册完成")

  ;; 3.5 注册创造模式标签页（必须在物品之前注册，因为物品需要引用标签页）
  (println "[ExampleMod] 注册创造模式标签页...")
  (reg/register-all! creative-tabs-registry)
  (println "[ExampleMod] 创造模式标签页注册完成")

  (println "[ExampleMod] 注册物品...")
  (reg/register-all! items-registry)
  (println "[ExampleMod] 物品注册完成")

  (println "[ExampleMod] 注册实体...")
  (reg/register-all! entities-registry)
  (println "[ExampleMod] 实体注册完成")

  ;; 3.6 注册实体属性（Minecraft 1.21 必需）
  (println "[ExampleMod] 注册实体属性...")
  (register-forest-guardian-attributes!)

  ;; 4. 设置网络通信
  (println "[ExampleMod] 设置网络通信...")
  (setup-network!)

  ;; 5. 设置事件监听器
  (println "[ExampleMod] 注册事件监听器...")
  (setup-events!)

  ;; 6. 🚀 开发模式增强: nREPL + 自动热重载
  (when (lib/dev-mode?)
    (println "[ExampleMod] 🛠️  检测到开发模式")

    ;; 启动 nREPL 服务器（用于 REPL 连接）
    (println "[ExampleMod] 启动 nREPL 服务器...")
    (nrepl/start-server!)
    (println "[ExampleMod] 📡 nREPL 已就绪 - 连接方式: Calva -> localhost:7888")

    ;; 启动自动文件监控和热重载
    (try
      (require '[com.fabriclj.dev.hot-reload :as reload]
               '[com.fabriclj.swiss-knife.common.platform.core :as platform]
               '[com.fabriclj.swiss-knife.common.game-objects.players :as players]
               '[com.fabriclj.swiss-knife.common.utils.text :as text]
               '[com.fabriclj.swiss-knife.common.gameplay.sounds :as sounds])
      ((resolve 'reload/start!)
       {:watch-paths ["example/src/main/clojure"]
        :on-reload (fn [ns]
                     ;; 控制台日志
                     (println (str "[ExampleMod/HotReload] 🔄 已重载: " ns))

                     ;; 游戏内通知（如果服务器可用）
                     (when-let [server ((resolve 'platform/get-server))]
                       ;; 发送彩色消息
                       (let [message ((resolve 'text/colored-text)
                                     (str "🔄 代码已热重载: " ns)
                                     :color :green)]
                         ((resolve 'players/broadcast-message!) server message))

                       ;; 播放提示音效（给所有玩家）
                       (doseq [player ((resolve 'players/get-all-players) server)]
                         (let [pos (.position player)
                               level (.level player)]
                           ((resolve 'sounds/play-sound!)
                            level
                            [(.x pos) (.y pos) (.z pos)]
                            :minecraft:entity.experience_orb.pickup
                            {:source :player :volume 0.5 :pitch 1.5})))))})
      (println "[ExampleMod] 🔥 文件监控已启动 - 保存 .clj 文件即可自动重载！")
      (println "[ExampleMod]    💡 修改代码后保存，将在游戏中看到通知并听到提示音")
      (catch Exception e
        (println "[ExampleMod] ⚠️  热重载模块未找到（可能未编译），跳过"))))

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
