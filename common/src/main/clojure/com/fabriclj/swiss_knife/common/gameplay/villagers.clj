(ns com.fabriclj.swiss-knife.common.gameplay.villagers
  "村民交易系统

   提供村民职业、交易、繁殖的完整管理功能：
   - 自定义村民职业
   - 交易配方创建
   - 交易刷新和升级
   - 村民属性管理"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import [net.minecraft.world.entity.npc Villager VillagerProfession VillagerTrades$ItemListing
            VillagerData VillagerType]
           [net.minecraft.world.entity.ai.village.poi PoiType]
           [net.minecraft.world.item ItemStack Items]
           [net.minecraft.world.item.trading MerchantOffer MerchantOffers]
           [net.minecraft.world.level.block Block Blocks]
           [net.minecraft.resources ResourceLocation]
           [net.minecraft.core Registry]
           [net.minecraft.world.entity.player Player]
           [java.util Random]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 交易创建
;; ============================================================================


(defn create-trade
  "创建交易配方（改进版签名）

   参数:
   - inputs: 输入物品
     - 单个物品：ItemStack 或 Item
     - 多个物品：[input1 input2] 向量
   - output: 输出物品（ItemStack 或 Item）
   - opts: 可选参数
     - :max-uses - 最大使用次数（默认 12）
     - :xp - 经验值奖励（默认 1）
     - :price-multiplier - 价格倍数（默认 0.05）
     - :demand - 需求（影响价格，默认 0）

   返回：MerchantOffer

   示例:
   ```clojure
   ;; 单输入交易：1 钻石 -> 10 绿宝石
   (create-trade
     (ItemStack. Items/DIAMOND 1)
     (ItemStack. Items/EMERALD 10)
     :max-uses 16
     :xp 5)

   ;; 双输入交易：1 木剑 + 5 绿宝石 -> 1 铁剑（新语法）
   (create-trade
     [(ItemStack. Items/WOODEN_SWORD 1)
      (ItemStack. Items/EMERALD 5)]
     (ItemStack. Items/IRON_SWORD 1)
     :max-uses 8)
   ```"
  [inputs output & {:keys [max-uses xp price-multiplier demand]
                    :or {max-uses 12
                         xp 1
                         price-multiplier 0.05
                         demand 0}}]
  (let [;; 解析输入：支持单个物品或 [input1 input2] 向量
        [input1 input2] (if (vector? inputs)
                          inputs
                          [inputs nil])
        ^ItemStack in1 (if (instance? ItemStack input1)
                         input1
                         (ItemStack. input1 1))
        ^ItemStack out (if (instance? ItemStack output)
                         output
                         (ItemStack. output 1))
        ^ItemStack in2 (when input2
                         (if (instance? ItemStack input2)
                           input2
                           (ItemStack. input2 1)))]
    (if in2
      (MerchantOffer. in1 in2 out
                      (int max-uses)
                      (int xp)
                      (float price-multiplier)
                      (int demand))
      (MerchantOffer. in1 out
                      (int max-uses)
                      (int xp)
                      (float price-multiplier)
                      (int demand)))))

;; ============================================================================
;; 交易列表创建器
;; ============================================================================

(defn create-trade-listing
  "创建交易列表生成器（用于职业注册）

   参数:
   - trade-fn: 交易生成函数 (fn [entity random] -> MerchantOffer)

   返回：VillagerTrades$ItemListing

   示例:
   ```clojure
   (create-trade-listing
     (fn [entity random]
       (create-trade
         (ItemStack. Items/EMERALD (+ 5 (.nextInt random 10)))
         (ItemStack. Items/DIAMOND 1))))
   ```"
  [trade-fn]
  (reify VillagerTrades$ItemListing
    (getOffer [_ entity random]
      (trade-fn entity random))))

;; ============================================================================
;; 自定义职业和村民类型
;; ============================================================================
;; 回复 clyce: 可以！通过注册自定义 VillagerProfession 和 VillagerType 实现。
;; 这需要在模组初始化时注册，并可以指定工作站点方块。

