(ns com.fabriclj.swiss-knife.common.gameplay.ai
  "AI 行为系统

   提供实体 AI 目标( Goals) 、行为树、寻路的高级封装。

   核心功能:
   - AI 目标创建和管理
   - 常用 AI 目标封装
   - 目标选择器
   - 寻路系统
   - 行为树( BehaviorTree)
   - 黑板系统( Blackboard)
   - 群体行为"
  (:require [clojure.string :as str])
  (:import (net.minecraft.world.entity Mob LivingEntity Entity PathfinderMob)
           (net.minecraft.world.entity.ai.goal Goal Goal$Flag GoalSelector
            WrappedGoal
            FloatGoal
            LookAtPlayerGoal
            RandomLookAroundGoal
            MeleeAttackGoal
            RangedAttackGoal
            FollowMobGoal
            WaterAvoidingRandomStrollGoal
            PanicGoal
            AvoidEntityGoal
            TemptGoal
            target.NearestAttackableTargetGoal
            target.HurtByTargetGoal
            target.OwnerHurtByTargetGoal
            target.OwnerHurtTargetGoal)
           (net.minecraft.world.entity.ai.navigation PathNavigation
            GroundPathNavigation)
           (net.minecraft.world.level.pathfinder Path)
           (net.minecraft.world.entity.player Player)
           (net.minecraft.world.entity.ai.targeting TargetingConditions)
           (net.minecraft.world.phys Vec3)
           (net.minecraft.core BlockPos)
           (net.minecraft.world.item ItemStack Items crafting.Ingredient)
           (java.util EnumSet)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; AI 目标创建
;; ============================================================================

(defn create-goal
  "创建自定义 AI 目标

   参数:
   - priority: 优先级( 数字越小优先级越高)
   - opts: 配置选项
     - :flags - 目标标志集合( :move/:look/:jump)
     - :can-use? - 是否可以使用的判断函数 (fn [entity] -> boolean)
     - :should-continue? - 是否继续执行的判断函数 (fn [entity] -> boolean)
     - :start! - 开始执行的函数 (fn [entity] -> void)
     - :tick! - 每 tick 执行的函数 (fn [entity] -> void)
     - :stop! - 停止执行的函数 (fn [entity] -> void)

   返回: Goal 实例

   示例:
   ```clojure
   (create-goal 5
     :can-use? (fn [entity]
                 (> (.getHealth entity) 10.0))
     :start! (fn [entity]
               (println \"Goal started!\"))
     :tick! (fn [entity]
              (println \"Ticking...\"))
     :stop! (fn [entity]
              (println \"Goal stopped!\")))
   ```"
  [priority & {:keys [flags can-use? should-continue? start! tick! stop!]
               :or {can-use? (constantly true)
                    should-continue? (constantly true)
                    start! (fn [_])
                    tick! (fn [_])
                    stop! (fn [_])}}]
  (let [goal-flags (if flags
                     (let [flag-class Goal$Flag
                           flag-set (EnumSet/noneOf flag-class)]
                       (doseq [flag flags]
                         (.add flag-set
                               (case flag
                                 :move (Enum/valueOf flag-class "MOVE")
                                 :look (Enum/valueOf flag-class "LOOK")
                                 :jump (Enum/valueOf flag-class "JUMP")
                                 :target (Enum/valueOf flag-class "TARGET"))))
                       flag-set)
                     (EnumSet/noneOf Goal$Flag))]
    (proxy [Goal] []
      (canUse []
        (try
          (can-use? (.mob ^Goal this))
          (catch Exception e
            (println "Error in canUse:" (.getMessage e))
            false)))
      (canContinueToUse []
        (try
          (should-continue? (.mob ^Goal this))
          (catch Exception e
            (println "Error in canContinueToUse:" (.getMessage e))
            false)))
      (start []
        (try
          (start! (.mob ^Goal this))
          (catch Exception e
            (println "Error in start:" (.getMessage e)))))
      (tick []
        (try
          (tick! (.mob ^Goal this))
          (catch Exception e
            (println "Error in tick:" (.getMessage e)))))
      (stop []
        (try
          (stop! (.mob ^Goal this))
          (catch Exception e
            (println "Error in stop:" (.getMessage e)))))
      (getFlags []
        goal-flags))))

;; ============================================================================
;; AI 目标管理
;; ============================================================================

(defn add-goal!
  "添加 AI 目标到实体

   参数:
   - entity: Mob 实体
   - priority: 优先级
   - goal: Goal 实例

   示例:
   ```clojure
   (add-goal! zombie 1 (FloatGoal. zombie))
   (add-goal! zombie 2 my-custom-goal)
   ```"
  [^Mob entity priority ^Goal goal]
  (.. entity goalSelector (addGoal (int priority) goal)))

(defn add-target-goal!
  "添加目标选择 AI 目标

   参数:
   - entity: Mob 实体
   - priority: 优先级
   - goal: Goal 实例"
  [^Mob entity priority ^Goal goal]
  (.. entity targetSelector (addGoal (int priority) goal)))

(defn remove-goal!
  "移除 AI 目标

   参数:
   - entity: Mob 实体
   - goal: Goal 实例"
  [^Mob entity ^Goal goal]
  (.. entity goalSelector (removeGoal goal)))

(defn remove-target-goal!
  "移除目标选择 AI 目标"
  [^Mob entity ^Goal goal]
  (.. entity targetSelector (removeGoal goal)))

(defn clear-goals!
  "清除所有 AI 目标

   参数:
   - entity: Mob 实体"
  [^Mob entity]
  (.. entity goalSelector removeAllGoals))

(defn clear-target-goals!
  "清除所有目标选择 AI 目标"
  [^Mob entity]
  (.. entity targetSelector removeAllGoals))

;; ============================================================================
;; 常用 AI 目标封装
;; ============================================================================

;; 移动类目标

(defn float-goal
  "漂浮目标( 在水中漂浮)

   参数:
   - entity: Mob 实体

   示例:
   ```clojure
   (add-goal! zombie 0 (float-goal zombie))
   ```"
  [^Mob entity]
  (FloatGoal. entity))

(defn wander-goal
  "随机游荡目标

   参数:
   - entity: PathfinderMob 实体
   - speed: 移动速度( 默认 1.0)
   - interval: 游荡间隔( tick，默认 120)

   示例:
   ```clojure
   (add-goal! cow 5 (wander-goal cow 1.0))
   ```"
  ([^PathfinderMob entity]
   (wander-goal entity 1.0 120))
  ([^PathfinderMob entity speed]
   (wander-goal entity speed 120))
  ([^PathfinderMob entity speed interval]
   (WaterAvoidingRandomStrollGoal. entity speed (int interval))))

(defn panic-goal
  "恐慌逃跑目标( 受到伤害时逃跑)

   参数:
   - entity: PathfinderMob 实体
   - speed: 逃跑速度( 默认 2.0)

   示例:
   ```clojure
   (add-goal! cow 1 (panic-goal cow 2.0))
   ```"
  ([^PathfinderMob entity]
   (panic-goal entity 2.0))
  ([^PathfinderMob entity speed]
   (PanicGoal. entity speed)))

(defn follow-mob-goal
  "跟随其他生物目标

   参数:
   - entity: Mob 实体
   - speed: 移动速度
   - stop-distance: 停止距离
   - area-size: 搜索区域大小

   示例:
   ```clojure
   (add-goal! baby-cow 5 (follow-mob-goal baby-cow 1.0 2.0 3.0))
   ```"
  [^Mob entity speed stop-distance area-size]
  (FollowMobGoal. entity speed stop-distance area-size))

(defn avoid-entity-goal
  "躲避实体目标

   参数:
   - entity: PathfinderMob 实体
   - avoid-class: 要躲避的实体类
   - max-distance: 最大检测距离
   - walk-speed: 行走速度
   - sprint-speed: 奔跑速度

   示例:
   ```clojure
   (add-goal! villager 1
     (avoid-entity-goal villager Zombie 8.0 0.6 1.0))
   ```"
  [^PathfinderMob entity avoid-class max-distance walk-speed sprint-speed]
  (AvoidEntityGoal. entity avoid-class max-distance walk-speed sprint-speed))

(defn tempt-goal
  "被物品吸引目标

   参数:
   - entity: PathfinderMob 实体
   - speed: 移动速度
   - items: 吸引物品( Ingredient)
   - can-scare: 是否会被吓跑

   示例:
   ```clojure
   (add-goal! cow 3
     (tempt-goal cow 1.25
       (Ingredient/of Items/WHEAT)
       false))
   ```"
  [^PathfinderMob entity speed items can-scare]
  (TemptGoal. entity speed items (boolean can-scare)))

;; 战斗类目标

(defn melee-attack-goal
  "近战攻击目标

   参数:
   - entity: PathfinderMob 实体
   - speed: 移动速度
   - follow-after-target-out-of-sight: 目标脱离视线后是否继续跟随

   示例:
   ```clojure
   (add-goal! zombie 2 (melee-attack-goal zombie 1.0 false))
   ```"
  ([^PathfinderMob entity speed]
   (melee-attack-goal entity speed false))
  ([^PathfinderMob entity speed follow]
   (MeleeAttackGoal. entity speed (boolean follow))))

(defn ranged-attack-goal
  "远程攻击目标

   参数:
   - entity: Mob 实体( 必须实现 RangedAttackMob 接口)
   - speed: 移动速度
   - attack-interval: 攻击间隔( tick)
   - max-distance: 最大攻击距离

   示例:
   ```clojure
   (add-goal! skeleton 4
     (ranged-attack-goal skeleton 1.0 20 15.0))
   ```"
  ([entity speed attack-interval max-distance]
   (RangedAttackGoal. entity speed (int attack-interval) (float max-distance))))

;; 交互类目标

(defn look-at-player-goal
  "看向玩家目标

   参数:
   - entity: Mob 实体
   - look-distance: 注视距离( 默认 6.0)
   - probability: 概率( 默认 0.02)

   示例:
   ```clojure
   (add-goal! villager 8 (look-at-player-goal villager 8.0))
   ```"
  ([^Mob entity]
   (look-at-player-goal entity 6.0 0.02))
  ([^Mob entity look-distance]
   (look-at-player-goal entity look-distance 0.02))
  ([^Mob entity look-distance probability]
   (LookAtPlayerGoal. entity Player (float look-distance) (float probability))))

(defn random-look-around-goal
  "随机四处张望目标

   参数:
   - entity: Mob 实体

   示例:
   ```clojure
   (add-goal! cow 9 (random-look-around-goal cow))
   ```"
  [^Mob entity]
  (RandomLookAroundGoal. entity))

;; ============================================================================
;; 目标选择器
;; ============================================================================

(defn nearest-attackable-target-goal
  "攻击最近的可攻击目标

   参数:
   - entity: Mob 实体
   - target-type: 目标类型( Class)
   - must-see: 是否必须看见( 默认 true)

   示例:
   ```clojure
   (add-target-goal! zombie 2
     (nearest-attackable-target-goal zombie Player))
   ```"
  ([^Mob entity target-type]
   (nearest-attackable-target-goal entity target-type true))
  ([^Mob entity target-type must-see]
   (net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal.
    entity target-type (boolean must-see))))

(defn hurt-by-target-goal
  "攻击伤害来源目标

   参数:
   - entity: PathfinderMob 实体
   - call-for-help: 呼叫同类帮助( 可选，Class 数组)

   示例:
   ```clojure
   (add-target-goal! zombie-pigman 1
     (hurt-by-target-goal zombie-pigman))
   ```"
  ([^PathfinderMob entity]
   (net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal.
    entity (into-array Class [])))
  ([^PathfinderMob entity & help-classes]
   (net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal.
    entity (into-array Class help-classes))))

(defn owner-hurt-by-target-goal
  "保护主人目标( 攻击伤害主人者)

   参数:
   - entity: TamableAnimal 实体

   示例:
   ```clojure
   (add-target-goal! wolf 1 (owner-hurt-by-target-goal wolf))
   ```"
  [entity]
  (net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal. entity))

(defn owner-hurt-target-goal
  "保护主人目标( 攻击主人攻击的对象) "
  [entity]
  (net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal. entity))

;; ============================================================================
;; 寻路系统
;; ============================================================================

(defn navigate-to-pos
  "导航到指定位置

   参数:
   - entity: Mob 实体
   - pos: 目标位置( BlockPos 或 [x y z])
   - speed: 移动速度

   返回: 是否成功开始导航"
  [^Mob entity pos speed]
  (let [^BlockPos target-pos (if (vector? pos)
                               (BlockPos. (int (nth pos 0))
                                          (int (nth pos 1))
                                          (int (nth pos 2)))
                               pos)
        ^PathNavigation nav (.getNavigation entity)]
    (boolean (.moveTo nav target-pos speed))))

(defn navigate-to-entity
  "导航到实体

   参数:
   - entity: Mob 实体
   - target: 目标实体
   - speed: 移动速度

   返回: 是否成功开始导航"
  [^Mob entity ^Entity target speed]
  (let [^PathNavigation nav (.getNavigation entity)]
    (boolean (.moveTo nav target speed))))

(defn stop-navigation
  "停止导航

   参数:
   - entity: Mob 实体"
  [^Mob entity]
  (.. entity getNavigation stop))

(defn is-navigating?
  "检查是否正在导航

   参数:
   - entity: Mob 实体

   返回: boolean"
  [^Mob entity]
  (not (.isDone (.getNavigation entity))))

(defn can-reach?
  "检查是否可达

   参数:
   - entity: Mob 实体
   - pos: 目标位置

   返回: boolean"
  [^Mob entity pos]
  (let [^BlockPos target-pos (if (vector? pos)
                               (BlockPos. (int (nth pos 0))
                                          (int (nth pos 1))
                                          (int (nth pos 2)))
                               pos)
        ^PathNavigation nav (.getNavigation entity)
        path (.createPath nav target-pos 0)]
    (and path (.canReach path))))

(defn get-path
  "获取路径

   参数:
   - entity: Mob 实体
   - target: 目标位置或实体

   返回: Path 或 nil"
  [^Mob entity target]
  (let [^PathNavigation nav (.getNavigation entity)]
    (cond
      (vector? target)
      (.createPath nav (BlockPos. (int (nth target 0))
                                  (int (nth target 1))
                                  (int (nth target 2))) 0)

      (instance? BlockPos target)
      (.createPath nav ^BlockPos target 0)

      (instance? Entity target)
      (.createPath nav ^Entity target 0)

      :else nil)))

;; 注意: 路径可视化功能应在客户端模块实现。
;;
;; 建议的客户端渲染功能:
;; 1. show-path - 显示完整寻路路径
;;    - 参数: entity, colors {:start :line :end :waypoint}
;;    - 渲染: 沿路径点绘制彩色线条
;; 2. show-navigation-goal - 显示导航目标
;;    - 参数: entity, colors {:start :end}
;;    - 渲染: 从实体到目标的直线
;; 3. show-ai-debug - 显示 AI 状态
;;    - 参数: entity
;;    - 渲染: 当前目标、优先级、状态
;;
;; 这些功能将在 client.rendering 或新的 client.debug-render 模块中实现，
;; 因为它们需要访问客户端渲染 API( RenderSystem、PoseStack 等) 。
;;
;; 实现参考位置: common/src/main/clojure/com/fabriclj/swiss-knife/client/rendering.clj

(defn set-path-search-range
  "设置寻路搜索范围

   参数:
   - entity: Mob 实体
   - range: 搜索范围( 方块数) "
  [^Mob entity range]
  (.. entity getNavigation (setMaxVisitedNodesMultiplier (float range))))


;; ============================================================================
;; 行为树系统
;; ============================================================================

(defprotocol BehaviorNode
  "行为树节点协议"
  (execute [this entity context]
    "执行节点

     参数:
     - entity: 实体
     - context: 上下文( 黑板)

     返回: :success/:failure/:running)"))

;; 叶子节点

(defrecord ConditionNode [predicate]
  BehaviorNode
  (execute [_ entity context]
    (if (predicate entity context)
      :success
      :failure)))

(defrecord ActionNode [action]
  BehaviorNode
  (execute [_ entity context]
    (action entity context)))

;; 组合节点

(defrecord SequenceNode [children]
  BehaviorNode
  (execute [_ entity context]
    (loop [nodes children]
      (if (empty? nodes)
        :success
        (case (execute (first nodes) entity context)
          :failure :failure
          :running :running
          :success (recur (rest nodes)))))))

(defrecord SelectorNode [children]
  BehaviorNode
  (execute [_ entity context]
    (loop [nodes children]
      (if (empty? nodes)
        :failure
        (case (execute (first nodes) entity context)
          :success :success
          :running :running
          :failure (recur (rest nodes)))))))

(defrecord ParallelNode [children]
  BehaviorNode
  (execute [_ entity context]
    (let [results (map #(execute % entity context) children)]
      (cond
        (some #{:failure} results) :failure
        (some #{:running} results) :running
        :else :success))))

;; 修饰节点

(defrecord InverterNode [child]
  BehaviorNode
  (execute [_ entity context]
    (case (execute child entity context)
      :success :failure
      :failure :success
      :running :running)))

(defrecord RepeaterNode [child times]
  BehaviorNode
  (execute [_ entity context]
    (loop [remaining times]
      (if (<= remaining 0)
        :success
        (case (execute child entity context)
          :failure :failure
          :running :running
          :success (recur (dec remaining)))))))

(defrecord UntilFailNode [child]
  BehaviorNode
  (execute [_ entity context]
    (loop []
      (case (execute child entity context)
        :failure :success
        :running :running
        :success (recur)))))

;; 构造函数

(defn condition-node
  "创建条件节点

   参数:
   - predicate: 判断函数 (fn [entity context] -> boolean)

   示例:
   ```clojure
   (condition-node (fn [entity ctx]
                     (> (.getHealth entity) 10.0)))
   ```"
  [predicate]
  (->ConditionNode predicate))

(defn action-node
  "创建行动节点

   参数:
   - action: 行动函数 (fn [entity context] -> :success/:failure/:running)

   示例:
   ```clojure
   (action-node (fn [entity ctx]
                  (.heal entity 5.0)
                  :success))
   ```"
  [action]
  (->ActionNode action))

(defn sequence-node
  "创建顺序节点( 所有子节点都成功才成功)

   参数:
   - children: 子节点列表

   示例:
   ```clojure
   (sequence-node
     [(condition-node low-health?)
      (action-node find-health-pack!)
      (action-node use-health-pack!)])
   ```"
  [& children]
  (->SequenceNode (vec children)))

(defn selector-node
  "创建选择节点( 任一子节点成功即成功)

   参数:
   - children: 子节点列表

   示例:
   ```clojure
   (selector-node
     (condition-node has-target?)
     (action-node find-target!)
     (action-node wander!))
   ```"
  [& children]
  (->SelectorNode (vec children)))

(defn parallel-node
  "创建并行节点( 同时执行多个子节点) "
  [& children]
  (->ParallelNode (vec children)))

(defn inverter-node
  "创建反转节点( 反转子节点结果) "
  [child]
  (->InverterNode child))

(defn repeater-node
  "创建重复节点( 重复执行 N 次) "
  [child times]
  (->RepeaterNode child times))

(defn until-fail-node
  "创建直到失败节点"
  [child]
  (->UntilFailNode child))

;; ============================================================================
;; 黑板系统
;; ============================================================================

(defn create-blackboard
  "创建黑板( AI 共享数据)

   参数:
   - initial-data: 初始数据( 可选)

   返回: atom

   示例:
   ```clojure
   (def blackboard (create-blackboard {:target nil :patrol-points []}))
   ```"
  ([]
   (atom {}))
  ([initial-data]
   (atom initial-data)))

(defn get-blackboard-value
  "获取黑板值

   参数:
   - blackboard: 黑板 atom
   - key: 键
   - default: 默认值( 可选)

   返回: 值"
  ([blackboard key]
   (get @blackboard key))
  ([blackboard key default]
   (get @blackboard key default)))

(defn set-blackboard-value!
  "设置黑板值

   参数:
   - blackboard: 黑板 atom
   - key: 键
   - value: 值"
  [blackboard key value]
  (swap! blackboard assoc key value))

(defn update-blackboard-value!
  "更新黑板值

   参数:
   - blackboard: 黑板 atom
   - key: 键
   - f: 更新函数"
  [blackboard key f]
  (swap! blackboard update key f))

;; ============================================================================
;; DSL 宏
;; ============================================================================

(defmacro defgoal
  "定义 AI 目标

   示例:
   ```clojure
   (defgoal follow-owner 5
     :flags [:move :look]
     :can-use? (fn [entity]
                 (and (has-owner? entity)
                      (far-from-owner? entity)))
     :tick! (fn [entity]
              (navigate-to-owner entity 1.0)))
   ```"
  [name priority & options]
  `(def ~name
     (create-goal ~priority ~@options)))

(defmacro defbehavior
  "定义行为树

   示例:
   ```clojure
   (defbehavior guard-behavior
     (selector-node
       (sequence-node
         (condition-node nearby-enemy?)
         (action-node attack-enemy!))
       (action-node patrol!)))
   ```"
  [name tree]
  `(def ~name ~tree))

(comment
  ;; 使用示例

  ;; ========== AI 目标 ==========

  ;; 1. 基本移动目标
  (add-goal! cow 0 (float-goal cow))
  (add-goal! cow 5 (wander-goal cow 1.0))
  (add-goal! cow 6 (look-at-player-goal cow 6.0))

  ;; 2. 战斗目标
  (add-goal! zombie 2 (melee-attack-goal zombie 1.0 false))
  (add-target-goal! zombie 2 (nearest-attackable-target-goal zombie Player))

  ;; 3. 自定义目标
  (def heal-goal
    (create-goal 3
                 :can-use? (fn [entity]
                             (< (.getHealth entity) 10.0))
                 :start! (fn [entity]
                           (println "Starting heal..."))
                 :tick! (fn [entity]
                          (.heal entity 0.1))
                 :stop! (fn [entity]
                          (println "Heal complete!"))))

  (add-goal! zombie heal-goal)

  ;; ========== 寻路 ==========

  ;; 4. 导航到位置
  (navigate-to-pos zombie [100 64 200] 1.0)

  ;; 5. 导航到实体
  (navigate-to-entity zombie player 1.5)

  ;; 6. 检查可达性
  (when (can-reach? zombie [100 64 200])
    (println "Can reach target!"))

  ;; ========== 行为树 ==========

  ;; 7. 简单行为树
  (defbehavior patrol-behavior
    (selector-node
     ;; 如果看到敌人就攻击
     (sequence-node
      (condition-node (fn [entity ctx]
                        (some? (.getTarget entity))))
      (action-node (fn [entity ctx]
                     (navigate-to-entity entity (.getTarget entity) 1.5)
                     :success)))
     ;; 否则巡逻
     (action-node (fn [entity ctx]
                    (let [patrol-points (get-blackboard-value ctx :patrol-points)
                          current-idx (get-blackboard-value ctx :patrol-idx 0)
                          target-pos (nth patrol-points current-idx)]
                      (when (navigate-to-pos entity target-pos 1.0)
                        (set-blackboard-value! ctx :patrol-idx
                                               (mod (inc current-idx) (count patrol-points))))
                      :running)))))

  ;; 8. 使用黑板
  (def blackboard (create-blackboard {:patrol-points [[100 64 200]
                                                      [200 64 100]
                                                      [150 64 150]]
                                     :patrol-idx 0}))

  ;; 9. 执行行为树
  (execute patrol-behavior zombie blackboard)

  ;; ========== 高级示例 ==========

  ;; 10. 复杂行为树: 守卫行为
  (defbehavior advanced-guard-behavior
    (sequence-node
     ;; 首先检查是否需要治疗
     (selector-node
      (inverter-node
       (condition-node (fn [entity _]
                         (< (.getHealth entity) 10.0))))
      (action-node (fn [entity _]
                     (navigate-to-pos entity [100 64 200] 0.8)  ; 逃跑
                     :success)))
     ;; 然后选择行动
     (selector-node
      ;; 优先攻击
      (sequence-node
       (condition-node (fn [entity _]
                         (some? (.getTarget entity))))
       (action-node (fn [entity _]
                      (navigate-to-entity entity (.getTarget entity) 1.5)
                      :success)))
      ;; 否则巡逻
      (action-node (fn [entity ctx]
                     :running)))))
  )