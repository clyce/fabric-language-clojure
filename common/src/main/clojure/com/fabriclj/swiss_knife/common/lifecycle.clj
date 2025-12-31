(ns com.fabriclj.swiss-knife.common.lifecycle
  "瑞士军刀 - 生命周期管理模块

   **模块定位**：统一的初始化和资源管理

   **核心功能**：
   - 统一的初始化入口
   - 正确的模块加载顺序
   - 资源清理和关闭管理
   - 客户端/服务端分离初始化

   **使用示例**：
   ```clojure

   ;; mod 主类 common init
   (lifecycle/init-common! \"mymod\"
     {:enable-generic-packets? true
      :enable-config-sync? true})

   ;; mod 主类 client init
   (lifecycle/init-client! \"mymod\"
     {:enable-hud? true
      :enable-debug? true})

   ;; mod 卸载时（如果需要）
   (lifecycle/shutdown! \"mymod\")
   ```

   **初始化顺序**：
   1. Common: 配置系统 → 网络系统 → 注册系统
   2. Client: HUD系统 → 调试系统 → 渲染系统

   **为什么需要这个模块**：
   - 避免用户忘记初始化某些系统
   - 确保初始化顺序正确（例如网络系统必须在配置同步之前初始化）
   - 提供统一的资源清理机制
   - 减少样板代码"
  (:require [com.fabriclj.swiss-knife.common.platform.core :as core]
            [com.fabriclj.swiss-knife.common.network.core :as network]
            [com.fabriclj.swiss-knife.common.config.sync :as config-sync]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; 初始化状态追踪
;; ============================================================================

(defonce ^:private initialization-state
  (atom {:common {}
         :client {}}))

(defn- mark-initialized!
  "标记某个模块已初始化"
  [side mod-id module]
  (swap! initialization-state assoc-in [side mod-id module] true))

(defn- initialized?
  "检查某个模块是否已初始化"
  [side mod-id module]
  (get-in @initialization-state [side mod-id module] false))

;; ============================================================================
;; Common 端初始化
;; ============================================================================

(defn init-common!
  "初始化通用模块（服务端+客户端共用）

   **参数**：
   - mod-id: Mod ID（字符串）
   - opts: 初始化选项（可选）

   **选项**：
   - `:enable-generic-packets?` - 启用通用数据包系统（默认 false）
   - `:enable-config-sync?` - 启用配置同步系统（默认 false）
   - `:packet-channel-name` - 自定义数据包通道名称（默认 \"swiss_knife_generic\"）

   **示例**：
   ```clojure

   ;; 最小配置
   (lifecycle/init-common! \"mymod\")

   ;; 启用所有功能
   (lifecycle/init-common! \"mymod\"
     {:enable-generic-packets? true
      :enable-config-sync? true})

   ;; 自定义通道名
   (lifecycle/init-common! \"mymod\"
     {:enable-generic-packets? true
      :packet-channel-name \"mymod_packets\"})
   ```

   **注意**：
   - 必须在 mod 主类的 common 初始化阶段调用
   - 如果启用配置同步，会自动启用通用数据包系统
   - 此函数幂等，重复调用不会有副作用"
  ([mod-id]
   (init-common! mod-id {}))
  ([mod-id opts]
   (when-not (initialized? :common mod-id :core)
     (core/log-info "[Swiss Knife]" "Initializing common modules for" mod-id)

     ;; 1. 初始化通用数据包系统（如果需要）
     (when (or (:enable-generic-packets? opts)
               (:enable-config-sync? opts))
       (let [channel-name (:packet-channel-name opts "swiss_knife_generic")]
         (core/log-info "[Swiss Knife]" "  - Initializing generic packet system with channel:" channel-name)
         (network/init-generic-packet-system! mod-id {:channel-name channel-name})
         (mark-initialized! :common mod-id :generic-packets)))

     ;; 2. 初始化配置同步系统（如果需要）
     (when (:enable-config-sync? opts)
       (core/log-info "[Swiss Knife]" "  - Initializing config sync system")
       (config-sync/register-config-sync-packets! mod-id)
       (mark-initialized! :common mod-id :config-sync))

     (mark-initialized! :common mod-id :core)
     (core/log-info "[Swiss Knife]" "Common initialization complete for" mod-id))))

;; ============================================================================
;; Client 端初始化
;; ============================================================================

(defn init-client!
  "初始化客户端模块

   **参数**：
   - mod-id: Mod ID（字符串）
   - opts: 初始化选项（可选）

   **选项**：
   - `:enable-hud?` - 启用 HUD 系统（默认 false）
   - `:enable-debug?` - 启用调试渲染系统（默认 false）

   **示例**：
   ```clojure

   ;; 最小配置
   (lifecycle/init-client! \"mymod\")

   ;; 启用 HUD
   (lifecycle/init-client! \"mymod\"
     {:enable-hud? true})

   ;; 启用所有功能
   (lifecycle/init-client! \"mymod\"
     {:enable-hud? true
      :enable-debug? true})
   ```

   **注意**：
   - 必须在 mod 主类的 client 初始化阶段调用
   - 只能在客户端调用
   - 此函数幂等，重复调用不会有副作用"
  ([mod-id]
   (init-client! mod-id {}))
  ([mod-id opts]
   (when (core/client-side?)
     (when-not (initialized? :client mod-id :core)
       (core/log-info "[Swiss Knife]" "Initializing client modules for" mod-id)

       ;; 1. 初始化 HUD 系统（如果需要）
       (when (:enable-hud? opts)
         (core/log-info "[Swiss Knife]" "  - Initializing HUD system")
         (try
           (require '[com.fabriclj.swiss-knife.client.rendering.hud :as hud])
           ((resolve 'com.fabriclj.swiss-knife.client.rendering.hud/init-hud-system!))
           (mark-initialized! :client mod-id :hud)
           (catch Exception e
             (core/log-error "[Swiss Knife]" "Failed to initialize HUD system:" (.getMessage e)))))

       ;; 2. 初始化调试系统（如果需要）
       (when (:enable-debug? opts)
         (core/log-info "[Swiss Knife]" "  - Initializing debug system")
         (try
           (require '[com.fabriclj.swiss-knife.client.rendering.debug-render :as debug-render])
           ;; Debug 系统不需要显式初始化
           (mark-initialized! :client mod-id :debug)
           (catch Exception e
             (core/log-error "[Swiss Knife]" "Failed to initialize debug system:" (.getMessage e)))))

       (mark-initialized! :client mod-id :core)
       (core/log-info "[Swiss Knife]" "Client initialization complete for" mod-id)))))

;; ============================================================================
;; 资源清理
;; ============================================================================

(defn shutdown!
  "清理和关闭所有已初始化的系统

   **参数**：
   - mod-id: Mod ID（字符串）

   **功能**：
   - 清理所有注册的事件处理器
   - 清理网络系统资源
   - 清理客户端渲染资源（如果在客户端）

   **示例**：
   ```clojure

   ;; mod 卸载时
   (lifecycle/shutdown! \"mymod\")
   ```

   **注意**：
   - 通常不需要手动调用，Minecraft 会自动清理
   - 主要用于开发时的热重载或测试"
  [mod-id]
  (core/log-info "[Swiss Knife]" "Shutting down" mod-id)

  ;; 清理 Common 资源
  (when (initialized? :common mod-id :core)
    ;; TODO: 添加事件处理器注销
    ;; TODO: 添加网络系统清理
    (swap! initialization-state update :common dissoc mod-id))

  ;; 清理 Client 资源
  (when (and (core/client-side?)
             (initialized? :client mod-id :core))
    ;; TODO: 添加 HUD 清理
    ;; TODO: 添加调试渲染清理
    (swap! initialization-state update :client dissoc mod-id))

  (core/log-info "[Swiss Knife]" "Shutdown complete for" mod-id))

;; ============================================================================
;; 工具函数
;; ============================================================================

(defn get-initialization-status
  "获取指定 mod 的初始化状态

   **参数**：
   - mod-id: Mod ID（字符串）

   **返回**：
   ```clojure

   {:common {:core true
             :generic-packets true
             :config-sync false}
    :client {:core true
             :hud true
             :debug false}}
   ```"
  [mod-id]
  {:common (get-in @initialization-state [:common mod-id] {})
   :client (get-in @initialization-state [:client mod-id] {})})

(defn print-initialization-status
  "打印所有 mod 的初始化状态（用于调试）"
  []
  (core/log-info "[Swiss Knife] Initialization Status:")
  (doseq [[side mods] @initialization-state]
    (core/log-info "  " side ":")
    (doseq [[mod-id modules] mods]
      (core/log-info "    " mod-id ":")
      (doseq [[module status] modules]
        (core/log-info "      " module ":" status)))))

(comment
  ;; 使用示例

  ;; 1. 基本初始化
  (init-common! "mymod")

  ;; 2. 启用所有功能
  (init-common! "mymod"
    {:enable-generic-packets? true
     :enable-config-sync? true})

  (init-client! "mymod"
    {:enable-hud? true
     :enable-debug? true})

  ;; 3. 查看初始化状态
  (get-initialization-status "mymod")
  (print-initialization-status)

  ;; 4. 关闭
  (shutdown! "mymod")
  )
