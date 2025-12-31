(ns com.fabriclj.swiss-knife.common.gameplay.sounds
  "瑞士军刀 - 音效系统模块

   提供音效播放、注册和控制功能。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.registry :as reg])
  (:import [net.minecraft.sounds SoundEvent SoundSource]
           [net.minecraft.world.entity Entity]
           [net.minecraft.world.entity.player Player]
           [net.minecraft.world.level Level]
           [net.minecraft.resources ResourceLocation]
           [net.minecraft.core BlockPos]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 音效源类型
;; ============================================================================

(def sound-sources
  "音效源类型映射"
  {:master SoundSource/MASTER
   :music SoundSource/MUSIC
   :record SoundSource/RECORDS
   :weather SoundSource/WEATHER
   :block SoundSource/BLOCKS
   :hostile SoundSource/HOSTILE
   :neutral SoundSource/NEUTRAL
   :player SoundSource/PLAYERS
   :ambient SoundSource/AMBIENT
   :voice SoundSource/VOICE})

(defn get-sound-source
  "获取音效源类型

   参数:
   - source: 关键字或 SoundSource

   返回：SoundSource"
  ^SoundSource [source]
  (if (keyword? source)
    (get sound-sources source SoundSource/MASTER)
    source))

;; ============================================================================
;; 音效注册
;; ============================================================================

(defn create-sound-event
  "创建音效事件

   参数:
   - id: ResourceLocation 或字符串

   返回：SoundEvent

   示例:
   ```clojure
   (create-sound-event \"mymod:magic_spell\")
   ```"
  ^SoundEvent [id]
  (let [^ResourceLocation loc (core/->resource-location id)]
    (SoundEvent/createVariableRangeEvent loc)))

(defn register-sound!
  "注册音效

   参数:
   - registry: 音效注册表
   - id: 音效 ID
   - opts: 可选参数
     - :fixed-range - 固定范围（距离）

   返回：RegistrySupplier

   示例:
   ```clojure
   (def sounds (reg/create-registry \"mymod\" :sound))

   (def magic-sound
     (register-sound! sounds \"magic_spell\"))

   (reg/register-all! sounds)
   ```"
  ([registry id]
   (register-sound! registry id {}))
  ([registry id opts]
   (let [{:keys [fixed-range]} opts]
     (reg/register registry id
                   (fn []
                     (if fixed-range
                       (SoundEvent/createFixedRangeEvent
                        (core/->resource-location
                         (str (core/resource-location registry id)))
                        fixed-range)
                       (create-sound-event
                        (str (core/resource-location registry id)))))))))

;; ============================================================================
;; 音效播放
;; ============================================================================

(defn play-sound!
  "播放音效

   参数:
   - level: Level
   - pos: 位置（Vec3、BlockPos 或向量 [x y z]）
   - sound: SoundEvent 或关键字（如 :minecraft:entity.player.hurt）
   - opts: 可选参数
     - :source - 音效源（默认 :master）
     - :volume - 音量（默认 1.0）
     - :pitch - 音调（默认 1.0）
     - :seed - 随机种子

   示例:
   ```clojure
   (play-sound! level [100 64 200] :minecraft:entity.experience_orb.pickup
     {:source :player
      :volume 1.0
      :pitch 1.2})
   ```"
  [^Level level pos sound & [opts]]
  (let [{:keys [source volume pitch seed]
         :or {source :master
              volume 1.0
              pitch 1.0
              seed 0}} opts
        [x y z] (cond
                  (vector? pos) pos
                  (map? pos) [(:x pos) (:y pos) (:z pos)]
                  (instance? BlockPos pos) [(.getX ^BlockPos pos)
                                            (.getY ^BlockPos pos)
                                            (.getZ ^BlockPos pos)]
                  :else pos)
        ^SoundEvent sound-event (if (keyword? sound)
                                  (net.minecraft.core.registries.BuiltInRegistries/SOUND_EVENT
                                   (.get (core/->resource-location sound)))
                                  sound)
        ^SoundSource source-type (get-sound-source source)]
    (.playSound level nil x y z sound-event source-type volume pitch seed)))

(defn play-sound-to-player!
  "向玩家播放音效（仅该玩家听到）

   参数:
   - player: Player
   - pos: 位置
   - sound: SoundEvent 或关键字
   - opts: 可选参数（同 play-sound!）

   示例:
   ```clojure
   (play-sound-to-player! player [100 64 200] :minecraft:block.note_block.pling
     {:volume 1.0 :pitch 2.0})
   ```"
  [^Player player pos sound & [opts]]
  (let [{:keys [source volume pitch]
         :or {source :master
              volume 1.0
              pitch 1.0}} opts
        [x y z] (cond
                  (vector? pos) pos
                  (map? pos) [(:x pos) (:y pos) (:z pos)]
                  (instance? BlockPos pos) [(.getX ^BlockPos pos)
                                            (.getY ^BlockPos pos)
                                            (.getZ ^BlockPos pos)])
        ^SoundEvent sound-event (if (keyword? sound)
                                  (net.minecraft.core.registries.BuiltInRegistries/SOUND_EVENT
                                   (.get (core/->resource-location sound)))
                                  sound)
        ^SoundSource source-type (get-sound-source source)]
    (.playSound player nil x y z sound-event source-type volume pitch)))

(defn play-sound-at-entity!
  "在实体位置播放音效

   参数:
   - entity: Entity
   - sound: SoundEvent 或关键字
   - opts: 可选参数

   示例:
   ```clojure
   (play-sound-at-entity! player :minecraft:entity.player.levelup
     {:volume 1.0 :pitch 1.0})
   ```"
  [^Entity entity sound & [opts]]
  (let [pos (.position entity)]
    (play-sound! (.level entity) [(.x pos) (.y pos) (.z pos)] sound opts)))

;; ============================================================================
;; 音效序列
;; ============================================================================

(defn play-sound-sequence!
  "播放音效序列（延迟播放）

   参数:
   - level: Level
   - sounds: 音效序列 [{:sound ... :delay ... :pos ... :opts ...}]

   示例:
   ```clojure
   (play-sound-sequence! level
     [{:sound :minecraft:block.note_block.pling
       :delay 0
       :pos [100 64 200]
       :opts {:pitch 1.0}}
      {:sound :minecraft:block.note_block.pling
       :delay 5
       :pos [100 64 200]
       :opts {:pitch 1.5}}
      {:sound :minecraft:block.note_block.pling
       :delay 10
       :pos [100 64 200]
       :opts {:pitch 2.0}}])
   ```"
  [level sounds]
  (doseq [{:keys [sound delay pos opts]} sounds]
    (future
      (Thread/sleep (* delay 50))  ; 1 tick = 50ms
      (play-sound! level pos sound opts))))

;; ============================================================================
;; 预设音效
;; ============================================================================

(def common-sounds
  "常用音效映射"
  {:click :minecraft:ui.button.click
   :level-up :minecraft:entity.player.levelup
   :hurt :minecraft:entity.player.hurt
   :death :minecraft:entity.player.death
   :eat :minecraft:entity.generic.eat
   :drink :minecraft:entity.generic.drink
   :equip :minecraft:item.armor.equip_generic
   :break :minecraft:entity.item.break
   :pickup :minecraft:entity.item.pickup
   :anvil :minecraft:block.anvil.use
   :enchant :minecraft:block.enchantment_table.use
   :portal :minecraft:block.portal.travel
   :explosion :minecraft:entity.generic.explode
   :firework :minecraft:entity.firework_rocket.launch
   :bell :minecraft:block.bell.use
   :pling :minecraft:block.note_block.pling
   :success :minecraft:entity.experience_orb.pickup
   :fail :minecraft:block.glass.break})

(defn quick-sound!
  "快速播放常用音效

   参数:
   - level: Level
   - pos: 位置
   - sound-key: 音效关键字（来自 common-sounds）
   - opts: 可选参数

   示例:
   ```clojure
   (quick-sound! level [100 64 200] :success)
   (quick-sound! level [100 64 200] :level-up {:volume 2.0})
   ```"
  [level pos sound-key & [opts]]
  (when-let [sound (get common-sounds sound-key)]
    (play-sound! level pos sound opts)))

;; ============================================================================
;; 音效构建器
;; ============================================================================

(defn sound-builder
  "创建音效构建器

   返回一个可以链式调用的构建器

   示例:
   ```clojure
   (-> (sound-builder)
       (with-sound :minecraft:entity.player.levelup)
       (with-volume 1.5)
       (with-pitch 1.2)
       (with-source :player)
       (play-at! level [100 64 200]))
   ```"
  []
  (atom {:sound nil
         :volume 1.0
         :pitch 1.0
         :source :master}))

(defn with-sound
  "设置音效"
  [builder sound]
  (swap! builder assoc :sound sound)
  builder)

(defn with-volume
  "设置音量"
  [builder volume]
  (swap! builder assoc :volume volume)
  builder)

(defn with-pitch
  "设置音调"
  [builder pitch]
  (swap! builder assoc :pitch pitch)
  builder)

(defn with-source
  "设置音效源"
  [builder source]
  (swap! builder assoc :source source)
  builder)

(defn play-at!
  "播放构建的音效"
  [builder level pos]
  (let [{:keys [sound volume pitch source]} @builder]
    (play-sound! level pos sound
                 {:volume volume
                  :pitch pitch
                  :source source})))

;; ============================================================================
;; 便捷宏
;; ============================================================================

(defmacro defsound
  "定义音效（语法糖）

   示例:
   ```clojure
   (defsound sounds magic-spell \"magic_spell\"
     :fixed-range 16.0)
   ```"
  [registry name id & opts]
  `(def ~name
     (register-sound! ~registry ~id ~(apply hash-map opts))))

(comment
  ;; 使用示例

  ;; 1. 注册音效
  (def sounds (reg/create-registry "mymod" :sound))

  (def magic-sound
    (register-sound! sounds "magic_spell"))

  (reg/register-all! sounds)

  ;; 2. 播放音效
  (play-sound! level [100 64 200] magic-sound
               {:volume 1.0 :pitch 1.2})

  ;; 3. 快速播放常用音效
  (quick-sound! level [100 64 200] :success)
  (quick-sound! level [100 64 200] :level-up {:volume 2.0})

  ;; 4. 向玩家播放
  (play-sound-to-player! player [100 64 200] :pling
                         {:volume 1.0 :pitch 2.0})

  ;; 5. 音效序列
  (play-sound-sequence! level
                        [{:sound :pling :delay 0 :pos [100 64 200] :opts {:pitch 1.0}}
                         {:sound :pling :delay 5 :pos [100 64 200] :opts {:pitch 1.5}}
                         {:sound :pling :delay 10 :pos [100 64 200] :opts {:pitch 2.0}}])

  ;; 6. 构建器模式
  (-> (sound-builder)
      (with-sound :level-up)
      (with-volume 1.5)
      (with-pitch 1.2)
      (play-at! level [100 64 200])))
