(ns com.fabriclj.swiss-knife.client.keybindings
  "瑞士军刀 - 按键绑定模块

   封装 Architectury API 的按键绑定系统。

   注意：此命名空间仅在客户端环境可用！"
  (:require [com.fabriclj.swiss-knife.common.core :as core]
            [com.fabriclj.swiss-knife.client.events :as events])
  (:import [dev.architectury.registry.client.keymappings KeyMappingRegistry]
           [net.minecraft.client KeyMapping]
           [org.lwjgl.glfw GLFW]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 按键码常量
;; ============================================================================

(def key-codes
  "常用按键码映射"
  {:unknown GLFW/GLFW_KEY_UNKNOWN
   :space GLFW/GLFW_KEY_SPACE
   :apostrophe GLFW/GLFW_KEY_APOSTROPHE
   :comma GLFW/GLFW_KEY_COMMA
   :minus GLFW/GLFW_KEY_MINUS
   :period GLFW/GLFW_KEY_PERIOD
   :slash GLFW/GLFW_KEY_SLASH
   :0 GLFW/GLFW_KEY_0
   :1 GLFW/GLFW_KEY_1
   :2 GLFW/GLFW_KEY_2
   :3 GLFW/GLFW_KEY_3
   :4 GLFW/GLFW_KEY_4
   :5 GLFW/GLFW_KEY_5
   :6 GLFW/GLFW_KEY_6
   :7 GLFW/GLFW_KEY_7
   :8 GLFW/GLFW_KEY_8
   :9 GLFW/GLFW_KEY_9
   :semicolon GLFW/GLFW_KEY_SEMICOLON
   :equal GLFW/GLFW_KEY_EQUAL
   :a GLFW/GLFW_KEY_A
   :b GLFW/GLFW_KEY_B
   :c GLFW/GLFW_KEY_C
   :d GLFW/GLFW_KEY_D
   :e GLFW/GLFW_KEY_E
   :f GLFW/GLFW_KEY_F
   :g GLFW/GLFW_KEY_G
   :h GLFW/GLFW_KEY_H
   :i GLFW/GLFW_KEY_I
   :j GLFW/GLFW_KEY_J
   :k GLFW/GLFW_KEY_K
   :l GLFW/GLFW_KEY_L
   :m GLFW/GLFW_KEY_M
   :n GLFW/GLFW_KEY_N
   :o GLFW/GLFW_KEY_O
   :p GLFW/GLFW_KEY_P
   :q GLFW/GLFW_KEY_Q
   :r GLFW/GLFW_KEY_R
   :s GLFW/GLFW_KEY_S
   :t GLFW/GLFW_KEY_T
   :u GLFW/GLFW_KEY_U
   :v GLFW/GLFW_KEY_V
   :w GLFW/GLFW_KEY_W
   :x GLFW/GLFW_KEY_X
   :y GLFW/GLFW_KEY_Y
   :z GLFW/GLFW_KEY_Z
   :left-bracket GLFW/GLFW_KEY_LEFT_BRACKET
   :backslash GLFW/GLFW_KEY_BACKSLASH
   :right-bracket GLFW/GLFW_KEY_RIGHT_BRACKET
   :grave-accent GLFW/GLFW_KEY_GRAVE_ACCENT
   :escape GLFW/GLFW_KEY_ESCAPE
   :enter GLFW/GLFW_KEY_ENTER
   :tab GLFW/GLFW_KEY_TAB
   :backspace GLFW/GLFW_KEY_BACKSPACE
   :insert GLFW/GLFW_KEY_INSERT
   :delete GLFW/GLFW_KEY_DELETE
   :right GLFW/GLFW_KEY_RIGHT
   :left GLFW/GLFW_KEY_LEFT
   :down GLFW/GLFW_KEY_DOWN
   :up GLFW/GLFW_KEY_UP
   :page-up GLFW/GLFW_KEY_PAGE_UP
   :page-down GLFW/GLFW_KEY_PAGE_DOWN
   :home GLFW/GLFW_KEY_HOME
   :end GLFW/GLFW_KEY_END
   :caps-lock GLFW/GLFW_KEY_CAPS_LOCK
   :scroll-lock GLFW/GLFW_KEY_SCROLL_LOCK
   :num-lock GLFW/GLFW_KEY_NUM_LOCK
   :print-screen GLFW/GLFW_KEY_PRINT_SCREEN
   :pause GLFW/GLFW_KEY_PAUSE
   :f1 GLFW/GLFW_KEY_F1
   :f2 GLFW/GLFW_KEY_F2
   :f3 GLFW/GLFW_KEY_F3
   :f4 GLFW/GLFW_KEY_F4
   :f5 GLFW/GLFW_KEY_F5
   :f6 GLFW/GLFW_KEY_F6
   :f7 GLFW/GLFW_KEY_F7
   :f8 GLFW/GLFW_KEY_F8
   :f9 GLFW/GLFW_KEY_F9
   :f10 GLFW/GLFW_KEY_F10
   :f11 GLFW/GLFW_KEY_F11
   :f12 GLFW/GLFW_KEY_F12
   :left-shift GLFW/GLFW_KEY_LEFT_SHIFT
   :left-control GLFW/GLFW_KEY_LEFT_CONTROL
   :left-alt GLFW/GLFW_KEY_LEFT_ALT
   :left-super GLFW/GLFW_KEY_LEFT_SUPER
   :right-shift GLFW/GLFW_KEY_RIGHT_SHIFT
   :right-control GLFW/GLFW_KEY_RIGHT_CONTROL
   :right-alt GLFW/GLFW_KEY_RIGHT_ALT
   :right-super GLFW/GLFW_KEY_RIGHT_SUPER})

(def key-categories
  "按键分类"
  {:movement "key.categories.movement"
   :misc "key.categories.misc"
   :gameplay "key.categories.gameplay"
   :inventory "key.categories.inventory"
   :creative "key.categories.creative"
   :multiplayer "key.categories.multiplayer"})

;; ============================================================================
;; 按键绑定创建
;; ============================================================================

(defn create-keybinding
  "创建按键绑定

   参数:
   - name: 按键名称（翻译键，如 \"key.mymod.action\"）
   - key: 默认按键（GLFW 键码或关键字）
   - category: 分类（字符串或关键字）

   返回：KeyMapping

   示例:
   ```clojure
   (create-keybinding
     \"key.mymod.special_ability\"
     :r
     :gameplay)
   ```"
  ^KeyMapping [^String name key category]
  (let [key-code (if (keyword? key)
                   (get key-codes key GLFW/GLFW_KEY_UNKNOWN)
                   key)
        category-str (if (keyword? category)
                       (get key-categories category "key.categories.misc")
                       category)]
    (KeyMapping. name key-code category-str)))

(defn register-keybinding!
  "注册按键绑定

   参数:
   - keybinding: KeyMapping 实例

   示例:
   ```clojure
   (def my-key (create-keybinding \"key.mymod.action\" :r :gameplay))
   (register-keybinding! my-key)
   ```"
  [^KeyMapping keybinding]
  (KeyMappingRegistry/register keybinding)
  keybinding)

;; ============================================================================
;; 按键状态查询
;; ============================================================================

(defn is-key-down?
  "检查按键是否按下

   参数:
   - keybinding: KeyMapping 实例

   返回：boolean"
  [^KeyMapping keybinding]
  (.isDown keybinding))

(defn consume-click!
  "消费按键点击（标记为已处理）

   参数:
   - keybinding: KeyMapping 实例

   返回：是否有未处理的点击"
  [^KeyMapping keybinding]
  (.consumeClick keybinding))

(defn get-key-name
  "获取按键显示名称

   参数:
   - keybinding: KeyMapping 实例

   返回：本地化的按键名称"
  ^String [^KeyMapping keybinding]
  (.. keybinding getTranslatedKeyMessage getString))

;; ============================================================================
;; 便捷注册
;; ============================================================================

(defonce ^:private registered-keys (atom {}))

(defn defkey!
  "定义并注册按键绑定

   参数:
   - id: 按键标识符（关键字）
   - name: 翻译键
   - default-key: 默认按键
   - category: 分类
   - handler: 处理函数（可选）

   返回：KeyMapping

   示例:
   ```clojure
   (defkey! :special-ability
     \"key.mymod.special\"
     :r
     :gameplay
     (fn []
       (println \"Special ability activated!\")))

   ;; 使用
   (when (is-key-down? (get-keybinding :special-ability))
     ...)
   ```"
  ([id name default-key category]
   (defkey! id name default-key category nil))
  ([id name default-key category handler]
   (let [keybinding (create-keybinding name default-key category)]
     (register-keybinding! keybinding)
     (swap! registered-keys assoc id keybinding)
     (when handler
       (events/on-client-tick
        (fn [_]
          (when (consume-click! keybinding)
            (handler)))))
     keybinding)))

(defn get-keybinding
  "获取已注册的按键绑定

   参数:
   - id: 按键标识符（关键字）

   返回：KeyMapping 或 nil"
  ^KeyMapping [id]
  (get @registered-keys id))

(defn list-keybindings
  "列出所有已注册的按键绑定"
  []
  @registered-keys)

;; ============================================================================
;; 宏
;; ============================================================================

(defmacro defkeybinding
  "定义按键绑定（语法糖）

   示例:
   ```clojure
   (defkeybinding special-key \"key.mymod.special\" :r :gameplay
     (println \"Key pressed!\"))
   ```"
  [binding-name translation-key default-key category & body]
  `(def ~binding-name
     (defkey! ~(keyword binding-name)
       ~translation-key
       ~default-key
       ~category
       (fn [] ~@body))))

(defmacro when-key-pressed
  "当按键按下时执行代码

   示例:
   ```clojure
   (when-key-pressed my-key
     (println \"Key pressed!\"))
   ```"
  [keybinding & body]
  `(when (consume-click! ~keybinding)
     ~@body))

(comment
  ;; 使用示例

  ;; 1. 创建按键绑定
  (def ability-key
    (create-keybinding "key.mymod.special_ability" :r :gameplay))

  (register-keybinding! ability-key)

  ;; 2. 使用宏定义
  (defkey! :open-menu
    "key.mymod.open_menu"
    :v
    :misc
    (fn []
      (println "Opening menu...")))

  ;; 3. 在 tick 中检查按键
  (events/on-client-tick
   (fn [_]
     (when-key-pressed ability-key
                       (println "Ability activated!"))))

  ;; 4. 持续按下检测
  (events/on-client-tick
   (fn [_]
     (when (is-key-down? ability-key)
       (println "Key is being held down"))))

  ;; 5. 使用 defkeybinding 宏
  (defkeybinding fly-key "key.mymod.fly" :f :movement
    (println "Toggling flight mode")))
