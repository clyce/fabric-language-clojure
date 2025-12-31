(ns com.example.core
  "示例 Clojure mod 主入口"
  (:require [com.fabriclj.core :as lib]
            [com.fabriclj.nrepl :as nrepl]))

(defn init
  "Mod 初始化函数"
  []
  (println "[ExampleMod] Initializing...")
  (println (str "[ExampleMod] Running on " (lib/platform-name)))
  (println (str "[ExampleMod] fabric-language-clojure version: " (lib/version)))

  ;; 开发模式下启动 nREPL
  (when (lib/dev-mode?)
    (println "[ExampleMod] Development mode detected, starting nREPL...")
    (nrepl/start-server!))

  (println "[ExampleMod] Initialization complete!"))
