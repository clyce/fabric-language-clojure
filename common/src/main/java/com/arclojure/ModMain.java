package com.arclojure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;

/**
 * Arclojure 模组主入口类（Java 壳）
 *
 * 本类作为 Clojure 运行时的引导器，遵循"Java 壳，Clojure 核"的架构模式。
 * 所有静态契约（模组入口、Mixin）由 Java 处理，动态逻辑由 Clojure 实现。
 */
public final class ModMain {
    /** 模组 ID */
    public static final String MOD_ID = "arclojure";

    /** 日志记录器 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Clojure 核心命名空间 */
    private static final String CORE_NAMESPACE = "com.arclojure.core";

    /** Clojure 客户端命名空间 */
    private static final String CLIENT_NAMESPACE = "com.arclojure.client";

    /** 标记 Clojure 运行时是否已初始化 */
    private static volatile boolean clojureInitialized = false;

    /**
     * 初始化 Clojure 运行时并加载核心命名空间
     *
     * 【关键步骤】必须在调用任何 Clojure 代码之前执行此方法。
     * 此方法会设置正确的类加载器上下文，确保 Clojure 能够在模组的 JAR 中找到所需的类。
     *
     * 【重要】使用反射避免在字节码中直接引用 Clojure 类型，
     * 否则 Architectury Transformer 会因为找不到 Clojure 类而失败。
     *
     * @param callerClass 调用者类，用于获取正确的类加载器
     */
    private static synchronized void ensureClojureRuntime(Class<?> callerClass) {
        if (clojureInitialized) {
            return;
        }

        LOGGER.info("[Arclojure] Bootstrapping Clojure runtime...");

        // 【关键】强制将当前线程的上下文类加载器设置为模组加载器
        // 否则 Clojure 将无法在 JAR 中找到核心库
        ClassLoader modClassLoader = callerClass.getClassLoader();
        Thread.currentThread().setContextClassLoader(modClassLoader);

        try {
            // 使用反射调用 Clojure API，避免在字节码中直接引用 clojure.lang.IFn
            Class<?> clojureClass = Class.forName("clojure.java.api.Clojure", true, modClassLoader);
            Method varMethod = clojureClass.getMethod("var", Object.class, Object.class);
            Object requireFn = varMethod.invoke(null, "clojure.core", "require");

            LOGGER.info("[Arclojure] Clojure runtime bootstrapped successfully");
            clojureInitialized = true;
        } catch (Exception e) {
            LOGGER.error("[Arclojure] Failed to bootstrap Clojure runtime", e);
            throw new RuntimeException("Failed to bootstrap Clojure runtime", e);
        }
    }

    /**
     * 模组通用初始化入口
     *
     * 由平台特定入口类（Fabric/Forge）调用。
     * 此方法引导 Clojure 运行时并调用 Clojure 侧的 init 函数。
     *
     * @param callerClass 调用者类，用于获取正确的类加载器
     */
    public static void init(Class<?> callerClass) {
        LOGGER.info("[Arclojure] Initializing mod...");

        try {
            // 确保 Clojure 运行时已初始化
            ensureClojureRuntime(callerClass);

            ClassLoader modClassLoader = callerClass.getClassLoader();

            // 使用反射调用 Clojure API
            Class<?> clojureClass = Class.forName("clojure.java.api.Clojure", true, modClassLoader);
            Method varMethod = clojureClass.getMethod("var", Object.class, Object.class);
            Method readMethod = clojureClass.getMethod("read", String.class);

            // 加载核心命名空间: (require 'com.arclojure.core)
            Object requireFn = varMethod.invoke(null, "clojure.core", "require");
            Object coreNs = readMethod.invoke(null, CORE_NAMESPACE);
            Method invokeFn = requireFn.getClass().getMethod("invoke", Object.class);
            invokeFn.invoke(requireFn, coreNs);

            // 调用 Clojure 定义的 init 函数: (com.arclojure.core/init)
            Object initFn = varMethod.invoke(null, CORE_NAMESPACE, "init");
            Method invoke0 = initFn.getClass().getMethod("invoke");
            invoke0.invoke(initFn);

            LOGGER.info("[Arclojure] Mod initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[Arclojure] Failed to initialize mod", e);
            throw new RuntimeException("Failed to initialize Clojure mod core", e);
        }
    }

    /**
     * 客户端初始化入口
     *
     * 由平台特定客户端入口类调用。
     * 此方法加载客户端专用的 Clojure 命名空间。
     *
     * @param callerClass 调用者类，用于获取正确的类加载器
     */
    public static void initClient(Class<?> callerClass) {
        LOGGER.info("[Arclojure] Initializing client...");

        try {
            // 确保 Clojure 运行时已初始化
            ensureClojureRuntime(callerClass);

            ClassLoader modClassLoader = callerClass.getClassLoader();

            // 使用反射调用 Clojure API
            Class<?> clojureClass = Class.forName("clojure.java.api.Clojure", true, modClassLoader);
            Method varMethod = clojureClass.getMethod("var", Object.class, Object.class);
            Method readMethod = clojureClass.getMethod("read", String.class);

            // 加载客户端命名空间: (require 'com.arclojure.client)
            Object requireFn = varMethod.invoke(null, "clojure.core", "require");
            Object clientNs = readMethod.invoke(null, CLIENT_NAMESPACE);
            Method invokeFn = requireFn.getClass().getMethod("invoke", Object.class);
            invokeFn.invoke(requireFn, clientNs);

            // 调用 Clojure 定义的 init-client 函数: (com.arclojure.client/init-client)
            Object initClientFn = varMethod.invoke(null, CLIENT_NAMESPACE, "init-client");
            Method invoke0 = initClientFn.getClass().getMethod("invoke");
            invoke0.invoke(initClientFn);

            LOGGER.info("[Arclojure] Client initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[Arclojure] Failed to initialize client", e);
            throw new RuntimeException("Failed to initialize Clojure client", e);
        }
    }
}
