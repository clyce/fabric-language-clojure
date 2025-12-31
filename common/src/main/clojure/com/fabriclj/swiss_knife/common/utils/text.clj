(ns com.fabriclj.swiss-knife.common.utils.text
  "文本和翻译工具

   提供 Minecraft Component 系统的 Clojure 封装，包括:
   - 翻译文本
   - 样式文本( 颜色、格式)
   - 交互文本( 点击、悬停)
   - 文本组合"
  (:import (net.minecraft.network.chat
              Component TextColor Style
              ClickEvent HoverEvent MutableComponent)))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 基础文本创建
;; ============================================================================

(defn translate
  "翻译键转文本

   参数:
   - key: 翻译键
   - args: 翻译参数( 可选)

   返回: Component

   示例:
   ```clojure
   (translate \"item.minecraft.diamond\")
   (translate \"commands.kill.success\" player-name)
   ```"
  [key & args]
  (if (seq args)
    (Component/translatable key (into-array Object args))
    (Component/translatable key)))

(defn literal-text
  "创建字面文本

   参数:
   - text: 文本内容

   返回: Component

   示例:
   ```clojure
   (literal-text \"Hello World\")
   ```"
  [text]
  (Component/literal text))

;; ============================================================================
;; 颜色和格式
;; ============================================================================

(defn colored-text
  "创建带颜色的文本

   参数:
   - text: 文本内容
   - color: 颜色( 关键字或 RGB 整数)
     关键字: :black/:dark_blue/:dark_green/:dark_aqua/:dark_red
             :dark_purple/:gold/:gray/:dark_gray/:blue/:green
             :aqua/:red/:light_purple/:yellow/:white

   示例:
   ```clojure
   (colored-text \"Hello\" :red)
   (colored-text \"World\" 0xFF0000)
   ```"
  [text color]
  (let [^MutableComponent comp (Component/literal text)
        text-color (if (keyword? color)
                     (TextColor/fromLegacyFormat
                      (case color
                        :black (net.minecraft.ChatFormatting/BLACK)
                        :dark_blue (net.minecraft.ChatFormatting/DARK_BLUE)
                        :dark_green (net.minecraft.ChatFormatting/DARK_GREEN)
                        :dark_aqua (net.minecraft.ChatFormatting/DARK_AQUA)
                        :dark_red (net.minecraft.ChatFormatting/DARK_RED)
                        :dark_purple (net.minecraft.ChatFormatting/DARK_PURPLE)
                        :gold (net.minecraft.ChatFormatting/GOLD)
                        :gray (net.minecraft.ChatFormatting/GRAY)
                        :dark_gray (net.minecraft.ChatFormatting/DARK_GRAY)
                        :blue (net.minecraft.ChatFormatting/BLUE)
                        :green (net.minecraft.ChatFormatting/GREEN)
                        :aqua (net.minecraft.ChatFormatting/AQUA)
                        :red (net.minecraft.ChatFormatting/RED)
                        :light_purple (net.minecraft.ChatFormatting/LIGHT_PURPLE)
                        :yellow (net.minecraft.ChatFormatting/YELLOW)
                        :white (net.minecraft.ChatFormatting/WHITE)
                        (net.minecraft.ChatFormatting/WHITE)))
                     (TextColor/fromRgb (int color)))]
    (.withStyle comp (-> (Style/EMPTY) (.withColor text-color)))))

(defn formatted-text
  "创建格式化文本

   参数:
   - text: 文本内容
   - opts: 格式选项
     - :color - 颜色
     - :bold - 粗体
     - :italic - 斜体
     - :underlined - 下划线
     - :strikethrough - 删除线
     - :obfuscated - 混淆

   示例:
   ```clojure
   (formatted-text \"Important!\"
                  :color :red
                  :bold true
                  :underlined true)
   (formatted-text \"Fancy text\"
                  :color 0x00FF00
                  :italic true
                  :obfuscated true)
   ```"
  [text & {:keys [color bold italic underlined strikethrough obfuscated]}]
  (let [^MutableComponent comp (Component/literal text)
        style (cond-> (Style/EMPTY)
                color (.withColor (if (keyword? color)
                                    (TextColor/fromLegacyFormat
                                     (case color
                                       :black (net.minecraft.ChatFormatting/BLACK)
                                       :dark_blue (net.minecraft.ChatFormatting/DARK_BLUE)
                                       :dark_green (net.minecraft.ChatFormatting/DARK_GREEN)
                                       :dark_aqua (net.minecraft.ChatFormatting/DARK_AQUA)
                                       :dark_red (net.minecraft.ChatFormatting/DARK_RED)
                                       :dark_purple (net.minecraft.ChatFormatting/DARK_PURPLE)
                                       :gold (net.minecraft.ChatFormatting/GOLD)
                                       :gray (net.minecraft.ChatFormatting/GRAY)
                                       :dark_gray (net.minecraft.ChatFormatting/DARK_GRAY)
                                       :blue (net.minecraft.ChatFormatting/BLUE)
                                       :green (net.minecraft.ChatFormatting/GREEN)
                                       :aqua (net.minecraft.ChatFormatting/AQUA)
                                       :red (net.minecraft.ChatFormatting/RED)
                                       :light_purple (net.minecraft.ChatFormatting/LIGHT_PURPLE)
                                       :yellow (net.minecraft.ChatFormatting/YELLOW)
                                       :white (net.minecraft.ChatFormatting/WHITE)))
                                    (TextColor/fromRgb (int color))))
                bold (.withBold (boolean bold))
                italic (.withItalic (boolean italic))
                underlined (.withUnderlined (boolean underlined))
                strikethrough (.withStrikethrough (boolean strikethrough))
                obfuscated (.withObfuscated (boolean obfuscated)))]
    (.withStyle comp style)))

