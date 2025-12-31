package com.fabriclj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Clojure 运行时管理器
 *
 * <p>
 * 提供 Clojure 运行时的初始化和管理功能。
 * 此类是语言支持库的内部组件，负责处理类加载器上下文和 Clojure 启动。
 *
 * <h2>职责</h2>
 * <ul>
 * <li>初始化 Clojure 运行时</li>
 * <li>管理类加载器上下文</li>
 * <li>提供命名空间加载功能</li>
 * <li>处理初始化错误</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <p>
 * 此类使用反射调用 Clojure API，以避免在字节码中直接引用 Clojure 类型，
 * 这对于与 Architectury Transformer 的兼容性至关重要。
 *
 * @since 1.0.0
 */
public final class ClojureRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClojureRuntime");

    /** 运行时是否已初始化 */
    private static volatile boolean initialized = false;

    /** Clojure API 方法缓存 */
    private static Method varMethod;
    private static Method readMethod;

    private ClojureRuntime() {
        // 禁止实例化
    }

    /**
     * 检查运行时是否已初始化
     *
     * @return true 如果已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 确保 Clojure 运行时已初始化
     *
     * <p>
     * 此方法是幂等的，可以安全地多次调用。
     *
     * @param callerClass 调用者类，用于获取正确的类加载器
     * @throws RuntimeException 如果初始化失败
     */
    public static synchronized void ensureInitialized(Class<?> callerClass) {
        if (initialized) {
            return;
        }

        LOGGER.info("[ClojureRuntime] Bootstrapping Clojure runtime...");

        try {
            // 设置正确的类加载器上下文
            ClassLoader modClassLoader = callerClass.getClassLoader();
            Thread.currentThread().setContextClassLoader(modClassLoader);

            // 初始化 Clojure API 引用
            Class<?> clojureClass = Class.forName("clojure.java.api.Clojure", true, modClassLoader);
            varMethod = clojureClass.getMethod("var", Object.class, Object.class);
            readMethod = clojureClass.getMethod("read", String.class);

            // 验证 Clojure 运行时可用
            varMethod.invoke(null, "clojure.core", "require");

            initialized = true;
            LOGGER.info("[ClojureRuntime] Clojure runtime bootstrapped successfully");

        } catch (Exception e) {
            LOGGER.error("[ClojureRuntime] Failed to bootstrap Clojure runtime", e);
            throw new RuntimeException("Failed to bootstrap Clojure runtime", e);
        }
    }

    /**
     * 加载 Clojure 命名空间
     *
     * @param namespace 命名空间名称，如 "com.mymod.core"
     * @throws RuntimeException 如果加载失败
     */
    public static void requireNamespace(String namespace) {
        if (!initialized) {
            throw new IllegalStateException("Clojure runtime not initialized. Call ensureInitialized() first.");
        }

        try {
            Object requireFn = varMethod.invoke(null, "clojure.core", "require");
            Object nsSymbol = readMethod.invoke(null, namespace);
            Method invokeMethod = requireFn.getClass().getMethod("invoke", Object.class);
            invokeMethod.invoke(requireFn, nsSymbol);

            LOGGER.debug("[ClojureRuntime] Loaded namespace: {}", namespace);
        } catch (Exception e) {
            LOGGER.error("[ClojureRuntime] Failed to load namespace: {}", namespace, e);
            throw new RuntimeException("Failed to load namespace: " + namespace, e);
        }
    }

    /**
     * 调用 Clojure 函数（无参数）
     *
     * @param namespace    命名空间
     * @param functionName 函数名
     * @return 函数返回值
     */
    public static Object invoke(String namespace, String functionName) {
        return invoke(namespace, functionName, new Object[0]);
    }

    /**
     * 调用 Clojure 函数
     *
     * @param namespace    命名空间
     * @param functionName 函数名
     * @param args         参数
     * @return 函数返回值
     */
    public static Object invoke(String namespace, String functionName, Object... args) {
        if (!initialized) {
            throw new IllegalStateException("Clojure runtime not initialized.");
        }

        try {
            Object fn = varMethod.invoke(null, namespace, functionName);

            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = Object.class;
            }
            Method invokeMethod = fn.getClass().getMethod("invoke", paramTypes);

            return invokeMethod.invoke(fn, args);
        } catch (Exception e) {
            LOGGER.error("[ClojureRuntime] Failed to invoke {}/{}", namespace, functionName, e);
            throw new RuntimeException("Failed to invoke " + namespace + "/" + functionName, e);
        }
    }

    /**
     * 获取 Clojure 变量的值
     *
     * @param namespace 命名空间
     * @param varName   变量名
     * @return 变量的值
     */
    public static Object getVar(String namespace, String varName) {
        if (!initialized) {
            throw new IllegalStateException("Clojure runtime not initialized.");
        }

        try {
            Object var = varMethod.invoke(null, namespace, varName);
            Method derefMethod = var.getClass().getMethod("deref");
            return derefMethod.invoke(var);
        } catch (Exception e) {
            LOGGER.error("[ClojureRuntime] Failed to get var {}/{}", namespace, varName, e);
            throw new RuntimeException("Failed to get var " + namespace + "/" + varName, e);
        }
    }
}
