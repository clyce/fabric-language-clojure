(ns com.arclojure.hooks
  "Arclojure Mixin 钩子命名空间

   此命名空间为 Java Mixin 类提供静态入口点。
   遵循「Java 代理模式」：
   1. Java Mixin 负责字节码注入
   2. Mixin 方法调用此命名空间的函数
   3. 具体逻辑在 Clojure 中实现

   【重要】此命名空间的所有公共函数都应设计为可被 Java 静态调用。"
  (:import [net.minecraft.world.entity.player Player]
           [org.spongepowered.asm.mixin.injection.callback CallbackInfo]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 玩家相关钩子
;; ============================================================================

(defn on-player-jump
  "玩家跳跃事件钩子

   由 MixinPlayer 的 @Inject 方法调用。

   参数：
   - player: 玩家实体
   - ci: Mixin CallbackInfo，可用于取消事件

   示例用法（在 Java Mixin 中）：
   ```java
   @Inject(method = \"jump\", at = @At(\"HEAD\"), cancellable = true)
   private void onJump(CallbackInfo ci) {
       ClojureHooks.onPlayerJump((Player)(Object)this, ci);
   }
   ```"
  [^Player player ^CallbackInfo ci]
  ;; 默认实现：不做任何事
  ;; 可在此添加自定义跳跃逻辑
  nil)

(defn on-player-tick
  "玩家 Tick 事件钩子

   每游戏刻（1/20 秒）调用一次。

   【性能警告】此函数在热路径上，避免使用惰性序列和反射。

   参数：
   - player: 玩家实体"
  [^Player player]
  ;; 默认实现：不做任何事
  nil)

;; ============================================================================
;; 事件处理器注册（供 Architectury 事件系统使用）
;; ============================================================================

(defn register-event-handlers!
  "注册 Architectury 事件处理器

   此函数应在模组初始化时调用。"
  []
  ;; TODO: 使用 Architectury 的事件 API 注册事件
  ;; 示例：
  ;; (-> (LifecycleEvent/SERVER_STARTING)
  ;;     (.register (reify Consumer
  ;;                  (accept [_ server]
  ;;                    (on-server-starting server)))))
  nil)
