(ns com.fabriclj.swiss-knife.client.rendering.hud
  "瑞士军刀 - HUD 渲染模块

   提供在游戏界面上绘制自定义 HUD 元素的功能。

   注意: 此命名空间仅在客户端环境可用！"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.swiss-knife.client.rendering.core :as render]
            [com.fabriclj.swiss-knife.client.events.core :as events]
            [com.fabriclj.swiss-knife.client.platform.core :as client])
  (:import (net.minecraft.client.gui GuiGraphics)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; HUD 渲染器管理
;; ============================================================================

(defonce ^:private hud-renderers (atom []))

(defn register-hud-renderer!
  "注册 HUD 渲染器

   参数:
   - renderer: 渲染函数 (fn [^GuiGraphics graphics delta-time] ...)
   - priority: 渲染优先级( 数字越小越先渲染，默认 0)
   - id: 渲染器 ID( 可选，用于后续移除)

   返回: 渲染器 ID

   示例:
   ```clojure
   (register-hud-renderer!
     (fn [graphics delta]
       (render/draw-string graphics \"Custom HUD\" 10 10 0xFFFFFF))
     0
     :my-hud)
   ```"
  ([renderer]
   (register-hud-renderer! renderer 0))
  ([renderer priority]
   (register-hud-renderer! renderer priority (java.util.UUID/randomUUID)))
  ([renderer priority id]
   (swap! hud-renderers conj {:id id
                              :renderer renderer
                              :priority priority})
   ;; 按优先级排序
   (swap! hud-renderers #(sort-by :priority %))
   id))

(defn unregister-hud-renderer!
  "移除 HUD 渲染器

   参数:
   - id: 渲染器 ID"
  [id]
  (swap! hud-renderers
         (fn [renderers]
           (remove #(= (:id %) id) renderers))))

(defn clear-hud-renderers!
  "清除所有 HUD 渲染器"
  []
  (reset! hud-renderers []))

;; ============================================================================
;; HUD 渲染触发
;; ============================================================================

(defn init-hud-system!
  "初始化 HUD 系统

   注意: 必须在客户端初始化时调用一次！"
  []
  (events/on-screen-render-post
   (fn [screen graphics mouse-x mouse-y delta]
     (when (nil? screen)  ; 仅在没有 GUI 时渲染( 即游戏中)
       (doseq [{:keys [renderer]} @hud-renderers]
         (try
           (renderer graphics delta)
           (catch Exception e
             (core/log-error "HUD render error:" (.getMessage e))))))
     (events/event-pass))))

;; ============================================================================
;; 预定义的 HUD 位置
;; ============================================================================

(defn get-screen-center
  "获取屏幕中心坐标

   返回: {:x x :y y}"
  []
  (let [{:keys [width height]} (client/get-window-size)]
    {:x (/ width 2)
     :y (/ height 2)}))

(defn get-top-left
  "获取屏幕左上角坐标( 带边距)

   参数:
   - margin: 边距( 默认 5) "
  ([]
   (get-top-left 5))
  ([margin]
   {:x margin :y margin}))

(defn get-top-right
  "获取屏幕右上角坐标( 带边距) "
  ([]
   (get-top-right 5))
  ([margin]
   (let [{:keys [width]} (client/get-window-size)]
     {:x (- width margin) :y margin})))

(defn get-bottom-left
  "获取屏幕左下角坐标( 带边距) "
  ([]
   (get-bottom-left 5))
  ([margin]
   (let [{:keys [height]} (client/get-window-size)]
     {:x margin :y (- height margin)})))

(defn get-bottom-right
  "获取屏幕右下角坐标( 带边距) "
  ([]
   (get-bottom-right 5))
  ([margin]
   (let [{:keys [width height]} (client/get-window-size)]
     {:x (- width margin) :y (- height margin)})))

;; ============================================================================
;; 便捷 HUD 元素
;; ============================================================================

(defn draw-text-hud
  "在指定位置绘制文本 HUD

   参数:
   - graphics: GuiGraphics
   - text: 文本
   - pos: 位置 {:x x :y y}
   - color: 颜色( 可选，默认白色)
   - shadow?: 是否有阴影( 可选，默认 true) "
  ([^GuiGraphics graphics text pos]
   (draw-text-hud graphics text pos 0xFFFFFF true))
  ([^GuiGraphics graphics text pos color]
   (draw-text-hud graphics text pos color true))
  ([^GuiGraphics graphics text {:keys [x y]} color shadow?]
   (render/draw-string graphics text x y color shadow?)))

(defn draw-bar-hud
  "绘制进度条 HUD

   参数:
   - graphics: GuiGraphics
   - x, y: 位置
   - width, height: 尺寸
   - value: 当前值
   - max-value: 最大值
   - bar-color: 进度条颜色
   - bg-color: 背景颜色( 可选)

   示例:
   ```clojure
   (draw-bar-hud graphics 10 10 100 10 50 100 0xFF00FF00 0xFF000000)
   ```"
  ([^GuiGraphics graphics x y width height value max-value bar-color]
   (draw-bar-hud graphics x y width height value max-value bar-color 0xFF000000))
  ([^GuiGraphics graphics x y width height value max-value bar-color bg-color]
   ;; 绘制背景
   (render/fill-rect graphics x y (+ x width) (+ y height) bg-color)
   ;; 绘制进度
   (let [filled-width (int (* width (/ value max-value)))]
     (when (> filled-width 0)
       (render/fill-rect graphics x y (+ x filled-width) (+ y height) bar-color)))))

(defn draw-item-hud
  "绘制物品图标 HUD

   参数:
   - graphics: GuiGraphics
   - item-stack: ItemStack
   - pos: 位置 {:x x :y y}
   - show-count?: 是否显示数量( 可选，默认 true) "
  ([^GuiGraphics graphics item-stack pos]
   (draw-item-hud graphics item-stack pos true))
  ([^GuiGraphics graphics item-stack {:keys [x y]} show-count?]
   (if show-count?
     (render/draw-item-with-count graphics item-stack x y)
     (render/draw-item graphics item-stack x y))))

;; ============================================================================
;; 宏
;; ============================================================================

(defmacro defhud
  "定义 HUD 渲染器( 语法糖)

   参数:
   - hud-name: HUD 名称( 符号)
   - priority-or-binding: 优先级( 数字) 或绑定向量
   - binding-or-body: 绑定向量或函数体
   - & body: 函数体( 当提供了优先级时)

   示例:
   ```clojure
   ;; 简单形式( 默认优先级 0)
   (defhud my-hud
     [graphics delta]
     (let [pos (get-top-left 10)]
       (draw-text-hud graphics \"My HUD\" pos)))

   ;; 带优先级
   (defhud my-hud 10
     [graphics delta]
     (draw-text-hud graphics \"My HUD\" (get-top-left 10)))
   ```"
  [hud-name & args]
  (if (and (>= (count args) 2)
           (number? (first args)))
    ;; 带优先级的形式: (defhud name priority [binding] body...)
    (let [[priority binding & body] args]
      `(def ~hud-name
         (register-hud-renderer!
          (fn ~binding ~@body)
          ~priority
          ~(keyword hud-name))))
    ;; 简单形式: (defhud name [binding] body...)
    (let [[binding & body] args]
      `(def ~hud-name
         (register-hud-renderer!
          (fn ~binding ~@body)
          0
          ~(keyword hud-name))))))

(comment
  ;; 使用示例

  ;; 初始化 HUD 系统
  (init-hud-system!)

  ;; 注册简单文本 HUD
  (register-hud-renderer!
   (fn [graphics delta]
     (let [pos (get-top-left 10)]
       (draw-text-hud graphics "Hello HUD!" pos)))
   0
   :hello-hud)

  ;; 注册生命值条 HUD
  (register-hud-renderer!
   (fn [graphics delta]
     (when-let [player (com.fabriclj.swiss-knife.client.core/get-player)]
       (let [health (.getHealth player)
             max-health (.getMaxHealth player)
             pos (get-top-left 10)]
         (draw-bar-hud graphics
                       (:x pos) (+ (:y pos) 20)
                       100 10
                       health max-health
                       0xFF00FF00 0xFF800000))))
   0
   :health-bar)

  ;; 使用宏定义
  (defhud fps-display :priority 0
    [graphics delta]
    (let [fps (com.fabriclj.swiss-knife.client.core/get-framerate)
          pos (get-top-right 10)]
      (draw-text-hud graphics (str "FPS: " fps) pos 0xFFFF00)))

  ;; 移除 HUD
  (unregister-hud-renderer! :hello-hud))
