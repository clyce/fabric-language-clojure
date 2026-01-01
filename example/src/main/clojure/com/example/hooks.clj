(ns com.example.hooks
  "示例 Mixin 钩子实现

   此命名空间包含由 Mixin 类调用的钩子函数。
   通过 ClojureBridge.invoke() 从 Java Mixin 调用这些函数。

   展示功能:
   - 玩家手持魔法宝石跳跃时，获得跳跃提升效果
   - 弹道命中树叶时召唤森林守卫"
  (:require [com.fabriclj.swiss-knife.common.game-objects.players :as players]
            [com.fabriclj.swiss-knife.common.gameplay.sounds :as sounds])
  (:import (net.minecraft.world.entity.player Player)
           (net.minecraft.world.effect MobEffects MobEffectInstance)
           (net.minecraft.world.entity.projectile Projectile Snowball)
           (net.minecraft.world.phys HitResult HitResult$Type BlockHitResult)
           (net.minecraft.world.level.block Blocks)
           (org.spongepowered.asm.mixin.injection.callback CallbackInfo)))

(defn on-player-jump
  "玩家跳跃钩子 - 手持魔法宝石时给予跳跃提升效果

   参数:
   - player: Player 实例
   - ci: CallbackInfo，可用于取消事件

   功能:
   - 检测玩家主手是否持有魔法宝石
   - 如果持有，给予 3 秒的跳跃提升 II 效果
   - 播放魔法音效"
  [^Player player ^CallbackInfo ci]
  (try
   (println "[ExampleMod/Hooks] on-player-jump called for player:" (.getName (.getGameProfile player)))
   
   ;; 获取玩家主手物品( 使用 swiss-knife)
   (let [main-hand (players/get-main-hand-item player)
         item (.getItem main-hand)
         item-id (.getDescriptionId item)]
     
     (println "[ExampleMod/Hooks] Main hand item:" item-id)

     ;; 检查是否持有魔法宝石
     (when (= item-id "item.example.magic_gem")
       (println "[ExampleMod/Hooks] Magic gem detected! Activating jump boost...")
       
       ;; 给予跳跃提升效果( 3 秒，等级 II)
       (let [jump-boost (MobEffectInstance. MobEffects/JUMP
                                            60   ; 持续时间( ticks) = 3 秒
                                            1    ; 等级 - 1 = II 级
                                            false ; 不是环境效果
                                            false)] ; 不显示粒子
         (.addEffect player jump-boost)
         (println "[ExampleMod/Hooks] Jump boost effect added"))

       ;; 发送提示消息
       (players/send-message! player
                              ((requiring-resolve 'com.fabriclj.swiss-knife.common.utils.text/colored-text)
                               "Magic Jump Activated!" :light-purple))
       (println "[ExampleMod/Hooks] Message sent")

       ;; 播放音效
       (let [level (.level player)
             pos (.position player)]
         ((requiring-resolve 'com.fabriclj.swiss-knife.common.gameplay.sounds/play-sound!)
          level pos :minecraft:entity.ender_dragon.flap
          {:source :player :volume 0.3 :pitch 2.0})
         (println "[ExampleMod/Hooks] Sound played"))

       (println "[ExampleMod/Hooks] Magic jump activated for:"
                (.getName (.getGameProfile player)))))

   (catch Exception e
     ;; 错误处理: 避免 Mixin 崩溃
     (println "[ExampleMod/Hooks] Error in on-player-jump:" (.getMessage e))
     (.printStackTrace e))))

(defn on-projectile-hit
  "弹道命中钩子 - 命中树叶时召唤森林守卫

   参数:
   - projectile: Projectile 实例
   - hit-result: HitResult 命中结果

   功能:
   - 检测雪球是否命中树叶
   - 如果命中，在命中位置上方生成森林守卫
   - 可以访问发射者数据: (.getOwner projectile)

   注意: 此函数由 ProjectileMixin 调用，在弹道命中时立即触发"
  [^Projectile projectile ^HitResult hit-result]
  (try
    (when (instance? Snowball projectile)
      (let [level (.level projectile)
            owner (.getOwner projectile) ; 获取发射者
            hit-type (.getType hit-result)]
        ;; 可以访问发射者数据，例如检查是否是玩家发射的
        (when (and owner (instance? Player owner))
          (println "[ExampleMod/Hooks] 雪球由玩家发射:" (.getName (.getGameProfile owner))))
        (when (= hit-type HitResult$Type/BLOCK)
          (let [block-hit ^BlockHitResult hit-result
                pos (.getBlockPos block-hit)
                state (.getBlockState level pos)
                block (.getBlock state)]
            ;; 检查是否命中树叶
            (when (or (= block Blocks/OAK_LEAVES)
                      (= block Blocks/SPRUCE_LEAVES)
                      (= block Blocks/BIRCH_LEAVES)
                      (= block Blocks/JUNGLE_LEAVES)
                      (= block Blocks/ACACIA_LEAVES)
                      (= block Blocks/DARK_OAK_LEAVES)
                      (= block Blocks/AZALEA_LEAVES)
                      (= block Blocks/FLOWERING_AZALEA_LEAVES)
                      (= block Blocks/MANGROVE_LEAVES)
                      (= block Blocks/CHERRY_LEAVES))
              ;; 在命中位置上方生成森林守卫
              (let [spawn-pos (.above pos)
                    spawn-fn (requiring-resolve 'com.example.core/spawn-forest-guardian!)]
                (println "[ExampleMod/Hooks] 雪球命中树叶于:" pos)
                (println "[ExampleMod/Hooks] 生成位置为:" spawn-pos)
                (when spawn-fn
                  (let [spawn-center (.getCenter spawn-pos)]
                    (println "[ExampleMod/Hooks] 调用生成函数，中心点:" spawn-center)
                    (spawn-fn level spawn-center)))))))))
    (catch Exception e
      ;; 错误处理: 避免 Mixin 崩溃
      (println "[ExampleMod/Hooks] 弹道命中处理错误: " (.getMessage e))
      (.printStackTrace e))))

;; ============================================================================
;; REPL 测试代码
;; ============================================================================

(comment
  ;; 在 nREPL 中测试
  ;; 注意: Mixin 钩子函数由游戏引擎调用，不能直接测试
  ;; 但可以测试其依赖的功能

  (require '[com.fabriclj.swiss-knife.common.game-objects.players :as p])
  (require '[com.fabriclj.swiss-knife.client.platform.core :as c])

  ;; 测试玩家消息发送
  (when-let [player (c/get-player)]
    (p/send-message! player
                     ((requiring-resolve 'com.fabriclj.swiss-knife.common.utils.text/colored-text)
                      "测试消息" :gold)))

  ;; 热重载后需要清除 ClojureBridge 缓存
  (com.fabriclj.ClojureBridge/clearCache "com.example.hooks")
  )
