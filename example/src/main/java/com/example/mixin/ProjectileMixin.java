package com.example.mixin;

import com.fabriclj.ClojureBridge;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 弹道实体 Mixin
 *
 * Hook 弹道命中事件，调用 Clojure 处理函数。
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    /**
     * 弹道命中时调用
     *
     * 使用 ClojureBridge.invoke() 调用 Clojure 函数。
     *
     * @param hitResult 命中结果
     * @param ci 回调信息
     */
    @Inject(method = "onHit", at = @At("HEAD"))
    private void exampleMod$onProjectileHit(HitResult hitResult, CallbackInfo ci) {
        // 调用 Clojure 函数: (com.example.hooks/on-projectile-hit projectile hit-result)
        ClojureBridge.invoke("com.example.hooks", "on-projectile-hit",
                             (Projectile)(Object)this, hitResult);
    }
}
