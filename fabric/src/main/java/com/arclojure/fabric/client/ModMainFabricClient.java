package com.arclojure.fabric.client;

import net.fabricmc.api.ClientModInitializer;

import com.arclojure.ModMain;

/**
 * Fabric 客户端入口点
 *
 * 此类负责初始化客户端专用的 Clojure 逻辑，如渲染、GUI 等。
 */
public final class ModMainFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 将当前类传递给引导器，以便获取正确的类加载器
        ModMain.initClient(ModMainFabricClient.class);
    }
}
