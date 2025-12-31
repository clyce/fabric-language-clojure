package com.fabriclj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clojure 桥接工具类
 *
 * <p>
 * 提供从 Java 代码（特别是 Mixin 类）调用 Clojure 函数的便捷方法。
 * 此类是语言支持库的核心公共 API，供其他 mod 的 Mixin 类使用。
 *
 * <h2>设计目标</h2>
 * <ul>
 * <li>提供简单、类型安全的 API 调用 Clojure 函数</li>
 * <li>缓存函数引用以提高性能</li>
 * <li>处理类加载器上下文问题</li>
 * <li>提供有意义的错误信息</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>
 * {
 *     &#64;code
 *     // 在用户 mod 的 Mixin 类中
 *     &#64;Mixin(Player.class)
 *     public class MyMixin {
 *         @Inject(method = "jump", at = @At("HEAD"))
 *         private void onJump(CallbackInfo ci) {
 *             ClojureBridge.invoke("com.mymod.hooks", "on-player-jump",
 *                     (Player) (Object) this, ci);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * 对应的 Clojure 代码：
 *
 * <pre>{@code
 * (ns com.mymod.hooks)
 *
 * (defn on-player-jump
 *   [player ci]
 *   (println "Player jumped!"))
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ClojureBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClojureBridge");

    /** 函数引用缓存 */
    private static final ConcurrentHashMap<String, Object> fnCache = new ConcurrentHashMap<>();

    /** invoke 方法缓存 */
    private static final ConcurrentHashMap<Integer, Method> invokeMethodCache = new ConcurrentHashMap<>();

    /** 已加载的命名空间 */
    private static final ConcurrentHashMap<String, Boolean> loadedNamespaces = new ConcurrentHashMap<>();

    /** Clojure API 方法引用 */
    private static volatile Method varMethod;
    private static volatile Method readMethod;
    private static volatile boolean initialized = false;

    private ClojureBridge() {
        // 禁止实例化
    }

    /**
     * 调用 Clojure 函数（无参数）
     *
     * @param namespace    命名空间，如 "com.mymod.hooks"
     * @param functionName 函数名，如 "on-player-jump"
     * @return 函数返回值
     */
    public static Object invoke(String namespace, String functionName) {
        return invokeInternal(namespace, functionName, new Object[0]);
    }

    /**
     * 调用 Clojure 函数（1 个参数）
     *
     * @param namespace    命名空间
     * @param functionName 函数名
     * @param arg1         第 1 个参数
     * @return 函数返回值
     */
    public static Object invoke(String namespace, String functionName, Object arg1) {
        return invokeInternal(namespace, functionName, new Object[] { arg1 });
    }

    /**
     * 调用 Clojure 函数（2 个参数）
     *
     * @param namespace    命名空间
     * @param functionName 函数名
     * @param arg1         第 1 个参数
     * @param arg2         第 2 个参数
     * @return 函数返回值
     */
    public static Object invoke(String namespace, String functionName, Object arg1, Object arg2) {
        return invokeInternal(namespace, functionName, new Object[] { arg1, arg2 });
    }

    /**
     * 调用 Clojure 函数（3 个参数）
     *
     * @param namespace    命名空间
     * @param functionName 函数名
     * @param arg1         第 1 个参数
     * @param arg2         第 2 个参数
     * @param arg3         第 3 个参数
     * @return 函数返回值
     */
    public static Object invoke(String namespace, String functionName,
            Object arg1, Object arg2, Object arg3) {
        return invokeInternal(namespace, functionName, new Object[] { arg1, arg2, arg3 });
    }

    /**
     * 调用 Clojure 函数（4 个参数）
     *
     * @param namespace    命名空间
     * @param functionName 函数名
     * @param arg1         第 1 个参数
     * @param arg2         第 2 个参数
     * @param arg3         第 3 个参数
     * @param arg4         第 4 个参数
     * @return 函数返回值
     */
    public static Object invoke(String namespace, String functionName,
            Object arg1, Object arg2, Object arg3, Object arg4) {
        return invokeInternal(namespace, functionName, new Object[] { arg1, arg2, arg3, arg4 });
    }

    /**
     * 调用 Clojure 函数（可变参数）
     *
     * @param namespace    命名空间
     * @param functionName 函数名
     * @param args         参数数组
     * @return 函数返回值
     */
    public static Object invokeVarargs(String namespace, String functionName, Object... args) {
        return invokeInternal(namespace, functionName, args);
    }

    /**
     * 检查命名空间是否已加载
     *
     * @param namespace 命名空间
     * @return true 如果已加载
     */
    public static boolean isNamespaceLoaded(String namespace) {
        return loadedNamespaces.containsKey(namespace);
    }

    /**
     * 预加载命名空间（可选，用于提前初始化）
     *
     * @param namespace 命名空间
     */
    public static void preloadNamespace(String namespace) {
        try {
            ensureInitialized();
            loadNamespace(namespace);
        } catch (Exception e) {
            LOGGER.error("[ClojureBridge] Failed to preload namespace: {}", namespace, e);
        }
    }

    /**
     * 清除函数缓存（用于开发时热重载）
     *
     * @param namespace 要清除的命名空间，如果为 null 则清除所有
     */
    public static void clearCache(String namespace) {
        if (namespace == null) {
            fnCache.clear();
            loadedNamespaces.clear();
            LOGGER.debug("[ClojureBridge] Cleared all function cache");
        } else {
            fnCache.entrySet().removeIf(e -> e.getKey().startsWith(namespace + "/"));
            loadedNamespaces.remove(namespace);
            LOGGER.debug("[ClojureBridge] Cleared cache for namespace: {}", namespace);
        }
    }

    // ========================================================================
    // 内部实现
    // ========================================================================

    private static Object invokeInternal(String namespace, String functionName, Object[] args) {
        try {
            ensureInitialized();

            // 获取或缓存函数引用
            String cacheKey = namespace + "/" + functionName;
            Object fn = fnCache.computeIfAbsent(cacheKey, k -> {
                try {
                    loadNamespace(namespace);
                    return varMethod.invoke(null, namespace, functionName);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get function: " + k, e);
                }
            });

            // 调用函数
            Method invokeMethod = getInvokeMethod(fn, args.length);
            return invokeMethod.invoke(fn, args);

        } catch (Exception e) {
            LOGGER.error("[ClojureBridge] Error invoking {}/{} with {} args",
                    namespace, functionName, args.length, e);
            return null;
        }
    }

    private static synchronized void ensureInitialized() throws Exception {
        if (initialized) {
            return;
        }

        ClassLoader classLoader = ClojureBridge.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);

        Class<?> clojureClass = Class.forName("clojure.java.api.Clojure", true, classLoader);
        varMethod = clojureClass.getMethod("var", Object.class, Object.class);
        readMethod = clojureClass.getMethod("read", String.class);

        initialized = true;
        LOGGER.debug("[ClojureBridge] Initialized successfully");
    }

    private static void loadNamespace(String namespace) throws Exception {
        if (loadedNamespaces.containsKey(namespace)) {
            return;
        }

        Object requireFn = varMethod.invoke(null, "clojure.core", "require");
        Object nsSymbol = readMethod.invoke(null, namespace);
        Method invokeMethod = requireFn.getClass().getMethod("invoke", Object.class);
        invokeMethod.invoke(requireFn, nsSymbol);

        loadedNamespaces.put(namespace, Boolean.TRUE);
        LOGGER.debug("[ClojureBridge] Loaded namespace: {}", namespace);
    }

    private static Method getInvokeMethod(Object fn, int argCount) throws NoSuchMethodException {
        return invokeMethodCache.computeIfAbsent(argCount, count -> {
            try {
                Class<?>[] paramTypes = new Class<?>[count];
                for (int i = 0; i < count; i++) {
                    paramTypes[i] = Object.class;
                }
                return fn.getClass().getMethod("invoke", paramTypes);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No invoke method for " + count + " args", e);
            }
        });
    }
}
