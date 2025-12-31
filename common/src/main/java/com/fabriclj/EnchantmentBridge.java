package com.fabriclj;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Java bridge for EnchantmentHelper methods
 * 
 * This class provides type-safe wrappers for EnchantmentHelper methods
 * to avoid Clojure reflection issues with generic types.
 * 
 * Minecraft 1.21+ uses Holder<Enchantment> instead of direct Enchantment objects.
 * 
 * Note: Many EnchantmentHelper methods changed significantly in MC 1.21.
 * This bridge uses only the methods that are confirmed to exist.
 */
public class EnchantmentBridge {
    
    /**
     * Get the total level of an enchantment from all equipment on an entity
     * 
     * @param enchantment The enchantment holder
     * @param entity The entity to check
     * @return The total enchantment level from all equipment
     */
    public static int getEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity) {
        return EnchantmentHelper.getEnchantmentLevel(enchantment, entity);
    }
    
    /**
     * Modify knockback value based on enchantments
     * 
     * Note: MC 1.21 requires DamageSource parameter
     * 
     * @param level The server level
     * @param stack The weapon item stack
     * @param target The target entity
     * @param damageSource The damage source
     * @param baseKnockback The base knockback value
     * @return The modified knockback value
     */
    public static float modifyKnockback(ServerLevel level, ItemStack stack, Entity target, 
                                       DamageSource damageSource, float baseKnockback) {
        return EnchantmentHelper.modifyKnockback(level, stack, target, damageSource, baseKnockback);
    }
    
    /**
     * Trigger post-attack enchantment effects (like Fire Aspect, Knockback, etc.)
     * 
     * @param level The server level
     * @param target The damaged entity
     * @param damageSource The damage source (contains attacker information)
     */
    public static void doPostAttackEffects(ServerLevel level, Entity target, DamageSource damageSource) {
        EnchantmentHelper.doPostAttackEffects(level, target, damageSource);
    }
}

