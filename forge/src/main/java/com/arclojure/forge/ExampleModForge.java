package com.arclojure.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import com.arclojure.ModMain;

/**
 * Forge 平台模组入口点
 *
 * 此类作为 Forge 加载器的入口，负责引导 Clojure 运行时。
 */
@Mod(ModMain.MOD_ID)
public final class ModMainForge {
    public ModMainForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(ModMain.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // 将当前类传递给引导器，以便获取正确的类加载器
        ModMain.init(ModMainForge.class);

        // 客户端初始化（仅在客户端运行）
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ModMainForgeClient::initClient);
    }
}

/**
 * Forge 客户端初始化辅助类
 */
class ModMainForgeClient {
    static void initClient() {
        ModMain.initClient(ModMainForgeClient.class);
    }
}
