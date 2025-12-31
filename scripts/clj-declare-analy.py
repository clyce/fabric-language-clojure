#!/usr/bin/env python3
"""
扫描Clojure文件，检测可能存在声明顺序异常的情况
（即在declare之前调用函数，但函数定义在调用之后）
"""

import os
import re
import sys
from pathlib import Path
from typing import List, Dict, Set, Tuple, Optional
from collections import defaultdict


class ClojureAnalyzer:
    """Clojure代码分析器"""

    def __init__(self):
        self.issues: List[Tuple[str, int, str]] = []  # (file, line, message)

    def find_clj_files(self, root_dir: str) -> List[Path]:
        """查找所有.clj文件"""
        clj_files = []
        root = Path(root_dir)
        for path in root.rglob("*.clj"):
            # 跳过build目录
            if "build" not in str(path):
                clj_files.append(path)
        return sorted(clj_files)

    def parse_file(self, file_path: Path) -> Dict:
        """解析Clojure文件，提取函数定义、declare和函数调用"""
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
            lines = content.split('\n')

        # 存储结果
        result = {
            'definitions': {},  # {symbol: line_number}
            'declares': set(),  # {symbol}
            'calls': [],  # [(line_number, symbol)]
            'namespaces': set(),  # 命名空间前缀，用于过滤外部调用
        }

        # 提取命名空间
        ns_match = re.search(r'\(ns\s+([^\s\)]+)', content)
        if ns_match:
            ns_name = ns_match.group(1)
            result['namespaces'].add(ns_name)
            # 也添加可能的别名
            result['namespaces'].add(ns_name.split('.')[-1])

        # 提取:require中的别名
        require_pattern = r':require\s+\[([^\]]+)\]'
        for match in re.finditer(require_pattern, content, re.MULTILINE | re.DOTALL):
            require_content = match.group(1)
            # 匹配 :as alias
            as_matches = re.findall(r':as\s+(\S+)', require_content)
            for alias in as_matches:
                result['namespaces'].add(alias)

        # 逐行分析
        for line_num, line in enumerate(lines, 1):
            stripped = line.strip()

            # 跳过注释和空行
            if not stripped or stripped.startswith(';'):
                continue

            # 提取函数定义 (defn symbol ...) 或 (def symbol ...)
            def_match = re.search(r'\(def(?:n)?\s+([^\s\)]+)', stripped)
            if def_match:
                symbol = def_match.group(1)
                # 移除可能的元数据标记
                symbol = re.sub(r'^\^?[:\w]+\s+', '', symbol).strip()
                if symbol and not symbol.startswith('('):
                    result['definitions'][symbol] = line_num

            # 提取declare (declare symbol1 symbol2 ...)
            declare_match = re.search(r'\(declare\s+([^\)]+)\)', stripped)
            if declare_match:
                symbols_str = declare_match.group(1)
                # 分割多个符号
                symbols = re.findall(r'([^\s\)]+)', symbols_str)
                for symbol in symbols:
                    result['declares'].add(symbol)

            # 提取函数调用 - 查找 (symbol ...) 形式的调用
            # 但排除一些特殊情况
            call_pattern = r'\(([a-zA-Z_\-\.\?\!]+[a-zA-Z0-9_\-\.\?\!]*)\s+'
            for match in re.finditer(call_pattern, stripped):
                symbol = match.group(1)

                # 跳过关键字、特殊形式、Java类等
                if symbol in ['if', 'when', 'let', 'fn', 'def', 'defn', 'ns',
                             'require', 'import', 'set!', 'do', 'cond', 'case',
                             'try', 'catch', 'finally', 'throw', '->', '->>',
                             'doto', 'and', 'or', 'not', 'nil?', 'some?', 'if-let',
                             'when-let', 'when-not', 'if-not', 'cond->', 'cond->>',
                             'some->', 'some->>', 'as->', 'loop', 'recur', 'reify',
                             'proxy', 'extend', 'extend-type', 'extend-protocol',
                             'deftype', 'defrecord', 'defprotocol', 'definterface',
                             'gen-class', 'gen-interface', 'declare', 'quote',
                             'var', 'deref', 'ref', 'atom', 'agent', 'future',
                             'delay', 'promise', 'locking', 'monitor-enter',
                             'monitor-exit', 'time', 'with-local-vars', 'binding',
                             'with-bindings', 'with-redefs', 'with-redefs-fn',
                             'alter-var-root', 'swap!', 'reset!', 'compare-and-set!',
                             'commute', 'ensure', 'dosync', 'io!', 'sync', 'ref-set',
                             'alter', 'commute', 'ref-history-count', 'ref-max-history',
                             'ref-min-history', 'ref-snapshots', 'ref-ensure',
                             'partial', 'comp', 'juxt', 'complement', 'constantly',
                             'identity', 'constantly', 'fnil', 'every-pred', 'some-fn',
                             'apply', 'map', 'mapv', 'mapcat', 'filter', 'filterv',
                             'remove', 'keep', 'keep-indexed', 'distinct', 'distinct?',
                             'concat', 'cons', 'conj', 'into', 'reduce', 'reductions',
                             'take', 'drop', 'take-while', 'drop-while', 'split-at',
                             'split-with', 'partition', 'partition-all', 'partition-by',
                             'interleave', 'interpose', 'cycle', 'repeat', 'repeatedly',
                             'iterate', 'range', 'rest', 'next', 'fnext', 'nnext',
                             'ffirst', 'nfirst', 'first', 'last', 'butlast', 'drop-last',
                             'take-last', 'take-nth', 'nth', 'nthnext', 'nthrest',
                             'frequencies', 'group-by', 'vals', 'keys', 'key', 'val',
                             'seq', 'vector', 'list', 'hash-map', 'array-map', 'sorted-map',
                             'sorted-map-by', 'sorted-set', 'sorted-set-by', 'hash-set',
                             'set', 'contains?', 'get', 'get-in', 'assoc', 'assoc-in',
                             'dissoc', 'dissoc-in', 'update', 'update-in', 'merge',
                             'merge-with', 'select-keys', 'zipmap', 'into-array',
                             'to-array', 'into-array', 'alength', 'aget', 'aset',
                             'make-array', 'vector-of', 'boolean-array', 'byte-array',
                             'char-array', 'short-array', 'int-array', 'long-array',
                             'float-array', 'double-array', 'object-array',
                             'str', 'string?', 'keyword', 'keyword?', 'symbol',
                             'symbol?', 'name', 'namespace', 'ident?', 'simple-ident?',
                             'qualified-ident?', 'qualified-keyword?', 'qualified-symbol?',
                             'simple-keyword?', 'simple-symbol?', 'gensym', 'gensym',
                             'format', 'printf', 'print', 'println', 'pr', 'prn',
                             'pr-str', 'prn-str', 'print-str', 'println-str',
                             'with-out-str', 'with-in-str', 'read', 'read-string',
                             'read-line', 'line-seq', 'slurp', 'spit', 'reader',
                             'writer', 'input-stream', 'output-stream', 'file-seq',
                             'sh', 'shutdown-agents', 'halt-when', 'halt-when!',
                             'error-handler', 'error-mode', 'send', 'send-off',
                             'send-via', 'restart-agent', 'await', 'await-for',
                             'await1', 'await-for1', 'agent-error', 'agent-errors',
                             'set-error-handler!', 'set-error-mode!', 'error-handler',
                             'error-mode', 'add-watch', 'remove-watch', 'notify-watches',
                             'add-watcher', 'remove-watcher', 'notify-watchers',
                             'realized?', 'deref', 'deref', 'deref', 'deref',
                             'realized?', 'realized?', 'realized?', 'realized?']:
                    continue

                # 跳过带命名空间前缀的调用（可能是外部库）
                if '.' in symbol and not any(symbol.startswith(ns + '.') for ns in result['namespaces']):
                    continue

                # 跳过Java类方法调用（通常是大写开头或包含$）
                if symbol[0].isupper() or '$' in symbol:
                    continue

                # 记录函数调用
                result['calls'].append((line_num, symbol))

        return result

    def analyze_file(self, file_path: Path):
        """分析单个文件"""
        try:
            result = self.parse_file(file_path)

            # 检查每个函数调用
            for call_line, symbol in result['calls']:
                # 如果符号已声明，跳过
                if symbol in result['declares']:
                    continue

                # 如果符号已定义，检查定义是否在调用之后
                if symbol in result['definitions']:
                    def_line = result['definitions'][symbol]
                    if def_line > call_line:
                        # 发现潜在问题：调用在定义之前，且没有declare
                        self.issues.append((
                            str(file_path),
                            call_line,
                            f"函数 '{symbol}' 在第 {call_line} 行被调用，但在第 {def_line} 行才定义（缺少declare）"
                        ))
                # 如果符号既未定义也未声明，可能是外部函数，暂时不报告
                # （因为可能是从其他命名空间导入的）

        except Exception as e:
            self.issues.append((
                str(file_path),
                0,
                f"分析文件时出错: {str(e)}"
            ))

    def analyze_directory(self, root_dir: str):
        """分析目录中的所有.clj文件"""
        clj_files = self.find_clj_files(root_dir)
        print(f"找到 {len(clj_files)} 个Clojure文件，开始分析...\n")

        for file_path in clj_files:
            self.analyze_file(file_path)

        return self.issues

    def print_report(self):
        """打印分析报告"""
        if not self.issues:
            print("[OK] 未发现声明顺序异常！")
            return

        print(f"发现 {len(self.issues)} 个潜在问题：\n")
        print("=" * 80)

        # 按文件分组
        by_file = defaultdict(list)
        for file_path, line_num, message in self.issues:
            by_file[file_path].append((line_num, message))

        for file_path in sorted(by_file.keys()):
            print(f"\n文件: {file_path}")
            print("-" * 80)
            for line_num, message in sorted(by_file[file_path]):
                print(f"  行 {line_num:4d}: {message}")


def main():
    """主函数"""
    # 获取项目根目录（脚本所在目录的父目录）
    script_dir = Path(__file__).parent
    root_dir = script_dir.parent

    print(f"分析目录: {root_dir}\n")

    analyzer = ClojureAnalyzer()
    analyzer.analyze_directory(str(root_dir))
    analyzer.print_report()

    # 如果有问题，返回非零退出码
    if analyzer.issues:
        sys.exit(1)
    else:
        sys.exit(0)


if __name__ == '__main__':
    main()