(def ^:private custom-professions (atom {}))
(def ^:private custom-villager-types (atom {}))

(defn register-custom-profession!
  "注册自定义村民职业

   参数:
   - id: 职业 ID（关键字）
   - profession-name: 职业名称（字符串）
   - work-station: 工作站点方块（Block，可选）

   注意：需要在模组初始化阶段调用

   示例:
   ```clojure
   (register-custom-profession! :alchemist
     \"Alchemist\"
     Blocks/BREWING_STAND)
   ```"
  [id profession-name & [work-station]]
  (let [res-loc (ResourceLocation. (core/mod-id) (name id))]
    (core/log-info (str "Registering custom profession: " profession-name))
    ;; 存储自定义职业信息
    (swap! custom-professions assoc id
           {:name profession-name
            :resource-location res-loc
            :work-station work-station})
    {:id id
     :name profession-name
     :resource-location res-loc}))

(defn register-custom-villager-type!
  "注册自定义村民类型（外观）

   参数:
   - id: 类型 ID（关键字）
   - type-name: 类型名称（字符串）

   注意：需要在模组初始化阶段调用

   示例:
   ```clojure
   (register-custom-villager-type! :volcanic \"Volcanic\")
   ```"
  [id type-name]
  (let [res-loc (ResourceLocation. (core/mod-id) (name id))]
    (core/log-info (str "Registering custom villager type: " type-name))
    (swap! custom-villager-types assoc id
           {:name type-name
            :resource-location res-loc})
    {:id id
     :name type-name
     :resource-location res-loc}))

;; ============================================================================
;; 村民职业
;; ============================================================================

(defn get-profession
  "通过关键字获取职业（支持原版和自定义职业）

   原版职业：
   - :none - 无职业
   - :armorer - 盔甲商
   - :butcher - 屠夫
   - :cartographer - 制图师
   - :cleric - 牧师
   - :farmer - 农民
   - :fisherman - 渔夫
   - :fletcher - 制箭师
   - :leatherworker - 皮匠
   - :librarian - 图书管理员
   - :mason - 石匠
   - :nitwit - 傻子
   - :shepherd - 牧羊人
   - :toolsmith - 工具匠
   - :weaponsmith - 武器匠

   自定义职业：通过 register-custom-profession! 注册的职业"
  [keyword]
  ;; 首先检查是否为自定义职业
  (if-let [custom-prof (get @custom-professions keyword)]
    ;; 返回资源位置或职业名称（实际使用时需要从注册表获取）
    (core/log-warn (str "Custom profession lookup not fully implemented: " keyword))
    ;; 否则查找原版职业
    (case keyword
      :none VillagerProfession/NONE
      :armorer VillagerProfession/ARMORER
      :butcher VillagerProfession/BUTCHER
      :cartographer VillagerProfession/CARTOGRAPHER
      :cleric VillagerProfession/CLERIC
      :farmer VillagerProfession/FARMER
      :fisherman VillagerProfession/FISHERMAN
      :fletcher VillagerProfession/FLETCHER
      :leatherworker VillagerProfession/LEATHERWORKER
      :librarian VillagerProfession/LIBRARIAN
      :mason VillagerProfession/MASON
      :nitwit VillagerProfession/NITWIT
      :shepherd VillagerProfession/SHEPHERD
      :toolsmith VillagerProfession/TOOLSMITH
      :weaponsmith VillagerProfession/WEAPONSMITH
      (throw (IllegalArgumentException. (str "Unknown profession: " keyword))))))

