package com.fabriclj;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientRawInputEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.InteractionEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;
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

    // ========================================================================
    // 服务端事件 - 解决类加载器问题
    // ========================================================================

    /**
     * 使用 Java Consumer 注册服务端 tick 事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerServerTickWithConsumer(Consumer<MinecraftServer> handler) {
        TickEvent.SERVER_PRE.register(new TickEvent.Server() {
            @Override
            public void tick(MinecraftServer server) {
                try {
                    handler.accept(server);
                } catch (Exception e) {
                    LOGGER.error("[EventBridge] Error in server tick consumer", e);
                }
            }
        });
    }

    /**
     * 使用 Java Consumer 注册世界 tick 事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerLevelTickWithConsumer(Consumer<Level> handler) {
        TickEvent.SERVER_LEVEL_PRE.register(level -> {
            try {
                handler.accept(level);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in level tick consumer", e);
            }
        });
    }

    /**
     * 使用 Java Consumer 注册玩家 tick 事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerPlayerTickWithConsumer(Consumer<Player> handler) {
        TickEvent.PLAYER_PRE.register(player -> {
            try {
                handler.accept(player);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in player tick consumer", e);
            }
        });
    }

    /**
     * 使用 Java Consumer 注册玩家加入事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerPlayerJoinWithConsumer(Consumer<Player> handler) {
        PlayerEvent.PLAYER_JOIN.register(player -> {
            try {
                handler.accept(player);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in player join consumer", e);
            }
        });
    }

    /**
     * 使用 Java Consumer 注册玩家离开事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerPlayerQuitWithConsumer(Consumer<Player> handler) {
        PlayerEvent.PLAYER_QUIT.register(player -> {
            try {
                handler.accept(player);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in player quit consumer", e);
            }
        });
    }

    /**
     * 使用 Java Consumer 注册服务器启动事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerServerStartingWithConsumer(Consumer<MinecraftServer> handler) {
        LifecycleEvent.SERVER_STARTING.register(server -> {
            try {
                handler.accept(server);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in server starting consumer", e);
            }
        });
    }

    /**
     * 使用 Java Consumer 注册服务器启动完成事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerServerStartedWithConsumer(Consumer<MinecraftServer> handler) {
        LifecycleEvent.SERVER_STARTED.register(server -> {
            try {
                handler.accept(server);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in server started consumer", e);
            }
        });
    }

    /**
     * 使用 Java Consumer 注册服务器停止事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerServerStoppingWithConsumer(Consumer<MinecraftServer> handler) {
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            try {
                handler.accept(server);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in server stopping consumer", e);
            }
        });
    }

    /**
     * 使用 Java Consumer 注册服务器停止完成事件
     *
     * @param handler Java Consumer 处理器
     */
    public static void registerServerStoppedWithConsumer(Consumer<MinecraftServer> handler) {
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            try {
                handler.accept(server);
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in server stopped consumer", e);
            }
        });
    }

    // ========================================================================
    // 方块事件 - 解决类加载器问题
    // ========================================================================

    /**
     * 使用函数式接口注册方块破坏事件
     *
     * @param handler 处理器 - 接收: Level, BlockPos, BlockState, ServerPlayer, IntValue
     */
    public static void registerBlockBreakWithHandler(java.util.function.Function<Object[], EventResult> handler) {
        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            try {
                return handler.apply(new Object[]{level, pos, state, player, xp});
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in block break handler", e);
                return EventResult.pass();
            }
        });
    }

    /**
     * 使用函数式接口注册方块放置事件
     *
     * @param handler 处理器 - 接收: Level, BlockPos, BlockState, Entity
     */
    public static void registerBlockPlaceWithHandler(java.util.function.Function<Object[], EventResult> handler) {
        BlockEvent.PLACE.register((level, pos, state, entity) -> {
            try {
                return handler.apply(new Object[]{level, pos, state, entity});
            } catch (Exception e) {
                LOGGER.error("[EventBridge] Error in block place handler", e);
                return EventResult.pass();
            }
        });
    }

    // ========================================================================
    // 实体事件 - 解决类加载器问题
    // ========================================================================

    /**
     * 使用函数式接口注册实体生成事件
     *
     * @param handler 处理器 - 接收: Entity, Level
     */
    public static void registerEntitySpawnWithHandler(java.util.function.Function<Object[], EventResult> handler) {
        EntityEvent.ADD.register(new EntityEvent.Add() {
            @Override
            public EventResult add(Entity entity, Level level) {
                try {
                    return handler.apply(new Object[]{entity, level});
                } catch (Exception e) {
                    LOGGER.error("[EventBridge] Error in entity spawn handler", e);
                    return EventResult.pass();
                }
            }
        });
    }

    // ========================================================================
    // 交互事件 - 解决类加载器问题
    // ========================================================================

    /**
     * 使用函数式接口注册右键方块事件
     *
     * @param handler 处理器 - 接收: Player, InteractionHand, BlockPos, Direction
     */
    public static void registerRightClickBlockWithHandler(
            java.util.function.Function<Object[], EventResult> handler) {
        InteractionEvent.RIGHT_CLICK_BLOCK.register(new InteractionEvent.RightClickBlock() {
            @Override
            public EventResult click(Player player, InteractionHand hand, BlockPos pos,
                    net.minecraft.core.Direction direction) {
                try {
                    return handler.apply(new Object[]{player, hand, pos, direction});
                } catch (Exception e) {
                    LOGGER.error("[EventBridge] Error in right click block handler", e);
                    return EventResult.pass();
                }
            }
        });
    }
}
