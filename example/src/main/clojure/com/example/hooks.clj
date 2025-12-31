(ns com.example.hooks
  "示例 Mixin 钩子实现

   此命名空间包含由 Mixin 类调用的钩子函数。
   通过 ClojureBridge.invoke() 从 Java Mixin 调用这些函数。

   展示功能：
   - 玩家手持魔法宝石跳跃时，获得跳跃提升效果"
  (:require [com.fabriclj.swiss-knife.common.game-objects.players :as players])
  (:import (net.minecraft.world.entity.player Player)
           (net.minecraft.world.effect MobEffects MobEffectInstance)
           (org.spongepowered.asm.mixin.injection.callback CallbackInfo)))

(defn on-player-jump
  "玩家跳跃钩子 - 手持魔法宝石时给予跳跃提升效果

   参数:
   - player: Player 实例
   - ci: CallbackInfo，可用于取消事件

   功能：
   - 检测玩家主手是否持有魔法宝石
   - 如果持有，给予 3 秒的跳跃提升 II 效果
   - 播放魔法音效"
  [^Player player ^CallbackInfo ci]
  (try
   ;; 获取玩家主手物品（使用 swiss-knife）
   (let [main-hand (players/get-main-hand-item player)
         item (.getItem main-hand)]

     ;; 检查是否持有魔法宝石
     (when (= (.getDescriptionId item) "item.example.magic_gem")
       ;; 给予跳跃提升效果（3 秒，等级 II）
       (let [jump-boost (MobEffectInstance. MobEffects/JUMP
                                            60   ; 持续时间（ticks）= 3 秒
                                            1    ; 等级 - 1 = II 级
                                            false ; 不是环境效果
                                            false)] ; 不显示粒子
         (.addEffect player jump-boost))

       ;; 发送提示消息
       (players/send-message! player
                              ((requiring-resolve 'com.fabriclj.swiss-knife.common.utils.text/literal)
                               "✨ 魔法跳跃！" :color :light-purple))

       ;; 播放音效
       (let [level (.level player)
             pos (.position player)]
         ((requiring-resolve 'com.fabriclj.swiss-knife.common.gameplay.sounds/play-sound!)
          level pos :minecraft:entity.ender_dragon.flap
          {:source :player :volume 0.3 :pitch 2.0}))

       (println "[ExampleMod/Hooks] 魔法跳跃激活："
                (.getName (.getGameProfile player)))))

   (catch Exception e
     ;; 错误处理：避免 Mixin 崩溃
     (println "[ExampleMod/Hooks] 错误：" (.getMessage e))
     (.printStackTrace e))))

;; ============================================================================
;; REPL 测试代码
;; ============================================================================

(comment
  ;; 在 nREPL 中测试
  ;; 注意：Mixin 钩子函数由游戏引擎调用，不能直接测试
  ;; 但可以测试其依赖的功能

  (require '[com.fabriclj.swiss-knife.common.game-objects.players :as p])
  (require '[com.fabriclj.swiss-knife.client.platform.core :as c])

  ;; 测试玩家消息发送
  (when-let [player (c/get-player)]
    (p/send-message! player
                     ((requiring-resolve 'com.fabriclj.swiss-knife.common.utils.text/literal)
                      "测试消息" :color :gold)))

  ;; 热重载后需要清除 ClojureBridge 缓存
  (com.fabriclj.ClojureBridge/clearCache "com.example.hooks")
  )
