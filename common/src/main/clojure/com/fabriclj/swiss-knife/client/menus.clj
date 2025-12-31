(ns com.fabriclj.swiss-knife.client.menus
  "瑞士军刀 - GUI/菜单系统模块

   提供自定义 GUI 屏幕的创建和管理功能。

   注意：此命名空间仅在客户端环境可用！"
  (:require [com.fabriclj.swiss-knife.common.core :as core]
            [com.fabriclj.swiss-knife.client.rendering :as render])
  (:import [net.minecraft.client.gui.screens Screen]
           [net.minecraft.client.gui.components Button Button$CreateNarration]
           [net.minecraft.client.gui.components.events GuiEventListener]
           [net.minecraft.client.gui GuiGraphics]
           [net.minecraft.network.chat Component]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 自定义屏幕创建
;; ============================================================================

(defn create-screen
  "创建自定义屏幕

   参数:
   - title: 标题（字符串或 Component）
   - init-fn: 初始化函数 (fn [screen] ...)
   - render-fn: 渲染函数 (fn [screen graphics mouse-x mouse-y delta] ...)
   - opts: 可选参数映射
     - :on-close - 关闭时回调 (fn [screen] ...)
     - :key-pressed - 按键回调 (fn [screen key scancode action mods] -> boolean)
     - :mouse-clicked - 鼠标点击回调 (fn [screen mouse-x mouse-y button] -> boolean)
     - :background - 是否绘制背景（默认 true）

   返回：Screen 实例

   示例:
   ```clojure
   (create-screen \"My GUI\"
     (fn [screen]
       ;; 添加按钮等组件
       (.addRenderableWidget screen my-button))
     (fn [screen graphics mouse-x mouse-y delta]
       ;; 绘制自定义内容
       (render/draw-string graphics \"Hello\" 10 10 0xFFFFFF))
     {:on-close (fn [_] (println \"Closed\"))
      :background true})
   ```"
  [title init-fn render-fn & [opts]]
  (let [title-component (if (string? title)
                          (Component/literal title)
                          title)
        {:keys [on-close key-pressed mouse-clicked background]
         :or {background true}} opts]
    (proxy [Screen] [title-component]
      (init []
        (proxy-super init)
        (when init-fn
          (init-fn this)))

      (render [^GuiGraphics graphics mouse-x mouse-y delta]
        (when background
          (.renderBackground this graphics mouse-x mouse-y delta))
        (when render-fn
          (render-fn this graphics mouse-x mouse-y delta))
        (proxy-super render graphics mouse-x mouse-y delta))

      (onClose []
        (when on-close
          (on-close this))
        (proxy-super onClose))

      (keyPressed [key scancode mods]
        (if key-pressed
          (key-pressed this key scancode mods)
          (proxy-super keyPressed key scancode mods)))

      (mouseClicked [mouse-x mouse-y button]
        (if mouse-clicked
          (mouse-clicked this mouse-x mouse-y button)
          (proxy-super mouseClicked mouse-x mouse-y button))))))

;; ============================================================================
;; 组件创建
;; ============================================================================

(defn create-button
  "创建按钮组件

   参数:
   - x, y: 位置
   - width, height: 尺寸
   - text: 按钮文本
   - on-press: 点击回调 (fn [button] ...)
   - opts: 可选参数
     - :tooltip - 工具提示文本
     - :enabled? - 是否启用（默认 true）

   返回：Button

   示例:
   ```clojure
   (create-button 10 10 100 20 \"Click Me\"
     (fn [button]
       (println \"Button clicked!\"))
     {:tooltip \"This is a button\"})
   ```"
  [x y width height text on-press & [opts]]
  (let [text-component (if (string? text)
                         (Component/literal text)
                         text)
        {:keys [tooltip enabled?] :or {enabled? true}} opts
        builder (Button/builder
                 text-component
                 (reify java.util.function.Consumer
                   (accept [_ button]
                     (on-press button))))]
    (-> builder
        (.bounds x y width height)
        (.build))))

(defn create-checkbox
  "创建复选框组件

   参数:
   - x, y: 位置
   - width, height: 尺寸
   - text: 文本
   - initial-value: 初始值（true/false）
   - on-change: 值改变回调 (fn [checkbox new-value] ...)

   返回：Checkbox

   示例:
   ```clojure
   (create-checkbox 10 10 150 20 \"Enable Feature\" false
     (fn [checkbox value]
       (println \"Checkbox is now\" value)))
   ```"
  [x y width height text initial-value on-change]
  (let [text-component (if (string? text)
                         (Component/literal text)
                         text)]
    (net.minecraft.client.gui.components.Checkbox/builder
     text-component
     (net.minecraft.client.Minecraft/getInstance)
     (.font))
    (.pos x y)
    (.selected initial-value)
    (.onValueChange (reify java.util.function.Consumer
                      (accept [_ value]
                        (on-change nil value))))
    (.build)))

(defn create-slider
  "创建滑块组件

   参数:
   - x, y: 位置
   - width, height: 尺寸
   - min-value: 最小值
   - max-value: 最大值
   - initial-value: 初始值
   - on-change: 值改变回调 (fn [slider value] ...)
   - opts: 可选参数
     - :format-fn - 格式化函数 (fn [value] -> string)

   示例:
   ```clojure
   (create-slider 10 10 200 20 0.0 100.0 50.0
     (fn [slider value]
       (println \"Value:\" value))
     {:format-fn #(str \"Volume: \" (int %) \"%\")})
   ```"
  [x y width height min-value max-value initial-value on-change & [opts]]
  (let [{:keys [format-fn] :or {format-fn str}} opts]
    (proxy [net.minecraft.client.gui.components.AbstractSliderButton]
           [x y width height
            (Component/literal (format-fn initial-value))
            (/ (- initial-value min-value) (- max-value min-value))]
      (updateMessage []
        (let [value (+ min-value (* (proxy-super getValue) (- max-value min-value)))]
          (proxy-super setMessage (Component/literal (format-fn value)))))
      (applyValue []
        (let [value (+ min-value (* (proxy-super getValue) (- max-value min-value)))]
          (on-change this value))))))

(defn create-text-field
  "创建文本输入框

   参数:
   - x, y: 位置
   - width, height: 尺寸
   - initial-text: 初始文本
   - opts: 可选参数
     - :max-length - 最大长度（默认 32）
     - :hint - 提示文本
     - :on-change - 文本改变回调

   示例:
   ```clojure
   (create-text-field 10 10 200 20 \"\"
     {:hint \"Enter name...\"
      :max-length 50
      :on-change (fn [field text]
                   (println \"Text:\" text))})
   ```"
  [x y width height initial-text & [opts]]
  (let [{:keys [max-length hint on-change] :or {max-length 32}} opts
        font (.font (net.minecraft.client.Minecraft/getInstance))
        field (net.minecraft.client.gui.components.EditBox.
               font x y width height (Component/literal ""))]
    (.setMaxLength field max-length)
    (.setValue field initial-text)
    (when hint
      (.setHint field (Component/literal hint)))
    (when on-change
      (.setResponder field
                     (reify java.util.function.Consumer
                       (accept [_ text]
                         (on-change field text)))))
    field))

(defn create-tab-button
  "创建标签页按钮

   参数:
   - x, y: 位置
   - width, height: 尺寸
   - text: 标签文本
   - active?: 是否激活
   - on-click: 点击回调

   返回：Button"
  [x y width height text active? on-click]
  (let [text-component (if (string? text)
                         (Component/literal text)
                         text)
        builder (Button/builder
                 text-component
                 (reify java.util.function.Consumer
                   (accept [_ button]
                     (on-click button))))]
    (-> builder
        (.bounds x y width height)
        (.build))))

(defn create-label
  "创建文本标签（仅用于在屏幕中显示）

   返回一个渲染函数，需要在 render-fn 中调用

   示例:
   ```clojure
   (def my-label (create-label \"Hello World\" 10 10 0xFFFFFF))

   ;; 在渲染函数中
   (fn [screen graphics mouse-x mouse-y delta]
     (my-label graphics))
   ```"
  [text x y color]
  (fn [^GuiGraphics graphics]
    (render/draw-string graphics text x y color)))

;; ============================================================================
;; 屏幕管理
;; ============================================================================

(defn open-screen!
  "打开屏幕

   参数:
   - screen: Screen 实例"
  [^Screen screen]
  (let [minecraft (net.minecraft.client.Minecraft/getInstance)]
    (.setScreen minecraft screen)))

(defn close-screen!
  "关闭当前屏幕"
  []
  (let [minecraft (net.minecraft.client.Minecraft/getInstance)]
    (.setScreen minecraft nil)))

(defn get-current-screen
  "获取当前屏幕

   返回：Screen 或 nil"
  ^Screen []
  (.screen (net.minecraft.client.Minecraft/getInstance)))

;; ============================================================================
;; 便捷屏幕模板
;; ============================================================================

(defn create-simple-menu
  "创建简单菜单屏幕（标题 + 按钮列表）

   参数:
   - title: 标题
   - buttons: 按钮列表 [{:text text :action fn} ...]
   - opts: 可选参数
     - :button-width - 按钮宽度（默认 200）
     - :button-height - 按钮高度（默认 20）
     - :spacing - 按钮间距（默认 24）

   示例:
   ```clojure
   (create-simple-menu \"Main Menu\"
     [{:text \"Start Game\" :action #(println \"Start!\")}
      {:text \"Options\" :action #(println \"Options\")}
      {:text \"Quit\" :action close-screen!}]
     {:button-width 200})
   ```"
  [title buttons & [opts]]
  (let [{:keys [button-width button-height spacing]
         :or {button-width 200
              button-height 20
              spacing 24}} opts]
    (create-screen title
                   (fn [screen]
                     (let [center-x (/ (.width screen) 2)
                           start-y (/ (.height screen) 2)
                           button-x (- center-x (/ button-width 2))]
                       (doseq [[idx {:keys [text action]}] (map-indexed vector buttons)]
                         (let [y (+ start-y (* idx spacing))
                               button (create-button button-x y button-width button-height
                                                     text
                                                     (fn [_] (action)))]
                           (.addRenderableWidget screen button)))))
                   (fn [screen graphics mouse-x mouse-y delta]
                     (let [title-width (.. screen font (width title))
                           center-x (/ (.width screen) 2)
                           title-x (- center-x (/ title-width 2))]
                       (render/draw-string graphics title title-x 20 0xFFFFFF)))
                   {:background true})))

(defn create-confirmation-dialog
  "创建确认对话框

   参数:
   - title: 标题
   - message: 消息文本
   - on-confirm: 确认回调
   - on-cancel: 取消回调（可选）

   示例:
   ```clojure
   (create-confirmation-dialog
     \"Confirm\"
     \"Are you sure?\"
     (fn [] (println \"Confirmed!\"))
     (fn [] (println \"Cancelled\")))
   ```"
  [title message on-confirm & [on-cancel]]
  (create-simple-menu title
                      [{:text "Confirm" :action (fn []
                                                  (on-confirm)
                                                  (close-screen!))}
                       {:text "Cancel" :action (fn []
                                                 (when on-cancel (on-cancel))
                                                 (close-screen!))}]
                      {}))

;; ============================================================================
;; 宏
;; ============================================================================

(defmacro defscreen
  "定义自定义屏幕（语法糖）

   示例:
   ```clojure
   (defscreen my-screen \"My Screen\"
     :init (fn [screen]
             (println \"Initializing...\"))
     :render (fn [screen graphics mx my delta]
               (render/draw-string graphics \"Content\" 10 10 0xFFFFFF))
     :on-close (fn [screen]
                 (println \"Closed\")))
   ```"
  [screen-name title & {:keys [init render on-close key-pressed mouse-clicked background]
                        :or {background true}}]
  `(def ~screen-name
     (create-screen ~title
                    ~init
                    ~render
                    {:on-close ~on-close
                     :key-pressed ~key-pressed
                     :mouse-clicked ~mouse-clicked
                     :background ~background})))

(comment
  ;; 使用示例

  ;; 1. 创建简单屏幕
  (def my-screen
    (create-screen "My GUI"
                   (fn [screen]
                     (let [button (create-button 10 10 100 20 "Click Me"
                                                 (fn [_]
                                                   (println "Clicked!")
                                                   (close-screen!)))]
                       (.addRenderableWidget screen button)))
                   (fn [screen graphics mx my delta]
                     (render/draw-centered-string graphics "Custom GUI"
                                                  (/ (.width screen) 2) 50 0xFFFFFF))
                   {:background true}))

  (open-screen! my-screen)

  ;; 2. 使用简单菜单模板
  (def main-menu
    (create-simple-menu "Main Menu"
                        [{:text "Play" :action #(println "Starting game...")}
                         {:text "Options" :action #(println "Opening options...")}
                         {:text "Quit" :action close-screen!}]))

  (open-screen! main-menu)

  ;; 3. 确认对话框
  (def confirm-dialog
    (create-confirmation-dialog
     "Delete Item?"
     "Are you sure you want to delete this item?"
     (fn [] (println "Item deleted!"))
     (fn [] (println "Cancelled"))))

  (open-screen! confirm-dialog)

  ;; 4. 使用宏定义
  (defscreen settings-screen "Settings"
    :init (fn [screen]
            (println "Settings initialized"))
    :render (fn [screen graphics mx my delta]
              (render/draw-string graphics "Settings Here" 10 10 0xFFFFFF))
    :on-close (fn [_]
                (println "Settings closed")))

  (open-screen! settings-screen))