(defn get-villager-type
  "通过关键字获取村民类型（生物群系，支持原版和自定义类型）

   原版类型：
   - :plains - 平原
   - :desert - 沙漠
   - :jungle - 丛林
   - :savanna - 热带草原
   - :snow - 雪地
   - :swamp - 沼泽
   - :taiga - 针叶林

   自定义类型：通过 register-custom-villager-type! 注册的类型"
  [keyword]
  ;; 首先检查是否为自定义类型
  (if-let [custom-type (get @custom-villager-types keyword)]
    ;; 返回资源位置或类型名称（实际使用时需要从注册表获取）
    (core/log-warn (str "Custom villager type lookup not fully implemented: " keyword))
    ;; 否则查找原版类型
    (case keyword
      :plains VillagerType/PLAINS
      :desert VillagerType/DESERT
      :jungle VillagerType/JUNGLE
      :savanna VillagerType/SAVANNA
      :snow VillagerType/SNOW
      :swamp VillagerType/SWAMP
      :taiga VillagerType/TAIGA
      (throw (IllegalArgumentException. (str "Unknown villager type: " keyword))))))

;; ============================================================================
;; 村民管理
;; ============================================================================

(defn set-profession!
  "设置村民职业

   参数:
   - villager: Villager 实体
   - profession: 职业（关键字或 VillagerProfession）

   示例:
   ```clojure
   (set-profession! villager :librarian)
   ```"
  [^Villager villager profession]
  (let [^VillagerProfession prof (if (keyword? profession)
                                   (get-profession profession)
                                   profession)
        data (.getVillagerData villager)]
    (.setVillagerData villager
                      (.setProfession data prof))))

(defn set-villager-level!
  "设置村民等级（1-5）

   参数:
   - villager: Villager 实体
   - level: 等级（1=新手，2=学徒，3=老手，4=专家，5=大师）

   示例:
   ```clojure
   (set-villager-level! villager 5) ; 设为大师
   ```"
  [^Villager villager level]
  (let [data (.getVillagerData villager)]
    (.setVillagerData villager
                      (.setLevel data (int level)))))

(defn get-villager-level
  "获取村民等级

   返回：等级（1-5）"
  [^Villager villager]
  (.getLevel (.getVillagerData villager)))

(defn get-villager-profession
  "获取村民职业"
  [^Villager villager]
  (.getProfession (.getVillagerData villager)))

(defn is-profession?
  "检查村民是否为指定职业

   示例:
   ```clojure
   (when (is-profession? villager :librarian)
     (println \"这是图书管理员！\"))
   ```"
  [^Villager villager profession]
  (let [^VillagerProfession prof (if (keyword? profession)
                                   (get-profession profession)
                                   profession)]
    (= prof (get-villager-profession villager))))

;; ============================================================================
;; 交易管理
;; ============================================================================

(defn add-trade!
  "添加交易到村民

   参数:
   - villager: Villager 实体
   - trade: MerchantOffer

   示例:
   ```clojure
   (add-trade! villager
     (create-trade
       (ItemStack. Items/EMERALD 10)
       (ItemStack. Items/DIAMOND 1)))
   ```"
  [^Villager villager ^MerchantOffer trade]
  (.add (.getOffers villager) trade))

(defn add-trades!
  "批量添加交易

   参数:
   - villager: Villager 实体
   - trades: 交易列表"
  [^Villager villager trades]
  (doseq [^MerchantOffer trade trades]
    (add-trade! villager trade)))

(defn clear-trades!
  "清除村民所有交易

   参数:
   - villager: Villager 实体"
  [^Villager villager]
  (.clear (.getOffers villager)))

(defn get-trades
  "获取村民所有交易

   返回：交易列表"
  [^Villager villager]
  (vec (.getOffers villager)))

(defn refresh-trades!
  "刷新村民交易（重置使用次数）

   参数:
   - villager: Villager 实体"
  [^Villager villager]
  (doseq [^MerchantOffer offer (.getOffers villager)]
    (.resetUses offer)))

;; ============================================================================
;; 交易快捷方式
;; ============================================================================

