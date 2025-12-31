(ns com.fabriclj.swiss-knife.client.debug-visualizer
  "增强调试可视化系统

   扩展 debug-render.clj，提供额外的可视化功能：
   - 网络流量监控和可视化
   - 区块加载状态可视化
   - 实体数量热图
   - 性能热点可视化
   - TPS 图表显示"
  (:require [com.fabriclj.swiss-knife.common.core :as core]
            [com.fabriclj.swiss-knife.client.debug-render :as debug]
            [com.fabriclj.swiss-knife.common.profiler :as prof])
  (:import [net.minecraft.client Minecraft]
           [net.minecraft.world.level.chunk LevelChunk]
           [net.minecraft.core BlockPos]
           [net.minecraft.world.phys Vec3]
           [com.mojang.blaze3d.vertex PoseStack]
           [java.awt Color]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 网络流量监控
;; ============================================================================

(def ^:private network-stats (atom {:sent 0
                                     :received 0
                                     :packets-sent 0
                                     :packets-received 0
                                     :history []}))

(def ^:private max-network-history 100)

(defn record-network-packet!
  "记录网络数据包

   参数:
   - direction: :sent 或 :received
   - size: 数据包大小（字节）

   内部使用，通常在网络事件中调用"
  [direction size]
  (swap! network-stats
         (fn [stats]
           (let [new-stats (-> stats
                               (update (if (= direction :sent) :sent :received) + size)
                               (update (if (= direction :sent) :packets-sent :packets-received) inc)
                               (update :history conj {:timestamp (System/currentTimeMillis)
                                                      :direction direction
                                                      :size size}))]
             (if (> (count (:history new-stats)) max-network-history)
               (update new-stats :history #(vec (drop (- (count %) max-network-history) %)))
               new-stats)))))

(defn get-network-stats
  "获取网络统计数据

   返回：{:sent 总发送字节
          :received 总接收字节
          :packets-sent 发送数据包数
          :packets-received 接收数据包数
          :history 历史记录
          :rate-sent 发送速率（字节/秒）
          :rate-received 接收速率（字节/秒）}

   示例:
   ```clojure
   (let [stats (get-network-stats)]
     (println \"Sent:\" (:sent stats) \"bytes\")
     (println \"Received:\" (:received stats) \"bytes\")
     (println \"Send rate:\" (:rate-sent stats) \"B/s\"))
   ```"
  []
  (let [stats @network-stats
        now (System/currentTimeMillis)
        recent-history (filter #(> (- now (:timestamp %)) 1000) (:history stats))
        recent-sent (reduce + 0 (map :size (filter #(= :sent (:direction %)) recent-history)))
        recent-received (reduce + 0 (map :size (filter #(= :received (:direction %)) recent-history)))]
    (assoc stats
           :rate-sent recent-sent
           :rate-received recent-received)))

(defn reset-network-stats!
  "重置网络统计数据"
  []
  (reset! network-stats {:sent 0
                         :received 0
                         :packets-sent 0
                         :packets-received 0
                         :history []}))

(defn render-network-overlay!
  "渲染网络流量叠加层

   在屏幕上显示网络统计信息

   参数:
   - graphics: GuiGraphics
   - x, y: 屏幕位置

   示例:
   ```clojure
   (mb/events/on-render-hud
     (fn [graphics partial-tick]
       (render-network-overlay! graphics 10 10)))
   ```"
  [graphics x y]
  (let [stats (get-network-stats)
        font (.font (Minecraft/getInstance))]
    ;; 渲染背景
    (.fill graphics x y (+ x 200) (+ y 80) 0x80000000)

    ;; 渲染文本
    (.drawString graphics font \"Network Stats\" (+ x 5) (+ y 5) 0xFFFFFF)
    (.drawString graphics font
                 (format \"Sent: %.2f KB\" (/ (:sent stats) 1024.0))
                 (+ x 5) (+ y 20) 0x00FF00)
    (.drawString graphics font
                 (format \"Received: %.2f KB\" (/ (:received stats) 1024.0))
                 (+ x 5) (+ y 35) 0x00FFFF)
    (.drawString graphics font
                 (format \"Rate: ↑%.1f KB/s ↓%.1f KB/s\"
                         (/ (:rate-sent stats) 1024.0)
                         (/ (:rate-received stats) 1024.0))
                 (+ x 5) (+ y 50) 0xFFFF00)
    (.drawString graphics font
                 (format \"Packets: ↑%d ↓%d\"
                         (:packets-sent stats)
                         (:packets-received stats))
                 (+ x 5) (+ y 65) 0xAAAAAA)))

;; ============================================================================
;; 区块加载可视化
;; ============================================================================

(def ^:private chunk-load-events (atom []))
(def ^:private max-chunk-events 50)

(defn record-chunk-load!
  "记录区块加载事件

   参数:
   - chunk-pos: 区块位置 [x z]
   - event-type: :load 或 :unload

   内部使用"
  [chunk-pos event-type]
  (swap! chunk-load-events
         (fn [events]
           (let [new-events (conj events {:pos chunk-pos
                                          :type event-type
                                          :timestamp (System/currentTimeMillis)})]
             (if (> (count new-events) max-chunk-events)
               (vec (drop (- (count new-events) max-chunk-events) new-events))
               new-events)))))

(defn show-chunk-borders!
  "显示区块边界

   参数:
   - level: Level
   - player-pos: 玩家位置
   - radius: 显示半径（区块数）
   - opts: 选项
     - :color - 边界颜色（默认 :white）
     - :height - 显示高度（默认玩家高度）
     - :duration - 持续时间（默认 20 tick）

   示例:
   ```clojure
   (show-chunk-borders! level player-pos 3
     :color :green
     :duration 60)
   ```"
  [level player-pos radius & {:keys [color height duration]
                              :or {color :white
                                   duration 20}}]
  (let [player-chunk-x (quot (int (.x player-pos)) 16)
        player-chunk-z (quot (int (.z player-pos)) 16)
        y (or height (int (.y player-pos)))]
    (doseq [cx (range (- player-chunk-x radius) (+ player-chunk-x radius 1))
            cz (range (- player-chunk-z radius) (+ player-chunk-z radius 1))]
      (let [x1 (* cx 16)
            z1 (* cz 16)
            x2 (+ x1 16)
            z2 (+ z1 16)]
        ;; 绘制区块边界的四条边
        (debug/show-line! (Vec3. x1 y z1) (Vec3. x2 y z1) :color color :duration duration)
        (debug/show-line! (Vec3. x2 y z1) (Vec3. x2 y z2) :color color :duration duration)
        (debug/show-line! (Vec3. x2 y z2) (Vec3. x1 y z2) :color color :duration duration)
        (debug/show-line! (Vec3. x1 y z2) (Vec3. x1 y z1) :color color :duration duration)))))

(defn show-loaded-chunks!
  "高亮显示已加载的区块

   参数:
   - level: Level
   - opts: 选项
     - :loaded-color - 已加载区块颜色（默认 :green）
     - :unloaded-color - 未加载区块颜色（默认 :red）
     - :duration - 持续时间

   示例:
   ```clojure
   (show-loaded-chunks! level
     :loaded-color :green
     :unloaded-color :gray
     :duration 100)
   ```"
  [level & {:keys [loaded-color unloaded-color duration]
            :or {loaded-color :green
                 unloaded-color :red
                 duration 20}}]
  (let [minecraft (Minecraft/getInstance)
        player (.player minecraft)
        player-pos (.position player)
        chunk-source (.getChunkSource level)
        view-distance 8]  ; 可配置
    (doseq [cx (range (- (quot (int (.x player-pos)) 16) view-distance)
                      (+ (quot (int (.x player-pos)) 16) view-distance))
            cz (range (- (quot (int (.z player-pos)) 16) view-distance)
                      (+ (quot (int (.z player-pos)) 16) view-distance))]
      (let [chunk (.getChunk chunk-source cx cz false)
            color (if chunk loaded-color unloaded-color)
            y (int (.y player-pos))]
        (when chunk
          (debug/show-area! (keyword (str \"chunk_\" cx \"_\" cz))
                           (Vec3. (* cx 16) (- y 10) (* cz 16))
                           (Vec3. (+ (* cx 16) 16) (+ y 10) (+ (* cz 16) 16))
                           :color color
                           :duration duration))))))

;; ============================================================================
;; 实体密度热图
;; ============================================================================

(defn get-entity-density
  "获取指定区域的实体密度

   参数:
   - level: Level
   - center: 中心位置
   - radius: 半径

   返回：实体密度映射 {chunk-pos entity-count}"
  [level center radius]
  (let [entities (.getAllEntities level)
        chunk-counts (atom {})]
    (doseq [entity entities]
      (let [pos (.position entity)
            dist (.distanceTo pos center)]
        (when (<= dist radius)
          (let [chunk-x (quot (int (.x pos)) 16)
                chunk-z (quot (int (.z pos)) 16)
                chunk-key [chunk-x chunk-z]]
            (swap! chunk-counts update chunk-key (fnil inc 0))))))
    @chunk-counts))

(defn show-entity-density-heatmap!
  "显示实体密度热图

   参数:
   - level: Level
   - center: 中心位置
   - radius: 半径（区块数）
   - opts: 选项
     - :duration - 持续时间
     - :min-color - 低密度颜色
     - :max-color - 高密度颜色

   示例:
   ```clojure
   (show-entity-density-heatmap! level player-pos 5
     :duration 100)
   ```"
  [level center radius & {:keys [duration min-color max-color]
                          :or {duration 20
                               min-color :green
                               max-color :red}}]
  (let [density-map (get-entity-density level center (* radius 16))
        max-density (if (seq density-map)
                     (apply max (vals density-map))
                     1)
        y (int (.y center))]
    (doseq [[[cx cz] count] density-map]
      (let [intensity (/ count max-density)
            ;; 从绿到红的渐变
            color (keyword (format \"#%02X%02X00\"
                                  (int (* 255 intensity))
                                  (int (* 255 (- 1 intensity)))))]
        (debug/show-area! (keyword (str \"density_\" cx \"_\" cz))
                         (Vec3. (* cx 16) (- y 5) (* cz 16))
                         (Vec3. (+ (* cx 16) 16) (+ y 5) (+ (* cz 16) 16))
                         :color color
                         :duration duration)))))

;; ============================================================================
;; TPS 图表显示
;; ============================================================================

(defn render-tps-graph!
  "渲染 TPS 历史图表

   参数:
   - graphics: GuiGraphics
   - x, y: 屏幕位置
   - width, height: 图表大小

   示例:
   ```clojure
   (mb/events/on-render-hud
     (fn [graphics partial-tick]
       (render-tps-graph! graphics 10 100 200 100)))
   ```"
  [graphics x y width height]
  (let [tps-stats (prof/get-tps-stats)]
    (when-let [history (:history tps-stats)]
      ;; 渲染背景
      (.fill graphics x y (+ x width) (+ y height) 0x80000000)

      ;; 渲染标题
      (let [font (.font (Minecraft/getInstance))]
        (.drawString graphics font \"TPS History\" (+ x 5) (+ y 5) 0xFFFFFF))

      ;; 渲染图表
      (let [tps-values (map :tps history)
            max-tps 20.0
            points (count tps-values)
            step-x (if (> points 1) (/ (- width 10) (dec points)) 0)]
        (doseq [[idx tps] (map-indexed vector tps-values)]
          (let [px (+ x 5 (* idx step-x))
                py (- (+ y height) (* (/ tps max-tps) (- height 20)))
                next-tps (when (< idx (dec points))
                          (nth tps-values (inc idx)))
                next-px (+ x 5 (* (inc idx) step-x))
                next-py (when next-tps
                         (- (+ y height) (* (/ next-tps max-tps) (- height 20))))
                color (cond
                       (>= tps 19.0) 0x00FF00  ; 绿色
                       (>= tps 15.0) 0xFFFF00  ; 黄色
                       :else 0xFF0000)]         ; 红色
            ;; 绘制点
            (.fill graphics (int px) (int py) (+ (int px) 2) (+ (int py) 2) color)
            ;; 绘制线
            (when next-py
              (.fill graphics (int px) (int py) (int next-px) (int next-py) color))))))))

;; ============================================================================
;; 性能热点可视化
;; ============================================================================

(defn show-performance-hotspots!
  "在3D世界中显示性能热点

   标记导致性能问题的位置

   参数:
   - level: Level
   - hotspots: 热点列表 [{:pos Vec3 :lag-ms float :label string}]
   - opts: 选项
     - :duration - 持续时间

   示例:
   ```clojure
   (show-performance-hotspots! level
     [{:pos (Vec3. 100 64 200)
       :lag-ms 5.2
       :label \"Heavy redstone\"}
      {:pos (Vec3. -50 70 150)
       :lag-ms 3.8
       :label \"Many entities\"}]
     :duration 200)
   ```"
  [level hotspots & {:keys [duration]
                     :or {duration 100}}]
  (doseq [{:keys [pos lag-ms label]} hotspots]
    (let [color (cond
                 (> lag-ms 10.0) :red
                 (> lag-ms 5.0) :yellow
                 :else :green)
          size (+ 1.0 (* 0.1 lag-ms))]
      ;; 显示标记区域
      (debug/show-area! (keyword (str \"hotspot_\" (.x pos) \"_\" (.z pos)))
                       (.subtract pos (Vec3. size size size))
                       (.add pos (Vec3. size size size))
                       :color color
                       :duration duration)

      ;; TODO: 添加文本标签（需要文本渲染支持）
      )))

;; ============================================================================
;; 综合调试面板
;; ============================================================================

(def ^:private debug-panel-enabled (atom false))

(defn toggle-debug-panel!
  "切换调试面板显示

   示例:
   ```clojure
   (mb/keybindings/create-keybinding :toggle-debug
     \"Toggle Debug Panel\"
     \"key.keyboard.f3.d\"
     :on-press toggle-debug-panel!)
   ```"
  []
  (swap! debug-panel-enabled not)
  (core/log-info (str \"Debug panel: \" (if @debug-panel-enabled \"ON\" \"OFF\"))))

(defn render-debug-panel!
  "渲染完整的调试面板

   包含网络、TPS、内存等信息

   参数:
   - graphics: GuiGraphics
   - partial-tick: 部分tick

   示例:
   ```clojure
   (mb/events/on-render-hud
     (fn [graphics partial-tick]
       (when @debug-panel-enabled
         (render-debug-panel! graphics partial-tick))))
   ```"
  [graphics partial-tick]
  (when @debug-panel-enabled
    ;; 渲染网络统计
    (render-network-overlay! graphics 10 10)

    ;; 渲染 TPS 图表
    (render-tps-graph! graphics 10 100 200 100)

    ;; 渲染内存使用
    (let [memory (prof/get-memory-usage)
          font (.font (Minecraft/getInstance))
          x 10
          y 210]
      (.fill graphics x y (+ x 200) (+ y 50) 0x80000000)
      (.drawString graphics font \"Memory\" (+ x 5) (+ y 5) 0xFFFFFF)
      (.drawString graphics font
                   (format \"Heap: %.0f/%.0f MB (%.1f%%)\"
                           (:heap-used-mb memory)
                           (:heap-max-mb memory)
                           (:heap-usage-percent memory))
                   (+ x 5) (+ y 20) 0x00FFFF)
      (.drawString graphics font
                   (format \"Non-Heap: %.0f MB\" (:non-heap-used-mb memory))
                   (+ x 5) (+ y 35) 0xAAAAAA))))

(comment
  ;; 使用示例

  ;; ========== 网络流量监控 ==========

  ;; 1. 记录网络数据包（在网络事件中）
  (mb/events/on-packet-sent
    (fn [packet]
      (record-network-packet! :sent (.size packet))))

  (mb/events/on-packet-received
    (fn [packet]
      (record-network-packet! :received (.size packet))))

  ;; 2. 显示网络统计
  (mb/events/on-render-hud
    (fn [graphics partial-tick]
      (render-network-overlay! graphics 10 10)))

  ;; 3. 获取网络统计数据
  (let [stats (get-network-stats)]
    (println \"Network rate:\" (:rate-sent stats) \"B/s\"))

  ;; ========== 区块可视化 ==========

  ;; 4. 显示区块边界
  (mb/events/on-client-tick
    (fn []
      (let [mc (Minecraft/getInstance)
            player (.player mc)
            level (.level player)]
        (show-chunk-borders! level (.position player) 3
          :color :cyan
          :duration 2))))

  ;; 5. 高亮已加载区块
  (show-loaded-chunks! level
    :loaded-color :green
    :unloaded-color :gray
    :duration 100)

  ;; ========== 实体密度热图 ==========

  ;; 6. 显示实体密度
  (show-entity-density-heatmap! level player-pos 5
    :duration 200)

  ;; ========== 性能热点 ==========

  ;; 7. 标记性能热点
  (let [hotspots (find-performance-hotspots level)]
    (show-performance-hotspots! level hotspots
      :duration 200))

  ;; ========== 综合调试面板 ==========

  ;; 8. 启用调试面板
  (toggle-debug-panel!)

  ;; 9. 渲染调试面板
  (mb/events/on-render-hud
    (fn [graphics partial-tick]
      (render-debug-panel! graphics partial-tick)))

  ;; 10. 创建按键绑定
  (mb/keybindings/create-keybinding :toggle-debug
    \"Toggle Debug Panel\"
    \"key.keyboard.f3.d\"
    :on-press toggle-debug-panel!))
