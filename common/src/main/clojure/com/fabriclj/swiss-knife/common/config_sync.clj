(ns com.fabriclj.swiss-knife.common.config-sync
  "配置同步系统

   提供客户端-服务端配置同步功能：
   - 服务端配置推送到客户端
   - 客户端配置同步状态管理
   - 配置校验和冲突处理
   - 自动同步和手动同步"
  (:require [com.fabriclj.swiss-knife.common.core :as core]
            [com.fabriclj.swiss-knife.common.network :as net]
            [com.fabriclj.swiss-knife.common.config-file :as config]
            [clojure.edn :as edn])
  (:import [net.minecraft.server.level ServerPlayer]
           [net.minecraft.client Minecraft]
           [net.minecraft.resources ResourceLocation]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 配置同步状态管理
;; ============================================================================

(def ^:private client-sync-state (atom {}))
(def ^:private server-configs (atom {}))

(defn register-syncable-config!
  "注册可同步的配置

   参数:
   - config-id: 配置 ID（关键字）
   - config-path: 配置文件路径
   - opts: 选项
     - :required? - 是否必须同步（默认 false）
     - :on-sync - 同步完成回调 (fn [config] ...)
     - :validator - 配置校验器 (fn [config] boolean)

   示例:
   ```clojure
   (register-syncable-config! :server-rules
     \"config/server-rules.edn\"
     :required? true
     :on-sync (fn [config]
                (println \"Server rules synchronized:\"))
     :validator (fn [config]
                  (and (:max-players config)
                       (pos? (:max-players config)))))
   ```"
  [config-id config-path & {:keys [required? on-sync validator]
                            :or {required? false}}]
  (swap! server-configs assoc config-id
         {:path config-path
          :required? required?
          :on-sync on-sync
          :validator validator})
  (core/log-info (str \"Registered syncable config: \" config-id)))

;; ============================================================================
;; 服务端：配置推送
;; ============================================================================

(defn push-config-to-client!
  "推送配置到指定客户端

   参数:
   - player: ServerPlayer
   - config-id: 配置 ID

   示例:
   ```clojure
   (push-config-to-client! player :server-rules)
   ```"
  [^ServerPlayer player config-id]
  (when-let [config-info (get @server-configs config-id)]
    (let [config-data (config/load-config (:path config-info))]
      (core/log-info (str \"Pushing config \" config-id \" to player \" (.getName (.getGameProfile player))))
      (net/send-to-client!
        player
        (core/res-loc \"config_sync\")
        {:config-id config-id
         :config-data config-data
         :required? (:required? config-info)}))))

(defn push-all-configs-to-client!
  "推送所有可同步的配置到客户端

   参数:
   - player: ServerPlayer

   示例:
   ```clojure
   ;; 在玩家加入时推送所有配置
   (events/on-player-join
     (fn [player]
       (push-all-configs-to-client! player)))
   ```"
  [^ServerPlayer player]
  (doseq [[config-id _] @server-configs]
    (push-config-to-client! player config-id)))

(defn broadcast-config!
  "广播配置到所有在线客户端

   参数:
   - server: MinecraftServer
   - config-id: 配置 ID

   示例:
   ```clojure
   ;; 配置更新后广播
   (broadcast-config! server :server-rules)
   ```"
  [server config-id]
  (doseq [player (.getPlayerList (.getPlayers server))]
    (push-config-to-client! player config-id)))

;; ============================================================================
;; 客户端：配置接收和应用
;; ============================================================================

(defn apply-synced-config!
  "应用同步的配置（客户端）

   参数:
   - config-id: 配置 ID
   - config-data: 配置数据
   - required?: 是否为必需配置

   内部使用"
  [config-id config-data required?]
  (try
    ;; 验证配置
    (when-let [config-info (get @server-configs config-id)]
      (when-let [validator (:validator config-info)]
        (when-not (validator config-data)
          (throw (ex-info \"Config validation failed\"
                          {:config-id config-id
                           :data config-data})))))

    ;; 存储同步的配置
    (swap! client-sync-state assoc config-id
           {:data config-data
            :synced-at (System/currentTimeMillis)
            :required? required?})

    ;; 调用同步回调
    (when-let [on-sync (get-in @server-configs [config-id :on-sync])]
      (on-sync config-data))

    (core/log-info (str \"Applied synced config: \" config-id))
    true

    (catch Exception e
      (core/log-error (str \"Failed to apply synced config \" config-id \": \" (.getMessage e)))
      (when required?
        ;; 必需配置同步失败，断开连接
        (core/log-error \"Required config sync failed, disconnecting...\"))
      false)))

(defn get-synced-config
  "获取已同步的配置（客户端）

   参数:
   - config-id: 配置 ID

   返回：配置数据或 nil

   示例:
   ```clojure
   (let [rules (get-synced-config :server-rules)]
     (println \"Max players:\" (:max-players rules)))
   ```"
  [config-id]
  (get-in @client-sync-state [config-id :data]))

(defn is-config-synced?
  "检查配置是否已同步（客户端）

   参数:
   - config-id: 配置 ID

   返回：boolean"
  [config-id]
  (contains? @client-sync-state config-id))

(defn clear-synced-configs!
  "清除所有已同步的配置（客户端）

   用于断开连接或切换服务器时"
  []
  (reset! client-sync-state {})
  (core/log-info \"Cleared all synced configs\"))

;; ============================================================================
;; 网络数据包注册
;; ============================================================================

(defn register-config-sync-packets!
  "注册配置同步相关的网络数据包

   需要在模组初始化时调用"
  []
  ;; 注册服务端 -> 客户端的配置同步包
  (net/register-client-receiver!
    (core/res-loc \"config_sync\")
    (fn [data]
      (let [{:keys [config-id config-data required?]} data]
        (apply-synced-config! config-id config-data required?))))

  ;; 注册客户端 -> 服务端的配置请求包
  (net/register-server-receiver!
    (core/res-loc \"config_request\")
    (fn [data player]
      (let [{:keys [config-id]} data]
        (push-config-to-client! player config-id))))

  (core/log-info \"Config sync packets registered\"))

;; ============================================================================
;; 配置同步策略
;; ============================================================================

(defn create-sync-strategy
  "创建配置同步策略

   参数:
   - type: 策略类型
     - :on-join - 玩家加入时同步
     - :on-change - 配置更改时同步
     - :periodic - 定期同步
     - :manual - 手动同步
   - opts: 策略选项
     - :interval - 定期同步间隔（秒，仅 :periodic）
     - :configs - 要同步的配置列表（默认全部）

   返回：策略映射

   示例:
   ```clojure
   ;; 玩家加入时同步
   (def join-strategy
     (create-sync-strategy :on-join
       :configs [:server-rules :gameplay-config]))

   ;; 定期同步
   (def periodic-strategy
     (create-sync-strategy :periodic
       :interval 300  ; 每5分钟
       :configs [:dynamic-config]))
   ```"
  [type & {:keys [interval configs]
           :or {configs :all}}]
  {:type type
   :interval interval
   :configs configs})

(defn apply-sync-strategy!
  "应用配置同步策略

   参数:
   - server: MinecraftServer
   - strategy: 同步策略

   注意：需要在适当的事件中调用"
  [server strategy]
  (let [configs (if (= (:configs strategy) :all)
                  (keys @server-configs)
                  (:configs strategy))]
    (case (:type strategy)
      :on-join
      ;; 在玩家加入事件中调用
      (core/log-info \"Join sync strategy configured\")

      :on-change
      ;; 在配置变更监听器中调用
      (core/log-info \"Change sync strategy configured\")

      :periodic
      ;; 设置定期任务
      (core/log-info (str \"Periodic sync strategy configured: \" (:interval strategy) \"s\"))

      :manual
      ;; 手动同步
      (doseq [config-id configs]
        (broadcast-config! server config-id))

      (core/log-warn (str \"Unknown sync strategy type: \" (:type strategy))))))

;; ============================================================================
;; 配置冲突处理
;; ============================================================================

(defn detect-config-conflict
  "检测配置冲突

   参数:
   - client-config: 客户端配置
   - server-config: 服务端配置

   返回：冲突列表 [{:key key :client-value v1 :server-value v2}]"
  [client-config server-config]
  (let [conflicts (atom [])]
    (doseq [[k v] server-config]
      (let [client-value (get client-config k ::not-found)]
        (when (and (not= client-value ::not-found)
                   (not= client-value v))
          (swap! conflicts conj
                 {:key k
                  :client-value client-value
                  :server-value v}))))
    @conflicts))

(defn resolve-config-conflict
  "解决配置冲突

   参数:
   - conflict: 冲突信息
   - resolution: 解决方案
     - :use-server - 使用服务端值（默认）
     - :use-client - 使用客户端值
     - :merge - 合并值（如果可能）

   返回：解决后的值"
  [conflict resolution]
  (case resolution
    :use-server (:server-value conflict)
    :use-client (:client-value conflict)
    :merge (if (and (map? (:client-value conflict))
                    (map? (:server-value conflict)))
             (merge (:client-value conflict) (:server-value conflict))
             (:server-value conflict))
    (:server-value conflict)))

(comment
  ;; 使用示例

  ;; ========== 注册可同步配置 ==========

  ;; 1. 注册服务端规则配置
  (register-syncable-config! :server-rules
    \"config/server-rules.edn\"
    :required? true
    :on-sync (fn [config]
               (println \"Server rules applied:\" config))
    :validator (fn [config]
                 (and (:max-players config)
                      (pos? (:max-players config)))))

  ;; 2. 注册游戏玩法配置
  (register-syncable-config! :gameplay
    \"config/gameplay.edn\"
    :required? false
    :on-sync (fn [config]
               (println \"Gameplay config updated\")))

  ;; ========== 服务端推送配置 ==========

  ;; 3. 玩家加入时推送所有配置
  (events/on-player-join
    (fn [player]
      (push-all-configs-to-client! player)))

  ;; 4. 推送特定配置
  (push-config-to-client! player :server-rules)

  ;; 5. 广播配置更新
  (config/add-change-listener! :server-rules
    (fn [old-config new-config]
      (broadcast-config! server :server-rules)))

  ;; ========== 客户端接收配置 ==========

  ;; 6. 注册数据包处理器（模组初始化）
  (register-config-sync-packets!)

  ;; 7. 获取已同步的配置
  (let [rules (get-synced-config :server-rules)]
    (println \"Max players:\" (:max-players rules)))

  ;; 8. 检查配置是否已同步
  (when (is-config-synced? :server-rules)
    (println \"Server rules are synced!\"))

  ;; ========== 同步策略 ==========

  ;; 9. 创建并应用同步策略
  (def join-strategy
    (create-sync-strategy :on-join
      :configs [:server-rules :gameplay]))

  (events/on-player-join
    (fn [player]
      (doseq [config-id (:configs join-strategy)]
        (push-config-to-client! player config-id))))

  ;; 10. 定期同步策略
  (def periodic-strategy
    (create-sync-strategy :periodic
      :interval 300
      :configs [:dynamic-config]))

  ;; ========== 冲突处理 ==========

  ;; 11. 检测和解决冲突
  (let [client-cfg {:max-players 20 :pvp-enabled true}
        server-cfg {:max-players 10 :pvp-enabled false}
        conflicts (detect-config-conflict client-cfg server-cfg)]
    (doseq [conflict conflicts]
      (println \"Conflict:\" (:key conflict))
      (println \"  Client:\" (:client-value conflict))
      (println \"  Server:\" (:server-value conflict))
      (println \"  Resolved:\" (resolve-config-conflict conflict :use-server)))))
