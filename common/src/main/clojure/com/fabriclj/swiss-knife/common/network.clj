(ns com.fabriclj.swiss-knife.common.network
  "瑞士军刀 - 网络通信模块

   封装 Architectury API 的网络系统，提供客户端-服务端通信功能。"
  (:require [com.fabriclj.swiss-knife.common.core :as core])
  (:import [dev.architectury.networking NetworkManager NetworkManager$Side]
           [net.minecraft.resources ResourceLocation]
           [net.minecraft.network FriendlyByteBuf]
           [net.minecraft.network.codec StreamCodec]
           [net.minecraft.network.protocol.common.custom CustomPacketPayload]
           [net.minecraft.server.level ServerPlayer]
           [net.minecraft.world.entity.player Player]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 数据包编解码
;; ============================================================================

(defn write-string!
  "写入字符串到缓冲区"
  [^FriendlyByteBuf buf ^String s]
  (.writeUtf buf s))

(defn read-string
  "从缓冲区读取字符串"
  ^String [^FriendlyByteBuf buf]
  (.readUtf buf))

(defn write-int!
  "写入整数"
  [^FriendlyByteBuf buf ^Integer i]
  (.writeInt buf i))

(defn read-int
  "读取整数"
  [^FriendlyByteBuf buf]
  (.readInt buf))

(defn write-long!
  "写入长整数"
  [^FriendlyByteBuf buf ^Long l]
  (.writeLong buf l))

(defn read-long
  "读取长整数"
  [^FriendlyByteBuf buf]
  (.readLong buf))

(defn write-float!
  "写入浮点数"
  [^FriendlyByteBuf buf ^Float f]
  (.writeFloat buf f))

(defn read-float
  "读取浮点数"
  [^FriendlyByteBuf buf]
  (.readFloat buf))

(defn write-double!
  "写入双精度浮点数"
  [^FriendlyByteBuf buf ^Double d]
  (.writeDouble buf d))

(defn read-double
  "读取双精度浮点数"
  [^FriendlyByteBuf buf]
  (.readDouble buf))

(defn write-boolean!
  "写入布尔值"
  [^FriendlyByteBuf buf ^Boolean b]
  (.writeBoolean buf b))

(defn read-boolean
  "读取布尔值"
  [^FriendlyByteBuf buf]
  (.readBoolean buf))

(defn write-uuid!
  "写入 UUID"
  [^FriendlyByteBuf buf ^java.util.UUID uuid]
  (.writeUUID buf uuid))

(defn read-uuid
  "读取 UUID"
  ^java.util.UUID [^FriendlyByteBuf buf]
  (.readUUID buf))

(defn write-nbt!
  "写入 NBT 数据"
  [^FriendlyByteBuf buf ^net.minecraft.nbt.CompoundTag nbt]
  (.writeNbt buf nbt))

(defn read-nbt
  "读取 NBT 数据"
  ^net.minecraft.nbt.CompoundTag [^FriendlyByteBuf buf]
  (.readNbt buf))

;; ============================================================================
;; EDN 数据编解码（Clojure 特色）
;; ============================================================================

(defn write-edn!
  "将 Clojure 数据写入缓冲区（序列化为字符串）

   参数:
   - buf: FriendlyByteBuf
   - data: 任意可序列化的 Clojure 数据

   示例:
   ```clojure
   (write-edn! buf {:type :fireball :damage 10 :targets [uuid1 uuid2]})
   ```"
  [^FriendlyByteBuf buf data]
  (write-string! buf (pr-str data)))

(defn read-edn
  "从缓冲区读取 Clojure 数据

   示例:
   ```clojure
   (let [data (read-edn buf)]
     (println \"Received:\" data))
   ```"
  [^FriendlyByteBuf buf]
  (clojure.edn/read-string (read-string buf)))

;; ============================================================================
;; 数据包定义
;; ============================================================================

(defonce ^:private registered-packets (atom {}))

(defn create-packet-type
  "创建数据包类型

   参数:
   - id: ResourceLocation 或字符串
   - encoder: 编码函数 (fn [buf data] ...)
   - decoder: 解码函数 (fn [buf] -> data)

   返回：数据包类型标识符

   示例:
   ```clojure
   (def my-packet
     (create-packet-type \"mymod:my_packet\"
       (fn [buf data]
         (write-string! buf (:message data))
         (write-int! buf (:number data)))
       (fn [buf]
         {:message (read-string buf)
          :number (read-int buf)})))
   ```"
  [id encoder decoder]
  (let [^ResourceLocation loc (core/->resource-location id)]
    (swap! registered-packets assoc loc {:encoder encoder :decoder decoder})
    loc))

(defn create-edn-packet-type
  "创建基于 EDN 的数据包类型（简化版）

   自动使用 EDN 序列化，无需手动编解码。

   参数:
   - id: 数据包 ID

   示例:
   ```clojure
   (def simple-packet (create-edn-packet-type \"mymod:simple\"))

   ;; 发送任意 Clojure 数据
   (send-to-server! simple-packet {:action :buy :item-id \"sword\" :count 1})
   ```"
  [id]
  (create-packet-type id
                      (fn [buf data] (write-edn! buf data))
                      (fn [buf] (read-edn buf))))

;; ============================================================================
;; 数据包注册
;; ============================================================================

(defn register-server-receiver!
  "注册服务端数据包接收器

   参数:
   - packet-type: 数据包类型（ResourceLocation）
   - handler: 处理函数 (fn [data ^ServerPlayer player] ...)

   示例:
   ```clojure
   (register-server-receiver! my-packet
     (fn [data player]
       (println \"Server received from\" (.getName player) \":\" data)
       ;; 处理逻辑...
       ))
   ```"
  [^ResourceLocation packet-type handler]
  (let [{:keys [decoder]} (get @registered-packets packet-type)]
    (NetworkManager/registerReceiver
     NetworkManager$Side/S2C
     packet-type
     (reify dev.architectury.networking.NetworkManager$NetworkReceiver
       (receive [_ buf context]
         (let [data (decoder buf)
               player (.getPlayer context)]
           (.queue context
                   (fn []
                     (handler data player)))))))))

(defn register-client-receiver!
  "注册客户端数据包接收器

   参数:
   - packet-type: 数据包类型（ResourceLocation）
   - handler: 处理函数 (fn [data ^Player player] ...)

   注意：仅在客户端环境调用！

   示例:
   ```clojure
   (register-client-receiver! my-packet
     (fn [data player]
       (println \"Client received:\" data)))
   ```"
  [^ResourceLocation packet-type handler]
  (let [{:keys [decoder]} (get @registered-packets packet-type)]
    (NetworkManager/registerReceiver
     NetworkManager$Side/C2S
     packet-type
     (reify dev.architectury.networking.NetworkManager$NetworkReceiver
       (receive [_ buf context]
         (let [data (decoder buf)
               player (.getPlayer context)]
           (.queue context
                   (fn []
                     (handler data player)))))))))

;; ============================================================================
;; 数据包发送
;; ============================================================================

(defn send-to-server!
  "从客户端发送数据包到服务端

   参数:
   - packet-type: 数据包类型
   - data: 要发送的数据

   示例:
   ```clojure
   (send-to-server! my-packet {:action :jump})
   ```"
  [^ResourceLocation packet-type data]
  (let [{:keys [encoder]} (get @registered-packets packet-type)
        buf (io.netty.buffer.Unpooled/buffer)]
    (encoder (FriendlyByteBuf. buf) data)
    (NetworkManager/sendToServer packet-type (FriendlyByteBuf. buf))))

(defn send-to-player!
  "从服务端发送数据包到指定玩家

   参数:
   - player: ServerPlayer
   - packet-type: 数据包类型
   - data: 要发送的数据

   示例:
   ```clojure
   (send-to-player! player my-packet {:message \"Hello!\"})
   ```"
  [^ServerPlayer player ^ResourceLocation packet-type data]
  (let [{:keys [encoder]} (get @registered-packets packet-type)
        buf (io.netty.buffer.Unpooled/buffer)]
    (encoder (FriendlyByteBuf. buf) data)
    (NetworkManager/sendToPlayer player packet-type (FriendlyByteBuf. buf))))

(defn send-to-all!
  "从服务端广播数据包到所有玩家

   参数:
   - server: MinecraftServer
   - packet-type: 数据包类型
   - data: 要发送的数据"
  [^net.minecraft.server.MinecraftServer server ^ResourceLocation packet-type data]
  (doseq [^ServerPlayer player (.getPlayerList (.players server))]
    (send-to-player! player packet-type data)))

(defn send-to-all-near!
  "发送数据包到指定位置附近的所有玩家

   参数:
   - level: ServerLevel
   - x, y, z: 坐标
   - radius: 半径
   - packet-type: 数据包类型
   - data: 数据"
  [^net.minecraft.server.level.ServerLevel level x y z radius ^ResourceLocation packet-type data]
  (let [{:keys [encoder]} (get @registered-packets packet-type)
        buf (io.netty.buffer.Unpooled/buffer)]
    (encoder (FriendlyByteBuf. buf) data)
    (doseq [^ServerPlayer player (.players level)]
      (let [pos (.position player)]
        (when (< (.distanceToSqr pos x y z) (* radius radius))
          (send-to-player! player packet-type data))))))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro defpacket
  "定义数据包类型（语法糖）

   示例:
   ```clojure
   (defpacket chat-message \"mymod:chat\"
     :encode (fn [buf data]
               (write-string! buf (:message data)))
     :decode (fn [buf]
               {:message (read-string buf)})
     :server (fn [data player]
               (println \"Server:\" data))
     :client (fn [data player]
               (println \"Client:\" data)))
   ```"
  [name id & {:keys [encode decode server client]}]
  `(do
     (def ~name
       (create-packet-type ~id ~encode ~decode))
     ~(when server
        `(register-server-receiver! ~name ~server))
     ~(when client
        `(register-client-receiver! ~name ~client))
     (var ~name)))

(defmacro defpacket-edn
  "定义基于 EDN 的数据包（自动序列化）

   示例:
   ```clojure
   (defpacket-edn simple-msg \"mymod:simple\"
     :server (fn [data player]
               (println \"Received:\" data)))
   ```"
  [name id & {:keys [server client]}]
  `(do
     (def ~name
       (create-edn-packet-type ~id))
     ~(when server
        `(register-server-receiver! ~name ~server))
     ~(when client
        `(register-client-receiver! ~name ~client))
     (var ~name)))

;; ============================================================================
;; 通用数据包系统
;; ============================================================================

;; TODO: 作为 lib 类的 mod，这里是不是应该由上层mod决定？
(defonce ^:private generic-packet-id
  (core/resource-location "swiss-knife" "generic"))

(defonce ^:private generic-handlers (atom {}))

(defn init-generic-packet-system!
  "初始化通用数据包系统

   允许通过字符串 ID 动态注册处理器，无需预定义数据包类型。

   注意：必须在 mod 初始化时调用一次！"
  []
  (create-edn-packet-type generic-packet-id)

  (register-server-receiver! generic-packet-id
                             (fn [data player]
                               (when-let [handler (get @generic-handlers (:id data))]
                                 (handler (:payload data) player))))

  (register-client-receiver! generic-packet-id
                             (fn [data player]
                               (when-let [handler (get @generic-handlers (:id data))]
                                 (handler (:payload data) player)))))

(defn register-generic-handler!
  "注册通用数据包处理器

   参数:
   - id: 处理器 ID（字符串或关键字）
   - side: :server 或 :client
   - handler: 处理函数

   示例:
   ```clojure
   (register-generic-handler! :my-action :server
     (fn [data player]
       (println \"Action:\" data)))

   ;; 发送
   (send-generic! :my-action {:value 123})
   ```"
  [id side handler]
  (swap! generic-handlers assoc id handler))

(defn send-generic!
  "发送通用数据包

   参数:
   - handler-id: 处理器 ID
   - data: 数据
   - target: 发送目标（可选，:server 或 ServerPlayer）"
  ([handler-id data]
   (send-to-server! generic-packet-id {:id handler-id :payload data}))
  ([handler-id data ^ServerPlayer target]
   (send-to-player! target generic-packet-id {:id handler-id :payload data})))

(comment
  ;; 使用示例

  ;; 1. 定义数据包类型（方式 1：EDN 自动序列化）
  (defpacket-edn chat-packet "mymod:chat"
    :server (fn [data player]
              (println (.getName player) "says:" (:message data)))
    :client (fn [data player]
              (println "Server broadcast:" (:message data))))

  ;; 客户端发送
  (send-to-server! chat-packet {:message "Hello world!"})

  ;; 服务端发送给玩家
  (send-to-player! player chat-packet {:message "Welcome!"})

  ;; 2. 定义数据包类型（方式 2：手动编解码）
  (defpacket position-packet "mymod:position"
    :encode (fn [buf data]
              (write-double! buf (:x data))
              (write-double! buf (:y data))
              (write-double! buf (:z data)))
    :decode (fn [buf]
              {:x (read-double buf)
               :y (read-double buf)
               :z (read-double buf)})
    :server (fn [data player]
              (println "Player at" data)))

  ;; 3. 通用数据包系统
  (init-generic-packet-system!)

  (register-generic-handler! :buy-item :server
                             (fn [data player]
                               (println "Player wants to buy" (:item data))))

  (send-generic! :buy-item {:item "sword" :count 1}))
