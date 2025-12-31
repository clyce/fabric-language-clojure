(ns com.fabriclj.swiss-knife.common.inventories
  "瑞士军刀 - 背包/容器系统模块

   提供自定义背包和容器的创建、管理功能。
   支持将背包附加到实体、方块或物品上。"
  (:require [com.fabriclj.swiss-knife.common.core :as core]
            [com.fabriclj.swiss-knife.common.items :as items])
  (:import [net.minecraft.world Container SimpleContainer]
           [net.minecraft.world.item ItemStack]
           [net.minecraft.world.entity.player Player]
           [net.minecraft.nbt CompoundTag ListTag]
           [net.minecraft.core Direction]
           [net.minecraft.core.component DataComponents]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 容器创建和操作
;; ============================================================================

(defn create-inventory
  "创建自定义背包/容器

   参数:
   - size: 容器大小（槽位数量）
   - opts: 可选参数
     - :max-stack-size - 最大堆叠数（默认 64）
     - :on-change - 内容改变回调 (fn [container] ...)

   返回：SimpleContainer

   示例:
   ```clojure
   (def my-inventory (create-inventory 27))  ; 3x9 背包
   (def small-pouch (create-inventory 9 {:max-stack-size 16}))
   ```"
  ([size]
   (create-inventory size {}))
  ([size opts]
   (let [{:keys [max-stack-size on-change] :or {max-stack-size 64}} opts
         container (SimpleContainer. size)]
     (.setMaxStackSize container max-stack-size)
     (when on-change
       (.addListener container
                     (reify net.minecraft.world.ContainerListener
                       (containerChanged [_ _]
                         (on-change container)))))
     container)))

(defn inventory-size
  "获取背包大小"
  [^Container inventory]
  (.getContainerSize inventory))

(defn get-slot
  "获取槽位中的物品

   参数:
   - inventory: Container
   - slot: 槽位索引（从 0 开始）

   返回：ItemStack"
  ^ItemStack [^Container inventory slot]
  (.getItem inventory slot))

(defn set-slot!
  "设置槽位中的物品

   参数:
   - inventory: Container
   - slot: 槽位索引
   - item-stack: ItemStack"
  [^Container inventory slot ^ItemStack item-stack]
  (.setItem inventory slot item-stack))

(defn clear-slot!
  "清空槽位"
  [^Container inventory slot]
  (.removeItemNoUpdate inventory slot))

(defn clear-inventory!
  "清空整个背包"
  [^Container inventory]
  (.clearContent inventory))

(defn is-empty?
  "检查背包是否为空"
  [^Container inventory]
  (.isEmpty inventory))

;; ============================================================================
;; 物品查找和操作
;; ============================================================================

(defn find-item
  "在背包中查找物品

   参数:
   - inventory: Container
   - predicate: 查找条件 (fn [^ItemStack stack] -> boolean)

   返回：{:slot 槽位索引 :stack ItemStack} 或 nil

   示例:
   ```clojure
   (find-item inventory
     (fn [stack]
       (= (.getItem stack) Items/DIAMOND)))
   ```"
  [^Container inventory predicate]
  (let [size (.getContainerSize inventory)]
    (loop [i 0]
      (when (< i size)
        (let [stack (.getItem inventory i)]
          (if (and (not (.isEmpty stack)) (predicate stack))
            {:slot i :stack stack}
            (recur (inc i))))))))

(defn find-all-items
  "查找背包中所有匹配的物品

   返回：[{:slot 槽位 :stack ItemStack}] 列表"
  [^Container inventory predicate]
  (let [size (.getContainerSize inventory)
        results (atom [])]
    (dotimes [i size]
      (let [stack (.getItem inventory i)]
        (when (and (not (.isEmpty stack)) (predicate stack))
          (swap! results conj {:slot i :stack stack}))))
    @results))

(defn count-items
  "统计背包中物品的数量

   参数:
   - inventory: Container
   - item: Item 或物品 ID

   返回：总数量

   示例:
   ```clojure
   (count-items inventory Items/DIAMOND)
   (count-items inventory :minecraft:diamond)
   ```"
  [^Container inventory item]
  (let [target-item (if (keyword? item)
                      (core/get-item item)
                      item)
        size (.getContainerSize inventory)
        total (atom 0)]
    (dotimes [i size]
      (let [stack (.getItem inventory i)]
        (when (and (not (.isEmpty stack))
                   (= (.getItem stack) target-item))
          (swap! total + (.getCount stack)))))
    @total))

(defn has-space?
  "检查背包是否有空间

   参数:
   - inventory: Container
   - item-stack: 要添加的物品（可选）

   返回：boolean"
  ([^Container inventory]
   (let [size (.getContainerSize inventory)]
     (loop [i 0]
       (if (< i size)
         (if (.isEmpty (.getItem inventory i))
           true
           (recur (inc i)))
         false))))
  ([^Container inventory ^ItemStack item-stack]
   (or (has-space? inventory)
       ;; 检查是否可以堆叠到现有物品
       (let [size (.getContainerSize inventory)]
         (loop [i 0]
           (if (< i size)
             (let [existing (.getItem inventory i)]
               (if (and (not (.isEmpty existing))
                        (items/same-item? existing item-stack)
                        (< (.getCount existing) (.getMaxStackSize existing)))
                 true
                 (recur (inc i))))
             false))))))

;; ============================================================================
;; 物品添加和移除
;; ============================================================================

(defn add-item!
  "向背包添加物品

   参数:
   - inventory: Container
   - item-stack: ItemStack

   返回：剩余未添加的物品（如果背包满了）

   示例:
   ```clojure
   (let [remaining (add-item! inventory diamond-stack)]
     (when-not (.isEmpty remaining)
       (println \"Inventory full!\")))
   ```"
  ^ItemStack [^Container inventory ^ItemStack item-stack]
  (let [size (.getContainerSize inventory)
        remaining (items/copy-stack item-stack)]
    ;; 首先尝试堆叠到现有物品
    (dotimes [i size]
      (when (> (.getCount remaining) 0)
        (let [existing (.getItem inventory i)]
          (when (and (not (.isEmpty existing))
                     (items/same-item? existing remaining))
            (let [space (- (.getMaxStackSize existing) (.getCount existing))
                  to-add (min space (.getCount remaining))]
              (when (> to-add 0)
                (.grow existing to-add)
                (.shrink remaining to-add)))))))
    ;; 然后尝试放入空槽位
    (dotimes [i size]
      (when (> (.getCount remaining) 0)
        (let [existing (.getItem inventory i)]
          (when (.isEmpty existing)
            (.setItem inventory i (items/copy-stack remaining))
            (.setCount remaining 0)))))
    remaining))

(defn remove-item!
  "从背包移除物品

   参数:
   - inventory: Container
   - item: Item 或物品 ID
   - count: 数量

   返回：实际移除的数量

   示例:
   ```clojure
   (remove-item! inventory Items/DIAMOND 10)
   ```"
  [^Container inventory item count]
  (let [target-item (if (keyword? item)
                      (core/get-item item)
                      item)
        size (.getContainerSize inventory)
        removed (atom 0)]
    (dotimes [i size]
      (when (< @removed count)
        (let [stack (.getItem inventory i)]
          (when (and (not (.isEmpty stack))
                     (= (.getItem stack) target-item))
            (let [to-remove (min (- count @removed) (.getCount stack))]
              (.shrink stack to-remove)
              (swap! removed + to-remove)
              (when (<= (.getCount stack) 0)
                (.setItem inventory i ItemStack/EMPTY)))))))
    @removed))

;; ============================================================================
;; NBT 序列化
;; ============================================================================

(defn save-to-nbt
  "将背包保存到 NBT

   参数:
   - inventory: Container

   返回：CompoundTag

   示例:
   ```clojure
   (def nbt (save-to-nbt my-inventory))
   ```"
  ^CompoundTag [^Container inventory]
  (let [tag (CompoundTag.)
        items-list (ListTag.)
        size (.getContainerSize inventory)]
    (dotimes [i size]
      (let [stack (.getItem inventory i)]
        (when-not (.isEmpty stack)
          (let [item-tag (CompoundTag.)]
            (.putByte item-tag "Slot" i)
            (.save stack item-tag)
            (.add items-list item-tag)))))
    (.put tag "Items" items-list)
    (.putInt tag "Size" size)
    tag))

(defn load-from-nbt!
  "从 NBT 加载背包

   参数:
   - inventory: Container
   - tag: CompoundTag

   示例:
   ```clojure
   (load-from-nbt! my-inventory nbt)
   ```"
  [^Container inventory ^CompoundTag tag]
  (clear-inventory! inventory)
  (let [items-list (.getList tag "Items" 10)  ; 10 = CompoundTag
        size (.size items-list)]
    (dotimes [i size]
      (let [item-tag (.getCompound items-list i)
            slot (.getByte item-tag "Slot")
            stack (ItemStack/parseOptional
                   (.registryAccess (core/get-server))
                   item-tag)]
        (when (and (>= slot 0) (< slot (.getContainerSize inventory)))
          (.setItem inventory slot stack))))))

;; ============================================================================
;; 物品栈背包（背包物品）
;; ============================================================================

(defn attach-inventory-to-item!
  "将背包数据附加到物品栈

   参数:
   - item-stack: ItemStack
   - inventory: Container

   示例:
   ```clojure
   (def backpack-item (items/item-stack :mymod:backpack))
   (attach-inventory-to-item! backpack-item my-inventory)
   ```"
  [^ItemStack item-stack ^Container inventory]
  (let [nbt (save-to-nbt inventory)]
    (items/set-custom-data! item-stack nbt)))

(defn get-inventory-from-item
  "从物品栈获取背包数据

   参数:
   - item-stack: ItemStack
   - size: 背包大小

   返回：Container

   示例:
   ```clojure
   (def inventory (get-inventory-from-item backpack-item 27))
   ```"
  ^Container [^ItemStack item-stack size]
  (let [inventory (create-inventory size)]
    (when-let [nbt (items/get-custom-data item-stack)]
      (load-from-nbt! inventory nbt))
    inventory))

;; ============================================================================
;; 侧面访问（用于漏斗等）
;; ============================================================================

(defn create-sided-inventory
  "创建支持侧面访问的背包

   参数:
   - inventory: 基础 Container
   - side-config: 侧面配置映射
     {:up [可访问槽位列表]
      :down [...]
      :north [...]
      ...}

   返回：带侧面访问的 Container

   示例:
   ```clojure
   (create-sided-inventory my-inventory
     {:up [0 1 2]      ; 上方只能访问前 3 个槽位
      :down [3 4 5]    ; 下方访问中间 3 个槽位
      :north []})      ; 北方不能访问
   ```"
  [^Container inventory side-config]
  (proxy [net.minecraft.world.WorldlyContainer] []
    (getContainerSize []
      (.getContainerSize inventory))

    (isEmpty []
      (.isEmpty inventory))

    (getItem [slot]
      (.getItem inventory slot))

    (removeItem [slot count]
      (.removeItem inventory slot count))

    (removeItemNoUpdate [slot]
      (.removeItemNoUpdate inventory slot))

    (setItem [slot stack]
      (.setItem inventory slot stack))

    (stillValid [player]
      (.stillValid inventory player))

    (clearContent []
      (.clearContent inventory))

    (getSlotsForFace [^Direction direction]
      (let [side (case (.getName direction)
                   "up" :up
                   "down" :down
                   "north" :north
                   "south" :south
                   "east" :east
                   "west" :west)
            slots (get side-config side [])]
        (int-array slots)))

    (canPlaceItemThroughFace [slot stack direction]
      (let [side (case (.getName direction)
                   "up" :up
                   "down" :down
                   "north" :north
                   "south" :south
                   "east" :east
                   "west" :west)
            allowed-slots (get side-config side [])]
        (some #(= % slot) allowed-slots)))

    (canTakeItemThroughFace [slot stack direction]
      (let [side (case (.getName direction)
                   "up" :up
                   "down" :down
                   "north" :north
                   "south" :south
                   "east" :east
                   "west" :west)
            allowed-slots (get side-config side [])]
        (some #(= % slot) allowed-slots)))))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro with-inventory
  "在背包上下文中执行操作

   示例:
   ```clojure
   (with-inventory [inv (create-inventory 27)]
     (add-item! inv diamond-stack)
     (add-item! inv gold-stack)
     (save-to-nbt inv))
   ```"
  [[binding init-form] & body]
  `(let [~binding ~init-form]
     ~@body))

(comment
  ;; 使用示例

  ;; 1. 创建背包
  (def my-inventory (create-inventory 27))

  ;; 2. 添加物品
  (add-item! my-inventory (items/item-stack :minecraft:diamond 64))

  ;; 3. 查找物品
  (def diamond-slot (find-item my-inventory
                               (fn [stack]
                                 (= (.getItem stack) Items/DIAMOND))))

  ;; 4. 统计物品
  (println "Diamonds:" (count-items my-inventory Items/DIAMOND))

  ;; 5. 保存和加载
  (def nbt (save-to-nbt my-inventory))
  (load-from-nbt! my-inventory nbt)

  ;; 6. 物品栈背包
  (def backpack-item (items/item-stack :mymod:backpack))
  (def backpack-inv (create-inventory 27))
  (add-item! backpack-inv (items/item-stack :minecraft:diamond 10))
  (attach-inventory-to-item! backpack-item backpack-inv)

  ;; 7. 侧面访问（用于自动化）
  (def sided-inv
    (create-sided-inventory my-inventory
                            {:up [0 1 2]      ; 输入槽位
                             :down [3 4 5]    ; 输出槽位
                             :north []        ; 不允许访问
                             :south []})))
