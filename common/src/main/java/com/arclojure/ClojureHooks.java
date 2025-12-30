package com.arclojure;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Clojure 钩子桥接类
 *
 * 此类作为 Java Mixin 和 Clojure 逻辑之间的桥梁。
 * Mixin 类调用此类的静态方法，这些方法再委托给 Clojure 函数。
 *
 * 遵循「Java 代理模式」：
 * 1. 第一层（Java Mixin）：处理字节码注入
 * 2. 第二层（此类）：静态桥接方法
 * 3. 第三层（Clojure）：实际业务逻辑
 *
 * 【重要】使用反射调用 Clojure 函数，避免在字节码中直接引用 clojure.lang.IFn，
 * 否则 Architectury Transformer 会因为找不到 Clojure 类而失败。
 */
public final class ClojureHooks {
    /** Clojure 钩子命名空间 */
    private static final String HOOKS_NAMESPACE = "com.arclojure.hooks";

    /** 缓存的 Clojure 函数引用（Object 类型避免直接引用 IFn） */
    private static volatile Object onPlayerJumpFn;
    private static volatile Object onPlayerTickFn;

    /** 缓存的 invoke 方法 */
    private static volatile Method invoke2Method; // invoke(Object, Object)
    private static volatile Method invoke1Method; // invoke(Object)

    /** 标记是否已初始化 */
    private static volatile boolean initialized = false;

    private ClojureHooks() {
        // 禁止实例化
    }

    /**
     * 确保 Clojure 钩子命名空间已加载
     */
    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (ClojureHooks.class) {
            if (initialized) {
                return;
            }

            try {
                ClassLoader classLoader = ClojureHooks.class.getClassLoader();

                // 使用反射获取 Clojure API
                Class<?> clojureClass = Class.forName("clojure.java.api.Clojure", true, classLoader);
                Method varMethod = clojureClass.getMethod("var", Object.class, Object.class);
                Method readMethod = clojureClass.getMethod("read", String.class);

                // 加载钩子命名空间: (require 'com.arclojure.hooks)
                Object requireFn = varMethod.invoke(null, "clojure.core", "require");
                Object hooksNs = readMethod.invoke(null, HOOKS_NAMESPACE);
                Method invokeFn = requireFn.getClass().getMethod("invoke", Object.class);
                invokeFn.invoke(requireFn, hooksNs);

                // 获取钩子函数引用
                onPlayerJumpFn = varMethod.invoke(null, HOOKS_NAMESPACE, "on-player-jump");
                onPlayerTickFn = varMethod.invoke(null, HOOKS_NAMESPACE, "on-player-tick");

                // 缓存 invoke 方法
                invoke2Method = onPlayerJumpFn.getClass().getMethod("invoke", Object.class, Object.class);
                invoke1Method = onPlayerTickFn.getClass().getMethod("invoke", Object.class);

                initialized = true;
            } catch (Exception e) {
                ModMain.LOGGER.error("[ClojureHooks] Failed to initialize hooks namespace", e);
            }
        }
    }

    // ========================================================================
    // 玩家相关钩子
    // ========================================================================

    /**
     * 玩家跳跃事件
     *
     * @param player 玩家实体
     * @param ci Mixin 回调信息
     */
    public static void onPlayerJump(Player player, CallbackInfo ci) {
        ensureInitialized();
        if (onPlayerJumpFn != null && invoke2Method != null) {
            try {
                invoke2Method.invoke(onPlayerJumpFn, player, ci);
            } catch (Exception e) {
                ModMain.LOGGER.error("[ClojureHooks] Error in onPlayerJump", e);
            }
        }
    }

    /**
     * 玩家 Tick 事件
     *
     * @param player 玩家实体
     */
    public static void onPlayerTick(Player player) {
        ensureInitialized();
        if (onPlayerTickFn != null && invoke1Method != null) {
            try {
                invoke1Method.invoke(onPlayerTickFn, player);
            } catch (Exception e) {
                ModMain.LOGGER.error("[ClojureHooks] Error in onPlayerTick", e);
            }
        }
    }
}
