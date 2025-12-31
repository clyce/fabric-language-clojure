(ns com.fabriclj.swiss-knife.common.builders.runtime
  "瑞士军刀 - 链式构建器模块

   **模块定位**：运行时链式API，提供流畅的属性构建
   
   **与 dsl 模块的关系**：
   - `dsl.clj` - 编译时宏展开，适合静态配置，代码简洁
   - `builders.clj` - 运行时链式API，适合动态构建，组合灵活
   
   **使用场景**：
   ```clojure
   ;; 使用场景 1：需要运行时决定属性
   (defn create-tiered-item [tier]
     (let [props (cond-> (item-properties)
                   (= tier :common) (with-stack-size 64)
                   (= tier :rare) (-> (with-stack-size 16)
                                      (with-rarity :rare))
                   (= tier :epic) (-> (with-stack-size 1)
                                      (with-rarity :epic)
                                      fireproof))]
       (Item. props)))
   
   ;; 使用场景 2：复杂的条件组合
   (let [props (-> (item-properties)
                   (with-stack-size 16)
                   (cond-> is-rare? (with-rarity :rare))
                   (cond-> is-fireproof? fireproof)
                   (cond-> is-food? (with-food food-props)))]
     (Item. props))
   
   ;; 使用场景 3：函数式组合
   (defn apply-tier-modifiers [props tier]
     (case tier
       :wood (with-durability props 59)
       :stone (with-durability props 131)
       :iron (with-durability props 250)))
   
   ;; 如果配置是静态的，推荐使用 dsl 宏更简洁
   (def props (-> (item-properties)
                  (with-stack-size 64)
                  (with-rarity :rare)))
   ;; 应改为：(defitem+ items my-item \"my_item\" :stack-size 64 :rarity :rare)
   ```
   
   **提示**：如果配置是编译时常量，使用 `dsl` 宏更简洁。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import [net.minecraft.world.item Item Item$Properties ItemStack]
           [net.minecraft.world.level.block Block Block$Properties]
           [net.minecraft.world.level.block.state BlockBehaviour BlockBehaviour$Properties]
           [net.minecraft.world.food FoodProperties FoodProperties$Builder]
           [net.minecraft.world.item Rarity CreativeModeTab CreativeModeTab$Builder]
           [net.minecraft.world.entity.ai.attributes AttributeModifier AttributeModifier$Operation]
           [net.minecraft.network.chat Component]
           [net.minecraft.resources ResourceLocation]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 物品属性构建器
;; ============================================================================

(defn item-properties
  "创建物品属性构建器

   返回：Item$Properties

   示例:
   ```clojure
   (-> (item-properties)
       (with-stack-size 16)
       (with-durability 500)
       (with-rarity :rare)
       (fireproof))
   ```"
  ^Item$Properties []
  (Item$Properties.))

(defn with-stack-size
  "设置堆叠数量"
  ^Item$Properties [^Item$Properties props size]
  (.stacksTo props size))

(defn with-durability
  "设置耐久度"
  ^Item$Properties [^Item$Properties props durability]
  (.durability props durability))

(defn with-rarity
  "设置稀有度

   参数:
   - props: Item$Properties
   - rarity: :common/:uncommon/:rare/:epic"
  ^Item$Properties [^Item$Properties props rarity]
  (let [rarity-val (case rarity
                     :common Rarity/COMMON
                     :uncommon Rarity/UNCOMMON
                     :rare Rarity/RARE
                     :epic Rarity/EPIC)]
    (.rarity props rarity-val)))

(defn fireproof
  "设置防火"
  ^Item$Properties [^Item$Properties props]
  (.fireResistant props))

(defn with-food
  "设置食物属性

   参数:
   - props: Item$Properties
   - food-props: FoodProperties"
  ^Item$Properties [^Item$Properties props ^FoodProperties food-props]
  (.food props food-props))

;; ============================================================================
;; 食物属性构建器
;; ============================================================================

(defn food-properties
  "创建食物属性构建器

   返回：FoodProperties$Builder

   示例:
   ```clojure
   (-> (food-properties)
       (with-nutrition 6)
       (with-saturation 0.6)
       (meat)
       (fast-eat)
       (build-food))
   ```"
  ^FoodProperties$Builder []
  (FoodProperties$Builder.))

(defn with-nutrition
  "设置营养值"
  ^FoodProperties$Builder [^FoodProperties$Builder builder nutrition]
  (.nutrition builder nutrition))

(defn with-saturation
  "设置饱和值"
  ^FoodProperties$Builder [^FoodProperties$Builder builder saturation]
  (.saturationModifier builder (float saturation)))

(defn meat
  "设置为肉类"
  ^FoodProperties$Builder [^FoodProperties$Builder builder]
  (.meat builder))

(defn fast-eat
  "设置快速食用"
  ^FoodProperties$Builder [^FoodProperties$Builder builder]
  (.fast builder))

(defn always-edible
  "设置总是可食用"
  ^FoodProperties$Builder [^FoodProperties$Builder builder]
  (.alwaysEdible builder))

(defn build-food
  "构建食物属性

   返回：FoodProperties"
  ^FoodProperties [^FoodProperties$Builder builder]
  (.build builder))

;; ============================================================================
;; 方块属性构建器
;; ============================================================================

(defn block-properties
  "创建方块属性构建器

   返回：BlockBehaviour$Properties

   示例:
   ```clojure
   (-> (block-properties)
       (with-strength 5.0 6.0)
       (with-sound :stone)
       (with-light-level 7)
       (requires-tool))
   ```"
  ^BlockBehaviour$Properties []
  (BlockBehaviour$Properties/of))

(defn with-strength
  "设置硬度和抗性

   参数:
   - hardness: 硬度
   - resistance: 抗性（可选，默认等于硬度）"
  (^BlockBehaviour$Properties [props hardness]
   (with-strength props hardness hardness))
  (^BlockBehaviour$Properties [^BlockBehaviour$Properties props hardness resistance]
   (.strength props (float hardness) (float resistance))))

(defn with-sound
  "设置音效类型

   参数:
   - sound: :stone/:wood/:metal/:glass/:grass/:gravel/:sand/:snow/:wool/:slime"
  ^BlockBehaviour$Properties [^BlockBehaviour$Properties props sound]
  (let [sound-type (case sound
                     :stone net.minecraft.world.level.block.SoundType/STONE
                     :wood net.minecraft.world.level.block.SoundType/WOOD
                     :metal net.minecraft.world.level.block.SoundType/METAL
                     :glass net.minecraft.world.level.block.SoundType/GLASS
                     :grass net.minecraft.world.level.block.SoundType/GRASS
                     :gravel net.minecraft.world.level.block.SoundType/GRAVEL
                     :sand net.minecraft.world.level.block.SoundType/SAND
                     :snow net.minecraft.world.level.block.SoundType/SNOW
                     :wool net.minecraft.world.level.block.SoundType/WOOL
                     :slime net.minecraft.world.level.block.SoundType/SLIME_BLOCK)]
    (.sound props sound-type)))

(defn with-light-level
  "设置光照等级 (0-15)"
  ^BlockBehaviour$Properties [^BlockBehaviour$Properties props level]
  (.lightLevel props (constantly level)))

(defn requires-tool
  "设置需要工具挖掘"
  ^BlockBehaviour$Properties [^BlockBehaviour$Properties props]
  (.requiresCorrectToolForDrops props))

(defn no-drops
  "设置不掉落物品"
  ^BlockBehaviour$Properties [^BlockBehaviour$Properties props]
  (.noLootTable props))

(defn indestructible
  "设置无法破坏"
  ^BlockBehaviour$Properties [^BlockBehaviour$Properties props]
  (.strength props -1.0 3600000.0))

;; ============================================================================
;; ItemStack 构建器
;; ============================================================================

(defn item-stack
  "创建物品堆栈

   参数:
   - item: Item
   - count: 数量（可选，默认 1）

   返回：ItemStack

   示例:
   ```clojure
   (-> (item-stack Items/DIAMOND_SWORD)
       (with-count 1)
       (with-custom-name \"Excalibur\")
       (add-lore \"A legendary sword\"))
   ```"
  (^ItemStack [item]
   (item-stack item 1))
  (^ItemStack [item count]
   (ItemStack. item count)))

(defn with-count
  "设置堆栈数量"
  ^ItemStack [^ItemStack stack count]
  (.setCount stack count)
  stack)

(defn with-custom-name
  "设置自定义名称"
  ^ItemStack [^ItemStack stack name]
  (.setHoverName stack (Component/literal name))
  stack)

(defn with-damage
  "设置耐久损坏值"
  ^ItemStack [^ItemStack stack damage]
  (.setDamageValue stack damage)
  stack)

;; ============================================================================
;; 创造模式标签页构建器
;; ============================================================================

(defn creative-tab
  "创建创造模式标签页构建器

   返回：CreativeModeTab$Builder

   示例:
   ```clojure
   (-> (creative-tab)
       (with-title \"My Mod\")
       (with-icon (ItemStack. Items/DIAMOND))
       (with-items [item1 item2 item3])
       (build-tab))
   ```"
  ^CreativeModeTab$Builder []
  (CreativeModeTab/builder))

(defn with-title
  "设置标签页标题"
  ^CreativeModeTab$Builder [^CreativeModeTab$Builder builder title]
  (.title builder (Component/literal title)))

(defn with-icon
  "设置标签页图标

   参数:
   - builder: CreativeModeTab$Builder
   - icon: ItemStack 或 Item"
  ^CreativeModeTab$Builder [^CreativeModeTab$Builder builder icon]
  (.icon builder
         (reify java.util.function.Supplier
           (get [_]
             (if (instance? ItemStack icon)
               icon
               (ItemStack. icon))))))

(defn with-items
  "设置标签页物品列表"
  ^CreativeModeTab$Builder [^CreativeModeTab$Builder builder items]
  (.displayItems builder
                 (reify net.minecraft.world.item.CreativeModeTab$DisplayItemsGenerator
                   (accept [_ params output]
                     (doseq [item items]
                       (.accept output
                                (if (instance? ItemStack item)
                                  item
                                  (ItemStack. item))))))))

(defn build-tab
  "构建创造模式标签页

   返回：CreativeModeTab"
  ^CreativeModeTab [^CreativeModeTab$Builder builder]
  (.build builder))

;; ============================================================================
;; 便捷组合构建器
;; ============================================================================

(defn build-simple-item
  "构建简单物品

   参数:
   - opts: {:stack-size :durability :rarity :fireproof?}

   返回：Item$Properties

   示例:
   ```clojure
   (build-simple-item {:stack-size 16 :rarity :rare :fireproof? true})
   ```"
  [opts]
  (let [{:keys [stack-size durability rarity fireproof?]} opts
        props (item-properties)]
    (cond-> props
      stack-size (with-stack-size stack-size)
      durability (with-durability durability)
      rarity (with-rarity rarity)
      fireproof? fireproof)))

(defn build-food-item
  "构建食物物品

   参数:
   - opts: {:nutrition :saturation :meat? :fast-eat? :always-edible? ...}

   返回：Item$Properties

   示例:
   ```clojure
   (build-food-item {:nutrition 6 :saturation 0.6 :meat? true})
   ```"
  [opts]
  (let [{:keys [nutrition saturation meat? fast-eat? always-edible?
                stack-size rarity]} opts
        food-props (-> (food-properties)
                       (with-nutrition nutrition)
                       (with-saturation saturation)
                       (cond-> meat? meat
                               fast-eat? fast-eat
                               always-edible? always-edible)
                       build-food)
        props (item-properties)]
    (cond-> (with-food props food-props)
      stack-size (with-stack-size stack-size)
      rarity (with-rarity rarity))))

(defn build-simple-block
  "构建简单方块

   参数:
   - opts: {:strength :sound :light-level :requires-tool? :no-drops?}

   返回：BlockBehaviour$Properties

   示例:
   ```clojure
   (build-simple-block {:strength [5.0 6.0] :sound :stone :light-level 7})
   ```"
  [opts]
  (let [{:keys [strength sound light-level requires-tool? no-drops?]} opts
        props (block-properties)]
    (cond-> props
      strength (apply with-strength (cons props strength))
      sound (with-sound sound)
      light-level (with-light-level light-level)
      requires-tool? requires-tool
      no-drops? no-drops)))

(comment
  ;; 使用示例

  ;; 1. 链式构建物品属性
  (def magic-wand-props
    (-> (item-properties)
        (with-stack-size 1)
        (with-durability 500)
        (with-rarity :rare)
        fireproof))

  ;; 2. 链式构建食物
  (def magic-apple-props
    (build-food-item
     {:nutrition 8
      :saturation 1.2
      :always-edible? true
      :rarity :epic}))

  ;; 3. 链式构建方块
  (def magic-stone-props
    (-> (block-properties)
        (with-strength 5.0 6.0)
        (with-sound :stone)
        (with-light-level 7)
        requires-tool))

  ;; 4. 构建 ItemStack
  (def enchanted-sword
    (-> (item-stack Items/DIAMOND_SWORD)
        (with-custom-name "Excalibur")
        (with-damage 0)))

  ;; 5. 快捷构建
  (def simple-item-props
    (build-simple-item
     {:stack-size 16
      :rarity :rare
      :fireproof? true})))
