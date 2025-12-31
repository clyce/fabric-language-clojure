(ns com.fabriclj.swiss-knife.common.registry.core
  "瑞士军刀 - 高级注册系统

   扩展 com.fabriclj.registry，提供更高级的注册功能和便捷宏。
   基于 Architectury API 的 DeferredRegister。"
  (:require [com.fabriclj.registry :as base-reg]
            [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.world.item Item Item$Properties)
           (net.minecraft.world.level.block Block)
           (net.minecraft.world.level.block.state BlockBehaviour$Properties)
           (net.minecraft.network.chat Component)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 重导出基础功能
;; ============================================================================

(def create-registry base-reg/create-registry)
(def register base-reg/register)
(def register-all! base-reg/register-all!)

;; ============================================================================
;; 物品属性构建器
;; ============================================================================

(defn item-properties
  "创建物品属性构建器

   支持的选项:
   - :stack-size n - 最大堆叠数量( 默认 64)
   - :durability n - 耐久度
   - :fire-resistant true - 抗火
   - :rarity :common/:uncommon/:rare/:epic - 稀有度
   - :food food-properties - 食物属性
   - :craft-remainder item - 合成剩余物

   示例:
   ```clojure
   (item-properties
     :stack-size 16
     :durability 250
     :fire-resistant true
     :rarity :rare)
   ```"
  [& {:keys [stack-size durability fire-resistant rarity food craft-remainder]
      :or {stack-size 64}}]
  (let [^Item$Properties props (Item$Properties.)]
    (when stack-size
      (.stacksTo props stack-size))
    (when durability
      (.durability props durability))
    (when fire-resistant
      (.fireResistant props))
    (when rarity
      (let [rarity-enum (case rarity
                          :common net.minecraft.world.item.Rarity/COMMON
                          :uncommon net.minecraft.world.item.Rarity/UNCOMMON
                          :rare net.minecraft.world.item.Rarity/RARE
                          :epic net.minecraft.world.item.Rarity/EPIC
                          rarity)]
        (.rarity props rarity-enum)))
    (when food
      (.food props food))
    (when craft-remainder
      (.craftRemainder props craft-remainder))
    props))

;; ============================================================================
;; 方块属性构建器
;; ============================================================================

(defn block-properties
  "创建方块属性构建器

   支持的选项:
   - :strength hardness resistance - 硬度和爆炸抗性
   - :no-collision true - 无碰撞箱
   - :no-occlusion true - 不遮挡
   - :friction f - 摩擦系数
   - :light-level fn - 光照等级函数
   - :sound-type type - 音效类型 (:wood/:stone/:metal/:grass/:gravel 等)
   - :requires-correct-tool true - 需要正确工具
   - :drops-nothing true - 不掉落物品

   示例:
   ```clojure
   (block-properties
     :strength 3.0 3.0
     :sound-type :metal
     :requires-correct-tool true
     :light-level (constantly 15))
   ```"
  [& {:keys [strength no-collision no-occlusion friction
             light-level sound-type requires-correct-tool drops-nothing]}]
  (let [^BlockBehaviour$Properties props
        (BlockBehaviour$Properties/of)]
    (when strength
      (let [[hardness resistance] (if (sequential? strength)
                                    strength
                                    [strength strength])]
        (.strength props hardness resistance)))
    (when no-collision
      (.noCollission props))
    (when no-occlusion
      (.noOcclusion props))
    (when friction
      (.friction props friction))
    (when light-level
      (.lightLevel props
                   (reify java.util.function.ToIntFunction
                     (applyAsInt [_ state]
                       (light-level state)))))
    (when sound-type
      (let [sound (case sound-type
                    :wood net.minecraft.world.level.block.SoundType/WOOD
                    :stone net.minecraft.world.level.block.SoundType/STONE
                    :metal net.minecraft.world.level.block.SoundType/METAL
                    :glass net.minecraft.world.level.block.SoundType/GLASS
                    :grass net.minecraft.world.level.block.SoundType/GRASS
                    :gravel net.minecraft.world.level.block.SoundType/GRAVEL
                    :sand net.minecraft.world.level.block.SoundType/SAND
                    :snow net.minecraft.world.level.block.SoundType/SNOW
                    :wool net.minecraft.world.level.block.SoundType/WOOL
                    sound-type)]
        (.sound props sound)))
    (when requires-correct-tool
      (.requiresCorrectToolForDrops props))
    (when drops-nothing
      (.noLootTable props))
    props))

;; ============================================================================
;; 便捷注册宏
;; ============================================================================

(defmacro defitem
  "定义并注册物品

   参数:
   - registry: 物品注册表
   - name: 物品名称( 符号)
   - item-or-props: Item 实例或 Item$Properties

   示例:
   ```clojure
   (defitem items magic-sword
     (Item. (item-properties :stack-size 1 :rarity :rare)))
   ```"
  [registry name item-or-props]
  (let [name-str (clojure.core/name name)]
    `(def ~name
       (register ~registry ~name-str
                 (fn []
                   (let [obj# ~item-or-props]
                     (if (instance? Item obj#)
                       obj#
                       (Item. obj#))))))))

(defmacro defblock
  "定义并注册方块

   参数:
   - registry: 方块注册表
   - name: 方块名称( 符号)
   - block-or-props: Block 实例或 Block$Properties

   示例:
   ```clojure
   (defblock blocks magic-ore
     (Block. (block-properties :strength 3.0 :sound-type :stone)))
   ```"
  [registry name block-or-props]
  (let [name-str (clojure.core/name name)]
    `(def ~name
       (register ~registry ~name-str
                 (fn []
                   (let [obj# ~block-or-props]
                     (if (instance? Block obj#)
                       obj#
                       (Block. obj#))))))))

(defmacro defblock-item
  "同时注册方块和对应的方块物品

   参数:
   - block-registry: 方块注册表
   - item-registry: 物品注册表
   - name: 名称( 符号)
   - block-props: 方块属性
   - item-props: 物品属性( 可选)

   返回: {:block block-var :item item-var}

   示例:
   ```clojure
   (defblock-item blocks items magic-ore
     (block-properties :strength 3.0)
     (item-properties))
   ```"
  ([block-registry item-registry name block-props]
   `(defblock-item ~block-registry ~item-registry ~name ~block-props (Item$Properties.)))
  ([block-registry item-registry name block-props item-props]
   (let [name-str (clojure.core/name name)
         block-sym (symbol (str name "-block"))
         item-sym (symbol (str name "-item"))]
     `(do
        (def ~block-sym
          (register ~block-registry ~name-str
                    (fn []
                      (let [props# ~block-props]
                        (if (instance? Block props#)
                          props#
                          (Block. props#))))))
        (def ~item-sym
          (register ~item-registry ~name-str
                    (fn []
                      (net.minecraft.world.item.BlockItem.
                       (.get ~block-sym)
                       ~item-props))))
        {:block (var ~block-sym)
         :item (var ~item-sym)}))))

;; ============================================================================
;; 批量注册
;; ============================================================================

(defn register-items
  "批量注册物品

   参数:
   - registry: 物品注册表
   - items-map: {名称 item-fn} 映射

   示例:
   ```clojure
   (register-items items
     {\"sword\" #(Item. (item-properties :stack-size 1))
      \"gem\" #(Item. (item-properties))})
   ```"
  [registry items-map]
  (into {}
        (map (fn [[name item-fn]]
               [(keyword name)
                (register registry name item-fn)])
             items-map)))

(defn register-blocks
  "批量注册方块

   参数:
   - registry: 方块注册表
   - blocks-map: {名称 block-fn} 映射"
  [registry blocks-map]
  (into {}
        (map (fn [[name block-fn]]
               [(keyword name)
                (register registry name block-fn)])
             blocks-map)))

;; ============================================================================
;; 注册表查询
;; ============================================================================

(defonce ^:private registries (atom {}))

(defn track-registry!
  "跟踪注册表( 用于后续查询)

   参数:
   - key: 注册表标识符( 关键字)
   - registry: DeferredRegister 实例

   示例:
   ```clojure
   (track-registry! :items items)
   ```"
  [key registry]
  (swap! registries assoc key registry)
  registry)

(defn get-tracked-registry
  "获取已跟踪的注册表"
  [key]
  (get @registries key))

(defn list-tracked-registries
  "列出所有已跟踪的注册表"
  []
  (keys @registries))

;; ============================================================================
;; 高级工具
;; ============================================================================

(defmacro with-registry
  "在注册表上下文中执行代码

   自动处理注册表的创建和注册。

   示例:
   ```clojure
   (with-registry [items (create-registry \"mymod\" :item)]
     (defitem items my-sword (Item. (item-properties)))
     (defitem items my-gem (Item. (item-properties))))
   ```"
  [[binding-name init-form] & body]
  `(let [~binding-name ~init-form]
     ~@body
     (register-all! ~binding-name)
     ~binding-name))

(defn simple-item
  "创建简单物品( 无特殊功能)

   参数:
   - opts: 物品属性选项( 传递给 item-properties)

   示例:
   ```clojure
   (register items \"gem\" #(simple-item :stack-size 64))
   ```"
  [& opts]
  (Item. (apply item-properties opts)))

(defn simple-block
  "创建简单方块( 无特殊功能)

   参数:
   - opts: 方块属性选项( 传递给 block-properties)

   示例:
   ```clojure
   (register blocks \"ore\" #(simple-block :strength 3.0))
   ```"
  [& opts]
  (Block. (apply block-properties opts)))

;; ============================================================================
;; 数据驱动注册
;; ============================================================================

(defn register-from-edn
  "从 EDN 数据注册物品/方块

   参数:
   - registry: 注册表
   - registry-type: :item 或 :block
   - edn-data: EDN 数据( 映射)

   EDN 格式示例:
   ```clojure
   {:items
     {:magic-sword {:type :item
                    :stack-size 1
                    :rarity :rare}
      :magic-ore {:type :block
                  :strength 3.0
                  :sound-type :stone}}
   ```"
  [registry registry-type edn-data]
  (doseq [[item-name props] edn-data]
    (let [name-str (name item-name)]
      (register registry name-str
                (fn []
                  (case registry-type
                    :item (simple-item props)
                    :block (simple-block props)
                    (throw (IllegalArgumentException.
                            (str "Unknown registry type: " registry-type)))))))))

(comment
  ;; 使用示例

  ;; 创建注册表
  (def items (create-registry "mymod" :item))
  (def blocks (create-registry "mymod" :block))

  ;; 注册简单物品
  (defitem items magic-gem
    (simple-item :stack-size 64 :rarity :rare))

  ;; 注册工具物品
  (defitem items magic-sword
    (Item. (item-properties
            :stack-size 1
            :durability 500
            :rarity :epic)))

  ;; 注册方块
  (defblock blocks magic-ore
    (simple-block
     :strength 3.0 5.0
     :sound-type :stone
     :requires-correct-tool true))

  ;; 同时注册方块和物品
  (defblock-item blocks items enchanted-block
    (block-properties :strength 5.0 :light-level (constantly 15))
    (item-properties :rarity :rare))

  ;; 批量注册
  (register-items items
                  {"ruby" #(simple-item :rarity :rare)
                   "sapphire" #(simple-item :rarity :rare)
                   "emerald" #(simple-item :rarity :rare)})

  ;; 执行所有注册
  (register-all! items blocks))
