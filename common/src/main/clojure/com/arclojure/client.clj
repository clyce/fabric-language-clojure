(ns com.arclojure.client
  "Arclojure 客户端命名空间

   此命名空间包含客户端专用的逻辑，如渲染、GUI、按键绑定等。
   仅在客户端环境下加载。"
  (:require [com.arclojure.core :as core]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 客户端初始化
;; ============================================================================

(defn init-client
  "客户端初始化函数

   此函数由 Java 引导类在客户端环境下调用。
   负责注册客户端专用的内容，如：
   - 渲染器
   - GUI 界面
   - 按键绑定
   - 粒子效果"
  []
  (println "[Arclojure/Clojure] Client initializing...")

  ;; TODO: 在此添加客户端初始化逻辑
  ;; 示例：
  ;; (register-renderers!)
  ;; (register-keybindings!)
  ;; (register-screens!)

  (println "[Arclojure/Clojure] Client initialized!"))

;; ============================================================================
;; 客户端工具函数
;; ============================================================================

(defn get-minecraft
  "获取 Minecraft 客户端实例"
  []
  (net.minecraft.client.Minecraft/getInstance))

(defn get-player
  "获取本地玩家实例（可能为 nil）"
  []
  (when-let [mc (get-minecraft)]
    (.player ^net.minecraft.client.Minecraft mc)))

(defn send-chat-message
  "向聊天栏发送消息（仅本地可见）"
  [^String message]
  (when-let [mc (get-minecraft)]
    (when-let [gui (.gui ^net.minecraft.client.Minecraft mc)]
      (when-let [chat (.getChat gui)]
        (.addMessage chat (net.minecraft.network.chat.Component/literal message))))))
