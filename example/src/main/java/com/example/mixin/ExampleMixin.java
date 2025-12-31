package com.example.mixin;

import com.fabriclj.ClojureBridge;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 示例 Mixin 类
 *
 * 演示如何在用户 mod 中编写 Mixin，并使用 ClojureBridge 调用 Clojure 代码。
 *
 * 【重要】Mixin 类必须用 Java 编写！
 * Clojure 不支持编译期字节码生成，因此无法直接编写 Mixin。
 */
@Mixin(Player.class)
public abstract class ExampleMixin {

    /**
     * 玩家跳跃时调用
     *
     * 使用 ClojureBridge.invoke() 调用 Clojure 函数。
     */
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void exampleMod$onJump(CallbackInfo ci) {
        // 调用 Clojure 函数: (com.example.hooks/on-player-jump player ci)
        ClojureBridge.invoke("com.example.hooks", "on-player-jump",
                             (Player)(Object)this, ci);
    }
}
