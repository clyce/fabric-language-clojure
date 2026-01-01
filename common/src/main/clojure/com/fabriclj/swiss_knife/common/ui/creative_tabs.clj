(ns com.fabriclj.swiss-knife.common.ui.creative-tabs
  "瑞士军刀 - 创造模式标签模块

   封装创造模式物品栏标签页的创建和管理。

   完全使用 Architectury API，提供跨平台支持（Fabric/Forge）。
   Minecraft 1.21+ 中，Creative Tabs 已作为标准注册表项。
   使用 arch$tab 方法在物品创建时指定标签页，这是 Architectury 推荐的方式。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.registry :as reg])
  (:import (net.minecraft.world.item CreativeModeTab Item ItemStack Item$Properties)
           (dev.architectury.registry CreativeTabRegistry)
           (net.minecraft.network.chat Component)
           (net.minecraft.resources ResourceLocation)
           (net.minecraft.core.registries Registries)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 标签页创建（使用 Architectury API）
;; ============================================================================

(defn create-tab-icon-supplier
  "创建图标 Supplier，处理 RegistrySupplier

   参数:
   - icon: Item、ItemStack 或 RegistrySupplier

   返回: Supplier<ItemStack>"
  [icon]
  (reify java.util.function.Supplier
    (get [_]
      (let [;; 处理 RegistrySupplier：调用 .get() 获取实际对象
            actual-icon (if (instance? dev.architectury.registry.registries.RegistrySupplier icon)
                         (.get ^dev.architectury.registry.registries.RegistrySupplier icon)
                         icon)]
        (if (instance? ItemStack actual-icon)
          actual-icon
          (ItemStack. ^Item actual-icon))))))

(defn create-tab
  "使用 Architectury API 创建创造模式标签页

   参数:
   - title: 标题（字符串或 Component）
   - icon: 图标（Item、ItemStack 或 RegistrySupplier）

   返回: CreativeModeTab

   示例:
   ```clojure
   (create-tab \"My Items\" my-icon-item)
   ```"
  [title icon]
  (let [title-component (if (string? title)
                          (Component/literal title)
                          title)
        icon-supplier (create-tab-icon-supplier icon)]
    (CreativeTabRegistry/create title-component icon-supplier)))

;; ============================================================================
;; 便捷注册
;; ============================================================================

(defn register-creative-tab!
  "注册创造模式标签页（完全使用 Architectury API，跨平台支持）

   参数:
   - registry: 创造标签注册表
   - mod-id: 模组 ID（字符串，如 \"example\"）
   - id: 标签 ID（字符串，如 \"main_tab\"）
   - title: 标题
   - icon: 图标（Item、ItemStack 或 RegistrySupplier）

   返回: RegistrySupplier<CreativeModeTab>

   注意: 物品需要在创建时使用 arch$tab 方法指定此标签页。
   示例见 with-tab 函数。

   示例:
   ```clojure
   (def tabs (reg/create-registry \"mymod\" :creative-tab))

   (def my-tab
     (register-creative-tab! tabs \"mymod\" \"main_tab\"
       \"My Mod Items\"
       my-icon-item))

   ;; 在创建物品时指定标签页
   (def my-item
     (reg/register items \"my_item\"
       (fn []
         (Item. (with-tab (Item$Properties.) my-tab)))))

   (reg/register-all! tabs items)
   ```"
  [registry mod-id id title icon]
  (reg/register registry id
                (fn []
                  (create-tab title icon))))

;; ============================================================================
;; 物品属性辅助函数
;; ============================================================================

(defn with-tab
  "为 Item.Properties 指定创造模式标签页（使用 Architectury 的 arch$tab 方法）

   参数:
   - props: Item$Properties
   - tab: CreativeModeTab 或 RegistrySupplier<CreativeModeTab>

   返回: Item$Properties（已设置标签页）

   注意: 如果 tab 是 RegistrySupplier，会延迟解析直到 Item 实际创建时。
   这允许在标签页注册之前定义物品。

   示例:
   ```clojure
   (def my-item
     (reg/register items \"my_item\"
       (fn []
         (Item. (with-tab (Item$Properties.) my-tab)))))
   ```"
  [^Item$Properties props tab]
  (let [actual-tab (if (instance? dev.architectury.registry.registries.RegistrySupplier tab)
                     (try
                       (.get ^dev.architectury.registry.registries.RegistrySupplier tab)
                       (catch Exception e
                         (throw (ex-info (str "无法解析创造模式标签页 RegistrySupplier。"
                                             "请确保在注册物品之前先调用 (reg/register-all! creative-tabs-registry)。"
                                             "原始错误: " (.getMessage e))
                                        {:tab tab
                                         :registry-id (when (instance? dev.architectury.registry.registries.RegistrySupplier tab)
                                                        (str (.getId ^dev.architectury.registry.registries.RegistrySupplier tab)))
                                         :cause e}))))
                     tab)]
    ;; 使用 Architectury 的 arch$tab 方法
    (.arch$tab props actual-tab)))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro defcreative-tab
  "定义创造模式标签页（语法糖）

   示例:
   ```clojure
   (defcreative-tab tabs my-tab \"mymod\" \"main_tab\"
     :title \"My Items\"
     :icon my-item)
   ```"
  [registry name mod-id id & {:keys [title icon]}]
  `(def ~name
     (register-creative-tab! ~registry ~mod-id ~id ~title ~icon)))

(comment
  ;; 使用示例

  ;; 创建注册表
  (def tabs (reg/create-registry "mymod" :creative-tab))
  (def items (reg/create-registry "mymod" :item))

  ;; 创建标签页
  (defcreative-tab tabs weapons-tab "mymod" "weapons"
    :title "Weapons"
    :icon magic-sword)

  ;; 创建物品并指定标签页
  (def sword
    (reg/register items "sword"
      (fn []
        (Item. (with-tab (Item$Properties.) weapons-tab)))))

  (def axe
    (reg/register items "axe"
      (fn []
        (Item. (with-tab (Item$Properties.) weapons-tab)))))

  ;; 注册所有内容
  (reg/register-all! tabs items))