;; ============================================================================
;; 交互文本
;; ============================================================================

(defn clickable-text
  "创建可点击文本

   参数:
   - text: 文本内容
   - action: 点击动作
     - [:open-url url] - 打开 URL
     - [:run-command cmd] - 运行命令
     - [:suggest-command cmd] - 建议命令
     - [:change-page page] - 改变书页
     - [:copy-to-clipboard text] - 复制到剪贴板

   示例:
   ```clojure
   (clickable-text \"Click me!\" [:open-url \"https://minecraft.net\"])
   (clickable-text \"Run command\" [:run-command \"/kill @s\"])
   (clickable-text \"Copy\" [:copy-to-clipboard \"Some text\"])
   ```"
  [text [action-type action-value]]
  (let [^MutableComponent comp (Component/literal text)
        click-event (case action-type
                      :open-url (ClickEvent. ClickEvent$Action/OPEN_URL action-value)
                      :run-command (ClickEvent. ClickEvent$Action/RUN_COMMAND action-value)
                      :suggest-command (ClickEvent. ClickEvent$Action/SUGGEST_COMMAND action-value)
                      :change-page (ClickEvent. ClickEvent$Action/CHANGE_PAGE (str action-value))
                      :copy-to-clipboard (ClickEvent. ClickEvent$Action/COPY_TO_CLIPBOARD action-value))]
    (.withStyle comp (-> (Style/EMPTY) (.withClickEvent click-event)))))

(defn hoverable-text
  "创建悬停提示文本

   参数:
   - text: 文本内容
   - hover: 悬停内容( Component 或字符串)

   示例:
   ```clojure
   (hoverable-text \"Hover me\" \"This is a tooltip\")
   (hoverable-text \"Info\" (colored-text \"Detailed info\" :aqua))
   ```"
  [text hover]
  (let [^MutableComponent comp (Component/literal text)
        hover-comp (if (instance? Component hover)
                     hover
                     (Component/literal hover))
        hover-event (HoverEvent. HoverEvent$Action/SHOW_TEXT hover-comp)]
    (.withStyle comp (-> (Style/EMPTY) (.withHoverEvent hover-event)))))

(comment
  ;; 使用示例

  ;; ========== 基础文本 ==========

  ;; 1. 翻译文本
  (translate "item.minecraft.diamond")
  (translate "commands.kill.success" "Steve")

  ;; 2. 字面文本
  (literal-text "Hello World")

  ;; ========== 颜色和格式 ==========

  ;; 3. 彩色文本
  (colored-text "Error!" :red)
  (colored-text "Success!" :green)
  (colored-text "Custom" 0xFF00FF)

  ;; 4. 格式化文本
  (formatted-text "Important!"
                  :color :red
                  :bold true
                  :underlined true)

  (formatted-text "Fancy"
                  :color 0x00FF00
                  :italic true
                  :obfuscated true
                  :strikethrough true)

  ;; ========== 交互文本 ==========

  ;; 5. 可点击文本
  (clickable-text "Visit Website" [:open-url "https://minecraft.net"])
  (clickable-text "Kill yourself" [:run-command "/kill @s"])
  (clickable-text "Teleport" [:suggest-command "/tp ~ ~10 ~"])
  (clickable-text "Copy" [:copy-to-clipboard "Important text"])

  ;; 6. 悬停提示
  (hoverable-text "Hover for info" "This is a tooltip")
  (hoverable-text "Diamond"
                  (formatted-text "Rare ore\nFound at Y < 16"
                                  :color :aqua
                                  :italic true))

  ;; ========== 组合使用 ==========

  ;; 7. 多重样式
  (-> "Click me!"
      literal-text
      (.withStyle (-> (Style/EMPTY)
                      (.withColor (TextColor/fromRgb 0xFF0000))
                      (.withBold true)
                      (.withClickEvent (ClickEvent. ClickEvent$Action/RUN_COMMAND "/help"))
                      (.withHoverEvent (HoverEvent. HoverEvent$Action/SHOW_TEXT
                                                    (Component/literal "Run /help"))))))

  ;; 8. 多个文本组合
  (-> (literal-text "Hello ")
      (.append (colored-text "World" :red))
      (.append (literal-text "!"))
      (.append (clickable-text " Click" [:run-command "/say Hi"]))))
