# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2024-XX-XX

### 🎉 Initial Release - Language Support Library

This release marks the transformation from a starter-kit template to a standalone language support library, similar to [fabric-language-kotlin](https://github.com/FabricMC/fabric-language-kotlin).

### Added

- **ClojureLanguageAdapter**: Implements Fabric's `LanguageAdapter` interface
  - Supports function references: `"com.mymod.core/init"`
  - Supports namespace references: `"com.mymod.core"` (calls `-main`)
  - Supports variable references for pre-built initializers

- **ClojureBridge**: Utility class for calling Clojure from Java Mixin classes
  - Cached function lookups for performance
  - Support for 0-4 arguments plus varargs
  - Cache clearing for hot-reload during development

- **ClojureRuntime**: Internal runtime management
  - ClassLoader context handling
  - Namespace loading utilities
  - Clojure function invocation helpers

- **Clojure Utility Namespaces**:
  - `com.fabriclj.core`: Platform detection, mod utilities
  - `com.fabriclj.registry`: DeferredRegister DSL for game content
  - `com.fabriclj.nrepl`: nREPL server management
  - `com.fabriclj.client`: Client-side utilities

- **Example Mod**: Complete example in `examples/example-mod/`
  - Demonstrates Clojure entrypoints
  - Shows Mixin + ClojureBridge pattern
  - Includes nREPL setup

- **Comprehensive Documentation**:
  - Quick start guide
  - Developer guide with best practices
  - Debug guide for nREPL workflow
  - Troubleshooting guide

### Changed

- **Project renamed** from `fabriclj` to `fabric-language-clojure`
- **Architecture refactored** from template to library pattern
- **Clojure packages no longer relocated** - uses standard `clojure.core` namespace
- **Mixin approach changed** - users write Java Mixin classes, use ClojureBridge for Clojure calls

### Removed

- Built-in example Mixin classes (moved to examples/)
- ClojureHooks.java (replaced by ClojureBridge)
- Template-specific files

### Bundled Libraries

| Library | Version |
|---------|---------|
| org.clojure:clojure | 1.11.1 |
| nrepl:nrepl | 1.3.0 |

### Migration from Template

If you were using the old template approach:

1. Add `fabric-language-clojure` as a dependency
2. Update `fabric.mod.json` to use `"adapter": "clojure"`
3. Replace `ClojureHooks` calls with `ClojureBridge.invoke()`
4. Remove bundled Clojure from your mod (it's now provided by the library)

See the [Migration Guide](docs/dev-guide.md) for details.

---

## Pre-1.0.0 (Template Era)

The project started as a Minecraft mod template combining Clojure with Architectury API. This history is preserved in the `main` branch.
