package com.example;

import net.fabricmc.api.ModInitializer;
import com.fabriclj.ClojureRuntime;

/**
 * 示例 Mod 的 Java 入口类
 *
 * 使用 ClojureRuntime 加载 Clojure 代码
 */
public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // 初始化 Clojure 运行时
        ClojureRuntime.ensureInitialized(ExampleMod.class);

        // 加载并调用 Clojure 初始化函数
        ClojureRuntime.requireNamespace("com.example.core");
        ClojureRuntime.invoke("com.example.core", "init");
    }
}
