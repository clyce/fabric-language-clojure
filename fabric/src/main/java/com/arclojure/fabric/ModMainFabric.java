package com.arclojure.fabric;

import net.fabricmc.api.ModInitializer;

import com.arclojure.ModMain;

/**
 * Fabric 平台模组入口点
 *
 * 此类作为 Fabric 加载器的入口，负责引导 Clojure 运行时。
 */
public final class ModMainFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // 将当前类传递给引导器，以便获取正确的类加载器
        ModMain.init(ModMainFabric.class);
    }
}
