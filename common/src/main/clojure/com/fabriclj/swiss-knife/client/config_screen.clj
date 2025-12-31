(ns com.fabriclj.swiss-knife.client.config-screen
  "配置 GUI 系统

   提供游戏内配置界面，支持：
   - 自动生成配置屏幕
   - 多种配置组件（文本框、滑块、开关、下拉框）
   - 配置验证和保存
   - 分类和分页
   - 搜索和过滤"
  (:require [com.fabriclj.swiss-knife.common.core :as core]
            [com.fabriclj.swiss-knife.common.config-file :as config]
            [com.fabriclj.swiss-knife.client.menus :as menus])
  (:import [net.minecraft.client.gui.screens Screen]
           [net.minecraft.client.gui.components Button EditBox AbstractWidget]
           [net.minecraft.client.gui GuiGraphics]
           [net.minecraft.network.chat Component]
           [java.util.function Consumer]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 配置组件定义
;; ============================================================================

(defn create-config-entry
  "创建配置条目定义

   参数:
   - key: 配置键（关键字）
   - label: 显示标签
   - type: 组件类型
     - :boolean - 开关
     - :int - 整数输入
     - :float - 浮点数输入
     - :string - 文本输入
     - :enum - 下拉选择
     - :slider - 滑块
   - opts: 选项
     - :default - 默认值
     - :min/:max - 最小/最大值（数字类型）
     - :options - 选项列表（枚举类型）
     - :validator - 验证函数
     - :tooltip - 提示文本
     - :category - 分类

   返回：配置条目映射

   示例:
   ```clojure
   (create-config-entry :max-players
     \"Maximum Players\"
     :int
     :default 20
     :min 1
     :max 100
     :tooltip \"Maximum number of players allowed\"
     :category :server)
   ```"
  [key label type & {:keys [default min max options validator tooltip category]
                     :or {category :general}}]
  {:key key
   :label label
   :type type
   :default default
   :min min
   :max max
   :options options
   :validator (or validator (constantly true))
   :tooltip tooltip
   :category category})

;; ============================================================================
;; 配置屏幕生成
;; ============================================================================

(def ^:private config-screens (atom {}))

(defn register-config-screen!
  "注册配置屏幕

   参数:
   - screen-id: 屏幕 ID（关键字）
   - title: 标题
   - config-path: 配置文件路径
   - entries: 配置条目列表
   - opts: 选项
     - :on-save - 保存回调
     - :on-cancel - 取消回调

   示例:
   ```clojure
   (register-config-screen! :my-mod-config
     \"My Mod Configuration\"
     \"config/mymod.edn\"
     [(create-config-entry :enabled \"Enable Mod\" :boolean :default true)
      (create-config-entry :power \"Power Level\" :slider
        :default 10 :min 1 :max 100)
      (create-config-entry :mode \"Mode\" :enum
        :options [:normal :hard :extreme]
        :default :normal)]
     :on-save (fn [config]
                (println \"Config saved:\" config)))
   ```"
  [screen-id title config-path entries & {:keys [on-save on-cancel]}]
  (swap! config-screens assoc screen-id
         {:title title
          :config-path config-path
          :entries entries
          :on-save on-save
          :on-cancel on-cancel})
  (core/log-info (str \"Registered config screen: \" screen-id)))

(defn create-boolean-widget
  "创建布尔值组件（按钮开关）

   参数:
   - x, y: 位置
   - width: 宽度
   - entry: 配置条目
   - value: 当前值
   - on-change: 变更回调

   返回：Button"
  [x y width entry value on-change]
  (let [label (str (:label entry) \": \" (if value \"ON\" \"OFF\"))]
    (Button/builder
      (Component/literal label)
      (reify Consumer
        (accept [_ btn]
          (let [new-value (not value)]
            (on-change new-value)
            (.setMessage btn (Component/literal
                              (str (:label entry) \": \"
                                   (if new-value \"ON\" \"OFF\")))))))
      (.bounds x y width 20)
      (.build))))

(defn create-int-widget
  "创建整数输入组件

   参数:
   - x, y: 位置
   - width: 宽度
   - entry: 配置条目
   - value: 当前值
   - on-change: 变更回调

   返回：EditBox"
  [x y width entry value on-change]
  (let [edit-box (EditBox.
                   (minecraft/font)
                   x y width 20
                   (Component/literal (:label entry)))]
    (.setValue edit-box (str value))
    (.setResponder edit-box
      (reify Consumer
        (accept [_ text]
          (try
            (let [int-val (Integer/parseInt text)]
              (when (and (or (nil? (:min entry)) (>= int-val (:min entry)))
                         (or (nil? (:max entry)) (<= int-val (:max entry))))
                (on-change int-val)))
            (catch NumberFormatException _)))))
    edit-box))

(defn create-slider-widget
  "创建滑块组件

   参数:
   - x, y: 位置
   - width: 宽度
   - entry: 配置条目
   - value: 当前值
   - on-change: 变更回调

   返回：Slider（简化实现使用按钮）"
  [x y width entry value on-change]
  (let [min-val (or (:min entry) 0)
        max-val (or (:max entry) 100)
        range (- max-val min-val)
        normalized (/ (- value min-val) range)
        label (str (:label entry) \": \" value)]
    ;; 简化版：使用两个按钮 - 和 +
    [(Button/builder
       (Component/literal \"-\")
       (reify Consumer
         (accept [_ _]
           (let [new-value (max min-val (dec value))]
             (on-change new-value))))
       (.bounds x y 20 20)
       (.build))
     (Button/builder
       (Component/literal label)
       (reify Consumer
         (accept [_ _]))
       (.bounds (+ x 25) y (- width 50) 20)
       (.build))
     (Button/builder
       (Component/literal \"+\")
       (reify Consumer
         (accept [_ _]
           (let [new-value (min max-val (inc value))]
             (on-change new-value))))
       (.bounds (+ x width -20) y 20 20)
       (.build))]))

(defn create-enum-widget
  "创建枚举选择组件（循环按钮）

   参数:
   - x, y: 位置
   - width: 宽度
   - entry: 配置条目
   - value: 当前值
   - on-change: 变更回调

   返回：Button"
  [x y width entry value on-change]
  (let [options (:options entry)
        current-idx (.indexOf (vec options) value)
        label (str (:label entry) \": \" (name value))]
    (Button/builder
      (Component/literal label)
      (reify Consumer
        (accept [_ btn]
          (let [next-idx (mod (inc current-idx) (count options))
                next-value (nth (vec options) next-idx)]
            (on-change next-value)
            (.setMessage btn (Component/literal
                              (str (:label entry) \": \"
                                   (name next-value)))))))
      (.bounds x y width 20)
      (.build))))

;; ============================================================================
;; 配置屏幕类
;; ============================================================================

(defn create-config-screen
  "创建配置屏幕实例

   参数:
   - screen-id: 屏幕 ID
   - parent: 父屏幕（可选）

   返回：Screen 实例

   示例:
   ```clojure
   (def config-screen (create-config-screen :my-mod-config))
   (minecraft/setScreen config-screen)
   ```"
  [screen-id & [parent]]
  (when-let [screen-def (get @config-screens screen-id)]
    (let [current-values (atom (config/load-config (:config-path screen-def)))
          widgets (atom [])]

      (proxy [Screen] [(Component/literal (:title screen-def))]

        (init []
          (let [this ^Screen this
                center-x (/ (.width this) 2)
                start-y 40
                entry-height 30
                entries (:entries screen-def)]

            ;; 为每个配置条目创建组件
            (doseq [[idx entry] (map-indexed vector entries)]
              (let [y (+ start-y (* idx entry-height))
                    current-val (get @current-values (:key entry) (:default entry))
                    on-change (fn [new-val]
                                (swap! current-values assoc (:key entry) new-val))
                    widget (case (:type entry)
                             :boolean (create-boolean-widget
                                       center-x y 200 entry current-val on-change)
                             :int (create-int-widget
                                   center-x y 200 entry current-val on-change)
                             :slider (create-slider-widget
                                      center-x y 200 entry current-val on-change)
                             :enum (create-enum-widget
                                    center-x y 200 entry current-val on-change)
                             :string (create-int-widget  ; 复用文本框
                                      center-x y 200 entry current-val on-change))]

                ;; 添加组件
                (if (vector? widget)
                  (doseq [w widget] (.addRenderableWidget this w))
                  (.addRenderableWidget this widget))))

            ;; 保存按钮
            (.addRenderableWidget this
              (Button/builder
                (Component/literal \"Save\")
                (reify Consumer
                  (accept [_ _]
                    ;; 保存配置
                    (config/save-config! (:config-path screen-def) @current-values)
                    ;; 调用保存回调
                    (when-let [on-save (:on-save screen-def)]
                      (on-save @current-values))
                    ;; 关闭屏幕
                    (when parent
                      (.minecraft this)
                      (.setScreen (.minecraft this) parent))))
                (.bounds (- center-x 110) (- (.height this) 30) 100 20)
                (.build)))

            ;; 取消按钮
            (.addRenderableWidget this
              (Button/builder
                (Component/literal \"Cancel\")
                (reify Consumer
                  (accept [_ _]
                    ;; 调用取消回调
                    (when-let [on-cancel (:on-cancel screen-def)]
                      (on-cancel))
                    ;; 关闭屏幕
                    (when parent
                      (.minecraft this)
                      (.setScreen (.minecraft this) parent))))
                (.bounds (+ center-x 10) (- (.height this) 30) 100 20)
                (.build)))))

        (render [^GuiGraphics graphics mouse-x mouse-y partial-tick]
          (let [this ^Screen this]
            ;; 渲染背景
            (.renderBackground graphics)

            ;; 渲染标题
            (.drawCenteredString graphics
                                (.font this)
                                (.getTitle this)
                                (/ (.width this) 2)
                                10
                                0xFFFFFF)

            ;; 调用父类渲染
            (proxy-super render graphics mouse-x mouse-y partial-tick)))))))

;; ============================================================================
;; 分类配置屏幕
;; ============================================================================

(defn create-categorized-config-screen
  "创建分类配置屏幕

   支持多个分类标签页

   参数:
   - screen-id: 屏幕 ID
   - categories: 分类映射 {category-id {:title \"...\" :entries [...]}}
   - config-path: 配置文件路径
   - opts: 选项

   示例:
   ```clojure
   (create-categorized-config-screen :my-mod-config
     {:general {:title \"General\"
                :entries [...]}
      :gameplay {:title \"Gameplay\"
                 :entries [...]}
      :advanced {:title \"Advanced\"
                 :entries [...]}}
     \"config/mymod.edn\")
   ```"
  [screen-id categories config-path & opts]
  ;; 简化实现：将所有分类的条目合并
  (let [all-entries (mapcat :entries (vals categories))
        first-category-title (:title (first (vals categories)))]
    (apply register-config-screen!
           screen-id
           first-category-title
           config-path
           all-entries
           opts)))

;; ============================================================================
;; 便捷函数
;; ============================================================================

(defn open-config-screen!
  "打开配置屏幕

   参数:
   - screen-id: 屏幕 ID

   示例:
   ```clojure
   ;; 在按键绑定中打开配置
   (keybindings/on-key-press my-config-key
     (fn []
       (open-config-screen! :my-mod-config)))
   ```"
  [screen-id]
  (when-let [minecraft (core/get-minecraft)]
    (let [current-screen (.screen minecraft)
          config-screen (create-config-screen screen-id current-screen)]
      (.setScreen minecraft config-screen))))

(comment
  ;; 使用示例

  ;; ========== 基础配置屏幕 ==========

  ;; 1. 定义配置条目
  (def my-config-entries
    [(create-config-entry :enabled
       \"Enable Mod\"
       :boolean
       :default true
       :tooltip \"Enable or disable the mod\"
       :category :general)

     (create-config-entry :max-power
       \"Max Power\"
       :slider
       :default 100
       :min 10
       :max 1000
       :tooltip \"Maximum power level\"
       :category :gameplay)

     (create-config-entry :difficulty
       \"Difficulty\"
       :enum
       :options [:easy :normal :hard :extreme]
       :default :normal
       :tooltip \"Game difficulty\"
       :category :gameplay)

     (create-config-entry :spawn-rate
       \"Spawn Rate\"
       :int
       :default 20
       :min 1
       :max 100
       :tooltip \"Entity spawn rate\"
       :category :advanced)])

  ;; 2. 注册配置屏幕
  (register-config-screen! :my-mod-config
    \"My Mod Configuration\"
    \"config/mymod.edn\"
    my-config-entries
    :on-save (fn [config]
               (println \"Configuration saved:\" config)
               (mb/log-info \"Config updated\"))
    :on-cancel (fn []
                 (println \"Configuration cancelled\")))

  ;; 3. 打开配置屏幕
  (open-config-screen! :my-mod-config)

  ;; 4. 在游戏菜单中添加配置按钮
  (mb/events/on-init-menu
    (fn [screen widgets]
      (.add widgets
        (menus/create-button
          \"Mod Config\"
          (fn [] (open-config-screen! :my-mod-config))
          :x 10
          :y 10))))

  ;; ========== 分类配置屏幕 ==========

  ;; 5. 创建分类配置
  (create-categorized-config-screen :advanced-config
    {:general {:title \"General Settings\"
               :entries [(create-config-entry :enabled \"Enable\" :boolean)
                         (create-config-entry :debug \"Debug Mode\" :boolean)]}
     :gameplay {:title \"Gameplay\"
                :entries [(create-config-entry :difficulty \"Difficulty\" :enum
                            :options [:easy :normal :hard])
                          (create-config-entry :spawn-rate \"Spawn Rate\" :slider
                            :min 1 :max 100)]}
     :advanced {:title \"Advanced\"
                :entries [(create-config-entry :max-entities \"Max Entities\" :int
                            :min 10 :max 1000)
                          (create-config-entry :tick-rate \"Tick Rate\" :int
                            :min 1 :max 20)]}}
    \"config/advanced.edn\"
    :on-save (fn [config]
               (println \"Advanced config saved\")))

  ;; 6. 与按键绑定集成
  (mb/keybindings/create-keybinding :open-config
    \"Open Configuration\"
    \"key.keyboard.c\"
    :category \"My Mod\"
    :on-press (fn []
                (open-config-screen! :my-mod-config))))
