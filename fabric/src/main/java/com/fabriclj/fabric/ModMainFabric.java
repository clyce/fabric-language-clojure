package com.fabriclj.fabric;

import net.fabricmc.api.ModInitializer;
import com.fabriclj.ModMain;

/**
 * Fabric Language Clojure 语言支持库入口
 *
 * <p>
 * 此类作为语言支持库本身在 Fabric 平台的入口点，
 * 负责初始化 Clojure 运行时和注册语言适配器。
 *
 * <p>
 * 注意: 用户 mod 不需要依赖此类，而是通过在 {@code fabric.mod.json}
 * 中使用 {@code "adapter": "clojure"} 来声明 Clojure 入口点。
 *
 * @see ClojureLanguageAdapter
 */
public final class ModMainFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // 初始化语言支持库
        ModMain.init(ModMainFabric.class);
    }
}
