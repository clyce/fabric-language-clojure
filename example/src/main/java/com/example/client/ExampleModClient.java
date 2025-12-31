package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import com.fabriclj.ClojureRuntime;

/**
 * 示例 Mod 的客户端入口类
 */
public class ExampleModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 加载并调用 Clojure 客户端初始化函数
        ClojureRuntime.requireNamespace("com.example.client");
        ClojureRuntime.invoke("com.example.client", "init-client");
    }
}
