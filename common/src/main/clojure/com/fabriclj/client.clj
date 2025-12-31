(ns com.fabriclj.client
  "fabric-language-clojure 客户端工具

   此命名空间提供客户端相关的工具函数。

   【注意】此命名空间中的函数只能在客户端环境调用！"
  (:import [net.minecraft.client Minecraft]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 客户端访问
;; ============================================================================

(defn minecraft
  "获取 Minecraft 客户端实例

   【警告】仅在客户端环境调用！"
  ^Minecraft []
  (Minecraft/getInstance))

(defn player
  "获取当前玩家

   返回 LocalPlayer 实例，如果未在游戏中则返回 nil"
  []
  (when-let [mc (minecraft)]
   (.player mc)))

(defn level
  "获取当前世界/关卡

   返回 ClientLevel 实例，如果未在游戏中则返回 nil"
  []
  (when-let [mc (minecraft)]
    (.level mc)))

(defn in-game?
  "检查玩家是否在游戏中"
  []
  (some? (player)))

;; ============================================================================
;; 初始化
;; ============================================================================

(defn init-client
  "客户端初始化函数

   此函数可由用户 mod 的客户端入口点调用。"
  []
  (println "[fabric-language-clojure] Client utilities initialized"))
