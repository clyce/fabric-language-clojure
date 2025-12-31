package com.fabriclj;

import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 用于检查 EnchantmentHelper 的实际方法签名
 * 
 * 编译此类以查看可用的方法
 */
public class InspectEnchantmentHelper {
    
    public static void listMethods() {
        Class<?> clazz = EnchantmentHelper.class;
        
        System.out.println("=== EnchantmentHelper 可用方法 ===");
        for (var method : clazz.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) 
                && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                System.out.println(method.getName() + " - " + method.toGenericString());
            }
        }
    }
    
    // 注释掉的方法签名示例 - 用于测试哪些存在
    
    // Minecraft 1.21 EnchantmentHelper 的一些可能签名:
    // - getLevel(Holder<Enchantment>, ItemStack) 
    // - getEnchantmentLevel(Holder<Enchantment>, LivingEntity)
    // - modifyDamage(ServerLevel, ItemStack, Entity, DamageSource, float)
    // - doPostAttackEffects(ServerLevel, Entity, DamageSource)
    // - doPostDamageEffects(LivingEntity, Entity)
    // - modifyItemEnchantmentValue(ItemStack, Consumer<ItemEnchantmentsComponent.Builder>)
}
