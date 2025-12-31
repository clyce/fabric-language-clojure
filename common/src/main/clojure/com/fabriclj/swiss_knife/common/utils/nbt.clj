(ns com.fabriclj.swiss-knife.common.utils.nbt
  "NBT 数据工具

   提供 Minecraft NBT( Named Binary Tag) 系统的 Clojure 封装:
   - NBT 解析和序列化
   - NBT 与 Clojure 数据结构互转
   - NBT 美化输出

   NBT 是 Minecraft 用于保存和传输结构化数据的格式。"
  (:import (net.minecraft.nbt CompoundTag Tag ListTag StringTag
            IntTag DoubleTag FloatTag LongTag ByteTag ShortTag TagParser))

(set! *warn-on-reflection* true)

;; ============================================================================
;; NBT 解析和序列化
;; ============================================================================

(defn parse-nbt-string
  "解析 NBT 格式字符串为 CompoundTag

   参数:
   - nbt-string: NBT 格式字符串

   返回: CompoundTag

   NBT 格式示例:
   - `{Health:20.0f,OnGround:1b}`
   - `{CustomName:'{\"text\":\"Diamond Sword\"}',Damage:10}`

   示例:
   ```clojure
   (parse-nbt-string \"{Health:20.0f,OnGround:1b}\")
   (parse-nbt-string \"{id:\\\"minecraft:diamond_sword\\\",Count:1b}\")
   ```"
  [^String nbt-string]
  (TagParser/parseTag nbt-string))

(defn nbt->pretty-string
  "NBT 转美化字符串

   参数:
   - nbt-tag: NBT Tag

   返回: 格式化的字符串

   示例:
   ```clojure
   (def nbt (CompoundTag.))
   (.putInt nbt \"health\" 20)
   (nbt->pretty-string nbt)
   ; => \"{health:20}\"
   ```"
  [^Tag nbt-tag]
  (.toString nbt-tag))

;; ============================================================================
;; NBT 与 Clojure 数据互转
;; ============================================================================

(defn nbt->map
  "NBT 转 Clojure Map

   递归转换 CompoundTag 为嵌套的 Clojure Map

   支持的类型:
   - CompoundTag -> Map
   - IntTag -> Integer
   - DoubleTag -> Double
   - FloatTag -> Float
   - StringTag -> String
   - ByteTag -> Byte
   - 其他 -> 字符串形式

   注意: ListTag 不会被转换，保留为字符串

   参数:
   - nbt: CompoundTag

   返回: Clojure Map

   示例:
   ```clojure
   (def nbt (CompoundTag.))
   (.putInt nbt \"level\" 10)
   (.putString nbt \"name\" \"Steve\")

   (nbt->map nbt)
   ; => {\"level\" 10, \"name\" \"Steve\"}
   ```"
  [^CompoundTag nbt]
  (into {}
        (map (fn [key]
               (let [tag (.get nbt key)]
                 [key (cond
                        (instance? CompoundTag tag) (nbt->map tag)
                        (instance? IntTag tag) (.getAsInt ^IntTag tag)
                        (instance? DoubleTag tag) (.getAsDouble ^DoubleTag tag)
                        (instance? FloatTag tag) (.getAsFloat ^FloatTag tag)
                        (instance? StringTag tag) (.getAsString ^StringTag tag)
                        (instance? LongTag tag) (.getAsLong ^LongTag tag)
                        (instance? ShortTag tag) (.getAsShort ^ShortTag tag)
                        (instance? ByteTag tag) (.getAsByte ^ByteTag tag)
                        :else (.toString tag))]))
             (.getAllKeys nbt))))

(defn map->nbt
  "Clojure Map 转 NBT

   递归转换 Clojure Map 为 CompoundTag

   支持的类型:
   - Integer -> IntTag
   - Long -> LongTag
   - Float -> FloatTag
   - Double -> DoubleTag
   - Boolean -> ByteTag (1/0)
   - String -> StringTag
   - Map -> CompoundTag (递归)
   - 其他 -> StringTag (转字符串)

   参数:
   - data: Clojure Map

   返回: CompoundTag

   示例:
   ```clojure
   (map->nbt {:level 10
              :name \"Steve\"
              :stats {:health 20.0
                      :armor 5}})
   ```"
  [data]
  (let [nbt (CompoundTag.)]
    (doseq [[k v] data]
      (let [key-str (name k)]
        (cond
          (instance? Integer v) (.putInt nbt key-str (int v))
          (instance? Long v) (.putLong nbt key-str (long v))
          (float? v) (.putFloat nbt key-str (float v))
          (double? v) (.putDouble nbt key-str (double v))
          (string? v) (.putString nbt key-str v)
          (boolean? v) (.putBoolean nbt key-str v)
          (map? v) (.put nbt key-str (map->nbt v))
          :else (.putString nbt key-str (str v)))))
    nbt))

;; ============================================================================
;; 便捷函数
;; ============================================================================

(defn get-nbt-value
  "从 CompoundTag 获取值

   参数:
   - nbt: CompoundTag
   - key: 键名
   - default: 默认值( 可选)

   示例:
   ```clojure
   (get-nbt-value nbt \"health\" 20)
   (get-nbt-value nbt \"name\" \"Unknown\")
   ```"
  ([^CompoundTag nbt key]
   (get-nbt-value nbt key nil))
  ([^CompoundTag nbt key default]
   (if (.contains nbt key)
     (let [tag (.get nbt key)]
       (cond
         (instance? IntTag tag) (.getAsInt ^IntTag tag)
         (instance? DoubleTag tag) (.getAsDouble ^DoubleTag tag)
         (instance? FloatTag tag) (.getAsFloat ^FloatTag tag)
         (instance? StringTag tag) (.getAsString ^StringTag tag)
         (instance? LongTag tag) (.getAsLong ^LongTag tag)
         (instance? ShortTag tag) (.getAsShort ^ShortTag tag)
         (instance? ByteTag tag) (.getAsByte ^ByteTag tag)
         (instance? CompoundTag tag) tag
         :else (.toString tag)))
     default)))

(defn merge-nbt
  "合并多个 CompoundTag

   后面的 NBT 会覆盖前面的同名键

   参数:
   - nbts: CompoundTag 序列

   返回: 合并后的 CompoundTag

   示例:
   ```clojure
   (def nbt1 (map->nbt {:health 20 :mana 100}))
   (def nbt2 (map->nbt {:health 15 :armor 5}))

   (merge-nbt nbt1 nbt2)
   ; => {health:15, mana:100, armor:5}
   ```"
  [& nbts]
  (let [result (CompoundTag.)]
    (doseq [^CompoundTag nbt nbts]
      (.merge result nbt))
    result))

(comment
  ;; 使用示例

  ;; ========== 基础操作 ==========

  ;; 1. 创建和读取 NBT
  (def nbt (CompoundTag.))
  (.putInt nbt "level" 10)
  (.putString nbt "name" "Steve")
  (.putDouble nbt "health" 20.0)

  (.getInt nbt "level")    ; => 10
  (.getString nbt "name")  ; => "Steve"

  ;; 2. 解析 NBT 字符串
  (def parsed (parse-nbt-string "{Health:20.0f,OnGround:1b}"))
  (.getFloat parsed "Health")  ; => 20.0

  ;; ========== 数据转换 ==========

  ;; 3. Map 转 NBT
  (def my-nbt
    (map->nbt {:level 10
               :name "Steve"
               :stats {:health 20.0
                       :armor 5}}))

  ;; 4. NBT 转 Map
  (nbt->map my-nbt)
  ; => {"level" 10, "name" "Steve", "stats" {"health" 20.0, "armor" 5}}

  ;; 5. 美化输出
  (nbt->pretty-string my-nbt)
  ; => "{level:10,name:Steve,stats:{health:20.0,armor:5}}"

  ;; ========== 便捷函数 ==========

  ;; 6. 安全获取值
  (get-nbt-value my-nbt "level" 0)        ; => 10
  (get-nbt-value my-nbt "missing" "N/A")  ; => "N/A"

  ;; 7. 合并 NBT
  (def nbt1 (map->nbt {:health 20 :mana 100}))
  (def nbt2 (map->nbt {:health 15 :armor 5}))
  (nbt->map (merge-nbt nbt1 nbt2))
  ; => {"health" 15, "mana" 100, "armor" 5}

  ;; ========== 实际应用 ==========

  ;; 8. 保存玩家数据
  (defn save-player-data [player custom-data]
    (let [nbt (map->nbt custom-data)]
      ;; 存储到玩家实体
      (-> (.getPersistentData player)
          (.put "my_mod_data" nbt))))

  ;; 9. 读取玩家数据
  (defn load-player-data [player]
    (let [persistent-data (.getPersistentData player)]
      (when (.contains persistent-data "my_mod_data")
        (nbt->map (.getCompound persistent-data "my_mod_data")))))

  ;; 10. 物品 NBT 操作
  (defn set-item-lore [item-stack lore-text]
    (let [nbt (if (.hasTag item-stack)
                (.getTag item-stack)
                (CompoundTag.))
          display (if (.contains nbt "display")
                    (.getCompound nbt "display")
                    (CompoundTag.))]
      (.putString display "Lore" lore-text)
      (.put nbt "display" display)
      (.setTag item-stack nbt)
      item-stack)))
  )