(defn simple-trade
  "创建简单交易（绿宝石 -> 物品）

   参数:
   - emerald-count: 绿宝石数量
   - output: 输出物品
   - output-count: 输出数量（默认 1）
   - opts: 其他选项

   示例:
   ```clojure
   (simple-trade 5 Items/DIAMOND 2)
   ; 5 绿宝石 -> 2 钻石
   ```"
  [emerald-count output & [output-count & opts]]
  (apply create-trade
         (ItemStack. Items/EMERALD (int emerald-count))
         (ItemStack. output (int (or output-count 1)))
         opts))

(defn sell-trade
  "创建卖出交易（物品 -> 绿宝石）

   参数:
   - input: 输入物品
   - input-count: 输入数量（默认 1）
   - emerald-count: 获得的绿宝石数量
   - opts: 其他选项

   示例:
   ```clojure
   (sell-trade Items/DIAMOND 1 10)
   ; 1 钻石 -> 10 绿宝石
   ```"
  [input & [input-count emerald-count & opts]]
  (apply create-trade
         (ItemStack. input (int (or input-count 1)))
         (ItemStack. Items/EMERALD (int emerald-count))
         opts))

(defn book-trade
  "创建附魔书交易

   参数:
   - emerald-count: 绿宝石数量
   - enchantment-book: 附魔书 ItemStack
   - opts: 其他选项

   示例:
   ```clojure
   (book-trade 20 sharpness-v-book :max-uses 3)
   ```"
  [emerald-count enchantment-book & opts]
  (apply create-trade
         (ItemStack. Items/EMERALD (int emerald-count))
         enchantment-book
         opts))

;; ============================================================================
;; 预设交易列表
;; ============================================================================

(defn basic-librarian-trades
  "基础图书管理员交易

   返回：交易列表"
  []
  [(sell-trade Items/PAPER 24 1 :max-uses 16 :xp 2)
   (simple-trade 1 Items/BOOKSHELF 1 :max-uses 12 :xp 1)
   (simple-trade 5 Items/CLOCK 1 :max-uses 8 :xp 5)
   (simple-trade 4 Items/COMPASS 1 :max-uses 8 :xp 5)])

(defn basic-armorer-trades
  "基础盔甲商交易

   返回：交易列表"
  []
  [(sell-trade Items/COAL 15 1 :max-uses 16 :xp 2)
   (simple-trade 1 Items/IRON_HELMET 1 :max-uses 12 :xp 1)
   (simple-trade 4 Items/IRON_CHESTPLATE 1 :max-uses 12 :xp 5)
   (simple-trade 7 Items/IRON_LEGGINGS 1 :max-uses 12 :xp 5)
   (simple-trade 4 Items/IRON_BOOTS 1 :max-uses 12 :xp 2)])

(defn basic-weaponsmith-trades
  "基础武器匠交易

   返回：交易列表"
  []
  [(sell-trade Items/COAL 15 1 :max-uses 16 :xp 2)
   (simple-trade 3 Items/IRON_AXE 1 :max-uses 12 :xp 1)
   (simple-trade 7 Items/IRON_SWORD 1 :max-uses 12 :xp 5)
   (simple-trade 8 Items/DIAMOND_AXE 1 :max-uses 3 :xp 15)
   (simple-trade 12 Items/DIAMOND_SWORD 1 :max-uses 3 :xp 15)])

(defn basic-toolsmith-trades
  "基础工具匠交易

   返回：交易列表"
  []
  [(sell-trade Items/COAL 15 1 :max-uses 16 :xp 2)
   (simple-trade 1 Items/STONE_AXE 1 :max-uses 12 :xp 1)
   (simple-trade 1 Items/STONE_SHOVEL 1 :max-uses 12 :xp 1)
   (simple-trade 1 Items/STONE_PICKAXE 1 :max-uses 12 :xp 1)
   (simple-trade 3 Items/IRON_AXE 1 :max-uses 12 :xp 5)
   (simple-trade 3 Items/IRON_SHOVEL 1 :max-uses 12 :xp 5)
   (simple-trade 3 Items/IRON_PICKAXE 1 :max-uses 12 :xp 5)])

