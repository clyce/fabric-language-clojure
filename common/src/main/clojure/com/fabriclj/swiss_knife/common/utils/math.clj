(ns com.fabriclj.swiss-knife.common.utils.math
  "数学和随机工具

   提供常用的数学计算和随机数生成功能:
   - 随机数生成( 整数、浮点数、概率)
   - 权重随机选择
   - 数学插值
   - 距离计算
   - 角度处理"
  (:import (java.util Random)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 随机数生成
;; ============================================================================

;; 全局随机数生成器
(defonce ^:private rng
  (Random.))

(defn random-int
  "生成随机整数 [min, max)

   参数:
   - min: 最小值( 包含)
   - max: 最大值( 不包含)

   示例:
   ```clojure
   (random-int 1 10)   ; => 1-9 之间的随机整数
   (random-int 0 100)  ; => 0-99 之间的随机整数
   ```"
  [min max]
  (+ min (.nextInt ^Random rng (- max min))))

(defn random-float
  "生成随机浮点数 [min, max)

   参数:
   - min: 最小值( 包含)
   - max: 最大值( 不包含)

   示例:
   ```clojure
   (random-float 0.0 1.0)    ; => 0.0-1.0 之间
   (random-float 10.0 20.0)  ; => 10.0-20.0 之间
   ```"
  [min max]
  (+ min (* (.nextFloat ^Random rng) (- max min))))

(defn random-chance
  "随机概率判断

   参数:
   - probability: 概率( 0.0-1.0)

   返回: boolean

   示例:
   ```clojure
   (when (random-chance 0.3)  ; 30% 概率
     (println \"Lucky!\"))

   (if (random-chance 0.1)
     (drop-rare-item)
     (drop-common-item))
   ```"
  [probability]
  (< (.nextFloat ^Random rng) probability))

(defn weighted-random
  "权重随机选择

   参数:
   - weighted-map: 权重映射 {item weight ...}

   返回: 随机选中的 item

   算法: 使用轮盘赌算法( Roulette Wheel Selection)

   示例:
   ```clojure
   ;; 10% 钻石，30% 金，60% 铁
   (weighted-random {:diamond 1 :gold 3 :iron 6})

   ;; 动态权重
   (let [items {:common-sword 50
                :rare-sword 10
                :legendary-sword 1}]
     (weighted-random items))
   ```"
  [weighted-map]
  (let [total-weight (reduce + (vals weighted-map))
        rand-val (.nextFloat ^Random rng)
        target (* rand-val total-weight)]
    (loop [items (seq weighted-map)
           accumulated 0]
      (if-let [[item weight] (first items)]
        (let [new-accumulated (+ accumulated weight)]
          (if (< target new-accumulated)
            item
            (recur (rest items) new-accumulated)))
        (first (keys weighted-map))))))

;; ============================================================================
;; 数学插值和范围
;; ============================================================================

(defn lerp
  "线性插值( Linear Interpolation)

   参数:
   - a: 起始值
   - b: 结束值
   - t: 插值参数( 0.0-1.0)

   公式: a + t * (b - a)

   示例:
   ```clojure
   (lerp 0 100 0.5)    ; => 50.0 (中点)
   (lerp 0 100 0.0)    ; => 0.0  (起始)
   (lerp 0 100 1.0)    ; => 100.0 (结束)
   (lerp 10 20 0.25)   ; => 12.5
   ```"
  [a b t]
  (+ a (* t (- b a))))

(defn clamp
  "限制值在指定范围内

   参数:
   - value: 要限制的值
   - min-val: 最小值
   - max-val: 最大值

   示例:
   ```clojure
   (clamp 150 0 100)   ; => 100 (超过上限)
   (clamp -10 0 100)   ; => 0   (低于下限)
   (clamp 50 0 100)    ; => 50  (在范围内)
   ```"
  [value min-val max-val]
  (max min-val (min max-val value)))

;; ============================================================================
;; 距离计算
;; ============================================================================

(defn distance-2d
  "计算 2D 距离( 忽略 Y 轴)

   用于计算水平距离

   参数:
   - x1, z1: 第一个点的坐标
   - x2, z2: 第二个点的坐标

   示例:
   ```clojure
   (distance-2d 0 0 3 4)  ; => 5.0
   ```"
  [x1 z1 x2 z2]
  (Math/sqrt (+ (Math/pow (- x2 x1) 2)
                (Math/pow (- z2 z1) 2))))

(defn distance-3d
  "计算 3D 距离

   参数:
   - x1, y1, z1: 第一个点的坐标
   - x2, y2, z2: 第二个点的坐标

   示例:
   ```clojure
   (distance-3d 0 0 0 1 1 1)  ; => 1.732...
   (distance-3d 0 0 0 3 4 0)  ; => 5.0
   ```"
  [x1 y1 z1 x2 y2 z2]
  (Math/sqrt (+ (Math/pow (- x2 x1) 2)
                (Math/pow (- y2 y1) 2)
                (Math/pow (- z2 z1) 2))))

(defn distance-squared-3d
  "计算 3D 距离的平方

   避免开方运算，用于性能敏感的距离比较

   参数:
   - x1, y1, z1: 第一个点的坐标
   - x2, y2, z2: 第二个点的坐标

   示例:
   ```clojure
   ;; 判断是否在范围内( 无需开方)
   (< (distance-squared-3d x1 y1 z1 x2 y2 z2) (* radius radius))
   ```"
  [x1 y1 z1 x2 y2 z2]
  (+ (Math/pow (- x2 x1) 2)
     (Math/pow (- y2 y1) 2)
     (Math/pow (- z2 z1) 2)))

;; ============================================================================
;; 角度处理
;; ============================================================================

(defn normalize-angle
  "归一化角度到 [-180, 180] 范围

   用于处理 Minecraft 的旋转角度

   参数:
   - angle: 角度( 度)

   示例:
   ```clojure
   (normalize-angle 270)   ; => -90
   (normalize-angle 450)   ; => 90
   (normalize-angle -270)  ; => 90
   ```"
  [angle]
  (let [a (mod (+ angle 180) 360)]
    (- a 180)))

(defn deg->rad
  "角度转弧度

   参数:
   - degrees: 角度

   示例:
   ```clojure
   (deg->rad 180)  ; => 3.14159... (Math/PI)
   (deg->rad 90)   ; => 1.5708...  (Math/PI / 2)
   ```"
  [degrees]
  (Math/toRadians degrees))

(defn rad->deg
  "弧度转角度

   参数:
   - radians: 弧度

   示例:
   ```clojure
   (rad->deg Math/PI)      ; => 180.0
   (rad->deg (/ Math/PI 2)) ; => 90.0
   ```"
  [radians]
  (Math/toDegrees radians))

(comment
  ;; 使用示例

  ;; ========== 随机数生成 ==========

  ;; 1. 基础随机
  (random-int 1 10)       ; 1-9 之间的整数
  (random-float 0.0 1.0)  ; 0.0-1.0 之间的浮点数

  ;; 2. 概率判断
  (when (random-chance 0.3)
    (println "30% 概率触发"))

  ;; 3. 权重随机
  (def loot-table
    {:common-item 50
     :rare-item 10
     :legendary-item 1})

  (weighted-random loot-table)  ; 大概率返回 :common-item

  ;; ========== 数学插值 ==========

  ;; 4. 线性插值( 平滑过渡)
  (lerp 0 100 0.5)  ; => 50.0
  (lerp 10 20 0.25) ; => 12.5

  ;; 5. 值限制
  (clamp 150 0 100)  ; => 100
  (clamp -10 0 100)  ; => 0

  ;; ========== 距离计算 ==========

  ;; 6. 2D 距离( 水平距离)
  (distance-2d 0 0 3 4)  ; => 5.0

  ;; 7. 3D 距离
  (distance-3d 0 0 0 1 1 1)  ; => 1.732...

  ;; 8. 性能优化: 使用平方距离
  (def radius 10)
  (def radius-squared (* radius radius))

  (< (distance-squared-3d 0 0 0 5 5 5) radius-squared)  ; 比 (< (distance-3d ...) radius) 快

  ;; ========== 角度处理 ==========

  ;; 9. 角度归一化
  (normalize-angle 270)   ; => -90
  (normalize-angle 450)   ; => 90

  ;; 10. 角度弧度转换
  (deg->rad 180)  ; => Math/PI
  (rad->deg Math/PI)  ; => 180.0

  ;; ========== 实际应用 ==========

  ;; 11. 随机掉落系统
  (defn drop-loot []
    (let [item (weighted-random {:diamond 1
                                 :gold 5
                                 :iron 20
                                 :dirt 100})]
      (give-player-item item)))

  ;; 12. 暴击系统
  (defn calculate-damage [base-damage]
    (if (random-chance 0.2)  ; 20% 暴击率
      (* base-damage 2.0)
      base-damage))

  ;; 13. 平滑移动
  (defn smooth-move [current target speed]
    (lerp current target speed))

  ;; 14. 范围检测
  (defn is-in-range? [pos1 pos2 radius]
    (let [[x1 y1 z1] pos1
          [x2 y2 z2] pos2]
      (< (distance-squared-3d x1 y1 z1 x2 y2 z2)
         (* radius radius)))))
