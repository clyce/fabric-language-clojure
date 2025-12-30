package com.arclojure.mixin;

import com.arclojure.ClojureHooks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家实体 Mixin 示例
 *
 * 此类演示了如何使用 Java 编写 Mixin，并将实际逻辑委托给 Clojure。
 *
 * 遵循「Java 代理模式」：
 * - 此类负责字节码层面的注入
 * - 实际业务逻辑通过 ClojureHooks 委托给 Clojure 代码
 *
 * 【重要】绝不要尝试用 Clojure 直接编写 Mixin 类！
 */
@Mixin(Player.class)
public abstract class MixinPlayer {

    /**
     * 注入玩家跳跃方法
     *
     * 在跳跃方法开始时调用 Clojure 钩子。
     * 可通过 CallbackInfo.cancel() 取消跳跃。
     */
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void arclojure$onJump(CallbackInfo ci) {
        ClojureHooks.onPlayerJump((Player)(Object)this, ci);
    }

    /**
     * 注入玩家 Tick 方法
     *
     * 每游戏刻调用一次，可用于持续性效果。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void arclojure$onTick(CallbackInfo ci) {
        ClojureHooks.onPlayerTick((Player)(Object)this);
    }
}
