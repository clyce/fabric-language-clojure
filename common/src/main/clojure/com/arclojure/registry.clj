(ns com.arclojure.registry
  "Arclojure 内容注册命名空间

   此命名空间封装了 Architectury API 的 DeferredRegister 系统，
   提供简洁的 Clojure DSL 用于注册游戏内容。"
  (:import [dev.architectury.registry.registries DeferredRegister RegistrySupplier]
           [net.minecraft.core.registries Registries]
           [net.minecraft.world.item Item Item$Properties CreativeModeTabs]
           [net.minecraft.world.level.block Block]
           [net.minecraft.world.level.block.state BlockBehaviour$Properties]
           [com.arclojure ModMain]))

;; 模组 ID（直接引用 Java 层定义，保证单一数据源）
(def ^:const mod-id ModMain/MOD_ID)

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 注册表定义
;; ============================================================================

(def ^DeferredRegister items
  "物品注册表"
  (DeferredRegister/create mod-id Registries/ITEM))

(def ^DeferredRegister blocks
  "方块注册表"
  (DeferredRegister/create mod-id Registries/BLOCK))

;; ============================================================================
;; 注册辅助宏
;; ============================================================================

(defmacro defitem
  "定义并注册一个物品

   用法：
   (defitem example-item
     (Item. (-> (Item$Properties.)
                (.stacksTo 16))))"
  [name item-form]
  `(def ~name
     (.register items ~(clojure.core/name name)
                (reify java.util.function.Supplier
                  (get [_] ~item-form)))))

(defmacro defblock
  "定义并注册一个方块

   用法：
   (defblock example-block
     (Block. (BlockBehaviour$Properties/of)))"
  [name block-form]
  `(def ~name
     (.register blocks ~(clojure.core/name name)
                (reify java.util.function.Supplier
                  (get [_] ~block-form)))))

;; ============================================================================
;; 示例内容（可删除或修改）
;; ============================================================================

;; 示例物品定义
;; (defitem example-item
;;   (Item. (-> (Item$Properties.)
;;              (.stacksTo 64))))

;; ============================================================================
;; 注册执行
;; ============================================================================

(defn register-all!
  "执行所有注册表的注册

   必须在模组初始化阶段调用此函数。"
  []
  (println "[Arclojure/Registry] Registering items...")
  (.register items)

  (println "[Arclojure/Registry] Registering blocks...")
  (.register blocks)

  (println "[Arclojure/Registry] All registrations complete!"))
