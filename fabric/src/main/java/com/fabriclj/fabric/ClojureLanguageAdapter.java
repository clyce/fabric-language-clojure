package com.fabriclj.fabric;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Clojure 语言适配器
 *
 * <p>
 * 实现 Fabric 的 {@link LanguageAdapter} 接口，允许其他 mod 使用 Clojure 编写入口点。
 *
 * <h2>支持的入口点格式</h2>
 * <ul>
 * <li><b>函数引用</b>: {@code "com.mymod.core/init"} - 调用命名空间中的函数</li>
 * <li><b>命名空间引用</b>: {@code "com.mymod.core"} - 自动调用 {@code -main} 函数</li>
 * <li><b>变量引用</b>: {@code "com.mymod.core/initializer"} -
 * 获取变量的值（必须是对应接口的实现）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <p>
 * 在 {@code fabric.mod.json} 中：
 *
 * <pre>{@code
 * {
 *   "entrypoints": {
 *     "main": [{
 *       "adapter": "clojure",
 *       "value": "com.mymod.core/init"
 *     }]
 *   },
 *   "depends": {
 *     "fabric-language-clojure": ">=1.0.0"
 *   }
 * }
 * }</pre>
 *
 * @see <a href=
 *      "https://github.com/FabricMC/fabric-language-kotlin">fabric-language-kotlin</a>
 */
public class ClojureLanguageAdapter implements LanguageAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("fabric-language-clojure");

    /** Clojure 运行时是否已初始化 */
    private static volatile boolean runtimeInitialized = false;

    /** Clojure API 方法缓存 */
    private static Method varMethod;
    private static Method readMethod;

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        LOGGER.info("[ClojureAdapter] Creating entrypoint '{}' for mod '{}' (type: {})",
                value, mod.getMetadata().getId(), type.getSimpleName());

        try {
            ensureClojureRuntime(mod);
            return createEntrypoint(mod, value, type);
        } catch (Exception e) {
            throw new LanguageAdapterException("Failed to create Clojure entrypoint: " + value, e);
        }
    }

    /**
     * 确保 Clojure 运行时已初始化
     */
    private synchronized void ensureClojureRuntime(ModContainer mod) throws Exception {
        if (runtimeInitialized) {
            return;
        }

        LOGGER.info("[ClojureAdapter] Bootstrapping Clojure runtime...");

        // 获取正确的类加载器
        ClassLoader modClassLoader = ClojureLanguageAdapter.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(modClassLoader);

        // 初始化 Clojure API 引用
        Class<?> clojureClass = Class.forName("clojure.java.api.Clojure", true, modClassLoader);
        varMethod = clojureClass.getMethod("var", Object.class, Object.class);
        readMethod = clojureClass.getMethod("read", String.class);

        // 确保 clojure.core 已加载
        varMethod.invoke(null, "clojure.core", "require");

        runtimeInitialized = true;
        LOGGER.info("[ClojureAdapter] Clojure runtime bootstrapped successfully");
    }

    /**
     * 创建入口点实例
     *
     * @param mod   mod 容器
     * @param value 入口点值，格式为 "namespace/function" 或 "namespace"
     * @param type  期望的接口类型
     * @return 实现了指定接口的代理对象
     */
    @SuppressWarnings("unchecked")
    private <T> T createEntrypoint(ModContainer mod, String value, Class<T> type) throws Exception {
        // 解析命名空间和函数名
        String namespace;
        String functionName;

        if (value.contains("/")) {
            String[] parts = value.split("/", 2);
            namespace = parts[0];
            functionName = parts[1];
        } else {
            namespace = value;
            functionName = "-main";
        }

        LOGGER.debug("[ClojureAdapter] Loading namespace '{}', function '{}'", namespace, functionName);

        // 加载命名空间
        Object requireFn = varMethod.invoke(null, "clojure.core", "require");
        Object nsSymbol = readMethod.invoke(null, namespace);
        Method invokeMethod = requireFn.getClass().getMethod("invoke", Object.class);
        invokeMethod.invoke(requireFn, nsSymbol);

        // 获取函数引用
        Object targetFn = varMethod.invoke(null, namespace, functionName);

        // 检查目标是否已经是所需类型的实例（变量引用情况）
        Method derefMethod = targetFn.getClass().getMethod("deref");
        Object derefValue = derefMethod.invoke(targetFn);

        if (type.isInstance(derefValue)) {
            LOGGER.debug("[ClojureAdapter] Using dereferenced value as entrypoint");
            return (T) derefValue;
        }

        // 否则创建代理对象
        LOGGER.debug("[ClojureAdapter] Creating proxy for interface {}", type.getName());
        return createProxy(type, targetFn);
    }

    /**
     * 创建代理对象，将接口方法调用转发到 Clojure 函数
     */
    @SuppressWarnings("unchecked")
    private <T> T createProxy(Class<T> type, Object clojureFn) throws Exception {
        Method invoke0 = clojureFn.getClass().getMethod("invoke");

        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                new ClojureEntrypointHandler(clojureFn, invoke0));
    }

    /**
     * 代理处理器，将方法调用转发到 Clojure 函数
     */
    private static class ClojureEntrypointHandler implements InvocationHandler {
        private final Object clojureFn;
        private final Method invokeMethod;

        ClojureEntrypointHandler(Object clojureFn, Method invokeMethod) {
            this.clojureFn = clojureFn;
            this.invokeMethod = invokeMethod;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理 Object 类的标准方法
            String methodName = method.getName();

            if ("equals".equals(methodName) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName)) {
                return "ClojureEntrypoint[" + clojureFn + "]";
            }

            // 调用 Clojure 函数
            // 对于 ModInitializer.onInitialize() 等无参方法，直接调用
            if (args == null || args.length == 0) {
                return invokeMethod.invoke(clojureFn);
            }

            // 对于有参数的方法，需要找到对应的 invoke 方法
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = Object.class;
            }
            Method invokeWithArgs = clojureFn.getClass().getMethod("invoke", paramTypes);
            return invokeWithArgs.invoke(clojureFn, args);
        }
    }
}
