(ns com.fabriclj.swiss-knife.common.datagen.lang
  "瑞士军刀 - 数据生成: 语言文件

   提供语言文件( lang) 的生成功能，支持多语言本地化。

   使用示例:
   ```clojure
   (require '[com.fabriclj.swiss-knife.common.datagen.lang :as lang])

   ;; 创建语言文件
   (lang/create-lang-file! \"./src/main/resources\" \"mymod\" \"en_us\"
     {:item {:magic_sword \"Magic Sword\"
             :magic_gem \"Magic Gem\"}
      :block {:magic_ore \"Magic Ore\"}
      :itemGroup {:my_tab \"My Items\"}})
   ```"
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 键名生成
;; ============================================================================

(defn item-key
  "生成物品翻译键

   参数:
   - namespace: 命名空间
   - item-name: 物品名称

   示例:
   ```clojure
   (item-key \"mymod\" \"magic_sword\")
   ; => \"item.mymod.magic_sword\"
   ```"
  [namespace item-name]
  (str "item." namespace "." item-name))

(defn block-key
  "生成方块翻译键

   参数:
   - namespace: 命名空间
   - block-name: 方块名称

   示例:
   ```clojure
   (block-key \"mymod\" \"magic_ore\")
   ; => \"block.mymod.magic_ore\"
   ```"
  [namespace block-name]
  (str "block." namespace "." block-name))

(defn entity-key
  "生成实体翻译键

   参数:
   - namespace: 命名空间
   - entity-name: 实体名称"
  [namespace entity-name]
  (str "entity." namespace "." entity-name))

(defn itemGroup-key
  "生成物品组翻译键

   参数:
   - namespace: 命名空间
   - group-name: 组名称

   示例:
   ```clojure
   (itemGroup-key \"mymod\" \"my_tab\")
   ; => \"itemGroup.mymod.my_tab\"
   ```"
  [namespace group-name]
  (str "itemGroup." namespace "." group-name))

(defn advancement-key
  "生成进度翻译键

   参数:
   - namespace: 命名空间
   - advancement-path: 进度路径( 如 \"story/mine_stone\")
   - key-type: 键类型( :title 或 :description)

   示例:
   ```clojure
   (advancement-key \"mymod\" \"story/first_diamond\" :title)
   ; => \"advancement.mymod.story.first_diamond.title\"
   ```"
  [namespace advancement-path key-type]
  (let [path-parts (str/replace advancement-path "/" ".")
        suffix (name key-type)]
    (str "advancement." namespace "." path-parts "." suffix)))

(defn command-key
  "生成命令翻译键

   参数:
   - namespace: 命名空间
   - command-name: 命令名称
   - message-key: 消息键"
  [namespace command-name message-key]
  (str "commands." namespace "." command-name "." message-key))

;; ============================================================================
;; 语言文件生成
;; ============================================================================

(defn- flatten-translations
  "将嵌套的翻译映射扁平化

   参数:
   - namespace: 命名空间
   - translations: 嵌套的翻译映射

   示例:
   ```clojure
   (flatten-translations \"mymod\"
     {:item {:sword \"Sword\"}
      :block {:ore \"Ore\"}})
   ; => {\"item.mymod.sword\" \"Sword\"
         \"block.mymod.ore\" \"Ore\"}
   ```"
  [namespace translations]
  (reduce
    (fn [acc [category items]]
      (let [category-name (name category)]
        (merge acc
               (reduce-kv
                 (fn [m k v]
                   (assoc m
                          (str category-name "." namespace "." (name k))
                          v))
                 {}
                 items))))
    {}
    translations))

(defn create-lang-data
  "创建语言文件数据

   参数:
   - namespace: 命名空间
   - translations: 翻译映射，格式:
     {:item {name translation}
      :block {name translation}
      :entity {name translation}
      :itemGroup {name translation}
      :custom {\"key\" \"translation\"}}

   示例:
   ```clojure
   (create-lang-data \"mymod\"
     {:item {:magic_sword \"Magic Sword\"}
      :block {:magic_ore \"Magic Ore\"}
      :custom {\"mymod.welcome\" \"Welcome!\"}})
   ```"
  [namespace translations]
  (let [flattened (flatten-translations namespace (dissoc translations :custom))
        custom (:custom translations {})]
    (merge flattened custom)))

;; ============================================================================
;; 文件保存
;; ============================================================================

(defn- ensure-directory
  "确保目录存在"
  [^String path]
  (let [dir (io/file path)]
    (when-not (.exists dir)
      (.mkdirs dir))))

(defn save-lang-file!
  "保存语言文件

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - lang-code: 语言代码( 如 \"en_us\", \"zh_cn\")
   - lang-data: 语言数据映射

   示例:
   ```clojure
   (save-lang-file! \"./src/main/resources\" \"mymod\" \"en_us\"
                    {\"item.mymod.sword\" \"Magic Sword\"
                     \"block.mymod.ore\" \"Magic Ore\"})
   ```"
  [base-path namespace lang-code lang-data]
  (let [full-path (str base-path "/assets/" namespace "/lang/" lang-code ".json")
        dir-path (subs full-path 0 (.lastIndexOf full-path "/"))]
    (ensure-directory dir-path)
    (with-open [writer (io/writer full-path)]
      (json/write lang-data writer :indent true :escape-slash false))
    (println "[DataGen] Generated lang file:" full-path)))

(defn create-lang-file!
  "创建并保存语言文件

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - lang-code: 语言代码
   - translations: 翻译映射( 嵌套格式)

   示例:
   ```clojure
   (create-lang-file! \"./src/main/resources\" \"mymod\" \"en_us\"
     {:item {:magic_sword \"Magic Sword\"
             :magic_gem \"Magic Gem\"}
      :block {:magic_ore \"Magic Ore\"}
      :itemGroup {:my_tab \"My Creative Tab\"}
      :custom {\"mymod.welcome\" \"Welcome to My Mod!\"}})
   ```"
  [base-path namespace lang-code translations]
  (let [lang-data (create-lang-data namespace translations)]
    (save-lang-file! base-path namespace lang-code lang-data)))

;; ============================================================================
;; 辅助函数
;; ============================================================================

(defn title-case
  "将下划线分隔的名称转换为标题格式

   参数:
   - s: 字符串( 如 \"magic_sword\")

   返回: 标题格式字符串( 如 \"Magic Sword\")

   示例:
   ```clojure
   (title-case \"magic_sword\")  ; => \"Magic Sword\"
   (title-case \"oak_log\")      ; => \"Oak Log\"
   ```"
  [s]
  (->> (str/split s #"_")
       (map str/capitalize)
       (str/join " ")))

(defn generate-translations
  "从名称列表生成翻译

   参数:
   - names: 名称列表
   - transform-fn: 可选，名称转换函数( 默认 title-case)

   返回: 名称到翻译的映射

   示例:
   ```clojure
   (generate-translations [\"magic_sword\" \"magic_gem\"])
   ; => {:magic_sword \"Magic Sword\"
         :magic_gem \"Magic Gem\"}
   ```"
  ([names]
   (generate-translations names title-case))
  ([names transform-fn]
   (into {}
         (map (fn [name]
                [(keyword name) (transform-fn name)])
              names))))

;; ============================================================================
;; 批量生成
;; ============================================================================

(defn generate-item-translations
  "从物品名称列表生成翻译

   参数:
   - item-names: 物品名称列表

   示例:
   ```clojure
   (generate-item-translations [\"magic_sword\" \"magic_gem\"])
   ; => {:magic_sword \"Magic Sword\"
         :magic_gem \"Magic Gem\"}
   ```"
  [item-names]
  (generate-translations item-names))

(defn generate-block-translations
  "从方块名称列表生成翻译

   参数:
   - block-names: 方块名称列表"
  [block-names]
  (generate-translations block-names))

(defn create-complete-lang-file!
  "创建完整的语言文件( 自动生成翻译)

   参数:
   - base-path: 基础路径
   - namespace: 命名空间
   - lang-code: 语言代码
   - items: 物品名称列表
   - blocks: 方块名称列表
   - custom: 自定义翻译映射

   示例:
   ```clojure
   (create-complete-lang-file! \"./src/main/resources\" \"mymod\" \"en_us\"
     [\"magic_sword\" \"magic_gem\"]
     [\"magic_ore\" \"magic_block\"]
     {\"mymod.welcome\" \"Welcome!\"})
   ```"
  [base-path namespace lang-code items blocks custom]
  (let [translations {:item (generate-item-translations items)
                      :block (generate-block-translations blocks)
                      :custom custom}]
    (create-lang-file! base-path namespace lang-code translations)))

(comment
  ;; 使用示例

  ;; 1. 基本使用
  (create-lang-file! "./src/main/resources" "mymod" "en_us"
    {:item {:magic_sword "Magic Sword"
            :magic_gem "Magic Gem"}
     :block {:magic_ore "Magic Ore"}
     :itemGroup {:my_tab "My Items"}})

  ;; 2. 自动生成翻译
  (create-complete-lang-file! "./src/main/resources" "mymod" "en_us"
    ["magic_sword" "magic_gem" "magic_wand"]
    ["magic_ore" "magic_block"]
    {"mymod.welcome" "Welcome to My Mod!"
     "mymod.farewell" "Thanks for playing!"})

  ;; 3. 多语言支持
  (create-lang-file! "./src/main/resources" "mymod" "zh_cn"
    {:item {:magic_sword "魔法剑"
            :magic_gem "魔法宝石"}
     :block {:magic_ore "魔法矿石"}})

  ;; 4. 生成键名
  (item-key "mymod" "sword")              ; => "item.mymod.sword"
  (advancement-key "mymod" "story/diamond" :title)  ; => "advancement.mymod.story.diamond.title"
  )
