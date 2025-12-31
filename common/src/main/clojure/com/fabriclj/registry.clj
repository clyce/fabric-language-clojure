(ns com.fabriclj.registry
  "fabric-language-clojure 注册表工具
   此命名空间封装了 Architectury API 的 DeferredRegister 系统，
   提供简洁的 Clojure DSL 用于注册游戏内容。

   用法示例:
   ```clojure
   (ns com.mymod.content
     (:require [com.fabriclj.registry :as reg])
     (:import [net.minecraft.world.item Item Item$Properties]))

   ;; 创建注册表
   (def items (reg/create-registry \"mymod\" :item))

   ;; 注册物品
   (def my-item
     (reg/register items \"my_item\"
       (fn [] (Item. (Item$Properties.)))))

   ;; 在 mod 初始化时调用
   (defn init []
     (reg/register-all! items))
   ```"
  (:import [dev.architectury.registry.registries DeferredRegister RegistrySupplier]
           [net.minecraft.core.registries Registries]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 注册表类型映射
;; ============================================================================

(def ^:private registry-types
  "注册表类型到 Minecraft 注册表的映射"
  {:item   Registries/ITEM
   :block  Registries/BLOCK
   :entity Registries/ENTITY_TYPE
   :block-entity Registries/BLOCK_ENTITY_TYPE
   :menu   Registries/MENU
   :recipe Registries/RECIPE_TYPE
   :sound  Registries/SOUND_EVENT
   :particle Registries/PARTICLE_TYPE
   :creative-tab Registries/CREATIVE_MODE_TAB})

;; ============================================================================
;; 注册表创建
;; ============================================================================

(defn create-registry
  "创建延迟注册表
   参数:
   - mod-id: 模组 ID
   - registry-type: 注册表类型关键字或 ResourceKey
     支持的关键字: :item, :block, :entity, :block-entity, :menu, :recipe, :sound, :particle, :creative-tab

   返回 DeferredRegister 实例

   用法:
   ```clojure
   (def items (create-registry \"mymod\" :item))
   (def blocks (create-registry \"mymod\" :block))
   ```"
  [^String mod-id registry-type]
  (let [registry-key (if (keyword? registry-type)
                       (get registry-types registry-type)
                       registry-type)]
    (when (nil? registry-key)
      (throw (IllegalArgumentException.
              (str "Unknown registry type: " registry-type
        ". Supported types: " (keys registry-types)))))
    (DeferredRegister/create mod-id registry-key)))

;; ============================================================================
;; 注册函数
;; ============================================================================

(defn register
  "向注册表注册一个条目
   参数:
   - registry: DeferredRegister 实例
   - name: 注册名称( 字符串)
   - supplier: 返回注册对象的函数

   返回 RegistrySupplier

   用法:
   ```clojure
   (def my-item
     (register items \"my_item\"
       (fn [] (Item. (Item$Properties.)))))
   ```"
  [^DeferredRegister registry ^String name supplier]
  (.register registry name
             (reify java.util.function.Supplier
               (get [_] (supplier)))))

(defn register-all!
  "执行注册表的注册
   必须在 mod 初始化阶段调用此函数。

   参数:
   - registries: 一个或多个 DeferredRegister 实例

   用法:
   ```clojure
   (register-all! items blocks entities)
   ```"
  [& registries]
  (doseq [^DeferredRegister registry registries]
    (.register registry)))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro defitem
  "定义并注册一个物品

   参数:
   - items-registry: DeferredRegister 实例
   - var-name: 变量名( 符号)
   - item-form: 返回 Item 实例的表达式

   用法:
   ```clojure
   (defitem items my-sword
     (Item. (-> (Item$Properties.)
                (.stacksTo 1))))
   ```"
  [items-registry var-name item-form]
  `(def ~var-name
     (register ~items-registry ~(name var-name)
               (fn [] ~item-form))))

(defmacro defblock
  "定义并注册一个方块

   参数:
   - blocks-registry: DeferredRegister 实例
   - var-name: 变量名( 符号)
   - block-form: 返回 Block 实例的表达式

   用法:
   ```clojure
   (defblock blocks my-ore
     (Block. (BlockBehaviour$Properties/of)))
   ```"
  [blocks-registry var-name block-form]
  `(def ~var-name
     (register ~blocks-registry ~(name var-name)
               (fn [] ~block-form))))
