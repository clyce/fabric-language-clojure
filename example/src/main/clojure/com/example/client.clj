(ns com.example.client
  "示例 Clojure mod 客户端入口

  展示客户端专用功能:
  - 按键绑定( R 键触发特殊能力)
  - HUD 渲染( 显示魔法能量条)
  - 粒子效果( 使用宝石时的视觉效果) "
  (:require [com.fabriclj.swiss-knife.common.lifecycle :as lifecycle]
            [com.fabriclj.swiss-knife.common.network.core :as net]
            [com.fabriclj.swiss-knife.client.platform.core :as client]
            [com.fabriclj.swiss-knife.client.ui.keybindings :as keys]
            [com.fabriclj.swiss-knife.client.rendering.hud :as hud]
            [com.fabriclj.swiss-knife.client.rendering.particles :as particles]
            [com.fabriclj.swiss-knife.common.game-objects.players :as players]
            [com.fabriclj.swiss-knife.common.game-objects.items :as items]))

;; ============================================================================
;; 按键绑定 - R 键触发特殊能力( 传送)
;; ============================================================================

(defn setup-keybindings!
  "设置按键绑定"
  []
  ;; 注册 R 键为特殊能力键
  (keys/defkey! :special-ability
    "key.example.special_ability"
    :r
    :gameplay
    (fn []
      (when-let [player (client/get-player)]
        ;; 检查玩家是否持有魔法宝石( 使用 swiss-knife)
        (let [main-hand (players/get-main-hand-item player)
              item (.getItem main-hand)]
          (if (= (.getDescriptionId item) "item.example.magic_gem")
            (do
              ;; 发送数据包到服务端请求传送
              (net/send-generic! "example" :special-ability {})
              (println "[ExampleMod/Client] 发送特殊能力请求"))
            (println "[ExampleMod/Client] 需要手持魔法宝石才能使用特殊能力！")))))))

;; ============================================================================
;; HUD 渲染 - 显示魔法能量条
;; ============================================================================

(defn render-magic-energy-hud
  "渲染魔法能量 HUD - 显示魔法宝石的耐久度"
  [graphics _delta]
  (when-let [player (client/get-player)]
    ;; 检查玩家主手是否持有魔法宝石( 使用 swiss-knife)
    (let [main-hand (players/get-main-hand-item player)
          item (.getItem main-hand)]
      (when (= (.getDescriptionId item) "item.example.magic_gem")
        (let [;; 获取耐久度信息( 使用 swiss-knife)
              current-durability (items/get-durability main-hand)
              max-damage (items/get-max-damage main-hand)
              durability-ratio (items/get-durability-ratio main-hand)

              ;; HUD 位置( 屏幕左下角)
              screen-height (client/window-height)
              bar-x 10
              bar-y (- screen-height 30)
              bar-width 100
              bar-height 10

              ;; 颜色: 根据耐久度变化( 绿->黄->红)
              bar-color (cond
                          (> durability-ratio 0.6) 0xFF00FF00  ; 绿色
                          (> durability-ratio 0.3) 0xFFFFFF00  ; 黄色
                          :else 0xFFFF0000)]                   ; 红色

          ;; 绘制能量条
          (hud/draw-bar-hud graphics
                           bar-x bar-y
                           bar-width bar-height
                           (double current-durability) (double max-damage)
                           bar-color)

          ;; 绘制文本标签
          (hud/draw-text-hud graphics
                            (str "魔法能量: " current-durability "/" max-damage)
                            (+ bar-x (/ bar-width 2))
                            (- bar-y 12)
                            0xFFFFFF))))))

(defn setup-hud!
  "设置 HUD 渲染器"
  []
  (hud/register-hud-renderer! render-magic-energy-hud 0 :magic-energy))

;; ============================================================================
;; 粒子效果 - 使用魔法宝石时的视觉效果
;; ============================================================================

(defn spawn-shoot-particles
  "在指定位置生成魔法弹发射粒子效果"
  [pos]
  (when-let [level (client/get-level)]
    (let [[x y z] pos
          eye-pos [x (+ y 1.6) z]]  ; 玩家眼睛高度
      ;; 生成环形粒子效果( 发射瞬间)
      (particles/circle-particles! :portal eye-pos 0.5 15)

      ;; 生成向前的粒子流
      (dotimes [i 5]
        (let [offset (* i 0.3)]
          (particles/spawn-particle! level :enchant
            (+ x (* offset 0.2)) (+ y 1.6 (* offset 0.1)) (+ z (* offset 0.2))
            0.0 0.0 0.0)))

      (println "[ExampleMod/Client] 生成魔法弹发射粒子效果"))))

;; ============================================================================
;; 主初始化函数
;; ============================================================================

(defn init-client
  "客户端初始化函数 - 由 Java 客户端入口点调用"
  []
  (println "[ExampleMod/Client] ============================================")
  (println "[ExampleMod/Client] 客户端正在初始化...")

  ;; 1. 统一初始化客户端系统
  (println "[ExampleMod/Client] 初始化 Swiss Knife 客户端系统...")
  (lifecycle/init-client! "example"
    {:enable-hud? true
     :enable-debug? false})

  ;; 2. 设置按键绑定
  (println "[ExampleMod/Client] 注册按键绑定...")
  (setup-keybindings!)

  ;; 3. 设置 HUD 渲染
  (println "[ExampleMod/Client] 注册 HUD 渲染器...")
  (setup-hud!)

  (println "[ExampleMod/Client] 客户端初始化完成！")
  (println "[ExampleMod/Client] ============================================"))

;; ============================================================================
;; REPL 测试代码
;; ============================================================================

(comment
  ;; 在 nREPL 中测试客户端功能
  (client/get-player)
  (client/get-level)
  (client/window-width)
  (client/window-height)

  ;; 测试粒子效果( 需要在游戏中，且有玩家)
  (when-let [player (client/get-player)]
    (let [pos [(.getX player) (.getY player) (.getZ player)]]
      (spawn-shoot-particles pos)))

  ;; 重新注册 HUD
  (setup-hud!)
  )
