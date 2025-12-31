(ns com.example.hooks
  "示例 Mixin 钩子实现

   此命名空间包含由 Mixin 类调用的钩子函数。
   通过 ClojureBridge.invoke() 从 Java Mixin 调用这些函数。"
  (:import (net.minecraft.world.entity.player Player)
           (org.spongepowered.asm.mixin.injection.callback CallbackInfo)))

(defn on-player-jump
  "玩家跳跃钩子

   参数:
   - player: Player 实例
   - ci: CallbackInfo，可用于取消事件

   用法（取消跳跃）:
   (.cancel ci)"
  [^Player player ^CallbackInfo ci]
  ;; 示例：打印跳跃消息
  (println (str "[ExampleMod] Player jumped: " (.getName (.getGameProfile player))))

  ;; 示例：取消跳跃（取消注释下面的代码）
  ;; (.cancel ci)
  )
