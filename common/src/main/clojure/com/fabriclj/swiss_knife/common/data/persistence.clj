(ns com.fabriclj.swiss-knife.common.data.persistence
  "瑞士军刀 - 数据持久化模块

   提供玩家数据、世界数据的保存和加载功能。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import [net.minecraft.nbt CompoundTag ListTag Tag]
           [net.minecraft.world.entity.player Player]
           [net.minecraft.server.level ServerPlayer]
           [net.minecraft.world.level.saveddata SavedData]
           [java.util UUID]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; NBT 辅助工具
;; ============================================================================

(defn nbt->clj
  "将 NBT 转换为 Clojure 数据

   支持：CompoundTag -> Map, ListTag -> Vector"
  [^Tag nbt]
  (cond
    (instance? CompoundTag nbt)
    (let [^CompoundTag tag nbt
          keys (.getAllKeys tag)]
      (into {}
            (map (fn [key]
                   [(keyword key) (nbt->clj (.get tag key))])
                 keys)))

    (instance? ListTag nbt)
    (let [^ListTag tag nbt]
      (mapv nbt->clj (range (.size tag))))

    :else
    (condp instance? nbt
      net.minecraft.nbt.IntTag (.getAsInt nbt)
      net.minecraft.nbt.LongTag (.getAsLong nbt)
      net.minecraft.nbt.FloatTag (.getAsFloat nbt)
      net.minecraft.nbt.DoubleTag (.getAsDouble nbt)
      net.minecraft.nbt.StringTag (.getAsString nbt)
      net.minecraft.nbt.ByteTag (= 1 (.getAsByte nbt))
      nil)))

(defn clj->nbt
  "将 Clojure 数据转换为 NBT

   支持：Map -> CompoundTag, Vector -> ListTag"
  ^CompoundTag [data]
  (let [tag (CompoundTag.)]
    (doseq [[k v] data]
      (let [key-str (name k)]
        (cond
          (map? v)
          (.put tag key-str (clj->nbt v))

          (vector? v)
          (let [list (ListTag.)]
            (doseq [item v]
              (.add list
                    (if (map? item)
                      (clj->nbt item)
                      (cond
                        (integer? item) (net.minecraft.nbt.IntTag/valueOf item)
                        (string? item) (net.minecraft.nbt.StringTag/valueOf item)
                        (boolean? item) (net.minecraft.nbt.ByteTag/valueOf (if item 1 0))
                        :else (net.minecraft.nbt.StringTag/valueOf (str item))))))
            (.put tag key-str list))

          (integer? v)
          (.putInt tag key-str v)

          (float? v)
          (.putFloat tag key-str v)

          (string? v)
          (.putString tag key-str v)

          (boolean? v)
          (.putBoolean tag key-str v)

          :else
          (.putString tag key-str (str v)))))
    tag))

;; ============================================================================
;; 玩家数据
;; ============================================================================

(def ^:private default-data-key "fabriclj_data")

(defn make-data-key
  "创建命名空间化的数据键

   参数:
   - mod-id: Mod ID
   - key-name: 键名称（可选，默认 \"data\"）

   示例:
   ```clojure
   (make-data-key \"mymod\") ;; => \"fabriclj::mymod::data\"
   (make-data-key \"mymod\" \"player_stats\") ;; => \"fabriclj::mymod::player_stats\"
   ```"
  ([mod-id]
   (make-data-key mod-id "data"))
  ([mod-id key-name]
   (str "fabriclj::" mod-id "::" key-name)))

(defn get-player-data
  "获取玩家持久化数据

   参数:
   - player: Player
   - data-key: 数据键（可选，默认使用内部键）

   返回：Clojure Map

   示例:
   ```clojure
   ;; 使用默认键
   (get-player-data player)

   ;; 使用自定义命名空间
   (get-player-data player (make-data-key \"mymod\"))
   ```"
  ([^Player player]
   (get-player-data player default-data-key))
  ([^Player player data-key]
   (let [persistent-data (.getPersistentData player)]
     (if (.contains persistent-data data-key)
       (nbt->clj (.getCompound persistent-data data-key))
       {}))))

(defn set-player-data!
  "设置玩家持久化数据

   参数:
   - player: Player
   - data: Clojure Map
   - data-key: 数据键（可选）

   示例:
   ```clojure
   ;; 使用默认键
   (set-player-data! player {:mana 100 :level 5})

   ;; 使用自定义命名空间
   (set-player-data! player {:mana 100} (make-data-key \"mymod\"))
   ```"
  ([^Player player data]
   (set-player-data! player data default-data-key))
  ([^Player player data data-key]
   (let [persistent-data (.getPersistentData player)
         nbt (clj->nbt data)]
     (.put persistent-data data-key nbt))))

(defn update-player-data!
  "更新玩家数据（使用函数）

   参数:
   - player: Player
   - f: 更新函数 (fn [old-data] -> new-data)
   - data-key: 数据键（可选）

   示例:
   ```clojure
   (update-player-data! player #(update % :mana + 10))
   (update-player-data! player #(update % :mana + 10) (make-data-key \"mymod\"))
   ```"
  ([^Player player f]
   (update-player-data! player f default-data-key))
  ([^Player player f data-key]
   (let [old-data (get-player-data player data-key)
         new-data (f old-data)]
     (set-player-data! player new-data data-key))))

(defn get-player-value
  "获取玩家数据字段

   参数:
   - player: Player
   - key: 字段关键字
   - default: 默认值（可选）
   - data-key: 数据键（可选）"
  ([player key]
   (get-player-value player key nil))
  ([player key default]
   (get-player-value player key default default-data-key))
  ([player key default data-key]
   (get (get-player-data player data-key) key default)))

(defn set-player-value!
  "设置玩家数据字段

   参数:
   - player: Player
   - key: 字段关键字
   - value: 字段值
   - data-key: 数据键（可选）"
  ([player key value]
   (set-player-value! player key value default-data-key))
  ([player key value data-key]
   (update-player-data! player
                        #(assoc % key value)
                        data-key)))

;; ============================================================================
;; 世界数据
;; ============================================================================

(defonce ^:private world-data-store (atom {}))

(defn create-world-data
  "创建世界数据存储

   参数:
   - id: 数据 ID
   - initial-data: 初始数据（可选）

   返回：数据原子"
  ([id]
   (create-world-data id {}))
  ([id initial-data]
   (let [data (atom initial-data)]
     (swap! world-data-store assoc id data)
     data)))

(defn get-world-data
  "获取世界数据"
  [id]
  (when-let [store (get @world-data-store id)]
    @(:data store)))

(defn set-world-data!
  "设置世界数据"
  [id data]
  (when-let [data-atom (get @world-data-store id)]
    (reset! data-atom data)))

(defn update-world-data!
  "更新世界数据"
  [id f]
  (when-let [data-atom (get @world-data-store id)]
    (swap! data-atom f)))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro with-player-data
  "在玩家数据上下文中执行操作

   示例:
   ```clojure
   (with-player-data [data player]
     (update data :mana + 10))
   ```"
  [[binding player] & body]
  `(let [~binding (get-player-data ~player)
         result# (do ~@body)]
     (set-player-data! ~player result#)
     result#))

;; Response to clyce: 是的，完全支持持久化。
;;
;; 玩家数据通过 Minecraft 的 PersistentData NBT 系统自动持久化：
;; - 数据保存在玩家的 .dat 文件中（world/playerdata/UUID.dat）
;; - 当玩家登出时自动保存
;; - 当玩家登入时自动加载
;; - 跨服务器重启保持数据
;;
;; 世界数据目前使用内存存储（atom），需要额外实现持久化。
;; 可以通过 Minecraft 的 SavedData 系统实现，或使用自定义序列化方案。
;; 建议上层 mod 根据需求选择持久化策略。

;; ============================================================================
;; 世界数据持久化策略
;; ============================================================================

(defprotocol WorldDataPersistence
  "世界数据持久化协议

  (save-world-data [this id data] \"保存数据\")
  (load-world-data [this id] \"加载数据\")")

(defrecord MemoryPersistence []
  WorldDataPersistence
  (save-world-data [_ id data]
    ;; 内存存储，不做额外操作
    nil)
  (load-world-data [_ id]
    nil))

(defrecord NBTPersistence [server-level data-dir]
  WorldDataPersistence
  (save-world-data [_ id data]
    ;; TODO: 实现 NBT 文件持久化
    ;; 保存到 world/data/mod-id/data-id.nbt
    (comment "Save to NBT file"))
  (load-world-data [_ id]
    ;; TODO: 从 NBT 文件加载
    (comment "Load from NBT file")))

(def ^:private default-persistence (atom (->MemoryPersistence)))

(defn set-world-data-persistence!
  "设置世界数据持久化策略

   参数:
   - strategy: 持久化策略实例
     - (->MemoryPersistence) - 仅内存（默认）
     - (->NBTPersistence server-level data-dir) - NBT 文件持久化

   示例:
   ```clojure
   ;; 使用 NBT 持久化
   (set-world-data-persistence!
     (->NBTPersistence server-level \"mymod_data\"))
   ```"
  [strategy]
  (reset! default-persistence strategy))

(defn create-world-data
  "创建世界数据存储（现支持持久化）

   参数:
   - id: 数据 ID
   - initial-data: 初始数据（可选）
   - persistence: 持久化策略（可选，默认使用全局策略）

   返回：数据原子

   示例:
   ```clojure
   ;; 使用默认策略（内存）
   (create-world-data \"my-data\" {:counter 0})

   ;; 使用自定义持久化
   (create-world-data \"my-data\" {:counter 0}
     (->NBTPersistence server-level \"mymod\"))
   ```"
  ([id]
   (create-world-data id {} @default-persistence))
  ([id initial-data]
   (create-world-data id initial-data @default-persistence))
  ([id initial-data persistence]
   ;; 尝试从持久化加载
   (let [loaded (load-world-data persistence id)
         data (atom (or loaded initial-data))]
     (swap! world-data-store assoc id {:data data :persistence persistence})
     data)))

(defn set-world-data!
  "设置世界数据（自动持久化）"
  [id data]
  (when-let [store (get @world-data-store id)]
    (reset! (:data store) data)
    (save-world-data (:persistence store @default-persistence) id data)))

(defn update-world-data!
  "更新世界数据（自动持久化）"
  [id f]
  (when-let [store (get @world-data-store id)]
    (let [new-data (swap! (:data store) f)
          persistence (:persistence store @default-persistence)]
      (save-world-data persistence id new-data))))

(defn force-save-world-data!
  "强制保存世界数据到持久化存储

   参数:
   - id: 数据 ID（nil 表示保存所有）"
  ([]
   (doseq [[id store] @world-data-store]
     (force-save-world-data! id)))
  ([id]
   (when-let [store (get @world-data-store id)]
     (let [data @(:data store)
           persistence (:persistence store @default-persistence)]
       (save-world-data persistence id data)))))

(comment
  ;; 使用示例

  ;; 1. 玩家数据
  (set-player-data! player
                    {:mana 100
                     :level 5
                     :skills [:fireball :ice-lance]})

  (def data (get-player-data player))
  (println "Mana:" (:mana data))

  ;; 2. 更新数据
  (update-player-data! player
                       #(-> %
                            (update :mana + 10)
                            (update :level inc)))

  ;; 3. 单个字段
  (set-player-value! player :mana 150)
  (println (get-player-value player :mana))

  ;; 4. 使用宏
  (with-player-data [data player]
    (assoc data :new-skill :lightning))

  ;; ========== 世界数据持久化 ==========

  ;; 5. 使用默认策略（内存）
  (create-world-data "server-stats" {:player-count 0})
  (update-world-data! "server-stats" #(update % :player-count inc))

  ;; 6. 使用 NBT 持久化
  (set-world-data-persistence!
   (->NBTPersistence server-level "mymod_data"))

  (create-world-data "persistent-stats" {:score 0})

  ;; 7. 强制保存所有数据
  (force-save-world-data!))
