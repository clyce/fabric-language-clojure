(ns com.fabriclj.swiss-knife.client.rendering.core
  "瑞士军刀 - 渲染工具模块
   提供渲染相关的便捷函数

   注意：此命名空间仅在客户端环境可用！"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core])
  (:import [net.minecraft.client.gui GuiGraphics]
           [net.minecraft.client.renderer.texture TextureAtlasSprite]
           [net.minecraft.resources ResourceLocation]
           [net.minecraft.world.item ItemStack]
           [com.mojang.blaze3d.systems RenderSystem]
           [com.mojang.blaze3d.vertex PoseStack]))

;; 启用反射警告
(set! *warn-on-reflection* true)

;; ============================================================================
;; 基础渲染
;; ============================================================================

(defn push-pose!
  "保存当前渲染矩阵
   必须与 pop-pose! 配对使用"
  [^PoseStack pose-stack]
  (.pushPose pose-stack))

(defn pop-pose!
  "恢复渲染矩阵"
  [^PoseStack pose-stack]
  (.popPose pose-stack))

(defmacro with-pose
  "在矩阵堆栈中执行渲染代码

   示例:
   ```clojure
   (with-pose pose-stack
     (translate! pose-stack 10 10 0)
     (draw-something))
   ```"
  [pose-stack & body]
  `(do
     (push-pose! ~pose-stack)
     (try
       ~@body
       (finally
         (pop-pose! ~pose-stack)))))

(defn translate!
  "平移渲染矩阵"
  [^PoseStack pose-stack x y z]
  (.translate (.last pose-stack) x y z))

(defn scale!
  "缩放渲染矩阵"
  [^PoseStack pose-stack x y z]
  (.scale (.last pose-stack) x y z))

(defn rotate!
  "旋转渲染矩阵（度数）"
  [^PoseStack pose-stack angle axis]
  (let [radians (Math/toRadians angle)]
    (case axis
      :x (.rotateX (.last pose-stack) radians)
      :y (.rotateY (.last pose-stack) radians)
      :z (.rotateZ (.last pose-stack) radians))))

;; ============================================================================
;; GuiGraphics 绘制
;; ============================================================================

(defn draw-string
  "绘制字符串
   参数:
   - graphics: GuiGraphics
   - text: 文本（字符串�?Component�?
   - x, y: 坐标
   - color: 颜色（整数，�?0xFFFFFF 为白色）
   - shadow?: 是否绘制阴影（可选，默认 true�?

   示例:
   ```clojure
   (draw-string graphics \"Hello World\" 10 10 0xFFFFFF)
   ```"
  ([^GuiGraphics graphics text x y color]
   (draw-string graphics text x y color true))
  ([^GuiGraphics graphics text x y color shadow?]
   (if shadow?
     (.drawString graphics
                  (.font (net.minecraft.client.Minecraft/getInstance))
                  text x y color)
     (.drawString graphics
                  (.font (net.minecraft.client.Minecraft/getInstance))
                  text x y color false))))

(defn draw-centered-string
  "绘制居中字符串

   参数:
   - graphics: GuiGraphics
   - text: 文本
   - center-x: 中心 X 坐标
   - y: Y 坐标
   - color: 颜色"
  [^GuiGraphics graphics text center-x y color]
  (.drawCenteredString graphics
                       (.font (net.minecraft.client.Minecraft/getInstance))
                       text center-x y color))

(defn fill-rect
  "填充矩形
   参数:
   - graphics: GuiGraphics
   - x1, y1: 左上角坐标
   - x2, y2: 右下角坐标
   - color: 颜色（整数，0xAARRGGBB 格式）

   示例:
   ```clojure
   (fill-rect graphics 10 10 100 50 0x80000000)  ; 半透明黑色
   ```"
  [^GuiGraphics graphics x1 y1 x2 y2 color]
  (.fill graphics x1 y1 x2 y2 color))

(defn fill-gradient
  "填充渐变矩形

   参数:
   - graphics: GuiGraphics
   - x1, y1, x2, y2: 矩形坐标
   - color1: 起始颜色
   - color2: 结束颜色

   示例:
   ```clojure
   (fill-gradient graphics 0 0 100 100 0xFF000000 0x00000000)
   ```"
  [^GuiGraphics graphics x1 y1 x2 y2 color1 color2]
  (.fillGradient graphics x1 y1 x2 y2 color1 color2))

(defn draw-item
  "绘制物品图标

   参数:
   - graphics: GuiGraphics
   - item-stack: ItemStack
   - x, y: 坐标

   示例:
   ```clojure
   (draw-item graphics (item-stack :minecraft:diamond) 10 10)
   ```"
  [^GuiGraphics graphics ^ItemStack item-stack x y]
  (.renderItem graphics item-stack x y))

(defn draw-item-with-count
  "绘制物品图标（带数量)

   参数:
   - graphics: GuiGraphics
   - item-stack: ItemStack
   - x, y: 坐标
   - count-text: 数量文本（可选）"
  ([^GuiGraphics graphics ^ItemStack item-stack x y]
   (.renderItem graphics item-stack x y)
   (.renderItemDecorations graphics
                           (.font (net.minecraft.client.Minecraft/getInstance))
                           item-stack x y))
  ([^GuiGraphics graphics ^ItemStack item-stack x y count-text]
   (.renderItem graphics item-stack x y)
   (.renderItemDecorations graphics
                           (.font (net.minecraft.client.Minecraft/getInstance))
                           item-stack x y count-text)))

(defn draw-texture
  "绘制纹理
   参数:
   - graphics: GuiGraphics
   - texture: ResourceLocation
   - x, y: 屏幕坐标
   - u, v: 纹理坐标
   - width, height: 尺寸

   示例:
   ```clojure
   (draw-texture graphics
     (core/resource-location \"mymod\" \"textures/gui/custom.png\")
     10 10 0 0 16 16)
   ```"
  ([^GuiGraphics graphics ^ResourceLocation texture x y width height]
   (.blit graphics texture x y 0 0 width height width height))
  ([^GuiGraphics graphics ^ResourceLocation texture x y u v width height]
   (.blit graphics texture x y u v width height 256 256))
  ([^GuiGraphics graphics ^ResourceLocation texture x y u v width height tex-width tex-height]
   (.blit graphics texture x y u v width height tex-width tex-height)))

;; ============================================================================
;; 颜色工具
;; ============================================================================

(defn rgba
  "创建 RGBA 颜色
   参数:
   - r, g, b: 0-255
   - a: 0-255（可选，默认 255）

   返回：整数颜色

   示例:
   ```clojure
   (rgba 255 0 0)        ; 红色
   (rgba 0 0 0 128)      ; 半透明黑色
   ```"
  ([r g b]
   (rgba r g b 255))
  ([r g b a]
   (bit-or
    (bit-shift-left (bit-and a 0xFF) 24)
    (bit-shift-left (bit-and r 0xFF) 16)
    (bit-shift-left (bit-and g 0xFF) 8)
    (bit-and b 0xFF))))

(defn rgb
  "创建 RGB 颜色值（完全不透明）
   示例:
   ```clojure
   (rgb 255 0 0)  ; 红色
   ```"
  [r g b]
  (rgba r g b 255))

(defn hex-color
  "创建十六进制颜色
   参数:
   - hex: 十六进制字符串（\"#FF0000\" | \"FF0000\"）

   示例:
   ```clojure
   (hex-color \"#FF0000\")  ; 红色
   (hex-color \"80FF0000\") ; 半透明红色
   ```"
  [^String hex]
  (let [clean (if (.startsWith hex "#")
                (.substring hex 1)
                hex)]
    (Long/parseLong clean 16)))

(def colors
  "预定义颜色"
  {:white 0xFFFFFFFF
   :black 0xFF000000
   :red 0xFFFF0000
   :green 0xFF00FF00
   :blue 0xFF0000FF
   :yellow 0xFFFFFF00
   :cyan 0xFF00FFFF
   :magenta 0xFFFF00FF
   :gray 0xFF808080
   :dark-gray 0xFF404040
   :light-gray 0xFFC0C0C0
   :transparent 0x00000000})

;; ============================================================================
;; 渲染状�?
;; ============================================================================

(defn enable-blend!
  "启用混合（透明度）"
  []
  (RenderSystem/enableBlend))

(defn disable-blend!
  "禁用混合"
  []
  (RenderSystem/disableBlend))

(defn set-shader-color!
  "设置着色器颜色（RGBA）.0-1.0"
  [r g b a]
  (RenderSystem/setShaderColor r g b a))

(defn reset-shader-color!
  "重置着色器颜色为白�?"
  []
  (set-shader-color! 1.0 1.0 1.0 1.0))

;; ============================================================================
;; 便捷�?
;; ============================================================================

(defmacro with-blend
  "在启用混合的情况下执行渲�?

   示例:
   ```clojure
   (with-blend
     (fill-rect graphics 0 0 100 100 0x80000000))
   ```"
  [& body]
  `(do
     (enable-blend!)
     (try
       ~@body
       (finally
         (disable-blend!)))))

(defmacro with-color
  "使用指定颜色执行渲染

   示例:
   ```clojure
   (with-color [1.0 0.0 0.0 1.0]  ; 红色
     (draw-texture ...))
   ```"
  [[r g b a] & body]
  `(do
     (set-shader-color! ~r ~g ~b ~a)
     (try
       ~@body
       (finally
         (reset-shader-color!)))))

(comment
  ;; 使用示例

  ;; 绘制文本
  (draw-string graphics "Hello World" 10 10 0xFFFFFF)
  (draw-centered-string graphics "Centered" 100 50 0xFFFFFF)

  ;; 填充矩形
  (fill-rect graphics 0 0 100 50 (rgba 0 0 0 128))
  (fill-gradient graphics 0 0 100 100 0xFF000000 0x00000000)

  ;; 绘制物品
  (draw-item graphics diamond-stack 10 10)
  (draw-item-with-count graphics sword-stack 30 10)

  ;; 绘制纹理
  (draw-texture graphics
                (core/resource-location "mymod" "textures/gui/icon.png")
                50 50 0 0 16 16)

  ;; 使用矩阵变换
  (with-pose (.pose graphics)
    (translate! (.pose graphics) 50 50 0)
    (scale! (.pose graphics) 2.0 2.0 1.0)
    (draw-item graphics item-stack 0 0))

  ;; 使用颜色
  (fill-rect graphics 0 0 100 100 (hex-color "#80FF0000"))
  (fill-rect graphics 0 0 50 50 (:red colors)))