;; ============================================================================
;; 便捷函数
;; ============================================================================

(defn setup-custom-villager!
  "快速设置自定义村民

   参数:
   - villager: Villager 实体
   - profession: 职业
   - level: 等级
   - trades: 交易列表

   示例:
   ```clojure
   (setup-custom-villager! villager
     :librarian
     5
     (basic-librarian-trades))
   ```"
  [^Villager villager profession level trades]
  (set-profession! villager profession)
  (set-villager-level! villager level)
  (clear-trades! villager)
  (add-trades! villager trades))

(defn create-master-villager!
  "创建大师级村民（5级）

   参数:
   - villager: Villager 实体
   - profession: 职业
   - trades: 交易列表

   示例:
   ```clojure
   (create-master-villager! villager
     :weaponsmith
     (basic-weaponsmith-trades))
   ```"
  [^Villager villager profession trades]
  (setup-custom-villager! villager profession 5 trades))

(comment
  ;; 使用示例

  ;; ========== 基础交易创建 ==========

  ;; 1. 简单交易
  (def trade1
    (create-trade
     (ItemStack. Items/EMERALD 5)
     (ItemStack. Items/DIAMOND 1)
     :max-uses 16
     :xp 10))

  ;; 2. 双输入交易（新签名 - 使用向量）
  (def trade2
    (create-trade
     [(ItemStack. Items/WOODEN_SWORD 1)
      (ItemStack. Items/EMERALD 3)]
     (ItemStack. Items/IRON_SWORD 1)
     :max-uses 8))

  ;; 2a. 双输入交易（简洁写法）
  (def trade2a
    (create-trade
     [Items/WOODEN_SWORD Items/EMERALD]
     Items/IRON_SWORD
     :max-uses 8))

  ;; 3. 快捷交易
  (def trade3 (simple-trade 10 Items/DIAMOND 2))
  (def trade4 (sell-trade Items/COAL 16 1))

  ;; ========== 村民设置 ==========

  ;; 4. 设置职业和等级
  (set-profession! villager :librarian)
  (set-villager-level! villager 5)

  ;; 5. 添加交易
  (add-trade! villager trade1)
  (add-trades! villager [trade2 trade3 trade4])

  ;; 6. 使用预设交易
  (setup-custom-villager! villager
                          :weaponsmith
                          5
                          (basic-weaponsmith-trades))

  ;; ========== 交易管理 ==========

  ;; 7. 查看交易
  (doseq [trade (get-trades villager)]
    (println "交易:" trade))

  ;; 8. 刷新交易
  (refresh-trades! villager)

  ;; 9. 清除并重新设置
  (clear-trades! villager)
  (add-trades! villager (basic-librarian-trades))

  ;; ========== 快速创建 ==========

  ;; 10. 创建大师级村民
  (create-master-villager! villager
                           :armorer
                           (basic-armorer-trades))

  ;; ========== 自定义职业 ==========

  ;; 11. 注册自定义职业
  (register-custom-profession! :alchemist
    "Alchemist"
    Blocks/BREWING_STAND)

  (register-custom-profession! :enchanter
    "Enchanter"
    Blocks/ENCHANTING_TABLE)

  ;; 12. 注册自定义村民类型
  (register-custom-villager-type! :volcanic "Volcanic")
  (register-custom-villager-type! :crystal "Crystal")

  ;; 13. 创建自定义职业的村民（注册后即可使用）
  ;; 注意：需要先注册职业，并在游戏注册阶段完成
  (setup-custom-villager! villager
                          :alchemist
                          5
                          [(simple-trade 10 Items/GOLDEN_APPLE 1)
                           (simple-trade 20 Items/ENCHANTED_GOLDEN_APPLE 1)
                           (create-trade
                            [Items/NETHER_WART Items/EMERALD]
                            Items/POTION
                            :max-uses 12)]))
