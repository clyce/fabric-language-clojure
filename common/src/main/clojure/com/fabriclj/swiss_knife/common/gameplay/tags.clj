(ns com.fabriclj.swiss-knife.common.gameplay.tags
  "瑞士军刀 - 标签系统模块

   提供方块标签、物品标签、实体标签的查询和创建。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (net.minecraft.tags TagKey ItemTags BlockTags EntityTypeTags FluidTags)
           (net.minecraft.core.registries Registries)
           (net.minecraft.world.item ItemStack Item)
           (net.minecraft.world.level.block Block BlockState)
           (net.minecraft.world.entity EntityType)
           (net.minecraft.resources ResourceLocation)
           (net.minecraft.core.registries Registries)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 标签创建
;; ============================================================================

(defn create-block-tag
  "创建方块标签

   参数:
   - id: ResourceLocation 或字符串

   示例:
   ```clojure
   (def my-tag (create-block-tag \"mymod:mineable/magic_tool\"))
   ```"
  ^TagKey [id]
  (TagKey/create Registries/BLOCK (core/->resource-location id)))

(defn create-item-tag
  "创建物品标签"
  ^TagKey [id]
  (TagKey/create Registries/ITEM (core/->resource-location id)))

(defn create-entity-tag
  "创建实体类型标签"
  ^TagKey [id]
  (TagKey/create Registries/ENTITY_TYPE (core/->resource-location id)))

(defn create-fluid-tag
  "创建流体标签"
  ^TagKey [id]
  (TagKey/create Registries/FLUID (core/->resource-location id)))

;; ============================================================================
;; 标签检查
;; ============================================================================

(defn block-has-tag?
  "检查方块是否有指定标签

   参数:
   - block-or-state: Block 或 BlockState
   - tag: TagKey 或字符串

   示例:
   ```clojure
   (block-has-tag? Blocks/OAK_LOG \"#minecraft:logs\")
   (block-has-tag? state my-tag)
   ```"
  [block-or-state tag]
  (let [^TagKey tag-key (if (instance? TagKey tag)
                          tag
                          (create-block-tag tag))
        state (if (instance? BlockState block-or-state)
                block-or-state
                (.defaultBlockState ^Block block-or-state))]
    (.is state tag-key)))

(defn item-has-tag?
  "检查物品是否有指定标签"
  [item-or-stack tag]
  (let [^TagKey tag-key (if (instance? TagKey tag)
                          tag
                          (create-item-tag tag))
        ^Item item (if (instance? ItemStack item-or-stack)
                     (.getItem ^ItemStack item-or-stack)
                     item-or-stack)]
    (.is (.builtInRegistryHolder item) tag-key)))

(defn entity-has-tag?
  "检查实体类型是否有指定标签"
  [^EntityType entity-type tag]
  (let [^TagKey tag-key (if (instance? TagKey tag)
                          tag
                          (create-entity-tag tag))]
    (.is (.builtInRegistryHolder entity-type) tag-key)))

;; ============================================================================
;; 常用原版标签
;; ============================================================================

(def vanilla-block-tags
  "常用原版方块标签"
  {:logs (create-block-tag "minecraft:logs")
   :planks (create-block-tag "minecraft:planks")
   :stone-blocks (create-block-tag "minecraft:stone_blocks")
   :dirt (create-block-tag "minecraft:dirt")
   :sand (create-block-tag "minecraft:sand")
   :leaves (create-block-tag "minecraft:leaves")
   :mineable-pickaxe (create-block-tag "minecraft:mineable/pickaxe")
   :mineable-axe (create-block-tag "minecraft:mineable/axe")
   :mineable-shovel (create-block-tag "minecraft:mineable/shovel")
   :mineable-hoe (create-block-tag "minecraft:mineable/hoe")
   :needs-stone-tool (create-block-tag "minecraft:needs_stone_tool")
   :needs-iron-tool (create-block-tag "minecraft:needs_iron_tool")
   :needs-diamond-tool (create-block-tag "minecraft:needs_diamond_tool")})

(def vanilla-item-tags
  "常用原版物品标签"
  {:logs (create-item-tag "minecraft:logs")
   :planks (create-item-tag "minecraft:planks")
   :swords (create-item-tag "minecraft:swords")
   :pickaxes (create-item-tag "minecraft:pickaxes")
   :axes (create-item-tag "minecraft:axes")
   :shovels (create-item-tag "minecraft:shovels")
   :hoes (create-item-tag "minecraft:hoes")
   :tools (create-item-tag "minecraft:tools")
   :armor (create-item-tag "minecraft:armor")
   :food (create-item-tag "minecraft:food")})

;; ============================================================================
;; 便捷函数
;; ============================================================================

(defn is-log?
  "检查是否为原木"
  [block-or-item]
  (if (or (instance? Block block-or-item)
          (instance? BlockState block-or-item))
    (block-has-tag? block-or-item (:logs vanilla-block-tags))
    (item-has-tag? block-or-item (:logs vanilla-item-tags))))

(defn is-tool?
  "检查物品是否为工具"
  [item-or-stack]
  (item-has-tag? item-or-stack (:tools vanilla-item-tags)))

(defn is-mineable-with-pickaxe?
  "检查方块是否可用镐子挖掘"
  [block-or-state]
  (block-has-tag? block-or-state (:mineable-pickaxe vanilla-block-tags)))

(defn needs-iron-tool?
  "检查方块是否需要铁质工具"
  [block-or-state]
  (block-has-tag? block-or-state (:needs-iron-tool vanilla-block-tags)))

(comment
  ;; 使用示例

  ;; 1. 创建自定义标签
  (def my-block-tag (create-block-tag "mymod:special_blocks"))
  (def my-item-tag (create-item-tag "mymod:magical_items"))

  ;; 2. 检查标签
  (block-has-tag? Blocks/OAK_LOG "minecraft:logs")
  (item-has-tag? Items/DIAMOND_SWORD "minecraft:swords")

  ;; 3. 使用便捷函数
  (is-log? Blocks/OAK_LOG)
  (is-tool? Items/DIAMOND_PICKAXE)
  (is-mineable-with-pickaxe? Blocks/STONE))
