(ns com.fabriclj.swiss-knife.common.gameplay.fuel
  "瑞士军刀 - 燃料系统模块

   封装物品燃料值注册，用于熔炉等方块。"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import (dev.architectury.registry FuelRegistry)
           (net.minecraft.world.item Item ItemStack)
           (net.minecraft.world.level.block Block)))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 燃料注册
;; ============================================================================

(defn register-fuel!
  "注册物品为燃料

   参数:
   - item: Item、Block、ItemStack、ResourceLocation 或 Keyword
   - burn-time: 燃烧时间( tick)

   说明:
   - 200 tick = 10 秒 = 可以烧炼 1 个物品
   - 常见燃料: 煤炭 1600 tick，木板 300 tick，木棍 100 tick

   示例:
   ```clojure
   (register-fuel! :minecraft:coal 1600)
   (register-fuel! my-item 3200)  ; 可以烧炼 16 个物品
   ```"
  [item burn-time]
  (cond
    (instance? Item item)
    (FuelRegistry/register burn-time ^Item item)

    (instance? Block item)
    (FuelRegistry/register burn-time ^Block item)

    (instance? ItemStack item)
    (FuelRegistry/register burn-time ^ItemStack item)

    :else
    (when-let [^Item item-obj (core/get-item item)]
      (FuelRegistry/register burn-time item-obj))))

(defn register-fuels!
  "批量注册燃料

   参数:
   - fuel-map: {物品 燃烧时间} 映射

   示例:
   ```clojure
   (register-fuels!
     {:minecraft:coal 1600
      :minecraft:coal_block 16000
      my-charcoal 1200})
   ```"
  [fuel-map]
  (doseq [[item burn-time] fuel-map]
    (register-fuel! item burn-time)))

;; ============================================================================
;; 预设燃料值
;; ============================================================================

(def fuel-values
  "常见燃料的燃烧时间( 参考值) "
  {:stick 100              ; 木棍
   :planks 300             ; 木板
   :sapling 100            ; 树苗
   :wooden-tool 200        ; 木工具
   :coal 1600              ; 煤炭
   :charcoal 1600          ; 木炭
   :coal-block 16000       ; 煤炭块
   :lava-bucket 20000      ; 岩浆桶
   :blaze-rod 2400         ; 烈焰棒
   :dried-kelp-block 4000  ; 干海带块
   })

(defn smelt-count
  "根据燃烧时间计算可烧炼物品数量

   参数:
   - burn-time: 燃烧时间( tick)

   返回: 可烧炼物品数量"
  [burn-time]
  (/ burn-time 200.0))

(defn burn-time-for-items
  "计算烧炼指定数量物品所需的燃烧时间

   参数:
   - item-count: 物品数量

   返回: 所需燃烧时间( tick) "
  [item-count]
  (* item-count 200))

;; ============================================================================
;; 宏
;; ============================================================================

(defmacro deffuel
  "定义燃料( 语法糖)

   示例:
   ```clojure
   (deffuel my-coal 1600)
   (deffuel my-super-fuel 10000)
   ```"
  [item burn-time]
  `(register-fuel! ~item ~burn-time))

(comment
  ;; 使用示例

  ;; 注册单个燃料
  (register-fuel! my-item 3200)  ; 可烧炼 16 个物品
  ;; 批量注册
  (register-fuels!
   {my-coal 1600
    my-charcoal 1200
    my-wood-plank 300})

  ;; 使用宏
  (deffuel my-super-coal 8000)

  ;; 计算燃烧时间
  (println "Coal can smelt" (smelt-count 1600) "items")  ; 8.0
  (println "Need" (burn-time-for-items 10) "ticks to smelt 10 items"))  ; 2000
