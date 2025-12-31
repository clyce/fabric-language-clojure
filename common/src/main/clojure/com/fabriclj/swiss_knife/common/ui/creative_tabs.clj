(ns com.fabriclj.swiss-knife.common.ui.creative-tabs
  "瑞士军刀 - 创造模式标签模块

   封装创造模式物品栏标签页的创建和管理。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.registry :as reg])
  (:import [net.minecraft.world.item CreativeModeTab CreativeModeTab$Builder Item ItemStack]
           [net.minecraft.network.chat Component]
           [net.minecraft.resources ResourceLocation]
           [net.minecraft.core.registries Registries]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 标签页创建
;; ============================================================================

(defn creative-tab-builder
  "创建创造模式标签页构建器

   参数:
   - title: 标题（字符串或 Component）
   - icon: 图标物品或物品栈

   返回：CreativeModeTab$Builder

   示例:
   ```clojure
   (-> (creative-tab-builder \"My Items\" my-item)
       (tab-display-items! [item1 item2 item3])
       (build-tab))
   ```"
  ^CreativeModeTab$Builder [title icon]
  (let [builder (CreativeModeTab/builder)
        title-component (if (string? title)
                          (Component/literal title)
                          title)
        icon-stack (if (instance? ItemStack icon)
                     icon
                     (ItemStack. ^Item icon))]
    (-> builder
        (.title title-component)
        (.icon (reify java.util.function.Supplier
                 (get [_] icon-stack))))))

(defn tab-display-items!
  "设置标签页显示的物品

   参数:
   - builder: CreativeModeTab$Builder
   - items: 物品列表（Item 或 ItemStack）

   返回：builder（用于链式调用）"
  ^CreativeModeTab$Builder [^CreativeModeTab$Builder builder items]
  (.displayItems builder
                 (reify net.minecraft.world.item.CreativeModeTab$DisplayItemsGenerator
                   (accept [_ params output]
                     (doseq [item items]
                       (if (instance? ItemStack item)
                         (.accept output item)
                         (.accept output (ItemStack. ^Item item))))))))

(defn build-tab
  "构建创造模式标签页

   返回：CreativeModeTab"
  ^CreativeModeTab [^CreativeModeTab$Builder builder]
  (.build builder))

;; ============================================================================
;; 便捷注册
;; ============================================================================

(defn register-creative-tab!
  "注册创造模式标签页

   参数:
   - registry: 创造标签注册表
   - id: 标签 ID（字符串）
   - title: 标题
   - icon: 图标
   - items: 物品列表

   返回：RegistrySupplier

   示例:
   ```clojure
   (def tabs (reg/create-registry \"mymod\" :creative-tab))

   (register-creative-tab! tabs \"main_tab\"
     \"My Mod Items\"
     my-icon-item
     [item1 item2 item3])

   (reg/register-all! tabs)
   ```"
  [registry id title icon items]
  (reg/register registry id
                (fn []
                  (-> (creative-tab-builder title icon)
                      (tab-display-items! items)
                      (build-tab)))))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro defcreative-tab
  "定义创造模式标签页（语法糖）

   示例:
   ```clojure
   (defcreative-tab tabs my-tab \"mymod:main\"
     :title \"My Items\"
     :icon my-item
     :items [item1 item2 item3])
   ```"
  [registry name id & {:keys [title icon items]}]
  `(def ~name
     (register-creative-tab! ~registry ~id ~title ~icon ~items)))

(comment
  ;; 使用示例

  ;; 创建注册表
  (def tabs (reg/create-registry "mymod" :creative-tab))

  ;; 方式 1：使用函数
  (def my-tab
    (register-creative-tab! tabs "main_tab"
                            "My Mod Items"
                            my-icon-item
                            [sword gem armor]))

  ;; 方式 2：使用宏
  (defcreative-tab tabs weapons-tab "weapons"
    :title "Weapons"
    :icon magic-sword
    :items [sword1 sword2 axe1])

  ;; 注册所有标签
  (reg/register-all! tabs))
