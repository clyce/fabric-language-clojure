(ns com.fabriclj.swiss-knife.common.gameplay.advancements
  "进度系统 (Advancements)

   提供进度创建、授予和管理功能:
   - 进度创建和注册
   - 进度触发条件
   - 进度奖励
   - 进度树管理"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.advancements Advancement AdvancementRewards AdvancementProgress)
           (net.minecraft.server.level ServerPlayer)
           (net.minecraft.world.item ItemStack Items)
           (net.minecraft.resources ResourceLocation)
           (net.minecraft.server PlayerAdvancements)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 进度管理
;; ============================================================================

(defn grant-advancement!
  "授予玩家进度

   参数:
   - player: ServerPlayer
   - advancement-id: 进度 ID( ResourceLocation 或字符串)

   返回: boolean( 是否成功)

   示例:
   ```clojure
   (grant-advancement! player \"mymod:first_item\")
   (grant-advancement! player (ResourceLocation/fromNamespaceAndPath \"mymod\" \"boss_kill\"))
   ```"
  [^ServerPlayer player advancement-id]
  (let [^ResourceLocation res-loc (if (instance? ResourceLocation advancement-id)
                                    advancement-id
                                    (ResourceLocation/parse (str advancement-id)))
        advancements (.getAdvancements player)
        server (.getServer player)
        advancement (.getAdvancement (.getAdvancements server) res-loc)]
    (when advancement
      (let [progress (.getOrStartProgress advancements advancement)]
        (if (.isDone progress)
          false
          (do
            (doseq [criterion (.getRemainingCriteria progress)]
              (.grantCriterion advancements advancement criterion))
            true))))))

(defn revoke-advancement!
  "撤销玩家进度

   参数:
   - player: ServerPlayer
   - advancement-id: 进度 ID

   返回: boolean( 是否成功) "
  [^ServerPlayer player advancement-id]
  (let [^ResourceLocation res-loc (if (instance? ResourceLocation advancement-id)
                                    advancement-id
                                    (ResourceLocation/parse (str advancement-id)))
        advancements (.getAdvancements player)
        server (.getServer player)
        advancement (.getAdvancement (.getAdvancements server) res-loc)]
    (when advancement
      (let [progress (.getOrStartProgress advancements advancement)]
        (if-not (.isDone progress)
          false
          (do
            (doseq [criterion (.getCompletedCriteria progress)]
              (.revokeCriterion advancements advancement criterion))
            true))))))

(defn has-advancement?
  "检查玩家是否拥有进度

   参数:
   - player: ServerPlayer
   - advancement-id: 进度 ID

   返回: boolean"
  [^ServerPlayer player advancement-id]
  (let [^ResourceLocation res-loc (if (instance? ResourceLocation advancement-id)
                                    advancement-id
                                    (ResourceLocation/parse (str advancement-id)))
        advancements (.getAdvancements player)
        server (.getServer player)
        advancement (.getAdvancement (.getAdvancements server) res-loc)]
    (if advancement
      (let [progress (.getOrStartProgress advancements advancement)]
        (.isDone progress))
      false)))

(defn get-advancement-progress
  "获取进度进展

   参数:
   - player: ServerPlayer
   - advancement-id: 进度 ID

   返回: 进度信息 {:completed? boolean
                 :total-criteria int
                 :completed-criteria int
                 :progress-percentage float}"
  [^ServerPlayer player advancement-id]
  (let [^ResourceLocation res-loc (if (instance? ResourceLocation advancement-id)
                                    advancement-id
                                    (ResourceLocation/parse (str advancement-id)))
        advancements (.getAdvancements player)
        server (.getServer player)
        advancement (.getAdvancement (.getAdvancements server) res-loc)]
    (if advancement
      (let [progress (.getOrStartProgress advancements advancement)
            completed-count (.size (.getCompletedCriteria progress))
            total-count (+ completed-count (.size (.getRemainingCriteria progress)))]
        {:completed? (.isDone progress)
         :total-criteria total-count
         :completed-criteria completed-count
         :progress-percentage (if (zero? total-count)
                               0.0
                               (* 100.0 (/ completed-count total-count)))})
      {:completed? false
       :total-criteria 0
       :completed-criteria 0
       :progress-percentage 0.0})))

;; ============================================================================
;; 进度数据结构( 用于数据生成)
;; ============================================================================

(defn advancement-data
  "创建进度数据( 用于 JSON 生成)

   参数:
   - id: 进度 ID
   - opts: 选项
     - :parent - 父进度 ID
     - :display - 显示信息 {:icon :title :description :frame :background :show-toast? :announce-chat? :hidden?}
     - :criteria - 触发条件映射
     - :requirements - 需求( 默认所有条件)
     - :rewards - 奖励 {:experience :loot :recipes :function}

   返回: 进度数据映射

   示例:
   ```clojure
   (advancement-data \"mymod:first_diamond\"
     :parent \"minecraft:story/mine_stone\"
     :display {:icon (ItemStack. Items/DIAMOND)
               :title \"获得钻石！\"
               :description \"挖到你的第一颗钻石\"
               :frame :task
               :show-toast? true
               :announce-chat? false}
     :criteria {:has_diamond
                {:trigger \"minecraft:inventory_changed\"
                 :conditions {:items [{:items [\"minecraft:diamond\"]}]}}}
     :rewards {:experience 100})
   ```"
  [id & {:keys [parent display criteria requirements rewards]}]
  {:id (if (string? id)
         id
         (str (.getNamespace ^ResourceLocation id) "/" (.getPath ^ResourceLocation id)))
   :parent parent
   :display display
   :criteria criteria
   :requirements (or requirements
                     (when criteria
                       [(vec (keys criteria))]))
   :rewards rewards})

;; ============================================================================
;; 显示信息
;; ============================================================================

(defn display-info
  "创建进度显示信息

   参数:
   - icon: 图标( ItemStack)
   - title: 标题( Component 或字符串)
   - description: 描述( Component 或字符串)
   - opts: 选项
     - :frame - 框架类型( :task/:challenge/:goal，默认 :task)
     - :background - 背景纹理路径( 可选，仅根进度使用)
     - :show-toast? - 显示成就弹窗( 默认 true)
     - :announce-chat? - 在聊天中公告( 默认 true)
     - :hidden? - 是否隐藏( 默认 false)

   返回: 显示信息映射"
  [icon title description & {:keys [frame background show-toast? announce-chat? hidden?]
                             :or {frame :task
                                  show-toast? true
                                  announce-chat? true
                                  hidden? false}}]
  {:icon (if (instance? ItemStack icon)
           {:item (.toString (.getItem ^ItemStack icon))}
           {:item (str icon)})
   :title (if (string? title)
            {:text title}
            title)
   :description (if (string? description)
                  {:text description}
                  description)
   :frame (case frame
            :task "task"
            :challenge "challenge"
            :goal "goal"
            "task")
   :background background
   :show_toast show-toast?
   :announce_to_chat announce-chat?
   :hidden hidden?})

;; ============================================================================
;; 常用触发条件
;; ============================================================================

(defn inventory-changed-criterion
  "物品栏变化条件( 获得物品)

   参数:
   - items: 物品列表( 字符串 ID 列表)
   - opts: 选项
     - :min-count - 最小数量
     - :max-count - 最大数量

   示例:
   ```clojure
   (inventory-changed-criterion [\"minecraft:diamond\"]
     :min-count 1)
   ```"
  [items & {:keys [min-count max-count]}]
  {:trigger "minecraft:inventory_changed"
   :conditions {:items (vec (for [item items]
                              (cond-> {:items [item]}
                                min-count (assoc-in [:count :min] min-count)
                                max-count (assoc-in [:count :max] max-count))))}})

(defn location-criterion
  "位置条件( 进入特定位置)

   参数:
   - opts: 选项
     - :dimension - 维度( \"minecraft:overworld\"/\"minecraft:the_nether\"/\"minecraft:the_end\")
     - :biome - 生物群系
     - :structure - 结构
     - :x :y :z - 坐标范围 {:min :max}

   示例:
   ```clojure
   (location-criterion
     :dimension \"minecraft:the_nether\")

   (location-criterion
     :biome \"minecraft:desert\"
     :y {:min 60 :max 70})
   ```"
  [& {:keys [dimension biome structure x y z]}]
  {:trigger "minecraft:location"
   :conditions (cond-> {}
                 dimension (assoc :dimension dimension)
                 biome (assoc :biome biome)
                 structure (assoc :structure structure)
                 x (assoc-in [:position :x] x)
                 y (assoc-in [:position :y] y)
                 z (assoc-in [:position :z] z))})

(defn entity-hurt-player-criterion
  "玩家受伤条件

   参数:
   - opts: 选项
     - :damage-type - 伤害类型
     - :min-damage - 最小伤害
     - :max-damage - 最大伤害

   示例:
   ```clojure
   (entity-hurt-player-criterion
     :damage-type \"minecraft:explosion\"
     :min-damage 10.0)
   ```"
  [& {:keys [damage-type min-damage max-damage]}]
  {:trigger "minecraft:entity_hurt_player"
   :conditions (cond-> {}
                 damage-type (assoc-in [:damage :type] damage-type)
                 min-damage (assoc-in [:damage :dealt :min] min-damage)
                 max-damage (assoc-in [:damage :dealt :max] max-damage))})

(defn killed-entity-criterion
  "击杀实体条件

   参数:
   - entity-type: 实体类型( \"minecraft:zombie\" 等)
   - opts: 选项
     - :killed-by-player? - 是否由玩家击杀( 默认 true)

   示例:
   ```clojure
   (killed-entity-criterion \"minecraft:ender_dragon\")
   (killed-entity-criterion \"minecraft:wither\")
   ```"
  [entity-type & {:keys [killed-by-player?] :or {killed-by-player? true}}]
  {:trigger "minecraft:player_killed_entity"
   :conditions {:entity {:type entity-type}
                :killing_blow (when killed-by-player?
                                {:direct_entity {:type "minecraft:player"}})}})

;; ============================================================================
;; 进度定义宏 (defadvancement)
;; ============================================================================

(defmacro defadvancement
  "定义进度的宏( 简化版)

   参数:
   - name: 变量名
   - id: 进度 ID( 字符串)
   - icon: 图标物品
   - title: 标题
   - description: 描述
   - opts: 选项
     - :parent - 父进度 ID
     - :frame - 框架类型( :task/:challenge/:goal)
     - :criteria - 触发条件
     - :rewards - 奖励
     - :background - 背景纹理
     - :show-toast? - 显示弹窗
     - :announce-chat? - 聊天公告
     - :hidden? - 是否隐藏

   示例:
   ```clojure
   (defadvancement my-first-diamond
     \"mymod:first_diamond\"
     Items/DIAMOND
     \"获得钻石！\"
     \"挖到你的第一颗钻石\"
     :parent \"minecraft:story/mine_stone\"
     :frame :task
     :criteria {:has_diamond (inventory-changed-criterion [\"minecraft:diamond\"])}
     :rewards {:experience 100})
   ```"
  [name id icon title description & opts]
  (let [opts-map (apply hash-map opts)
        parent (:parent opts-map)
        frame (:frame opts-map :task)
        criteria (:criteria opts-map)
        rewards (:rewards opts-map)
        background (:background opts-map)
        show-toast? (:show-toast? opts-map true)
        announce-chat? (:announce-chat? opts-map true)
        hidden? (:hidden? opts-map false)]
    `(def ~name
       (advancement-data ~id
         ~@(when parent [:parent parent])
         :display (display-info
                    (ItemStack. ~icon)
                    ~title
                    ~description
                    :frame ~frame
                    ~@(when background [:background background])
                    :show-toast? ~show-toast?
                    :announce-chat? ~announce-chat?
                    :hidden? ~hidden?)
         ~@(when criteria [:criteria criteria])
         ~@(when rewards [:rewards rewards])))))

(defmacro defadvancement-root
  "定义根进度( 带背景图)

   参数:
   - name: 变量名
   - id: 进度 ID
   - icon: 图标物品
   - title: 标题
   - description: 描述
   - background: 背景纹理路径

   示例:
   ```clojure
   (defadvancement-root my-mod-root
     \"mymod:root\"
     Items/GRASS_BLOCK
     \"我的模组\"
     \"开始你的旅程\"
     \"minecraft:textures/gui/advancements/backgrounds/adventure.png\")
   ```"
  [name id icon title description background]
  `(def ~name
     (advancement-data ~id
       :display (display-info
                  (ItemStack. ~icon)
                  ~title
                  ~description
                  :frame :task
                  :background ~background
                  :show-toast? false
                  :announce-chat? false)
       :criteria {:auto {:trigger "minecraft:tick"}})))

(defmacro defadvancement-challenge
  "定义挑战进度( 高亮显示，有聊天公告)

   参数:
   - name: 变量名
   - id: 进度 ID
   - icon: 图标物品
   - title: 标题
   - description: 描述
   - parent: 父进度 ID
   - criteria: 触发条件
   - opts: 额外选项

   示例:
   ```clojure
   (defadvancement-challenge defeat-dragon
     \"mymod:defeat_dragon\"
     Items/DRAGON_HEAD
     \"击败末影龙\"
     \"战胜末地的统治者\"
     \"mymod:enter_end\"
     {:killed (killed-entity-criterion \"minecraft:ender_dragon\")}
     :rewards {:experience 1000})
   ```"
  [name id icon title description parent criteria & opts]
  (let [opts-map (apply hash-map opts)
        rewards (:rewards opts-map)]
    `(def ~name
       (advancement-data ~id
         :parent ~parent
         :display (display-info
                    (ItemStack. ~icon)
                    ~title
                    ~description
                    :frame :challenge
                    :announce-chat? true)
         :criteria ~criteria
         ~@(when rewards [:rewards rewards])))))

;; ============================================================================
;; 预设进度示例
;; ============================================================================

(defn example-advancement-tree
  "示例进度树

   返回: 进度数据列表"
  []
  [(advancement-data "mymod:root"
     :display (display-info
                (ItemStack. Items/GRASS_BLOCK)
                "我的模组"
                "欢迎来到我的模组！"
                :frame :task
                :background "minecraft:textures/gui/advancements/backgrounds/stone.png"
                :show-toast? false
                :announce-chat? false)
     :criteria {:auto {:trigger "minecraft:tick"}})

   (advancement-data "mymod:first_item"
     :parent "mymod:root"
     :display (display-info
                (ItemStack. Items/DIAMOND)
                "获得第一个物品"
                "获得模组中的任意物品"
                :frame :task)
     :criteria {:has_item (inventory-changed-criterion ["mymod:custom_item"])}
     :rewards {:experience 50})

   (advancement-data "mymod:master"
     :parent "mymod:first_item"
     :display (display-info
                (ItemStack. Items/NETHER_STAR)
                "成为大师"
                "完成所有挑战"
                :frame :challenge)
     :criteria {:all_done (inventory-changed-criterion ["mymod:master_item"])}
     :rewards {:experience 1000})])

(comment
  ;; 使用示例

  ;; ========== 进度管理 ==========

  ;; 1. 授予进度
  (grant-advancement! player "mymod:first_diamond")
  (grant-advancement! player "minecraft:story/mine_stone")

  ;; 2. 检查进度
  (when (has-advancement? player "mymod:first_diamond")
    (println "玩家已获得钻石！"))

  ;; 3. 获取进度进展
  (let [progress (get-advancement-progress player "mymod:boss_kill")]
    (println "完成度: " (:progress-percentage progress) "%"))

  ;; 4. 撤销进度
  (revoke-advancement! player "mymod:first_diamond")

  ;; ========== 进度数据生成 ==========

  ;; 5. 创建简单进度
  (def my-advancement
    (advancement-data "mymod:first_diamond"
      :parent "minecraft:story/mine_stone"
      :display (display-info
                 (ItemStack. Items/DIAMOND)
                 "获得钻石！"
                 "挖到你的第一颗钻石"
                 :frame :task)
      :criteria {:has_diamond
                 (inventory-changed-criterion ["minecraft:diamond"])}
      :rewards {:experience 100}))

  ;; 6. 创建位置进度
  (def nether-advancement
    (advancement-data "mymod:enter_nether"
      :parent "mymod:root"
      :display (display-info
                 (ItemStack. Items/NETHERRACK)
                 "进入下界"
                 "穿过下界传送门"
                 :frame :task)
      :criteria {:entered (location-criterion
                            :dimension "minecraft:the_nether")}))

  ;; 7. 创建挑战进度
  (def dragon-advancement
    (advancement-data "mymod:kill_dragon"
      :parent "mymod:enter_end"
      :display (display-info
                 (ItemStack. Items/DRAGON_HEAD)
                 "击败末影龙"
                 "击败末地的统治者"
                 :frame :challenge
                 :announce-chat? true)
      :criteria {:killed (killed-entity-criterion "minecraft:ender_dragon")}
      :rewards {:experience 1000}))

  ;; 8. 创建进度树
  (def my-advancement-tree (example-advancement-tree))

  ;; ========== 使用 defadvancement 宏 ==========

  ;; 9. 使用 defadvancement 宏( 更简洁)
  (defadvancement my-first-diamond
    "mymod:first_diamond"
    Items/DIAMOND
    "获得钻石！"
    "挖到你的第一颗钻石"
    :parent "minecraft:story/mine_stone"
    :frame :task
    :criteria {:has_diamond (inventory-changed-criterion ["minecraft:diamond"])}
    :rewards {:experience 100})

  ;; 10. 使用 defadvancement-root( 定义根进度)
  (defadvancement-root my-mod-root
    "mymod:root"
    Items/GRASS_BLOCK
    "我的模组"
    "开始你的冒险之旅"
    "minecraft:textures/gui/advancements/backgrounds/adventure.png")

  ;; 11. 使用 defadvancement-challenge( 定义挑战)
  (defadvancement-challenge defeat-wither
    "mymod:defeat_wither"
    Items/NETHER_STAR
    "击败凋零"
    "召唤并击败凋零"
    "mymod:enter_nether"
    {:killed (killed-entity-criterion "minecraft:wither")}
    :rewards {:experience 500})

  ;; 12. 定义进度树( 使用宏)
  (defadvancement-root my-adventure-root
    "mymod:adventure_root"
    Items/COMPASS
    "冒险开始"
    "踏上新的旅程"
    "minecraft:textures/gui/advancements/backgrounds/adventure.png")

  (defadvancement first-exploration
    "mymod:first_exploration"
    Items/MAP
    "探索世界"
    "走出你的出生点"
    :parent "mymod:adventure_root"
    :criteria {:walked (location-criterion :x {:min 100})}
    :rewards {:experience 10})

  (defadvancement-challenge ultimate-explorer
    "mymod:ultimate_explorer"
    Items/DRAGON_HEAD
    "终极探险家"
    "访问所有维度"
    "mymod:first_exploration"
    {:all_dimensions (location-criterion)}  ; 实际需要更复杂的条件
    :rewards {:experience 2000}))
