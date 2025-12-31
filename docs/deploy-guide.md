# fabric-language-clojure 部署指南

本文档说明如何构建、测试和发布 fabric-language-clojure 语言库。

## 目录

- [发布前准备](#发布前准备)
- [构建发布版本](#构建发布版本)
- [验证构建产物](#验证构建产物)
- [发布到 Maven 仓库](#发布到-maven-仓库)
- [发布到模组平台](#发布到模组平台)
- [版本管理](#版本管理)
- [CI/CD 自动化](#cicd-自动化)

---

## 发布前准备

### 1. 更新版本号

编辑 `gradle.properties`:

```properties
# 语义化版本: 主版本.次版本.修订版
mod_version = 1.0.0

# 或带 Clojure 版本标识
mod_version = 1.0.0+clojure.1.11.1
```

**版本号规范**:
- **主版本**（Major）: 不兼容的 API 变更
- **次版本**（Minor）: 向后兼容的功能新增
- **修订版**（Patch）: 向后兼容的问题修正
- **+clojure.x.x.x**: 可选，标识捆绑的 Clojure 版本

### 2. 更新 CHANGELOG

创建或更新 `CHANGELOG.md`:

```markdown
# Changelog

## [1.0.0] - 2025-12-30

### Added
- Clojure 语言适配器支持
- ClojureBridge 用于 Mixin 桥接
- 捆绑 Clojure 1.11.1 和 nREPL 1.3.0
- 注册表 DSL 工具

### Changed
- N/A

### Fixed
- N/A
```

### 3. 排除 example 模块

**重要**: 部署时必须排除 `example` 项目，因为它只是测试 mod，不是语言库的一部分。

编辑 `settings.gradle`:

```groovy
include 'common'
include 'fabric'
// include 'example'  // ⚠️ 部署时注释掉！
// include 'forge'
```

**为什么要排除**:
- `example` 是一个独立的 mod，不是语言库本身
- 发布语言库不应包含示例代码的编译产物
- 用户应该参考 GitHub 上的源码示例，而不是打包的 JAR

### 4. 停止 Gradle Daemon

```powershell
.\gradlew.bat --stop
```

确保使用全新的 Daemon 和配置。

---

## 构建发布版本

### 1. 清理旧构建

```powershell
# Windows
Remove-Item -Recurse -Force common\build, fabric\build

# Linux/macOS
rm -rf common/build fabric/build
```

### 2. 完整构建

```powershell
.\gradlew.bat build -x checkClojure -x compileClojure
```

**预期输出**:
```
BUILD SUCCESSFUL in Xs
```

### 3. 验证构建产物

```powershell
Get-ChildItem -Recurse fabric\build\libs -Include *.jar
```

应该看到:
```
fabric-language-clojure-fabric-1.0.0.jar          # 最终发布文件 (~4-5MB)
fabric-language-clojure-fabric-1.0.0-sources.jar  # 源码 JAR
fabric-language-clojure-fabric-1.0.0-dev-shadow.jar  # 开发用（不发布）
```

---

## 验证构建产物

### 1. 检查 JAR 内容

```powershell
# 列出 JAR 内容
jar -tf fabric\build\libs\fabric-language-clojure-fabric-1.0.0.jar | Select-Object -First 30
```

**必须包含**:
- ✅ `com/fabriclj/` - 语言库的 Java 类
- ✅ `clojure/` - Clojure 运行时（relocated）
- ✅ `nrepl/` - nREPL 库（relocated）
- ✅ `clojure/com/fabriclj/` - 语言库的 Clojure 源码
- ✅ `fabric.mod.json` - Fabric 模组元数据

**不应包含**:
- ❌ `com/example/` - example 模块的代码
- ❌ Minecraft 类（已被 Loom provided）
- ❌ Fabric API 类（已被 provided）

### 2. 验证 fabric.mod.json

```powershell
# 提取并查看
jar -xf fabric\build\libs\fabric-language-clojure-fabric-1.0.0.jar fabric.mod.json
Get-Content fabric.mod.json
```

**检查要点**:
- ✅ `id` 为 `fabric-language-clojure`
- ✅ `version` 正确
- ✅ `languageAdapters` 包含 `"clojure": "com.fabriclj.fabric.ClojureLanguageAdapter"`
- ✅ `depends` 的版本范围合理

### 3. 测试加载

在一个测试 Minecraft 实例中:

1. 创建 `.minecraft/mods/` 目录
2. 复制 `fabric-language-clojure-fabric-1.0.0.jar`
3. 启动游戏
4. 检查日志:
   ```
   [fabric-language-clojure] Initializing Fabric Language Clojure
   [fabric-language-clojure] Clojure runtime initialized successfully
   ```

### 4. 测试 example mod（可选）

恢复 `settings.gradle` 中的 `include 'example'`:

```powershell
# 停止 Daemon 以刷新配置
.\gradlew.bat --stop

# 构建 example
.\gradlew.bat :example:build -x checkClojure -x compileClojure

# 将两个 JAR 都放入 mods 目录测试
Copy-Item fabric\build\libs\fabric-language-clojure-fabric-1.0.0.jar .minecraft\mods\
Copy-Item example\build\libs\example-clojure-mod-fabric-1.0.0.jar .minecraft\mods\
```

启动游戏，检查 example mod 是否正常工作。

**测试完成后，记得重新注释掉 `include 'example'`**。

---

## 发布到 Maven 仓库

### 方案 1: 发布到 Maven Central

#### 配置 Gradle

在 `fabric/build.gradle` 添加:

```groovy
plugins {
    id 'maven-publish'
    id 'signing'
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java

            groupId = 'com.fabriclj'
            artifactId = 'fabric-language-clojure-fabric'
            version = project.version

            pom {
                name = 'Fabric Language Clojure'
                description = 'Fabric language module for Clojure. Adds support for Clojure entrypoints and bundles the Clojure runtime.'
                url = 'https://github.com/pumpkingod/fabric-language-clojure'

                licenses {
                    license {
                        name = 'MIT License'
                        url = 'https://opensource.org/licenses/MIT'
                    }
                }

                developers {
                    developer {
                        id = 'arclojure-team'
                        name = 'Arclojure Team'
                    }
                }

                scm {
                    connection = 'scm:git:git://github.com/pumpkingod/fabric-language-clojure.git'
                    developerConnection = 'scm:git:ssh://github.com/pumpkingod/fabric-language-clojure.git'
                    url = 'https://github.com/pumpkingod/fabric-language-clojure'
                }
            }
        }
    }

    repositories {
        maven {
            url = version.endsWith('SNAPSHOT')
                ? 'https://oss.sonatype.org/content/repositories/snapshots/'
                : 'https://oss.sonatype.org/service/local/staging/deploy/maven2/'
            credentials {
                username = project.findProperty('ossrhUsername') ?: System.getenv('OSSRH_USERNAME')
                password = project.findProperty('ossrhPassword') ?: System.getenv('OSSRH_PASSWORD')
            }
        }
    }
}

signing {
    sign publishing.publications.mavenJava
}
```

#### 配置凭据

在 `~/.gradle/gradle.properties` 添加:

```properties
ossrhUsername=your-username
ossrhPassword=your-password
signing.keyId=your-key-id
signing.password=your-key-password
signing.secretKeyRingFile=/path/to/secring.gpg
```

#### 发布

```powershell
.\gradlew.bat publish
```

### 方案 2: 发布到 GitHub Packages

在 `fabric/build.gradle`:

```groovy
publishing {
    repositories {
        maven {
            name = 'GitHubPackages'
            url = 'https://maven.pkg.github.com/pumpkingod/fabric-language-clojure'
            credentials {
                username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
                password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

发布:

```powershell
$env:GITHUB_ACTOR = "your-username"
$env:GITHUB_TOKEN = "ghp_your_token"
.\gradlew.bat publish
```

### 方案 3: 本地测试（Maven Local）

```powershell
.\gradlew.bat publishToMavenLocal
```

发布到 `~/.m2/repository/com/fabriclj/fabric-language-clojure-fabric/`

其他项目可以通过添加 `mavenLocal()` 仓库来使用。

---

## 发布到模组平台

### CurseForge

1. 登录 [CurseForge 控制台](https://www.curseforge.com/minecraft/mc-mods)
2. 创建新项目或进入现有项目
3. 点击"Upload File"
4. 上传 `fabric-language-clojure-fabric-1.0.0.jar`
5. 填写:
   - **Display Name**: `1.0.0`
   - **Game Version**: `1.20.1` 等
   - **Release Type**: `Release`
   - **Changelog**: 复制 CHANGELOG.md 内容
6. 标记为 **Library** 类型
7. 点击"Save and Publish"

### Modrinth

1. 登录 [Modrinth](https://modrinth.com/dashboard/projects)
2. 创建新项目或进入现有项目
3. 点击"Upload a version"
4. 拖拽 JAR 文件
5. 填写:
   - **Version Number**: `1.0.0`
   - **Version Title**: `1.0.0 - Initial Release`
   - **Changelog**: Markdown 格式
   - **Game Versions**: `1.20.1` 等
   - **Loaders**: `Fabric`
   - **Channel**: `Release`
6. 标记为 **Library** 类型
7. 点击"Publish"

### 使用 minotaur 自动发布（可选）

在 `fabric/build.gradle` 添加:

```groovy
plugins {
    id 'com.modrinth.minotaur' version '2.+'
}

modrinth {
    token = System.getenv('MODRINTH_TOKEN')
    projectId = 'your-project-id'
    versionNumber = project.version
    versionType = 'release'
    uploadFile = remapJar
    gameVersions = ['1.20.1']
    loaders = ['fabric']
    changelog = file('CHANGELOG.md').text
}
```

发布:

```powershell
$env:MODRINTH_TOKEN = "your-token"
.\gradlew.bat modrinth
```

---

## 版本管理

### 语义化版本

遵循 [SemVer 2.0.0](https://semver.org/):

| 版本变更 | 示例 | 说明 |
|---------|------|------|
| 主版本 | `1.x.x` → `2.0.0` | 破坏性 API 变更 |
| 次版本 | `1.0.x` → `1.1.0` | 向后兼容的新功能 |
| 修订版 | `1.0.0` → `1.0.1` | 向后兼容的问题修正 |

### Git 标签

发布后创建 Git 标签:

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

### 分支策略

```
main         - 稳定版本，每次发布打 tag
├─ develop   - 开发分支
├─ feature/* - 新功能分支
└─ hotfix/*  - 紧急修复分支
```

---

## CI/CD 自动化

### GitHub Actions 示例

创建 `.github/workflows/release.yml`:

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v3

      - name: Prepare for release
        run: |
          # 注释掉 example 模块
          sed -i "s/include 'example'/\/\/ include 'example'/" settings.gradle

      - name: Build with Gradle
        run: ./gradlew build -x checkClojure -x compileClojure

      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            fabric/build/libs/fabric-language-clojure-fabric-*.jar
            !fabric/build/libs/*-dev-shadow.jar
          body_path: CHANGELOG.md
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Publish to Modrinth
        if: startsWith(github.ref, 'refs/tags/')
        run: ./gradlew modrinth
        env:
          MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}

      - name: Publish to Maven
        run: ./gradlew publish
        env:
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
```

### 手动发布检查清单

发布前检查:

- [ ] 更新 `gradle.properties` 中的版本号
- [ ] 更新 `CHANGELOG.md`
- [ ] **注释掉 `settings.gradle` 中的 `include 'example'`**
- [ ] 停止 Gradle Daemon (`gradlew --stop`)
- [ ] 清理构建目录
- [ ] 执行完整构建
- [ ] 验证 JAR 内容（不包含 example 代码）
- [ ] 在测试实例中加载验证
- [ ] 提交代码并推送
- [ ] 创建 Git 标签
- [ ] 发布到 Maven 仓库
- [ ] 发布到 CurseForge/Modrinth
- [ ] 在 GitHub 创建 Release
- [ ] **恢复 `include 'example'` 以便继续开发**

---

## 常见问题

### Q: 为什么 JAR 文件这么大（4-5MB）？

**正常现象**。因为捆绑了:
- Clojure 运行时（~3.7MB）
- nREPL（~200KB）
- 其他依赖

### Q: 是否需要发布 common 模块？

**不需要**。用户只需要 `fabric-language-clojure-fabric` JAR。

`common` 模块的代码已经通过 Architectury 转换并包含在 `fabric` JAR 中。

### Q: 是否需要 relocate Clojure？

**已经做了**。`fabric/build.gradle` 中的 Shadow 配置会将 Clojure 类重定位到 `com.fabriclj.shadow.clojure`，避免与其他 mod 冲突。

### Q: 如何发布快照版本？

版本号使用 `-SNAPSHOT` 后缀:

```properties
mod_version = 1.1.0-SNAPSHOT
```

快照版本不应发布到模组平台，只发布到 Maven 仓库的 snapshots 分支。

### Q: 发布后发现问题怎么办？

1. **小问题**: 发布 patch 版本（如 `1.0.1`）
2. **严重问题**:
   - 从 CurseForge/Modrinth 下架问题版本
   - 尽快发布修复版本
   - 在项目页面添加警告

---

## 下一步

- [开发指南](dev-guide.md) - 继续开发新功能
- [故障排查](troubleshooting.md) - 解决常见问题
- [测试指南](testing.md) - 编写测试用例
