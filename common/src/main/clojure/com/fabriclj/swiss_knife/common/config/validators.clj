(ns com.fabriclj.swiss-knife.common.config.validators
  "瑞士军刀 - 配置验证器

   提供常用的配置值验证器，用于确保配置文件的有效性。

   使用示例:
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.config.core :as config]
            '[com.fabriclj.swiss-knife.common.config.validators :as v])

   (config/register-config! \"mymod\" \"default\"
     {:max-players 20
      :spawn-rate 0.5
      :difficulty :normal}
     :validator (v/all-of
                  (v/has-keys? :max-players :spawn-rate :difficulty)
                  (v/validate-key :max-players (v/positive-integer?))
                  (v/validate-key :spawn-rate (v/in-range? 0.0 1.0))
                  (v/validate-key :difficulty (v/one-of? :easy :normal :hard))))
   ```")

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 基础验证器
;; ============================================================================

(defn positive-number?
  "验证是否为正数

   示例:
   ```clojure
   ((positive-number?) 5)    ; => true
   ((positive-number?) -1)   ; => false
   ((positive-number?) 0)    ; => false
   ```"
  []
  (fn [x]
    (and (number? x) (pos? x))))

(defn non-negative-number?
  "验证是否为非负数

   示例:
   ```clojure
   ((non-negative-number?) 0)   ; => true
   ((non-negative-number?) -1)  ; => false
   ```"
  []
  (fn [x]
    (and (number? x) (not (neg? x)))))

(defn positive-integer?
  "验证是否为正整数

   示例:
   ```clojure
   ((positive-integer?) 5)     ; => true
   ((positive-integer?) 5.5)   ; => false
   ((positive-integer?) -1)    ; => false
   ```"
  []
  (fn [x]
    (and (integer? x) (pos? x))))

(defn in-range?
  "验证数值是否在指定范围内( 包含边界)

   参数:
   - min: 最小值( 包含)
   - max: 最大值( 包含)

   示例:
   ```clojure
   ((in-range? 0 100) 50)    ; => true
   ((in-range? 0 100) 150)   ; => false
   ((in-range? 0.0 1.0) 0.5) ; => true
   ```"
  [min max]
  (fn [x]
    (and (number? x)
         (>= x min)
         (<= x max))))

(defn one-of?
  "验证值是否在给定的集合中

   参数:
   - values: 可接受的值( 可变参数)

   示例:
   ```clojure
   ((one-of? :easy :normal :hard) :normal)  ; => true
   ((one-of? :easy :normal :hard) :extreme) ; => false
   ((one-of? 1 2 3) 2)                      ; => true
   ```"
  [& values]
  (let [value-set (set values)]
    (fn [x]
      (contains? value-set x))))

(defn non-empty-string?
  "验证是否为非空字符串

   示例:
   ```clojure
   ((non-empty-string?) \"hello\")  ; => true
   ((non-empty-string?) \"\")       ; => false
   ((non-empty-string?) nil)      ; => false
   ```"
  []
  (fn [x]
    (and (string? x)
         (not (empty? x)))))

(defn min-length?
  "验证字符串或集合的最小长度

   参数:
   - n: 最小长度

   示例:
   ```clojure
   ((min-length? 3) \"hello\")     ; => true
   ((min-length? 3) \"hi\")        ; => false
   ((min-length? 2) [1 2 3])     ; => true
   ```"
  [n]
  (fn [x]
    (>= (count x) n)))

(defn max-length?
  "验证字符串或集合的最大长度

   参数:
   - n: 最大长度"
  [n]
  (fn [x]
    (<= (count x) n)))

(defn matches-pattern?
  "验证字符串是否匹配正则表达式

   参数:
   - pattern: 正则表达式( 字符串或 Pattern 对象)

   示例:
   ```clojure
   ((matches-pattern? #\"[a-z]+\") \"hello\")  ; => true
   ((matches-pattern? #\"[a-z]+\") \"Hello\")  ; => false
   ((matches-pattern? #\"\\d{3}-\\d{4}\") \"123-4567\") ; => true
   ```"
  [pattern]
  (let [regex (if (string? pattern)
                (re-pattern pattern)
                pattern)]
    (fn [x]
      (and (string? x)
           (boolean (re-matches regex x))))))

;; ============================================================================
;; 集合验证器
;; ============================================================================

(defn has-keys?
  "验证 map 是否包含所有指定的键

   参数:
   - keys: 必需的键( 可变参数)

   示例:
   ```clojure
   ((has-keys? :name :age) {:name \"Alice\" :age 25})  ; => true
   ((has-keys? :name :age) {:name \"Alice\"})          ; => false
   ```"
  [& keys]
  (fn [m]
    (and (map? m)
         (every? #(contains? m %) keys))))

(defn every-value?
  "验证 map 或集合中的每个值是否都满足谓词

   参数:
   - pred: 谓词函数

   示例:
   ```clojure
   ((every-value? pos?) {:a 1 :b 2 :c 3})      ; => true
   ((every-value? pos?) {:a 1 :b -1 :c 3})     ; => false
   ((every-value? string?) [\"a\" \"b\" \"c\"]) ; => true
   ```"
  [pred]
  (fn [coll]
    (cond
      (map? coll) (every? pred (vals coll))
      (coll? coll) (every? pred coll)
      :else false)))

(defn validate-key
  "验证 map 中特定键的值

   参数:
   - key: 要验证的键
   - validator: 验证器函数

   示例:
   ```clojure
   ((validate-key :age (positive-integer?))
     {:name \"Alice\" :age 25})  ; => true

   ((validate-key :age (positive-integer?))
     {:name \"Alice\" :age -1})  ; => false
   ```"
  [key validator]
  (fn [m]
    (and (map? m)
         (contains? m key)
         (validator (get m key)))))

;; ============================================================================
;; 组合验证器
;; ============================================================================

(defn all-of
  "所有验证器都必须通过

   参数:
   - validators: 验证器函数( 可变参数)

   示例:
   ```clojure
   (def validate-age
     (all-of
       (positive-integer?)
       (in-range? 0 150)))

   (validate-age 25)   ; => true
   (validate-age 200)  ; => false
   ```"
  [& validators]
  (fn [x]
    (every? #(% x) validators)))

(defn any-of
  "至少一个验证器通过

   参数:
   - validators: 验证器函数( 可变参数)

   示例:
   ```clojure
   (def validate-id
     (any-of
       string?
       (positive-integer?)))

   (validate-id \"abc\")  ; => true
   (validate-id 123)    ; => true
   (validate-id -1)     ; => false
   ```"
  [& validators]
  (fn [x]
    (boolean (some #(% x) validators))))

(defn none-of
  "所有验证器都必须不通过

   参数:
   - validators: 验证器函数( 可变参数) "
  [& validators]
  (fn [x]
    (not-any? #(% x) validators)))

(defn optional
  "使验证器可选( nil 值也通过)

   参数:
   - validator: 验证器函数

   示例:
   ```clojure
   ((optional (positive-integer?)) nil)  ; => true
   ((optional (positive-integer?)) 5)    ; => true
   ((optional (positive-integer?)) -1)   ; => false
   ```"
  [validator]
  (fn [x]
    (or (nil? x)
        (validator x))))

;; ============================================================================
;; 自定义验证器
;; ============================================================================

(defn custom
  "创建自定义验证器

   参数:
   - pred: 谓词函数
   - error-msg: 可选的错误消息

   示例:
   ```clojure
   (def validate-even
     (custom #(even? %) \"Value must be even\"))

   (validate-even 4)  ; => true
   (validate-even 3)  ; => false
   ```"
  ([pred]
   pred)
  ([pred error-msg]
   (fn [x]
     (let [result (pred x)]
       (if result
         true
         (do
           (println "Validation failed:" error-msg)
           false))))))

;; ============================================================================
;; 预设组合验证器
;; ============================================================================

(defn percentage?
  "验证是否为百分比( 0-100 的数字)

   示例:
   ```clojure
   ((percentage?) 50)   ; => true
   ((percentage?) 150)  ; => false
   ```"
  []
  (in-range? 0 100))

(defn probability?
  "验证是否为概率( 0.0-1.0 的数字)

   示例:
   ```clojure
   ((probability?) 0.5)  ; => true
   ((probability?) 1.5)  ; => false
   ```"
  []
  (in-range? 0.0 1.0))

(defn port-number?
  "验证是否为有效的端口号( 1-65535)

   示例:
   ```clojure
   ((port-number?) 8080)   ; => true
   ((port-number?) 70000)  ; => false
   ```"
  []
  (all-of
    (positive-integer?)
    (in-range? 1 65535)))

(defn file-path?
  "验证是否为非空字符串( 基础文件路径验证)

   示例:
   ```clojure
   ((file-path?) \"/path/to/file\")  ; => true
   ((file-path?) \"\")              ; => false
   ```"
  []
  (non-empty-string?))

(comment
  ;; 使用示例

  ;; 1. 基础验证
  ((positive-number?) 5)  ; => true
  ((in-range? 0 100) 50)  ; => true
  ((one-of? :a :b :c) :b) ; => true

  ;; 2. 组合验证
  (def validate-age
    (all-of
      (positive-integer?)
      (in-range? 0 150)))

  (validate-age 25)  ; => true

  ;; 3. 配置验证
  (require '[com.fabriclj.swiss-knife.common.config.core :as config])

  (config/register-config! "mymod" "default"
    {:max-players 20
     :spawn-rate 0.5
     :difficulty :normal}
    :validator (all-of
                 (has-keys? :max-players :spawn-rate :difficulty)
                 (validate-key :max-players (positive-integer?))
                 (validate-key :spawn-rate (probability?))
                 (validate-key :difficulty (one-of? :easy :normal :hard))))

  ;; 4. 复杂验证
  (def validate-server-config
    (all-of
      (has-keys? :port :max-players :motd)
      (validate-key :port (port-number?))
      (validate-key :max-players (all-of (positive-integer?) (in-range? 1 100)))
      (validate-key :motd (all-of (non-empty-string?) (max-length? 100)))))

  (validate-server-config
    {:port 25565
     :max-players 20
     :motd "My Server"})  ; => true
  )
