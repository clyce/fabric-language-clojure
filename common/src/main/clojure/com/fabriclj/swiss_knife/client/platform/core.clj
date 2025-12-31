(ns com.fabriclj.swiss-knife.client.platform.core
  "瑞士军刀 - 客户端核心工具

   提供客户端专用的工具函数和访问器

   注意: 此命名空间仅在客户端环境可用！"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as common])
  (:import (net.minecraft.client Minecraft)
           (net.minecraft.client.player LocalPlayer)
           (net.minecraft.client.multiplayer ClientLevel)
           (net.minecraft.client.gui.screens Screen)
           (net.minecraft.world.phys HitResult HitResult$Type)
           (com.mojang.blaze3d.platform Window)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 客户端访�?
;; ============================================================================

(defn get-minecraft
  "获取 Minecraft 客户端实例

   返回: Minecraft"
  ^Minecraft []
  (Minecraft/getInstance))

(defn get-player
  "获取本地玩家

   返回: LocalPlayer | nil( 如果不在游戏中) "
  ^LocalPlayer []
  (.player (get-minecraft)))

(defn get-level
  "获取客户端世界

   返回: ClientLevel | nil"
  ^ClientLevel []
  (.level (get-minecraft)))

(defn is-in-game?
  "检查是否在游戏中( 有玩家和世界) "
  []
  (and (get-player) (get-level)))

;; ============================================================================
;; 屏幕管理
;; ============================================================================

(defn get-current-screen
  "获取当前显示的屏幕

   返回: Screen | nil( 如果在游戏中) "
  ^Screen []
  (.screen (get-minecraft)))

(defn set-screen!
  "设置当前屏幕

   参数:
   - screen: Screen 实例 | nil( 返回游戏)

   示例:
   ```clojure
   (set-screen! my-custom-screen)
   (set-screen! nil)  ; 关闭屏幕
   ```"
  [^Screen screen]
  (.setScreen (get-minecraft) screen))

(defn is-paused?
  "检查游戏是否暂停"
  []
  (.isPaused (get-minecraft)))

(defn pause!
  "暂停游戏( 仅单人游戏) "
  []
  (.pauseGame (get-minecraft) false))

;; ============================================================================
;; 鼠标和视�?
;; ============================================================================

(defn get-crosshair-target
  "获取准星指向的目标

   返回: HitResult( BlockHitResult | EntityHitResult) "
  ^HitResult []
  (.hitResult (get-minecraft)))

(defn get-crosshair-type
  "获取准星指向的类型

   返回: :block | :entity | :miss"
  []
  (let [^HitResult result (get-crosshair-target)]
    (condp = (.getType result)
      HitResult$Type/BLOCK :block
      HitResult$Type/ENTITY :entity
      HitResult$Type/MISS :miss)))

(defn get-crosshair-block-pos
  "获取准星指向的方块位置

   返回: BlockPos | nil"
  []
  (when (= :block (get-crosshair-type))
    (.getBlockPos ^net.minecraft.world.phys.BlockHitResult (get-crosshair-target))))

(defn get-crosshair-entity
  "获取准星指向的实体

   返回: Entity | nil"
  []
  (when (= :entity (get-crosshair-type))
    (.getEntity ^net.minecraft.world.phys.EntityHitResult (get-crosshair-target))))

(defn get-mouse-position
  "获取鼠标位置

   返回: {:x x :y y}"
  []
  (let [^net.minecraft.client.MouseHandler mouse (.mouseHandler (get-minecraft))]
    {:x (.xpos mouse)
     :y (.ypos mouse)}))

(defn get-pick-distance
  "获取当前交互距离

   返回: 方块距离"
  []
  (if-let [connection (.getConnection (get-minecraft))]
    (.. connection getPlayerInfo getReachDistance)
    4.5))

;; ============================================================================
;; 窗口信息
;; ============================================================================

(defn get-window
  "获取游戏窗口

   返回: Window"
  ^Window []
  (.getWindow (get-minecraft)))

(defn get-window-size
  "获取窗口大小

   返回: {:width w :height h}"
  []
  (let [^Window window (get-window)]
    {:width (.getWidth window)
     :height (.getHeight window)}))

(defn window-width
  "获取窗口宽度

   返回: 窗口宽度( 像素) "
  []
  (.getWidth (get-window)))

(defn window-height
  "获取窗口高度

   返回: 窗口高度( 像素) "
  []
  (.getHeight (get-window)))

(defn get-gui-scale
  "获取 GUI 缩放比例"
  []
  (.getGuiScale (get-window)))

(defn is-fullscreen?
  "检查是否全屏"
  []
  (.isFullscreen (get-window)))

(defn get-framerate
  "获取当前帧率"
  []
  (.getFps (get-minecraft)))

;; ============================================================================
;; 游戏时间
;; ============================================================================

(defn get-partial-tick
  "获取部分 tick 时间( 用于平滑动画)
   返回: 0.0 ~ 1.0 之间的浮点数"
  []
  (.getFrameTime (get-minecraft)))

(defn get-delta-time
  "获取帧间隔时间( 秒) "
  []
  (.getDeltaFrameTime (get-minecraft)))

;; ============================================================================
;; 客户端设�?
;; ============================================================================

(defn get-options
  "获取游戏选项

   返回: Options"
  []
  (.options (get-minecraft)))

(defn get-render-distance
  "获取渲染距离( 区块) "
  []
  (.. (get-minecraft) options renderDistance get))

(defn get-fov
  "获取视野( FOV) "
  []
  (.. (get-minecraft) options fov get))

(defn get-language
  "获取当前语言代码
   返回: 如 \"en_us\", \"zh_cn\""
  ^String []
  (.. (get-minecraft) getLanguageManager getSelected))

;; ============================================================================
;; 性能信息
;; ============================================================================

(defn get-debug-info
  "获取调试信息
   返回: 包含各种调试数据的映射"
  []
  (let [mc (get-minecraft)]
    {:fps (.getFps mc)
     :chunk-updates (.. mc levelRenderer getLastViewDistance)
     :entities (when-let [level (get-level)]
                 (.getEntityCount level))
     :particles (when-let [pm (.particleEngine mc)]
                  (.countParticles pm))}))

;; ============================================================================
;; 客户端专用宏
;; ============================================================================

(defmacro client-safe
  "安全地执行客户端代码( 捕获异常)

   示例:
   ```clojure
   (client-safe
     (println \"Player at\" (.position (get-player))))
   ```"
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (common/log-error "Client error:" (.getMessage e#))
       nil)))

(defmacro when-in-game
  "仅在游戏中执行

   示例:
   ```clojure
   (when-in-game
     (println \"Player health:\" (.getHealth (get-player))))
   ```"
  [& body]
  `(when (is-in-game?)
     ~@body))

(comment
  ;; 使用示例

  ;; 获取客户端信�?
  (def mc (get-minecraft))
  (def player (get-player))
  (def level (get-level))

  ;; 检查准星指�?
  (when (= :block (get-crosshair-type))
    (println "Looking at block:" (get-crosshair-block-pos)))

  ;; 窗口信息
  (let [{:keys [width height]} (get-window-size)]
    (println "Window:" width "x" height))

  ;; 性能信息
  (println "FPS:" (get-framerate))
  (println "Debug:" (get-debug-info))

  ;; 安全执行
  (client-safe
   (println "Player pos:" (.position (get-player)))))
