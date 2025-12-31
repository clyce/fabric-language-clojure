(ns com.fabriclj.swiss-knife.common.builders.macros
  "瑞士军刀 - DSL 增强模块

   **模块定位**: 编译时宏，提供声明式语法糖

   **与 builders 模块的关系**:
   - `dsl.clj` - 编译时宏展开，适合静态配置，代码简洁
   - `builders.clj` - 运行时链式API，适合动态构建，组合灵活

   **使用场景**:
   ```clojure
   ;; 使用 DSL 宏 - 静态配置，代码简洁
   (defitem+ items magic-wand \"magic_wand\"
     :stack-size 1
     :durability 500
     :rarity :rare)

   ;; 使用 Builders - 动态构建，运行时决定
   (let [rarity (if premium? :epic :rare)
         props (-> (builders/item-properties)
                   (builders/with-stack-size 1)
                   (builders/with-durability 500)
                   (builders/with-rarity rarity))]
     (Item. props))

   ;; 不要在宏中使用运行时值
   (defitem+ items my-item \"my_item\"
     :rarity (if premium? :epic :rare))  ; 编译错误！
   ```

   **提示**: 如果需要条件逻辑或动态配置，请使用 `builders` 模块。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.swiss-knife.common.registry.core :as registry]
            [com.fabriclj.swiss-knife.common.events.core :as events])
  (:import (net.minecraft.world.item Item Item$Properties BlockItem)
           (net.minecraft.world.level.block Block)
           (net.minecraft.world.level.block.state BlockBehaviour BlockBehaviour$Properties)
           (net.minecraft.world.food FoodProperties)
           (net.minecraft.world.item.context UseOnContext)
           (net.minecraft.world InteractionResult)
           (net.minecraft.world.entity.player Player)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 增强的物品注册 DSL
;; ============================================================================

(defmacro defitem+
  "增强的物品注册宏

   提供更简洁和功能丰富的物品定义语法

   参数:
   - registry: 注册表
   - name: 物品名称( 符号)
   - id: 物品 ID( 字符串)
   - options: 配置选项

   选项:
   - :stack-size - 堆叠数量
   - :durability - 耐久度
   - :as-food - 食物属性 {:nutrition N :saturation S :meat? bool}
   - :as-fuel - 燃料燃烧时间( tick)
   - :rarity - 稀有度 (:common/:uncommon/:rare/:epic)
   - :fireproof? - 是否防火
   - :on-use - 右键使用 (fn [level player hand] -> InteractionResult)
   - :on-use-on-block - 对方块右键 (fn [context] -> InteractionResult)
   - :on-attack - 攻击实体时 (fn [stack target attacker] -> boolean)
   - :on-inventory-tick - 在背包中每 tick (fn [stack level entity slot selected?] -> nil)
   - :tooltip - 工具提示 (fn [stack] -> [text...])

   示例:
   ```clojure
   (defitem+ items magic-wand \"magic_wand\"
     :stack-size 1
     :durability 500
     :rarity :rare
     :fireproof? true
     :on-use (fn [level player hand]
               (println \"Cast spell!\")
               InteractionResult/SUCCESS))
   ```"
   [registry name id & {:as options}]
  (let [{:keys [stack-size durability as-food as-fuel rarity fireproof?
                on-use on-use-on-block on-attack on-inventory-tick tooltip]} options]
    `(def ~name
       (registry/register ~registry ~id
         (fn []
           (let [props# (Item$Properties.)]
             ;; 基础属性
             ~@(when stack-size
                 [`(.stacksTo props# ~stack-size)])
             ~@(when durability
                 [`(.durability props# ~durability)])
             ~@(when fireproof?
                 [`(.fireResistant props#)])
             ~@(when rarity
                 (let [rarity-val (case rarity
                                    :common 'net.minecraft.world.item.Rarity/COMMON
                                    :uncommon 'net.minecraft.world.item.Rarity/UNCOMMON
                                    :rare 'net.minecraft.world.item.Rarity/RARE
                                    :epic 'net.minecraft.world.item.Rarity/EPIC)]
                   [`(.rarity props# ~rarity-val)]))

              ;; 食物属性
              ~@(when as-food
                  (let [{:keys [nutrition saturation meat?]} as-food]
                   [`(let [food-props# (-> (FoodProperties$Builder.)
                                           (.nutrition ~nutrition)
                                           (.saturationModifier ~(float saturation))
                                           ~@(when meat?
                                               ['(.meat)])
                                           .build)]
                       (.food props# food-props#))]))

              ;; 创建物品实例
              (proxy [Item] [props#]
                ~@(when on-use
                    [`(use [level# player# hand#]
                        (~on-use level# player# hand#))])
                ~@(when on-use-on-block
                    [`(useOn [^UseOnContext context#]
                        (~on-use-on-block context#))])
                ~@(when on-attack
                    [`(hurtEnemy [stack# target# attacker#]
                        (~on-attack stack# target# attacker#))])
                ~@(when on-inventory-tick
                    [`(inventoryTick [stack# level# entity# slot# selected?#]
                        (~on-inventory-tick stack# level# entity# slot# selected?#))]))))))))

(defmacro deftool+
  "定义工具类物品( 剑/镐/斧/铲/锄)

   参数:
   - registry: 注册表
   - name: 物品名称
   - id: 物品 ID
   - tool-type: 工具类型 (:sword/:pickaxe/:axe/:shovel/:hoe)
   - tier: 工具等级 (:wood/:stone/:iron/:gold/:diamond/:netherite 或自定义 Tier)
   - options: 其他选项
     - :attack-damage - 额外攻击伤害
     - :attack-speed - 攻击速度
     - :rarity - 稀有度
     - :on-attack - 攻击回调
     - 其他选项同 defitem+

   示例:
   ```clojure
   (deftool+ items magic-sword \"magic_sword\" :sword :diamond
     :attack-damage 3.0
     :attack-speed -2.4
     :rarity :epic
     :on-attack (fn [stack target attacker]
                  (println \"Dealing magic damage!\")
                  true))
   ```"
  [registry name id tool-type tier & {:as options}]
  (let [{:keys [attack-damage attack-speed rarity fireproof? on-attack]} options
        tier-obj (case tier
                   :wood 'net.minecraft.world.item.Tiers/WOOD
                   :stone 'net.minecraft.world.item.Tiers/STONE
                   :iron 'net.minecraft.world.item.Tiers/IRON
                   :gold 'net.minecraft.world.item.Tiers/GOLD
                   :diamond 'net.minecraft.world.item.Tiers/DIAMOND
                   :netherite 'net.minecraft.world.item.Tiers/NETHERITE
                   tier)
        base-class (case tool-type
                     :sword 'net.minecraft.world.item.SwordItem
                     :pickaxe 'net.minecraft.world.item.PickaxeItem
                     :axe 'net.minecraft.world.item.AxeItem
                     :shovel 'net.minecraft.world.item.ShovelItem
                     :hoe 'net.minecraft.world.item.HoeItem)]
    `(def ~name
       (registry/register ~registry ~id
         (fn []
           (let [props# (Item$Properties.)]
             ~@(when fireproof?
                 [`(.fireResistant props#)])
             ~@(when rarity
                 (let [rarity-val (case rarity
                                    :common 'net.minecraft.world.item.Rarity/COMMON
                                    :uncommon 'net.minecraft.world.item.Rarity/UNCOMMON
                                    :rare 'net.minecraft.world.item.Rarity/RARE
                                    :epic 'net.minecraft.world.item.Rarity/EPIC)]
                   [`(.rarity props# ~rarity-val)]))

             (proxy [~base-class] [~tier-obj
                                   ~(or attack-damage
                                        (case tool-type
                                          :sword 3.0
                                          :pickaxe 1.0
                                          :axe 6.0
                                          :shovel 1.5
                                          :hoe 0.0))
                                   ~(or attack-speed
                                        (case tool-type
                                          :sword -2.4
                                          :pickaxe -2.8
                                          :axe -3.2
                                          :shovel -3.0
                                          :hoe -3.0))
                                   props#]
               ~@(when on-attack
                   [`(hurtEnemy [stack# target# attacker#]
                       (~on-attack stack# target# attacker#))]))))))))

(defmacro defweapon+
  "定义武器类物品( 剑的别名，语义更清晰)

   用法同 deftool+，但 tool-type 固定为 :sword

   示例:
   ```clojure
   (defweapon+ items legendary-blade \"legendary_blade\" :diamond
     :attack-damage 5.0
     :rarity :epic)
   ```"
  [registry name id tier & options]
  `(deftool+ ~registry ~name ~id :sword ~tier ~@options))

(defmacro defarmor+
  "定义护甲类物品

   参数:
   - registry: 注册表
   - name: 物品名称
   - id: 物品 ID
   - armor-type: 护甲类型 (:helmet/:chestplate/:leggings/:boots)
   - material: 护甲材质 (:leather/:chainmail/:iron/:gold/:diamond/:netherite)
   - options: 其他选项
     - :defense - 防御值( 可选，默认使用材质默认值)
     - :toughness - 护甲韧性( 可选)
     - :knockback-resistance - 击退抗性( 可选)
     - :rarity - 稀有度

   示例:
   ```clojure
   (defarmor+ items magic-helmet \"magic_helmet\" :helmet :diamond
     :defense 4
     :toughness 3.0
     :rarity :epic)
   ```"
  [registry name id armor-type material & {:as options}]
  (let [{:keys [defense toughness knockback-resistance rarity fireproof?]} options
        armor-material (case material
                         :leather 'net.minecraft.world.item.ArmorMaterials/LEATHER
                         :chainmail 'net.minecraft.world.item.ArmorMaterials/CHAINMAIL
                         :iron 'net.minecraft.world.item.ArmorMaterials/IRON
                         :gold 'net.minecraft.world.item.ArmorMaterials/GOLD
                         :diamond 'net.minecraft.world.item.ArmorMaterials/DIAMOND
                         :netherite 'net.minecraft.world.item.ArmorMaterials/NETHERITE
                         material)
        armor-slot (case armor-type
                     :helmet 'net.minecraft.world.entity.EquipmentSlot/HEAD
                     :chestplate 'net.minecraft.world.entity.EquipmentSlot/CHEST
                     :leggings 'net.minecraft.world.entity.EquipmentSlot/LEGS
                     :boots 'net.minecraft.world.entity.EquipmentSlot/FEET)]
    `(def ~name
       (registry/register ~registry ~id
         (fn []
           (let [props# (Item$Properties.)]
             ~@(when fireproof?
                 [`(.fireResistant props#)])
             ~@(when rarity
                 (let [rarity-val (case rarity
                                    :common 'net.minecraft.world.item.Rarity/COMMON
                                    :uncommon 'net.minecraft.world.item.Rarity/UNCOMMON
                                    :rare 'net.minecraft.world.item.Rarity/RARE
                                    :epic 'net.minecraft.world.item.Rarity/EPIC)]
                   [`(.rarity props# ~rarity-val)]))

             (net.minecraft.world.item.ArmorItem.
               ~armor-material
               ~armor-slot
               props#)))))))

(defmacro defblock+
  "增强的方块注册宏

   参数:
   - block-registry: 方块注册表
   - item-registry: 物品注册表
   - name: 方块名称
   - id: 方块 ID
   - options: 配置选项

   选项:
   - :material - 材质 (:stone/:wood/:metal/:dirt/:plant)
   - :strength - 硬度和抗性 [hardness resistance]
   - :sound - 音效类型 (:stone/:wood/:metal/:glass)
   - :light-level - 光照等级 (0-15)
   - :requires-tool? - 是否需要正确工具才能掉落( 见上方说明)
   - :no-drops? - 是否不掉落( 例如基岩)
   - :block-item? - 是否创建物品形式( 默认 true)
   - :on-use - 右键交互 (fn [state level pos player hand hit] -> InteractionResult)
   - :on-break - 破坏时 (fn [state level pos player] -> nil)

   示例:
   ```clojure
   (defblock+ blocks items magic-stone \"magic_stone\"
     :material :stone
     :strength [5.0 6.0]
     :sound :stone
     :light-level 7
     :requires-tool? true
     :on-use (fn [state level pos player hand hit]
               (println \"Activated!\")
               InteractionResult/SUCCESS))
   ```"
  [block-registry item-registry name id & {:as options}]
  (let [{:keys [material strength sound light-level requires-tool?
                no-drops? block-item? on-use on-break]
         :or {block-item? true}} options]
    `(do
       ;; 注册方块
       (def ~name
         (registry/register ~block-registry ~id
           (fn []
             (let [props# (BlockBehaviour$Properties/of)]
               ;; 基础属性
               ~@(when strength
                   (let [[hardness resistance] strength]
                     [`(.strength props# ~(float hardness) ~(float resistance))]))
               ~@(when sound
                   (let [sound-type (case sound
                                      :stone 'net.minecraft.world.level.block.SoundType/STONE
                                      :wood 'net.minecraft.world.level.block.SoundType/WOOD
                                      :metal 'net.minecraft.world.level.block.SoundType/METAL
                                      :glass 'net.minecraft.world.level.block.SoundType/GLASS
                                      :grass 'net.minecraft.world.level.block.SoundType/GRASS)]
                     [`(.sound props# ~sound-type)]))
               ~@(when light-level
                   [`(.lightLevel props# (constantly ~light-level))])
               ~@(when requires-tool?
                   [`(.requiresCorrectToolForDrops props#)])
               ~@(when no-drops?
                   [`(.noLootTable props#)])

               ;; 创建方块实例
               (proxy [Block] [props#]
                 ~@(when on-use
                     [`(useWithoutItem [state# level# pos# player# hit#]
                         (~on-use state# level# pos# player# hit#))])
                 ~@(when on-break
                     [`(onRemove [state# level# pos# new-state# moving#]
                         (~on-break state# level# pos#)
                         (proxy-super onRemove state# level# pos# new-state# moving#))]))))))

       ;; 注册方块物品
       ~@(when block-item?
           [`(def ~(symbol (str name "-item"))
              (registry/register ~item-registry ~id
                (fn []
                  (BlockItem. (.get ~name) (Item$Properties.)))))]))))

;; ============================================================================
;; 事件处理 DSL
;; ============================================================================

(defmacro on-event
  "简化的事件监听器

   示例:
   ```clojure
   (on-event :player-tick [player]
     (when (some-condition? player)
       (do-something player)))
   ```"
  [event-type bindings & body]
  `(events/defevent ~(gensym (str "event-" (name event-type) "-"))
     (fn ~bindings
       ~@body)))

(defmacro when-player-joins
  "玩家加入时执行

   示例:
   ```clojure
   (when-player-joins [player]
     (send-message player \"Welcome!\"))
   ```"
  [bindings & body]
  `(on-event :player-join ~bindings ~@body))

(defmacro when-block-broken
  "方块被破坏时执行

   示例:
   ```clojure
   (when-block-broken [level pos state player]
     (when (= state Blocks/DIAMOND_ORE)
       (spawn-explosion level pos)))
   ```"
  [bindings & body]
  `(on-event :block-break ~bindings ~@body))

;; ============================================================================
;; 配置 DSL
;; ============================================================================

(defmacro defconfig
  "定义配置

   示例:
   ```clojure
   (defconfig my-mod-config
     :mana-regen-rate 10
     :max-spell-level 5
     :enable-pvp? true)
   ```"
  [name & {:as config}]
  `(def ~name
     ~config))

;; ============================================================================
;; 条件执行
;; ============================================================================

(defmacro when-server
  "仅在服务端执行

   示例:
   ```clojure
   (when-server
     (save-data level data))
   ```"
  [& body]
  `(when (core/server-side?)
     ~@body))

(defmacro when-client
  "仅在客户端执行"
  [& body]
  `(when (core/client-side?)
     ~@body))

;; ============================================================================
;; 链式操作
;; ============================================================================

(defmacro ->item
  "创建物品堆栈的链式操作

   示例:
   ```clojure
   (->item Items/DIAMOND_SWORD
     (set-count 1)
     (set-damage 10)
     (add-enchantment Enchantments/SHARPNESS 5))
   ```"
  [item & operations]
  (let [stack-sym (gensym "stack")]
    `(let [~stack-sym (net.minecraft.world.item.ItemStack. ~item)]
       ~@(map (fn [op]
                `(~(first op) ~stack-sym ~@(rest op)))
              operations)
       ~stack-sym)))

(comment
  ;; 使用示例

  ;; 1. 增强的物品注册
  (defitem+ items magic-wand "magic_wand"
    :stack-size 1
    :durability 500
    :as-food {:nutrition 4 :saturation 0.3}
    :as-fuel 1600
    :rarity :rare
    :fireproof? true
    :on-use (fn [level player hand]
              (println "Magic!")
              InteractionResult/SUCCESS))

  ;; 2. 增强的方块注册
  (defblock+ blocks items magic-stone "magic_stone"
    :material :stone
    :strength [5.0 6.0]
    :sound :stone
    :light-level 7
    :requires-tool? true
    :on-use (fn [state level pos player hand hit]
              (println "Activated!")
              InteractionResult/SUCCESS))

  ;; 3. 工具类注册
  (deftool+ items diamond-hammer "diamond_hammer" :pickaxe :diamond
    :attack-damage 5.0
    :attack-speed -2.8
    :rarity :rare
    :on-attack (fn [stack target attacker]
                 (println "Hammer strike!")
                 true))

  ;; 4. 武器注册
  (defweapon+ items legendary-sword "legendary_sword" :netherite
    :attack-damage 8.0
    :rarity :epic)

  ;; 5. 护甲注册
  (defarmor+ items magic-helmet "magic_helmet" :helmet :diamond
    :defense 4
    :toughness 3.0
    :rarity :rare)

  ;; 6. 事件处理
  (when-player-joins [player]
    (send-message player "Welcome!"))

  (when-block-broken [level pos state player]
    (println "Block broken at" pos))

  ;; 7. 条件执行
  (when-server
    (save-world-data))

  (when-client
    (play-sound)))
