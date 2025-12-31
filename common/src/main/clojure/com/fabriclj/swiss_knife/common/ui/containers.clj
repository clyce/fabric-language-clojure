(ns com.fabriclj.swiss-knife.common.ui.containers
  "瑞士军刀 - 容器菜单/GUI 系统模块

   ⚠️ 职责说明:
   本模块专注于 GUI 层( AbstractContainerMenu) ，处理玩家与容器的交互界面。

   **与其他模块的关系**:
   - `common.inventories` - 数据层( Container) ，存储物品数据
   - `common.containers` - GUI层( AbstractContainerMenu) ，处理界面交互
   - `client.screens` - 全屏菜单( Screen) ，不涉及容器同步

   **使用场景**:
   - 自定义方块GUI( 如箱子、工作台、熔炉)
   - 需要服务端-客户端同步的容器界面
   - 玩家可以与之交互的物品栏界面

   **基础容器操作请使用 `common.inventories`**"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.swiss-knife.common.ui.inventories :as inv]
            [com.fabriclj.registry :as reg])
  (:import (net.minecraft.world.inventory AbstractContainerMenu MenuType Slot)
           (net.minecraft.world.entity.player Player Inventory)
           (net.minecraft.world.item ItemStack)
           (net.minecraft.world Container SimpleContainer)
           (net.minecraft.core BlockPos)
           (net.minecraft.network FriendlyByteBuf)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 容器操作( 向后兼容，推荐使用 inventories 模块)
;; ============================================================================

;; ⚠️ 注意: 基础容器操作已移至 common.inventories
;; 这里保留向后兼容的别名

(defn create-container
  "创建简单容器

   ⚠️ 推荐使用: `inventories/create-inventory`

   此函数保留用于向后兼容。"
  ^SimpleContainer [size]
  (inv/create-inventory size))

(defn container-size
  "获取容器大小

   ⚠️ 推荐使用: `inventories/inventory-size`"
  [^Container container]
  (inv/inventory-size container))

(defn get-item-in-slot
  "获取槽位中的物品

   ⚠️ 推荐使用: `inventories/get-slot`"
  ^ItemStack [^Container container slot]
  (inv/get-slot container slot))

(defn set-item-in-slot!
  "设置槽位中的物品

   ⚠️ 推荐使用: `inventories/set-slot!`"
  [^Container container slot ^ItemStack item-stack]
  (inv/set-slot! container slot item-stack))

(defn clear-container!
  "清空容器

   ⚠️ 推荐使用: `inventories/clear-inventory!`"
  [^Container container]
  (inv/clear-inventory! container))

;; ============================================================================
;; 菜单类型注册
;; ============================================================================

(defn create-menu-type
  "创建菜单类型

   参数:
   - factory: 菜单工厂函数 (fn [sync-id player-inventory] -> AbstractContainerMenu)

   返回: MenuType

   示例:
   ```clojure
   (def my-menu-type
     (create-menu-type
       (fn [sync-id player-inventory]
         (MyCustomMenu. sync-id player-inventory))))
   ```"
  ^MenuType [factory]
  (MenuType.
   (reify net.minecraft.world.inventory.MenuType$MenuSupplier
     (create [_ sync-id player-inventory]
       (factory sync-id player-inventory)))))

(defn register-menu-type!
  "注册菜单类型

   参数:
   - registry: 菜单注册表
   - id: 菜单 ID
   - factory: 菜单工厂函数

   返回: RegistrySupplier

   示例:
   ```clojure
   (def menus (reg/create-registry \"mymod\" :menu))

   (def my-menu
     (register-menu-type! menus \"my_menu\"
       (fn [sync-id player-inventory]
         (MyCustomMenu. sync-id player-inventory))))

   (reg/register-all! menus)
   ```"
  [registry id factory]
  (reg/register registry id
                (fn [] (create-menu-type factory))))

;; ============================================================================
;; 自定义菜单创建
;; ============================================================================

(defn create-custom-menu
  "创建自定义容器菜单

   参数:
   - menu-type: MenuType
   - sync-id: 同步 ID
   - player-inventory: 玩家背包
   - container: 容器
   - opts: 可选参数
     - :container-rows - 容器行数( 默认 3)
     - :container-cols - 容器列数( 默认 9)
     - :add-player-inventory? - 是否添加玩家背包( 默认 true)
     - :add-player-hotbar? - 是否添加玩家快捷栏( 默认 true)
     - :quick-move-fn - 快速移动函数( Shift+点击)

   返回: AbstractContainerMenu

   示例:
   ```clojure
   (create-custom-menu my-menu-type sync-id player-inventory my-container
     {:container-rows 3
      :container-cols 9
      :add-player-inventory? true})
   ```"
  [^MenuType menu-type sync-id ^Inventory player-inventory ^Container container & [opts]]
  (let [{:keys [container-rows container-cols add-player-inventory? add-player-hotbar?
                quick-move-fn]
         :or {container-rows 3
              container-cols 9
              add-player-inventory? true
              add-player-hotbar? true}} opts
        container-slots (* container-rows container-cols)]
    (proxy [AbstractContainerMenu] [menu-type sync-id]
      (quickMoveStack [^Player player slot-index]
        (if quick-move-fn
          (quick-move-fn this player slot-index)
          ItemStack/EMPTY))

      (stillValid [^Player player]
        (.stillValid container player)))))

;; ============================================================================
;; 槽位布局辅助
;; ============================================================================

(defn add-slot!
  "添加槽位到菜单

   参数:
   - menu: AbstractContainerMenu
   - container: Container
   - slot-index: 槽位索引
   - x, y: 屏幕位置

   返回: Slot"
  ^Slot [^AbstractContainerMenu menu ^Container container slot-index x y]
  (.addSlot menu (Slot. container slot-index x y)))

(defn add-container-slots!
  "批量添加容器槽位( 网格布局)

   参数:
   - menu: AbstractContainerMenu
   - container: Container
   - start-x, start-y: 起始位置
   - rows, cols: 行数和列数
   - slot-spacing: 槽位间距( 默认 18)

   示例:
   ```clojure
   ;; 添加 3x9 的容器槽位
   (add-container-slots! menu container 8 18 3 9)
   ```"
  ([menu container start-x start-y rows cols]
   (add-container-slots! menu container start-x start-y rows cols 18))
  ([^AbstractContainerMenu menu ^Container container start-x start-y rows cols slot-spacing]
   (doseq [row (range rows)
           col (range cols)]
     (let [slot-index (+ (* row cols) col)
           x (+ start-x (* col slot-spacing))
           y (+ start-y (* row slot-spacing))]
       (add-slot! menu container slot-index x y)))))

(defn add-player-inventory-slots!
  "添加玩家背包槽位( 标准 3x9 布局)

   参数:
   - menu: AbstractContainerMenu
   - player-inventory: 玩家背包
   - start-x, start-y: 起始位置"
  [^AbstractContainerMenu menu ^Inventory player-inventory start-x start-y]
  (doseq [row (range 3)
          col (range 9)]
    (let [slot-index (+ 9 (* row 9) col)  ; 9 是快捷栏槽位
          x (+ start-x (* col 18))
          y (+ start-y (* row 18))]
      (add-slot! menu player-inventory slot-index x y))))

(defn add-player-hotbar-slots!
  "添加玩家快捷栏槽位( 标准 1x9 布局)

   参数:
   - menu: AbstractContainerMenu
   - player-inventory: 玩家背包
   - start-x, start-y: 起始位置"
  [^AbstractContainerMenu menu ^Inventory player-inventory start-x start-y]
  (doseq [col (range 9)]
    (let [x (+ start-x (* col 18))]
      (add-slot! menu player-inventory col x start-y))))

;; ============================================================================
;; 标准容器布局
;; ============================================================================

(def standard-layouts
  "标准容器布局配置"
  {:chest-small {:rows 3 :cols 9 :title "Small Chest"}
   :chest-large {:rows 6 :cols 9 :title "Large Chest"}
   :furnace {:rows 1 :cols 3 :title "Furnace"}
   :brewing-stand {:rows 1 :cols 5 :title "Brewing Stand"}
   :hopper {:rows 1 :cols 5 :title "Hopper"}
   :dispenser {:rows 3 :cols 3 :title "Dispenser"}
   :dropper {:rows 3 :cols 3 :title "Dropper"}})

(defn create-standard-menu
  "创建标准布局的容器菜单

   参数:
   - layout: 布局类型( 关键字，如 :chest-small)
   - menu-type: MenuType
   - sync-id: 同步 ID
   - player-inventory: 玩家背包
   - container: 容器

   示例:
   ```clojure
   (create-standard-menu :chest-small my-menu-type sync-id player-inv container)
   ```"
  [layout menu-type sync-id player-inventory container]
  (let [{:keys [rows cols]} (get standard-layouts layout)]
    (create-custom-menu menu-type sync-id player-inventory container
                        {:container-rows rows
                         :container-cols cols})))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro defmenu
  "定义自定义菜单类型( 语法糖)

   示例:
   ```clojure
   (defmenu my-menu menus \"my_menu\"
     [sync-id player-inventory]
     (let [container (create-container 27)]
       (create-custom-menu my-menu-type sync-id player-inventory container
         {:container-rows 3 :container-cols 9})))
   ```"
  [menu-name registry id args & body]
  `(def ~menu-name
     (register-menu-type! ~registry ~id
                          (fn ~args
                            ~@body))))

(comment
  ;; 使用示例

  ;; 1. 创建容器
  (def my-container (create-container 27))  ; 3x9 箱子

  ;; 2. 注册菜单类型
  (def menus (reg/create-registry "mymod" :menu))

  (def my-menu
    (register-menu-type! menus "my_chest"
                         (fn [sync-id player-inventory]
                           (let [container (create-container 27)]
                             (create-custom-menu my-menu sync-id player-inventory container
                                                 {:container-rows 3
                                                  :container-cols 9})))))

  (reg/register-all! menus)

  ;; 3. 使用标准布局
  (create-standard-menu :chest-small my-menu-type sync-id player-inv container)

  ;; 4. 自定义槽位布局
  (let [menu (proxy [AbstractContainerMenu] [my-menu-type sync-id]
               (stillValid [player] true))]
    ;; 添加容器槽位
    (add-container-slots! menu container 8 18 3 9)
    ;; 添加玩家背包
    (add-player-inventory-slots! menu player-inventory 8 84)
    ;; 添加快捷栏
    (add-player-hotbar-slots! menu player-inventory 8 142)))
