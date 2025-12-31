(ns com.fabriclj.nrepl
  "fabric-language-clojure nREPL 服务管理
   此命名空间提供嵌入式 nREPL 服务器，允许在游戏运行时
   通过 REPL 进行实时代码修改和调试。

   【安全警告】nREPL 服务仅应在开发环境启用！

   用法示例:
   ```clojure
   (ns com.mymod.core
     (:require [com.fabriclj.nrepl :as nrepl]
               [com.fabriclj.core :as core]))

   (defn init []
     ;; 仅在开发模式下启动 nREPL
     (when (core/dev-mode?)
       (nrepl/start-server!)))
   ```"
  (:require [nrepl.server :as nrepl]))

;; ============================================================================
;; 配置
;; ============================================================================

(def default-port
  "nREPL 默认端口"
  7888)

(def default-bind
  "nREPL 默认绑定地址( 仅本地访问) "
  "127.0.0.1")

;; ============================================================================
;; 服务状态
;; ============================================================================

;; nREPL 服务器实例原子
(defonce ^:private server-atom (atom nil))

;; ============================================================================
;; 公共 API
;; ============================================================================

(defn start-server!
  "启动 nREPL 服务器

   参数:
   - port: 端口号( 默认 7888)
   - bind: 绑定地址( 默认 127.0.0.1)

   返回启动的服务器实例，如果已启动则返回现有实例。

   用法:
   ```clojure
   (start-server!)              ; 使用默认端口 7888
   (start-server! 9999)         ; 使用自定义端口
   (start-server! 9999 \"0.0.0.0\") ; 允许远程连接( 危险！)
   ```"
  ([] (start-server! default-port default-bind))
  ([port] (start-server! port default-bind))
  ([port bind]
   (if-let [existing @server-atom]
     (do
       (println "[nREPL] Server already running")
       existing)
     (try
       (let [server (nrepl/start-server :port port :bind bind)]
         (reset! server-atom server)
         (println (str "[nREPL] Server started on " bind ":" port))
         (println "[nREPL] Connect with: lein repl :connect" port)
(println "[nREPL] Or use Calva: 'Connect to a running REPL' -> localhost:" port)
         server)
       (catch Exception e
         (println "[nREPL] Failed to start server:" (.getMessage e))
         nil)))))

(defn stop-server!
  "停止 nREPL 服务器"
  []
  (when-let [server @server-atom]
    (try
      (nrepl/stop-server server)
      (reset! server-atom nil)
      (println "[nREPL] Server stopped")
      (catch Exception e
        (println "[nREPL] Error stopping server:" (.getMessage e))))))

(defn restart-server!
  "重启 nREPL 服务器"
  ([] (restart-server! default-port default-bind))
  ([port] (restart-server! port default-bind))
  ([port bind]
   (stop-server!)
   (start-server! port bind)))

(defn server-running?
  "检查 nREPL 服务器是否正在运行"
  []
  (some? @server-atom))

(defn get-port
  "获取当前 nREPL 服务器端口( 如果正在运行) "
  []
  (when-let [server @server-atom]
    (:port server)))
