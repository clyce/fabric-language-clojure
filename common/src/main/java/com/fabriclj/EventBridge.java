package com.fabriclj;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 事件桥接类 - 解决 Clojure DynamicClassLoader 与 Fabric/Architectury knot 类加载器的隔离问题
 *
 * <p>
 * 此类在正确的类加载器上下文中实现 Architectury 事件接口，然后回调 Clojure 函数。
 */
public final class EventBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("EventBridge");

    private EventBridge() {
        // 禁止实例化
    }

    // ========================================================================
    // 客户端 Tick 事件
    // ========================================================================

    /**
     * 注册客户端 tick 事件
     *
     * @param namespace    Clojure 命名空间
     * @param functionName Clojure 函数名
     */
    public static void registerClientTick(String namespace, String functionName) {
        ClientTickEvent.CLIENT_PRE.register(new ClientTickEvent.Client() {
            @Override
            public void tick(Minecraft minecraft) {
                try {
                    ClojureBridge.invoke(namespace, functionName, minecraft);
                } catch (Exception e) {
                    LOGGER.error("[EventBridge] Error in client tick handler", e);
                }
            }
        });
    }

    /**
     * 注册客户端世界 tick 事件
     *
     * @param namespace    Clojure 命名空间
     * @param functionName Clojure 函数名
     */
    public static void registerClientLevelTick(String namespace, String functionName) {
        ClientTickEvent.CLIENT_LEVEL_PRE.register(level -> {
            try {
                ClojureBridge.invoke(namespace, functionName, level);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in client level tick handler", e);
            }
        });
    }

    // ========================================================================
    // 客户端生命周期事件
    // ========================================================================

    /**
     * 注册客户端启动事件
     *
     * @param namespace    Clojure 命名空间
     * @param functionName Clojure 函数名
     */
    public static void registerClientStarted(String namespace, String functionName) {
        ClientLifecycleEvent.CLIENT_STARTED.register(minecraft -> {
            try {
                ClojureBridge.invoke(namespace, functionName, minecraft);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in client started handler", e);
            }
        });
    }

    /**
     * 注册客户端停止事件
     *
     * @param namespace    Clojure 命名空间
     * @param functionName Clojure 函数名
     */
    public static void registerClientStopping(String namespace, String functionName) {
        ClientLifecycleEvent.CLIENT_STOPPING.register(minecraft -> {
            try {
                ClojureBridge.invoke(namespace, functionName, minecraft);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in client stopping handler", e);
            }
        });
    }

    // ========================================================================
    // GUI 渲染事件
    // ========================================================================

    /**
     * 注册屏幕渲染后事件
     *
     * @param namespace    Clojure 命名空间
     * @param functionName Clojure 函数名
     */
    public static void registerScreenRenderPost(String namespace, String functionName) {
        ClientGuiEvent.RENDER_POST.register((screen, graphics, mouseX, mouseY, delta) -> {
            try {
                ClojureBridge.invokeVarargs(namespace, functionName, screen, graphics, mouseX, mouseY, delta);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in screen render post handler", e);
            }
        });
    }

    /**
     * 注册屏幕渲染前事件
     *
     * @param namespace    Clojure 命名空间
     * @param functionName Clojure 函数名
     */
    public static void registerScreenRenderPre(String namespace, String functionName) {
        ClientGuiEvent.RENDER_PRE.register((screen, graphics, mouseX, mouseY, delta) -> {
            try {
                ClojureBridge.invokeVarargs(namespace, functionName, screen, graphics, mouseX, mouseY, delta);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in screen render pre handler", e);
            }
            return EventResult.pass();
        });
    }

    // ========================================================================
    // 玩家事件
    // ========================================================================

    /**
     * 注册客户端玩家加入事件
     *
     * @param namespace    Clojure 命名空间
     * @param functionName Clojure 函数名
     */
    public static void registerClientPlayerJoin(String namespace, String functionName) {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            try {
                ClojureBridge.invoke(namespace, functionName, player);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in client player join handler", e);
            }
        });
    }

    /**
     * 注册客户端玩家离开事件
     *
     * @param namespace    Clojure 命名空间
     * @param functionName Clojure 函数名
     */
    public static void registerClientPlayerQuit(String namespace, String functionName) {
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            try {
                ClojureBridge.invoke(namespace, functionName, player);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in client player quit handler", e);
            }
        });
    }

    // ========================================================================
    // 通用事件注册器（使用 Consumer 回调）
    // ========================================================================

    /**
     * 使用 Java Consumer 注册客户端 tick 事件（供 Clojure 使用）
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerClientTickWithConsumer(Consumer<Minecraft> handler) {
        ClientTickEvent.CLIENT_PRE.register(new ClientTickEvent.Client() {
            @Override
            public void tick(Minecraft minecraft) {
                try {
                    handler.accept(minecraft);
                } catch (Exception e) {
                    LOGGER.error("[EventBridge] Error in client tick consumer", e);
                }
            }
        });
    }

    /**
     * 使用 Java Consumer 注册客户端世界 tick 事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerClientLevelTickWithConsumer(Consumer<ClientLevel> handler) {
        ClientTickEvent.CLIENT_LEVEL_PRE.register(level -> {
            try {
                handler.accept(level);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in client level tick consumer", e);
            }
        });
    }

    /**
     * 使用 Java Consumer 注册屏幕渲染后事件
     *
     * @param handler 处理器 - 接收: screen, graphics, mouseX, mouseY, delta
     */
    public static void registerScreenRenderPostWithConsumer(Consumer<Object[]> handler) {
        ClientGuiEvent.RENDER_POST.register((screen, graphics, mouseX, mouseY, delta) -> {
            try {
                handler.accept(new Object[]{screen, graphics, mouseX, mouseY, delta});
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in screen render post consumer", e);
            }
        });
    }
}
