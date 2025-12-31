package com.fabriclj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * fabric-language-clojure 语言支持库主入口
 *
 * <p>
 * 此类作为语言支持库本身的初始化入口，同时提供向后兼容的 API，
 * 供用户 mod 在 Forge 平台或其他需要 Java 入口类的场景使用。
 *
 * <h2>用途</h2>
 * <ul>
 * <li>初始化语言支持库</li>
 * <li>提供 Forge 平台的辅助方法</li>
 * <li>提供向后兼容的 API</li>
 * </ul>
 *
 * <h2>Forge 使用示例</h2>
 *
 * <pre>{@code
 * // 用户 mod 的 Forge 入口类
 * @Mod("mymod")
 * public class MyMod {
 *     public MyMod() {
 *         ModMain.loadClojureMod(MyMod.class, "com.mymod.core", "init");
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ModMain {
    /** 语言支持库 ID */
    public static final String MOD_ID = "fabric-language-clojure";

    /** 日志记录器 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ModMain() {
        // 禁止实例化
    }

    /**
     * 初始化语言支持库
     *
     * <p>
     * 此方法由 Fabric 平台的入口类调用，或由 ClojureLanguageAdapter 自动触发。
     *
     * @param callerClass 调用者类
     */
    public static void init(Class<?> callerClass) {
        LOGGER.info("[fabric-language-clojure] Initializing language support library...");
        ClojureRuntime.ensureInitialized(callerClass);
        LOGGER.info("[fabric-language-clojure] Language support library initialized");
    }

    /**
     * 初始化语言支持库的客户端部分
     *
     * @param callerClass 调用者类
     */
    public static void initClient(Class<?> callerClass) {
        LOGGER.debug("[fabric-language-clojure] Client initialization (no-op for language library)");
        // 语言库本身不需要客户端初始化
        // 用户 mod 的客户端初始化由 ClojureLanguageAdapter 处理
    }

    /**
     * 加载 Clojure mod（Forge 辅助方法）
     *
     * <p>
     * 此方法供 Forge 平台使用，因为 Forge 不支持语言适配器，
     * 需要用户编写 Java 入口类来引导 Clojure 代码。
     *
     * <h2>使用示例</h2>
     *
     * <pre>{@code
     * @Mod("mymod")
     * public class MyMod {
     *     public MyMod() {
     *         ModMain.loadClojureMod(MyMod.class, "com.mymod.core", "init");
     *     }
     * }
     * }</pre>
     *
     * @param callerClass  调用者类（用于获取类加载器）
     * @param namespace    Clojure 命名空间
     * @param functionName 要调用的函数名
     */
    public static void loadClojureMod(Class<?> callerClass, String namespace, String functionName) {
        LOGGER.info("[fabric-language-clojure] Loading Clojure mod: {}/{}", namespace, functionName);

        try {
            // 确保运行时已初始化
            ClojureRuntime.ensureInitialized(callerClass);

            // 加载命名空间并调用函数
            ClojureRuntime.requireNamespace(namespace);
            ClojureRuntime.invoke(namespace, functionName);

            LOGGER.info("[fabric-language-clojure] Clojure mod loaded successfully: {}", namespace);
        } catch (Exception e) {
            LOGGER.error("[fabric-language-clojure] Failed to load Clojure mod: {}/{}", namespace, functionName, e);
            throw new RuntimeException("Failed to load Clojure mod", e);
        }
    }

    /**
     * 加载 Clojure mod 客户端（Forge 辅助方法）
     *
     * @param callerClass  调用者类
     * @param namespace    Clojure 命名空间
     * @param functionName 要调用的函数名
     */
    public static void loadClojureModClient(Class<?> callerClass, String namespace, String functionName) {
        LOGGER.info("[fabric-language-clojure] Loading Clojure mod client: {}/{}", namespace, functionName);

        try {
            ClojureRuntime.ensureInitialized(callerClass);
            ClojureRuntime.requireNamespace(namespace);
            ClojureRuntime.invoke(namespace, functionName);

            LOGGER.info("[fabric-language-clojure] Clojure mod client loaded successfully: {}", namespace);
        } catch (Exception e) {
            LOGGER.error("[fabric-language-clojure] Failed to load Clojure mod client: {}/{}", namespace, functionName,
                    e);
            throw new RuntimeException("Failed to load Clojure mod client", e);
        }
    }
}
