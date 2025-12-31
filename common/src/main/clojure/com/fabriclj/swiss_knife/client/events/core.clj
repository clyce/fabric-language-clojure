(ns com.fabriclj.swiss-knife.client.events.core
  "瑞士军刀 - 客户端事件
   封装客户端专用事件( 渲染、输入等)
   注意: 此命名空间仅在客户端环境可用！"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (dev.architectury.event EventResult)
           (dev.architectury.event.events.client ClientGuiEvent
                                                 ClientLifecycleEvent
                                                 ClientPlayerEvent
                                                 ClientRawInputEvent
                                                 ClientScreenInputEvent)
           (dev.architectury.event.events.client ClientTickEvent)
           (net.minecraft.client Minecraft)
           (net.minecraft.client.player LocalPlayer)
           (net.minecraft.client.multiplayer ClientLevel)
           (net.minecraft.client.gui.screens Screen)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 客户端生命周期期
;; ============================================================================

(defn on-client-setup
  "客户端设置阶段触发

   参数:
   - handler: 函数 (fn [^Minecraft minecraft] ...)"
  [handler]
  (.register (ClientLifecycleEvent/CLIENT_SETUP)
             (reify java.util.function.Consumer
               (accept [_ minecraft]
                 (handler minecraft)))))

(defn on-client-started
  "客户端启动完成触发"
  [handler]
  (.register (ClientLifecycleEvent/CLIENT_STARTED)
             (reify java.util.function.Consumer
               (accept [_ minecraft]
                 (handler minecraft)))))

(defn on-client-stopping
  "客户端停止时触发"
  [handler]
  (.register (ClientLifecycleEvent/CLIENT_STOPPING)
             (reify java.util.function.Consumer
               (accept [_ minecraft]
                 (handler minecraft)))))

;; ============================================================================
;; 客户端Tick
;; ============================================================================

(defn on-client-tick
  "每个客户端tick 触发( 20次/秒)
   参数:
   - handler: 函数 (fn [^Minecraft minecraft] ...)

   注意: 此函数每秒调用 20 次，避免执行耗时操作"
  [handler]
  (.register (ClientTickEvent/CLIENT_PRE)
             (reify java.util.function.Consumer
               (accept [_ minecraft]
                 (handler minecraft)))))

(defn on-client-level-tick
  "每个客户端世界tick 触发"
  [handler]
  (.register (ClientTickEvent/CLIENT_LEVEL_PRE)
             (reify java.util.function.Consumer
               (accept [_ level]
                 (handler level)))))

(defn on-client-player-tick
  "每个客户端玩家tick 触发"
  [handler]
  (.register (ClientPlayerEvent/CLIENT_PLAYER_QUIT)
             (reify java.util.function.Consumer
               (accept [_ player]
                 (handler player)))))

;; ============================================================================
;; 玩家事件
;; ============================================================================

(defn on-client-player-join
  "客户端玩家加入世界时触发
   参数:
   - handler: 函数 (fn [^LocalPlayer player] ...)"
  [handler]
  (.register (ClientPlayerEvent/CLIENT_PLAYER_JOIN)
             (reify java.util.function.Consumer
               (accept [_ player]
                 (handler player)))))

(defn on-client-player-quit
  "客户端玩家离开世界时触发"
  [handler]
  (.register (ClientPlayerEvent/CLIENT_PLAYER_QUIT)
             (reify java.util.function.Consumer
               (accept [_ player]
                 (handler player)))))

(defn on-client-player-respawn
  "客户端玩家重生时触发"
  [handler]
  (.register (ClientPlayerEvent/CLIENT_PLAYER_RESPAWN)
             (reify java.util.function.Consumer
               (accept [_ new-player old-player]
                 (handler new-player old-player)))))

;; ============================================================================
;; 原始输入事件
;; ============================================================================

(defn on-mouse-clicked
  "鼠标按下时触发

   参数:
   - handler: 函数 (fn [^Minecraft minecraft button action mods] ...) -> EventResult

   返回:
   - (event-pass) - 继续处理
   - (event-interrupt) - 阻止默认行为

   参数说明:
   - button: 0=左键, 1=右键, 2=中键
   - action: 0=释放, 1=按下
   - mods: 修饰键( Shift/Ctrl/Alt"
  [handler]
  (.register (ClientRawInputEvent/MOUSE_CLICKED_PRE)
             (reify java.util.function.Consumer
               (accept [_ click-context]
                 (handler (.minecraft click-context)
                          (.button click-context)
                          (.action click-context)
                          (.mods click-context))))))

(defn on-mouse-scrolled
  "鼠标滚轮滚动时触发

   参数:
   - handler: 函数 (fn [^Minecraft minecraft amount] ...) -> EventResult

   参数说明:
   - amount: 滚动量( 正数向上，负数向下) "
  [handler]
  (.register (ClientRawInputEvent/MOUSE_SCROLLED)
             (reify java.util.function.Consumer
               (accept [_ scroll-context]
                 (handler (.minecraft scroll-context)
                          (.amount scroll-context))))))

(defn on-key-pressed
  "键盘按下时触发

   参数:
   - handler: 函数 (fn [^Minecraft minecraft key scancode action mods] ...) -> EventResult

   参数说明:
   - key: GLFW 键码
   - scancode: 扫描
   - action: 0=释放, 1=按下, 2=重复
   - mods: 修饰"
  [handler]
  (.register (ClientRawInputEvent/KEY_PRESSED)
             (reify java.util.function.Consumer
               (accept [_ key-context]
                 (handler (.minecraft key-context)
                          (.key key-context)
                          (.scancode key-context)
                          (.action key-context)
                          (.mods key-context))))))

;; ============================================================================
;; GUI 事件
;; ============================================================================

(defn on-screen-init-pre
  "屏幕初始化前触发

   参数:
   - handler: 函数 (fn [^Minecraft minecraft ^Screen screen add-widget remove-widget] ...) -> EventResult"
  [handler]
  (.register (ClientGuiEvent/INIT_PRE)
             (reify java.util.function.Consumer
               (accept [_ init-context]
                 (handler (.minecraft init-context)
                          (.screen init-context)
                          (.addWidget init-context)
                          (.removeWidget init-context))))))

(defn on-screen-render-pre
  "屏幕渲染前触发

   参数:
   - handler: 函数 (fn [^Screen screen ^GuiGraphics graphics mouse-x mouse-y delta] ...) -> EventResult"
  [handler]
  (.register (ClientGuiEvent/RENDER_PRE)
             (reify java.util.function.Consumer
               (accept [_ render-context]
                 (handler (.screen render-context)
                          (.graphics render-context)
                          (.mouseX render-context)
                          (.mouseY render-context)
                          (.delta render-context))))))

(defn on-screen-render-post
  "屏幕渲染后触发"
  [handler]
  (.register (ClientGuiEvent/RENDER_POST)
             (reify java.util.function.Consumer
               (accept [_ render-context]
                 (handler (.screen render-context)
                          (.graphics render-context)
                          (.mouseX render-context)
                          (.mouseY render-context)
                          (.delta render-context))))))

;; ============================================================================
;; 辅助函数
;; ============================================================================

(defn event-pass
  "事件继续传"
  []
  (EventResult/pass))

(defn event-interrupt
  "中断事件

   注意: EventResult/interrupt 只接受一个 boolean 参数。
   如果需要返回值，请直接返回 InteractionResult 或使用其他事件 API。"
  ([]
   (EventResult/interrupt false))
  ([value]
   ;; EventResult/interrupt 只接受 boolean，不支持返回值
   ;; 如果需要返回值，应该在事件处理中直接处理
   (EventResult/interrupt true)))

;; ============================================================================
;; 便捷
;; ============================================================================

(defmacro on-key
  "注册特定按键事件( 语法糖)

   示例:
   ```clojure
   (on-key org.lwjgl.glfw.GLFW/GLFW_KEY_F
     (fn []
       (println \"F key pressed!\")))
   ```"
  [key-code handler-fn]
  `(on-key-pressed
    (fn [_# key# _# action# _#]
      (when (and (= key# ~key-code) (= action# 1))
        (~handler-fn)
        (event-interrupt)))))

(comment
  ;; 使用示例

  ;; 客户端生命周期期
  (on-client-started
   (fn [minecraft]
     (println "Client started!")))

  ;; 玩家加入
  (on-client-player-join
   (fn [player]
     (println "Joined world as" (.getName player))))

  ;; 鼠标点击
  (on-mouse-clicked
   (fn [minecraft button action mods]
     (when (and (= button 0) (= action 1))
       (println "Left click!")
       (event-pass))))

  ;; 键盘按键
  (on-key-pressed
   (fn [minecraft key scancode action mods]
     (when (= action 1)
       (println "Key pressed:" key))
     (event-pass)))

  ;; 特定按键
  (on-key org.lwjgl.glfw.GLFW/GLFW_KEY_H
          (fn []
            (println "H key pressed!")))

  ;; GUI 渲染
  (on-screen-render-post
   (fn [screen graphics mouse-x mouse-y delta]
     ;; 绘制自定UI
     (event-pass))))
