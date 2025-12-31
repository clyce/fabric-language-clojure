package com.fabriclj.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import com.fabriclj.ModMain;

/**
 * Fabric Language Clojure 客户端入口
 *
 * <p>
 * 此类作为语言支持库在客户端的入口点。
 * 语言库本身不需要特殊的客户端初始化，此入口点主要用于确保
 * Clojure 运行时在客户端环境也能正常工作。
 */
public final class ModMainFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModMain.initClient(ModMainFabricClient.class);
    }
}
