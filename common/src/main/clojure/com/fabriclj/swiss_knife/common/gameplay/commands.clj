(ns com.fabriclj.swiss-knife.common.gameplay.commands
  "瑞士军刀 - 命令系统模块

   提供简化的命令注册和参数解析。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (com.mojang.brigadier CommandDispatcher)
           (com.mojang.brigadier.arguments IntegerArgumentType StringArgumentType)
           (com.mojang.brigadier.builder LiteralArgumentBuilder RequiredArgumentBuilder)
           (com.mojang.brigadier.context CommandContext)
           (net.minecraft.commands CommandSourceStack Commands)
           (net.minecraft.network.chat Component)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 命令构建
;; ============================================================================

(defn literal
  "创建字面量命令节点

   参数:
   - name: 命令名称

   返回: LiteralArgumentBuilder"
  ^LiteralArgumentBuilder [^String name]
  (Commands/literal name))

(defn argument
  "创建参数节点

   参数:
   - name: 参数名称
   - type: 参数类型( :int/:string 等)

   返回: RequiredArgumentBuilder"
  ^RequiredArgumentBuilder [^String name type]
  (let [arg-type (case type
                   :int (IntegerArgumentType/integer)
                   :int-min (IntegerArgumentType/integer 0)
                   :string (StringArgumentType/string)
                   :string-word (StringArgumentType/word)
                   :greedy-string (StringArgumentType/greedyString)
                   type)]
    (Commands/argument name arg-type)))

(defn executes
  "设置命令执行器

   参数:
   - builder: 命令构建器
   - handler: 执行函数 (fn [^CommandContext context] -> int)

   返回: builder"
  [builder handler]
  (.executes builder
             (reify com.mojang.brigadier.Command
               (run [_ context]
                 (handler context)))))

;; ============================================================================
;; 参数获取
;; ============================================================================

(defn get-source
  "获取命令源"
  ^CommandSourceStack [^CommandContext context]
  (.getSource context))

(defn get-int-arg
  "获取整数参数"
  [^CommandContext context ^String name]
  (IntegerArgumentType/getInteger context name))

(defn get-string-arg
  "获取字符串参数"
  ^String [^CommandContext context ^String name]
  (StringArgumentType/getString context name))

(defn get-player
  "获取执行命令的玩家"
  [^CommandContext context]
  (.getPlayer (get-source context)))

(defn send-feedback
  "发送反馈消息

   参数:
   - context: CommandContext
   - message: 消息文本
   - broadcast?: 是否广播( 可选，默认 false) "
  ([context message]
   (send-feedback context message false))
  ([^CommandContext context message broadcast?]
   (.sendSuccess (get-source context)
                 (reify java.util.function.Supplier
                   (get [_]
                     (if (string? message)
                       (Component/literal message)
                       message)))
                 broadcast?)))

(defn send-error
  "发送错误消息"
  [^CommandContext context message]
  (.sendFailure (get-source context)
                (if (string? message)
                  (Component/literal message)
                  message)))

;; ============================================================================
;; 命令注册
;; ============================================================================

(defonce ^:private commands (atom []))

(defn register-command!
  "注册命令

   参数:
   - name: 命令名称
   - builder-fn: 构建器函数 (fn [] -> LiteralArgumentBuilder)

   示例:
   ```clojure
   (register-command! \"hello\"
     (fn []
       (-> (literal \"hello\")
           (executes
             (fn [context]
               (send-feedback context \"Hello World!\")
               1)))))
   ```"
  [name builder-fn]
  (swap! commands conj {:name name :builder builder-fn}))

(defn apply-commands!
  "应用所有已注册的命令到调度器

   参数:
   - dispatcher: CommandDispatcher

   注意: 通常在服务器启动时由框架调用"
  [^CommandDispatcher dispatcher]
  (doseq [{:keys [name builder]} @commands]
    (.register dispatcher (builder))))

;; ============================================================================
;; DSL 宏
;; ============================================================================


(defmacro defcommand
  "定义命令( 语法糖)

   示例:
   ```clojure
   (defcommand heal
     (-> (literal \"heal\")
         (then (argument \"amount\" :int)
               (executes
                 (fn [context]
                   (let [player (get-player context)
                         amount (get-int-arg context \"amount\")]
                     (.heal player amount)
                     (send-feedback context
                       (str \"Healed \" amount \" hearts\"))
                     1))))))
   ```"
  [name & body]
  `(register-command! ~(clojure.core/name name)
                      (fn [] ~@body)))

(comment
  ;; 使用示例

  ;; 1. 简单命令
  (register-command! "hello"
                     (fn []
                       (-> (literal "hello")
                           (executes
                            (fn [context]
                              (send-feedback context "Hello World!")
                              1)))))

  ;; 2. 带参数的命令
  (register-command! "heal"
                     (fn []
                       (-> (literal "heal")
                           (.then
                            (-> (argument "amount" :int)
                                (executes
                                 (fn [context]
                                   (let [player (get-player context)
                                         amount (get-int-arg context "amount")]
                                     (.heal player amount)
                                     (send-feedback context
                                                    (str "Healed " amount " hearts"))
                                     1))))))))

  ;; 3. 使用宏
  (defcommand teleport
    (-> (literal "tp")
        (.then
         (-> (argument "x" :int)
             (.then
              (-> (argument "y" :int)
                  (.then
                   (-> (argument "z" :int)
                       (executes
                        (fn [context]
                          (let [player (get-player context)
                                x (get-int-arg context "x")
                                y (get-int-arg context "y")
                                z (get-int-arg context "z")]
                            (.teleportTo player x y z)
                            (send-feedback context
                                           (str "Teleported to " x " " y " " z))
                            1)))))))))))
  )