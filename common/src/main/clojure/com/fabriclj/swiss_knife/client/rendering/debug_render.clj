(ns com.fabriclj.swiss-knife.client.rendering.debug-render
  "客户端调试渲染系统

   **功能概述**
   - AI 寻路路径可视化
   - 导航目标显示
   - AI 状态信息
   - 碰撞箱显示
   - 区域边界绘制

   **架构设计**
   本模块采用一体化设计，包含数据采集和渲染逻辑。

   **函数分类**
   - 数据层: `show-*` 函数( 如 `show-ai-path!`) 注册要显示的数据
   - 渲染层: `render-*` 函数( 如 `render-ai-path`) 执行实际渲染
   - 管理层: 状态管理、清除、启用/禁用

   **使用模式**
   ```clojure
   ;; 1. 注册要可视化的数据
   (debug-render/show-ai-path! entity :color :green)

   ;; 2. 在渲染事件中调用渲染函数
   (events/on-render-level
     (fn [pose-stack buffers partial-tick]
       (debug-render/render-all-debug-data pose-stack buffers)))
   ```

   **未来改进方向**( 可选)
   - 数据层独立为 common 模块，支持服务端调试
   - 渲染层保持在 client 模块
   - 添加数据快照和回放功能

   ⚠️ 注意: 此模块仅在客户端可用"
  (:require [com.fabriclj.swiss-knife.client.rendering.core :as render]
            [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (com.mojang.blaze3d.vertex PoseStack VertexConsumer)
           (net.minecraft.client Minecraft)
           (net.minecraft.client.renderer MultiBufferSource RenderType)
           (net.minecraft.world.entity Mob Entity)
           (net.minecraft.world.entity.ai.navigation PathNavigation)
           (net.minecraft.world.level.pathfinder Path Node)
           (net.minecraft.world.phys Vec3 AABB)
           (net.minecraft.core BlockPos)
           (net.minecraft.world.phys.shapes VoxelShape)
           (org.joml Matrix4f)
           (java.awt Color)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 调试渲染状态管理( 数据层)
;; ============================================================================
;;
;; 这部分负责存储需要可视化的数据。
;; 数据由 show-* 函数注册，由 render-debug-overlays! 统一渲染。

(defonce ^:private debug-render-state
  (atom {:path-renders {}
         :navigation-renders {}
         :ai-debug-renders {}
         :bbox-renders {}
         :area-renders {}
         :line-renders {}}))

(defn clear-all-debug-renders!
  "清除所有调试渲染"
  []
  (reset! debug-render-state
          {:path-renders {}
           :navigation-renders {}
           :ai-debug-renders {}
           :bbox-renders {}
           :area-renders {}
           :line-renders {}}))

;; ============================================================================
;; 颜色工具( 辅助函数)
;; ============================================================================

(defn ->color
  "转换为 Color 对象"
  [c]
  (cond
    (instance? Color c) c
    (keyword? c) (case c
                   :red (Color. 255 0 0 200)
                   :green (Color. 0 255 0 200)
                   :blue (Color. 0 0 255 200)
                   :yellow (Color. 255 255 0 200)
                   :cyan (Color. 0 255 255 200)
                   :magenta (Color. 255 0 255 200)
                   :white (Color. 255 255 255 200)
                   :orange (Color. 255 165 0 200)
                   :purple (Color. 128 0 128 200)
                   (Color. 255 255 255 200))
    (vector? c) (Color. (int (nth c 0))
                        (int (nth c 1))
                        (int (nth c 2))
                        (int (or (nth c 3 nil) 200)))
    :else (Color. 255 255 255 200)))

(defn color-rgba
  "获取 RGBA 值"
  [color]
  (let [^Color c (->color color)]
    {:r (/ (.getRed c) 255.0)
     :g (/ (.getGreen c) 255.0)
     :b (/ (.getBlue c) 255.0)
     :a (/ (.getAlpha c) 255.0)}))

;; ============================================================================
;; 基础渲染工具( 渲染层 - 底层)
;; ============================================================================
;;
;; 提供基础的几何图形渲染函数。
;; 这些函数被更高层的渲染函数调用。

(defn render-line
  "渲染一条线

   参数:
   - pose-stack: PoseStack
   - buffer-source: MultiBufferSource
   - from: 起点 Vec3 或 [x y z]
   - to: 终点 Vec3 或 [x y z]
   - color: 颜色
   - width: 线宽( 默认 2.0) "
  [^PoseStack pose-stack ^MultiBufferSource buffer-source from to color & {:keys [width]
                                                                           :or {width 2.0}}]
  (let [^Vec3 from-vec (if (vector? from)
                         (Vec3. (double (nth from 0))
                                (double (nth from 1))
                                (double (nth from 2)))
                         from)
        ^Vec3 to-vec (if (vector? to)
                       (Vec3. (double (nth to 0))
                              (double (nth to 1))
                              (double (nth to 2)))
                       to)
        {:keys [r g b a]} (color-rgba color)
        buffer (.getBuffer buffer-source (RenderType/lines))
        matrix (.last pose-stack)
        pos-matrix (.pose matrix)
        normal-matrix (.normal matrix)]

    ;; 渲染线段
    (.vertex buffer pos-matrix
             (float (.x from-vec))
             (float (.y from-vec))
             (float (.z from-vec)))
    (.color buffer (float r) (float g) (float b) (float a))
    (.normal buffer normal-matrix 0.0 1.0 0.0)
    (.endVertex buffer)

    (.vertex buffer pos-matrix
             (float (.x to-vec))
             (float (.y to-vec))
             (float (.z to-vec)))
    (.color buffer (float r) (float g) (float b) (float a))
    (.normal buffer normal-matrix 0.0 1.0 0.0)
    (.endVertex buffer)))

(defn render-point
  "渲染一个点( 小方块)

   参数:
   - pose-stack: PoseStack
   - buffer-source: MultiBufferSource
   - pos: 位置 Vec3 或 [x y z]
   - color: 颜色
   - size: 大小( 默认 0.2) "
  [^PoseStack pose-stack ^MultiBufferSource buffer-source pos color & {:keys [size]
                                                                       :or {size 0.2}}]
  (let [^Vec3 pos-vec (if (vector? pos)
                        (Vec3. (double (nth pos 0))
                               (double (nth pos 1))
                               (double (nth pos 2)))
                        pos)
        half-size (/ size 2.0)
        x (.x pos-vec)
        y (.y pos-vec)
        z (.z pos-vec)]
    ;; 渲染一个小立方体( 6条边)
    (render-line pose-stack buffer-source
                 [(- x half-size) (- y half-size) (- z half-size)]
                 [(+ x half-size) (- y half-size) (- z half-size)]
                 color)
    (render-line pose-stack buffer-source
                 [(- x half-size) (+ y half-size) (- z half-size)]
                 [(+ x half-size) (+ y half-size) (- z half-size)]
                 color)
    (render-line pose-stack buffer-source
                 [(- x half-size) (- y half-size) (+ z half-size)]
                 [(+ x half-size) (- y half-size) (+ z half-size)]
                 color)
    (render-line pose-stack buffer-source
                 [(- x half-size) (+ y half-size) (+ z half-size)]
                 [(+ x half-size) (+ y half-size) (+ z half-size)]
                 color)))

;; ============================================================================
;; AI 寻路路径可视化
;; ============================================================================

(defn show-path!
  "显示 AI 寻路路径

   参数:
   - entity: Mob 实体
   - opts: 可选参数
     - :colors - 颜色配置 {:start :line :end :waypoint}
     - :duration - 显示持续时间( tick，默认 100)
     - :show-waypoints? - 是否显示路径点( 默认 true)

   示例:
   ```clojure
   (show-path! zombie
     :colors {:start :green
              :line :yellow
              :end :red
              :waypoint :cyan}
     :duration 200)
   ```"
  [^Mob entity & {:keys [colors duration show-waypoints?]
                  :or {colors {:start :green
                               :line :yellow
                               :end :red
                               :waypoint :cyan}
                       duration 100
                       show-waypoints? true}}]
  (when-let [^PathNavigation nav (.getNavigation entity)]
    (when-let [^Path path (.getPath nav)]
      (let [entity-id (.getId entity)
            nodes (vec (for [i (range (.getNodeCount path))]
                         (.getNode path i)))
            render-data {:entity-id entity-id
                         :nodes nodes
                         :colors colors
                         :show-waypoints? show-waypoints?
                         :expire-tick (+ (System/currentTimeMillis) (* duration 50))}]
        (swap! debug-render-state assoc-in [:path-renders entity-id] render-data)))))

(defn- render-path
  "内部: 渲染路径"
  [^PoseStack pose-stack ^MultiBufferSource buffer-source render-data]
  (let [{:keys [nodes colors show-waypoints?]} render-data
        start-color (:start colors)
        line-color (:line colors)
        end-color (:end colors)
        waypoint-color (:waypoint colors)]

    (when (seq nodes)
      ;; 渲染起点
      (let [^Node first-node (first nodes)]
        (render-point pose-stack buffer-source
                      [(.x first-node) (+ (.y first-node) 0.5) (.z first-node)]
                      start-color
                      :size 0.3))

      ;; 渲染路径线段
      (doseq [[^Node from-node ^Node to-node] (partition 2 1 nodes)]
        (render-line pose-stack buffer-source
                     [(.x from-node) (+ (.y from-node) 0.5) (.z from-node)]
                     [(.x to-node) (+ (.y to-node) 0.5) (.z to-node)]
                     line-color
                     :width 2.0)

        ;; 渲染路径点
        (when show-waypoints?
          (render-point pose-stack buffer-source
                        [(.x to-node) (+ (.y to-node) 0.5) (.z to-node)]
                        waypoint-color
                        :size 0.15)))

      ;; 渲染终点
      (let [^Node last-node (last nodes)]
        (render-point pose-stack buffer-source
                      [(.x last-node) (+ (.y last-node) 0.5) (.z last-node)]
                      end-color
                      :size 0.3)))))

;; ============================================================================
;; 导航目标可视化( 数据注册 + 渲染)
;; ============================================================================
;;
;; show-navigation-goal! - 注册导航目标数据
;; 内部渲染逻辑 - 渲染导航目标标记

(defn show-navigation-goal!
  "显示导航目标( 从实体到目标的直线)

   参数:
   - entity: Mob 实体
   - target-pos: 目标位置 BlockPos 或 [x y z]
   - opts: 可选参数
     - :colors - 颜色配置 {:start :line :end}
     - :duration - 显示持续时间( tick，默认 100)

   示例:
   ```clojure
   (show-navigation-goal! zombie [100 64 200]
     :colors {:start :blue :line :white :end :red}
     :duration 200)
   ```"
  [^Mob entity target-pos & {:keys [colors duration]
                             :or {colors {:start :blue
                                          :line :white
                                          :end :red}
                                  duration 100}}]
  (let [entity-id (.getId entity)
        entity-pos (.position entity)
        target-vec (if (vector? target-pos)
                     (Vec3. (double (nth target-pos 0))
                            (double (nth target-pos 1))
                            (double (nth target-pos 2)))
                     (if (instance? BlockPos target-pos)
                       (.getCenter ^BlockPos target-pos)
                       target-pos))
        render-data {:entity-id entity-id
                     :start-pos entity-pos
                     :target-pos target-vec
                     :colors colors
                     :expire-tick (+ (System/currentTimeMillis) (* duration 50))}]
    (swap! debug-render-state assoc-in [:navigation-renders entity-id] render-data)))

(defn- render-navigation-goal
  "内部: 渲染导航目标"
  [^PoseStack pose-stack ^MultiBufferSource buffer-source render-data]
  (let [{:keys [start-pos target-pos colors]} render-data
        start-color (:start colors)
        line-color (:line colors)
        end-color (:end colors)]

    ;; 渲染起点
    (render-point pose-stack buffer-source start-pos start-color :size 0.25)

    ;; 渲染直线
    (render-line pose-stack buffer-source start-pos target-pos line-color :width 2.5)

    ;; 渲染终点
    (render-point pose-stack buffer-source target-pos end-color :size 0.3)))

;; ============================================================================
;; AI 状态可视化
;; ============================================================================

(defn show-ai-debug!
  "显示 AI 状态信息

   参数:
   - entity: Mob 实体
   - opts: 可选参数
     - :duration - 显示持续时间( tick，默认 100)
     - :show-goals? - 显示目标列表( 默认 true)
     - :show-target? - 显示攻击目标( 默认 true)

   示例:
   ```clojure
   (show-ai-debug! zombie
     :duration 200
     :show-goals? true
     :show-target? true)
   ```"
  [^Mob entity & {:keys [duration show-goals? show-target?]
                  :or {duration 100
                       show-goals? true
                       show-target? true}}]
  (let [entity-id (.getId entity)
        goals (when show-goals?
                (try
                  (vec (for [^net.minecraft.world.entity.ai.goal.WrappedGoal wrapped-goal
                             (.getAvailableGoals (.goalSelector entity))]
                         {:priority (.getPriority wrapped-goal)
                          :running (.isRunning wrapped-goal)
                          :class (-> wrapped-goal .getGoal .getClass .getSimpleName)}))
                  (catch Exception _ [])))
        target (when show-target?
                 (.getTarget entity))
        render-data {:entity-id entity-id
                     :entity entity
                     :goals goals
                     :target target
                     :show-goals? show-goals?
                     :show-target? show-target?
                     :expire-tick (+ (System/currentTimeMillis) (* duration 50))}]
    (swap! debug-render-state assoc-in [:ai-debug-renders entity-id] render-data)))

(defn- render-ai-debug
  "内部: 渲染 AI 状态( 文本显示) "
  [^PoseStack pose-stack ^MultiBufferSource buffer-source render-data]
  (let [{:keys [^Mob entity goals target show-goals? show-target?]} render-data
        entity-pos (.position entity)
        ^Minecraft mc (Minecraft/getInstance)
        font (.font mc)]

    ;; 渲染实体名称
    (let [name (.getName entity)
          health (.getHealth entity)
          max-health (.getMaxHealth entity)]
      ;; TODO: 实现文本渲染( 需要额外的渲染支持)
      )

    ;; 渲染目标连线
    (when (and show-target? target)
      (render-line pose-stack buffer-source
                   entity-pos
                   (.position ^Entity target)
                   :red
                   :width 2.0))))

;; ============================================================================
;; 碰撞箱可视化( 数据注册 + 渲染)
;; ============================================================================
;;
;; show-bounding-box! - 注册碰撞箱数据
;; 内部渲染逻辑 - 渲染碰撞箱线条

(defn show-bounding-box!
  "显示实体碰撞箱

   参数:
   - entity: 实体
   - opts: 可选参数
     - :color - 颜色( 默认 :green)
     - :duration - 显示持续时间( tick，默认 100)

   示例:
   ```clojure
   (show-bounding-box! player :color :cyan :duration 200)
   ```"
  [^Entity entity & {:keys [color duration]
                     :or {color :green
                          duration 100}}]
  (let [entity-id (.getId entity)
        bbox (.getBoundingBox entity)
        render-data {:entity-id entity-id
                     :entity entity
                     :color color
                     :expire-tick (+ (System/currentTimeMillis) (* duration 50))}]
    (swap! debug-render-state assoc-in [:bbox-renders entity-id] render-data)))

(defn- render-bounding-box
  "内部: 渲染碰撞箱"
  [^PoseStack pose-stack ^MultiBufferSource buffer-source render-data]
  (let [{:keys [^Entity entity color]} render-data
        ^AABB bbox (.getBoundingBox entity)
        min-x (.minX bbox)
        min-y (.minY bbox)
        min-z (.minZ bbox)
        max-x (.maxX bbox)
        max-y (.maxY bbox)
        max-z (.maxZ bbox)]

    ;; 渲染 12 条边
    ;; 底面 4 条边
    (render-line pose-stack buffer-source [min-x min-y min-z] [max-x min-y min-z] color)
    (render-line pose-stack buffer-source [max-x min-y min-z] [max-x min-y max-z] color)
    (render-line pose-stack buffer-source [max-x min-y max-z] [min-x min-y max-z] color)
    (render-line pose-stack buffer-source [min-x min-y max-z] [min-x min-y min-z] color)

    ;; 顶面 4 条边
    (render-line pose-stack buffer-source [min-x max-y min-z] [max-x max-y min-z] color)
    (render-line pose-stack buffer-source [max-x max-y min-z] [max-x max-y max-z] color)
    (render-line pose-stack buffer-source [max-x max-y max-z] [min-x max-y max-z] color)
    (render-line pose-stack buffer-source [min-x max-y max-z] [min-x max-y min-z] color)

    ;; 垂直 4 条边
    (render-line pose-stack buffer-source [min-x min-y min-z] [min-x max-y min-z] color)
    (render-line pose-stack buffer-source [max-x min-y min-z] [max-x max-y min-z] color)
    (render-line pose-stack buffer-source [max-x min-y max-z] [max-x max-y max-z] color)
    (render-line pose-stack buffer-source [min-x min-y max-z] [min-x max-y max-z] color)))

;; ============================================================================
;; 区域可视化
;; ============================================================================

(defn show-area!
  "显示一个区域( 立方体)

   参数:
   - id: 区域唯一标识
   - from-pos: 起点位置 [x y z]
   - to-pos: 终点位置 [x y z]
   - opts: 可选参数
     - :color - 颜色( 默认 :yellow)
     - :duration - 显示持续时间( tick，默认 100，0 表示永久)

   示例:
   ```clojure
   (show-area! :spawn-zone [0 60 0] [20 80 20]
     :color :green
     :duration 0) ; 永久显示
   ```"
  [id from-pos to-pos & {:keys [color duration]
                         :or {color :yellow
                              duration 100}}]
  (let [render-data {:id id
                     :from-pos from-pos
                     :to-pos to-pos
                     :color color
                     :expire-tick (if (zero? duration)
                                    Long/MAX_VALUE
                                    (+ (System/currentTimeMillis) (* duration 50)))}]
    (swap! debug-render-state assoc-in [:area-renders id] render-data)))

(defn hide-area!
  "隐藏区域"
  [id]
  (swap! debug-render-state update :area-renders dissoc id))

(defn- render-area
  "内部: 渲染区域"
  [^PoseStack pose-stack ^MultiBufferSource buffer-source render-data]
  (let [{:keys [from-pos to-pos color]} render-data
        [min-x min-y min-z] from-pos
        [max-x max-y max-z] to-pos]

    ;; 渲染立方体边框( 与碰撞箱类似)
    (render-line pose-stack buffer-source [min-x min-y min-z] [max-x min-y min-z] color)
    (render-line pose-stack buffer-source [max-x min-y min-z] [max-x min-y max-z] color)
    (render-line pose-stack buffer-source [max-x min-y max-z] [min-x min-y max-z] color)
    (render-line pose-stack buffer-source [min-x min-y max-z] [min-x min-y min-z] color)

    (render-line pose-stack buffer-source [min-x max-y min-z] [max-x max-y min-z] color)
    (render-line pose-stack buffer-source [max-x max-y min-z] [max-x max-y max-z] color)
    (render-line pose-stack buffer-source [max-x max-y max-z] [min-x max-y max-z] color)
    (render-line pose-stack buffer-source [min-x max-y max-z] [min-x max-y min-z] color)

    (render-line pose-stack buffer-source [min-x min-y min-z] [min-x max-y min-z] color)
    (render-line pose-stack buffer-source [max-x min-y min-z] [max-x max-y min-z] color)
    (render-line pose-stack buffer-source [max-x min-y max-z] [max-x max-y max-z] color)
    (render-line pose-stack buffer-source [min-x min-y max-z] [min-x max-y max-z] color)))

;; ============================================================================
;; 线条可视化
;; ============================================================================

(defn show-line!
  "显示一条线

   参数:
   - from: 起点 Vec3 或 [x y z]
   - to: 终点 Vec3 或 [x y z]
   - opts: 可选参数
     - :color - 颜色( 默认 :white)
     - :width - 线宽( 默认 2.0)
     - :duration - 显示持续时间( tick，默认 100，0 表示永久)

   返回: 线条 ID( 用于后续删除)

   示例:
   ```clojure
   (show-line! (Vec3. 0 64 0) (Vec3. 100 64 100)
     :color :green
     :duration 200)
   ```"
  [from to & {:keys [color width duration]
              :or {color :white
                   width 2.0
                   duration 100}}]
  (let [line-id (keyword (str "line_" (System/currentTimeMillis) "_" (hash [from to])))
        render-data {:id line-id
                     :from from
                     :to to
                     :color color
                     :width width
                     :expire-tick (if (zero? duration)
                                   Long/MAX_VALUE
                                   (+ (System/currentTimeMillis) (* duration 50)))}]
    (swap! debug-render-state assoc-in [:line-renders line-id] render-data)
    line-id))

(defn hide-line!
  "隐藏线条

   参数:
   - line-id: 线条 ID( show-line! 返回的值)"
  [line-id]
  (swap! debug-render-state update :line-renders dissoc line-id))

(defn- render-line-data
  "内部: 渲染线条数据"
  [^PoseStack pose-stack ^MultiBufferSource buffer-source render-data]
  (let [{:keys [from to color width]} render-data]
    (render-line pose-stack buffer-source from to color :width width)))

;; ============================================================================
;; 主渲染函数
;; ============================================================================

(defn render-debug-overlays!
  "渲染所有调试覆盖层

   此函数应在客户端渲染事件中调用

   参数:
   - pose-stack: PoseStack
   - buffer-source: MultiBufferSource
   - camera-pos: 相机位置 Vec3"
  [^PoseStack pose-stack ^MultiBufferSource buffer-source ^Vec3 camera-pos]
  (let [current-time (System/currentTimeMillis)]

    ;; 平移到相对相机的坐标
    (.pushPose pose-stack)
    (.translate pose-stack
                (- (.x camera-pos))
                (- (.y camera-pos))
                (- (.z camera-pos)))

    ;; 渲染路径
    (doseq [[entity-id render-data] (:path-renders @debug-render-state)]
      (when (< current-time (:expire-tick render-data))
        (render-path pose-stack buffer-source render-data))
      (when (>= current-time (:expire-tick render-data))
        (swap! debug-render-state update :path-renders dissoc entity-id)))

    ;; 渲染导航目标
    (doseq [[entity-id render-data] (:navigation-renders @debug-render-state)]
      (when (< current-time (:expire-tick render-data))
        (render-navigation-goal pose-stack buffer-source render-data))
      (when (>= current-time (:expire-tick render-data))
        (swap! debug-render-state update :navigation-renders dissoc entity-id)))

    ;; 渲染 AI 状态
    (doseq [[entity-id render-data] (:ai-debug-renders @debug-render-state)]
      (when (< current-time (:expire-tick render-data))
        (render-ai-debug pose-stack buffer-source render-data))
      (when (>= current-time (:expire-tick render-data))
        (swap! debug-render-state update :ai-debug-renders dissoc entity-id)))

    ;; 渲染碰撞箱
    (doseq [[entity-id render-data] (:bbox-renders @debug-render-state)]
      (when (< current-time (:expire-tick render-data))
        (render-bounding-box pose-stack buffer-source render-data))
      (when (>= current-time (:expire-tick render-data))
        (swap! debug-render-state update :bbox-renders dissoc entity-id)))

    ;; 渲染区域
    (doseq [[area-id render-data] (:area-renders @debug-render-state)]
      (when (< current-time (:expire-tick render-data))
        (render-area pose-stack buffer-source render-data))
      (when (>= current-time (:expire-tick render-data))
        (swap! debug-render-state update :area-renders dissoc area-id)))

    ;; 渲染线条
    (doseq [[line-id render-data] (:line-renders @debug-render-state)]
      (when (< current-time (:expire-tick render-data))
        (render-line-data pose-stack buffer-source render-data))
      (when (>= current-time (:expire-tick render-data))
        (swap! debug-render-state update :line-renders dissoc line-id)))

    (.popPose pose-stack)))

(comment
  ;; 使用示例

  ;; ========== AI 路径可视化 ==========

  ;; 1. 显示寻路路径
  (show-path! zombie
              :colors {:start :green
                       :line :yellow
                       :end :red
                       :waypoint :cyan}
              :duration 200
              :show-waypoints? true)

  ;; 2. 显示导航目标
  (show-navigation-goal! zombie [100 64 200]
                         :colors {:start :blue
                                  :line :white
                                  :end :red}
                         :duration 200)

  ;; 3. 显示 AI 状态
  (show-ai-debug! zombie
                  :duration 200
                  :show-goals? true
                  :show-target? true)

  ;; ========== 碰撞箱和区域 ==========

  ;; 4. 显示碰撞箱
  (show-bounding-box! player
                      :color :cyan
                      :duration 100)

  ;; 5. 显示区域
  (show-area! :spawn-zone [0 60 0] [20 80 20]
              :color :green
              :duration 0) ; 永久显示

  (hide-area! :spawn-zone)

  ;; ========== 清理 ==========

  ;; 6. 清除所有调试渲染
  (clear-all-debug-renders!)

  ;; ========== 在渲染事件中使用 ==========

  ;; 注册渲染事件
  (require '[com.fabriclj.swiss-knife.client.events.core :as events])

  (events/on-render-world
   (fn [pose-stack buffer-source camera-pos partial-tick]
     (render-debug-overlays! pose-stack buffer-source camera-pos))))
