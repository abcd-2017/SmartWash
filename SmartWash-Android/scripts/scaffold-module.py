#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
============================================================
  SmartWash-Android 模块脚手架（对齐 kmp-ohos-template/script/scaffold-module.py）

用法:
    python scripts/scaffold-module.py --type feature --name game
    python scripts/scaffold-module.py --type feature --name order --split
    python scripts/scaffold-module.py --type common --name util
    python scripts/scaffold-module.py --type feature --name game --delete --yes
    python scripts/scaffold-module.py --type feature --name demo --dry-run

类型:
    common   通用库（common/{name}，扁平结构，按需追加 compose/hilt/serialization）
    feature  业务域（feature/{name}，默认单模块；--split 生成 api/impl 拆分）

选项:
    --split             feature 拆分为 api/impl 两个模块（默认单模块）
    --host <host>       深链 host（默认等于模块名；落点 = {Class}Route.Home）
    --no-app-register   不向 app/build.gradle 注入实现模块依赖（默认注入）
    --with-compose       common: 追加 compose convention
    --with-hilt          common: 追加 hilt convention
    --with-serialization common: 追加 serialization convention
    --delete             删除模块（settings 注销 + app 依赖移除 + git rm）
    --force              目标文件已存在时允许覆盖（默认拒绝）
    --yes                删除时跳过交互确认
    --dry-run            只打印将执行的操作与文件内容，不落盘、不改 git

约束（与本仓库 CLAUDE.md 铁律一致）:
    - feature api 模块：零 Hilt 运行时、零 feature 依赖（仅 common）
    - feature impl 模块：包名/namespace 保持 com.smartwash.feature.{name}（与模块路径解耦）
    - 每个模块根目录生成 AGENTS.md（供 agent 读取的模块文档）
    - 新模块一律经本脚本创建，禁止手搓目录（见 AGENTS.md「新增模块 SOP」）
============================================================
"""

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

# ============================================================
#  全局配置
# ============================================================

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SETTINGS = PROJECT_ROOT / "settings.gradle"
APP_BUILD = PROJECT_ROOT / "app" / "build.gradle"

BASE_PACKAGE = "com.smartwash"

CREATED_FILES = []
DRY_RUN = False


# ============================================================
#  工具函数
# ============================================================

def to_module_name(name: str) -> str:
    """连字符转下划线（包名/baseName 等场合）"""
    return name.replace("-", "_")


def to_class_name(name: str) -> str:
    """模块名 -> 类名前缀，如 points -> Points、my-widget -> MyWidget"""
    return "".join(word.capitalize() for word in name.replace("-", "_").split("_"))


def package_path(package: str) -> str:
    return package.replace(".", "/")


def render(template: str, mapping: dict) -> str:
    """@KEY@ 占位符替换（避免与 Kotlin/Gradle 的 $、{} 冲突）"""
    out = template
    for key, value in mapping.items():
        out = out.replace("@%s@" % key, value)
    return out


def make_dirs(path: Path) -> None:
    if DRY_RUN:
        return
    path.mkdir(parents=True, exist_ok=True)


def write_file(path: Path, content: str, force: bool = False) -> None:
    rel = path.relative_to(PROJECT_ROOT)

    if DRY_RUN:
        print("  [dry-run] 将写入 %s:" % rel)
        print("  " + "-" * 60)
        for line in content.rstrip("\n").split("\n"):
            print("  " + line)
        print("  " + "-" * 60)
        CREATED_FILES.append(path)
        return

    if path.exists() and not force:
        print("[ERROR] 目标文件已存在: %s" % rel)
        print("        默认拒绝覆盖，以免误伤既有模块；如需覆盖请加 --force")
        sys.exit(1)

    make_dirs(path.parent)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    CREATED_FILES.append(path)
    print("  创建: %s" % rel)


def ensure_module_dir_available(module_dir: Path, kind_desc: str) -> None:
    if not module_dir.exists():
        return
    entries = [e for e in module_dir.iterdir() if e.name != "build"]
    if entries:
        print("[ERROR] 模块目录已存在且非空: %s" % module_dir.relative_to(PROJECT_ROOT))
        print("        与现有模块同名冲突（%s），请更换名称，或先 --delete 删除" % kind_desc)
        sys.exit(1)


def git_add_files(files) -> None:
    if DRY_RUN or not files:
        return
    existing = [str(f.relative_to(PROJECT_ROOT)) for f in files if f.exists()]
    if not existing:
        return
    try:
        subprocess.run(["git", "add"] + existing, cwd=str(PROJECT_ROOT),
                       check=True, capture_output=True)
        print("  git add: %d 个文件已暂存" % len(existing))
    except subprocess.CalledProcessError as e:
        print("  [WARN] git add 失败: %s" % e.stderr.decode(errors="replace").strip())


def git_rm_files(paths) -> None:
    if DRY_RUN or not paths:
        return
    existing = [str(p.relative_to(PROJECT_ROOT)) for p in paths if p.exists()]
    if not existing:
        return
    try:
        subprocess.run(["git", "rm", "-r"] + existing, cwd=str(PROJECT_ROOT),
                       check=True, capture_output=True)
        print("  git rm: %d 个路径已删除并暂存" % len(existing))
    except subprocess.CalledProcessError as e:
        print("[ERROR] git rm 失败，已中止删除流程: %s" % e.stderr.decode(errors="replace").strip())
        raise


# ============================================================
#  settings.gradle 注册 / 注销
# ============================================================

def register_in_settings(gradle_paths) -> None:
    if not SETTINGS.exists():
        print("  [WARN] 找不到 settings.gradle，跳过注册")
        return
    content = SETTINGS.read_text(encoding="utf-8")

    new_lines = []
    for path in gradle_paths:
        line = "include '%s'" % path
        if line in content:
            print("  跳过注册（已存在）: %s @ settings.gradle" % path)
            continue
        new_lines.append(line)

    if not new_lines:
        return

    if DRY_RUN:
        print("  [dry-run] 将向 settings.gradle 追加 %d 行:" % len(new_lines))
        for line in new_lines:
            print("    " + line)
        return

    content = content.rstrip() + "\n" + "\n".join(new_lines) + "\n"
    SETTINGS.write_text(content, encoding="utf-8")
    print("  注册 %d 个模块到 settings.gradle" % len(new_lines))


def unregister_from_settings(gradle_paths) -> None:
    if not SETTINGS.exists():
        print("  [WARN] 找不到 settings.gradle，跳过注销")
        return
    lines = SETTINGS.read_text(encoding="utf-8").splitlines(keepends=True)

    targets = {"include '%s'" % p for p in gradle_paths}
    result = []
    removed_any = False
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.strip() in targets:
            removed_any = True
            print("  注销: %s @ settings.gradle" % line.strip())
            i += 1
            if result and result[-1].strip() == "" and i < len(lines) and lines[i].strip() == "":
                i += 1
            continue
        result.append(line)
        i += 1

    if not removed_any:
        return
    if DRY_RUN:
        print("  [dry-run] 将从 settings.gradle 删除 %d 个 include 行" % len(targets))
        return

    while result and result[-1].strip() == "":
        result.pop()
    SETTINGS.write_text("".join(result), encoding="utf-8")


# ============================================================
#  app/build.gradle 依赖注入 / 移除
# ============================================================

def register_in_app(gradle_path: str, comment: str) -> None:
    """在 app/build.gradle 最后一个 feature 依赖行之后注入实现模块依赖（默认开启）"""
    if not APP_BUILD.exists():
        print("  [WARN] 找不到 app/build.gradle，跳过依赖注入")
        return
    content = APP_BUILD.read_text(encoding="utf-8")
    dep = 'implementation project("%s")' % gradle_path
    if dep in content:
        print("  跳过 app 依赖注入（已存在 %s）" % gradle_path)
        return

    lines = content.split("\n")
    idx = -1
    for i, line in enumerate(lines):
        if re.match(r'^\s*implementation project\(":feature:[^"]+"\)\s*$', line):
            idx = i
    if idx < 0:
        print("  [WARN] 未找到 app/build.gradle 中 feature 依赖锚点，跳过依赖注入")
        return

    if DRY_RUN:
        print("  [dry-run] 将向 app/build.gradle 注入:")
        print("    // %s" % comment)
        print("    %s" % dep)
        return

    indent = "    "
    lines[idx + 1:idx + 1] = ["%s// %s" % (indent, comment), "%s%s" % (indent, dep)]
    APP_BUILD.write_text("\n".join(lines), encoding="utf-8")
    print("  注入 app 依赖: %s" % gradle_path)


def unregister_from_app(gradle_path: str) -> None:
    if not APP_BUILD.exists():
        print("  [WARN] 找不到 app/build.gradle，跳过依赖移除")
        return
    content = APP_BUILD.read_text(encoding="utf-8")
    dep = 'implementation project("%s")' % gradle_path
    if dep not in content:
        print("  跳过 app 依赖移除（未找到 %s）" % gradle_path)
        return
    if DRY_RUN:
        print("  [dry-run] 将从 app/build.gradle 移除: %s（含其上方注释块）" % dep)
        return

    out = []
    removed = False
    for line in content.split("\n"):
        if line.strip() == dep:
            while out and out[-1].strip().startswith("//"):
                out.pop()
            removed = True
            continue
        out.append(line)
    if removed:
        APP_BUILD.write_text("\n".join(out), encoding="utf-8")
        print("  移除 app 依赖: %s" % gradle_path)


# ============================================================
#  模板: build.gradle
# ============================================================

TEMPLATE_FEATURE_BUILD = """\
// :feature:@NAME@ —— @NAME@ 域（脚手架生成，单模块）。
// 路由常量 @CLASS@Route 为模块内单一事实来源，app 壳 MainActivity NavHost 一行
// @NAME_GRAPH@ 聚合；深链 host "@HOST@"（di/@CLASS@DeepLinkModule 经 @IntoSet 贡献）。
apply from: "$rootDir/config/android-library.gradle"
apply from: "$rootDir/config/compose.gradle"
apply from: "$rootDir/config/hilt.gradle"
apply from: "$rootDir/config/serialization.gradle"

android {
    namespace 'com.smartwash.feature.@NAME@'
}

dependencies {
    // 响应信封 ApiResult / RequestState / AppLogger / 触感 / pressable
    implementation project(':common:utils')
    // Retrofit（api 传递）+ @RequireAuthorization + NetworkException
    implementation project(':common:network')
    // 清氧设计系统 theme + 共享组件
    implementation project(':common:ui')

    implementation libs.androidx.lifecycle.viewmodel
    implementation libs.androidx.hilt.navigation.compose
    implementation libs.androidx.navigation.compose
    implementation libs.androidx.compose.foundation
    implementation libs.androidx.compose.animation
    implementation libs.androidx.material.icons.extended
    implementation libs.androidx.annotation

    testImplementation libs.junit
}
"""

TEMPLATE_API_BUILD = """\
// :feature:@NAME@:api —— @NAME@ 域对外契约（脚手架生成，api/impl 拆分）。
// 路由常量 @CLASS@Route + 网络接口 @CLASS@ServiceApi；零 Hilt 运行时、零 feature 依赖（铁律 1/2）。
apply from: "$rootDir/config/android-library.gradle"
apply from: "$rootDir/config/serialization.gradle"

android {
    namespace 'com.smartwash.feature.@NAME@.api'
}

dependencies {
    implementation libs.kotlinx.coroutines.core
    implementation libs.androidx.annotation
    // SmartWashRoute 类型安全路由契约 + ApiResult 响应信封
    implementation project(':common:utils')
    // @RequireAuthorization 注解（retrofit 经其 api 传递）
    implementation project(':common:network')
}
"""

TEMPLATE_IMPL_BUILD = """\
// :feature:@NAME@:impl —— @NAME@ 域实现（脚手架生成，api/impl 拆分）。
// 包名保持 com.smartwash.feature.@NAME@（与模块路径解耦，同 coupon/points 先例）。
apply from: "$rootDir/config/android-library.gradle"
apply from: "$rootDir/config/compose.gradle"
apply from: "$rootDir/config/hilt.gradle"

android {
    namespace 'com.smartwash.feature.@NAME@'
}

dependencies {
    implementation project(':feature:@NAME@:api')

    implementation project(':common:utils')
    implementation project(':common:network')
    implementation project(':common:ui')

    implementation libs.androidx.lifecycle.viewmodel
    implementation libs.androidx.hilt.navigation.compose
    implementation libs.androidx.navigation.compose
    implementation libs.androidx.compose.foundation
    implementation libs.kotlinx.coroutines.android

    testImplementation libs.junit
}
"""

TEMPLATE_COMMON_BUILD = """\
// :common:@NAME@ —— 通用能力（脚手架生成）。
// 按需追加 convention（见 config/）：compose / hilt / serialization。
apply from: "$rootDir/config/android-library.gradle"
@EXTRA_APPLY@

android {
    namespace 'com.smartwash.common.@NAME@'
}

dependencies {
    // TODO: 声明依赖（common → common:init 为铁律 1 合法方向）
}
"""


# ============================================================
#  模板: 源码
# ============================================================

TEMPLATE_MANIFEST = """\
<?xml version="1.0" encoding="utf-8"?>
<!-- @NAME@ 模块（脚手架生成）：无组件声明，manifest merger 合入宿主。 -->
<manifest />
"""

TEMPLATE_ROUTE = """\
package @PACKAGE@

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @NAME@ 域类型安全路由（模块内单一事实来源）。
 * 深链 host "@HOST@" 的落点 = @CLASS@Route.Home。
 */
sealed interface @CLASS@Route : SmartWashRoute {

    /** @NAME@ 主页 */
    @Serializable
    @SerialName("@CLASS@Home")
    data object Home : @CLASS@Route
}
"""

TEMPLATE_SERVICE_API = """\
package @PACKAGE@

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import retrofit2.http.GET

/**
 * @NAME@ 域网络接口（契约源：后端 controller/web/ 对应 Controller）。
 * 需登录接口加 @RequireAuthorization；返回统一 ApiResult<T>。
 */
interface @CLASS@ServiceApi {

    // TODO: 按后端接口补齐。示例：
    // @GET("/web/auth/@NAME@/xxx")
    // @RequireAuthorization
    // suspend fun getXxx(): ApiResult<XxxVo>
}
"""

TEMPLATE_GRAPH = """\
package @UI_PACKAGE@

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import @API_PACKAGE@.@CLASS@Route
import @PAGE_PACKAGE@.@CLASS@Page

/**
 * @NAME@ 域路由聚合：app 壳 MainActivity NavHost 一行 [@NAME_GRAPH@] 注册。
 */
fun NavGraphBuilder.@NAME_GRAPH@(navController: NavHostController) {
    composable<@CLASS@Route.Home> { @CLASS@Page(navController) }
}
"""

TEMPLATE_HILT_MODULE = """\
package @DI_PACKAGE@

import @API_PACKAGE@.@CLASS@ServiceApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * @NAME@ 域网络供给：Retrofit 实例由 common:network 的 NetworkModule 提供。
 */
@Module
@InstallIn(SingletonComponent::class)
object @CLASS@ApiModule {

    @Provides
    @Singleton
    fun provide@CLASS@ServiceApi(retrofit: Retrofit): @CLASS@ServiceApi =
        retrofit.create(@CLASS@ServiceApi::class.java)
}
"""

TEMPLATE_DEEPLINK_MODULE = """\
package @DI_PACKAGE@

import com.smartwash.common.utils.deeplink.DeepLinkContributor
import com.smartwash.common.utils.deeplink.DeepLinkRouteFactory
import @API_PACKAGE@.@CLASS@Route
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * @NAME@ 域深链贡献者（@IntoSet 汇入壳层 DeepLinkRegistry）。
 * host "@HOST@" → @CLASS@Route.Home
 */
@Module
@InstallIn(SingletonComponent::class)
object @CLASS@DeepLinkModule {

    @Provides
    @IntoSet
    fun provide@CLASS@DeepLinkContributor(): DeepLinkContributor = DeepLinkContributor {
        mapOf(
            "@HOST@" to DeepLinkRouteFactory { _, _ -> @CLASS@Route.Home },
        )
    }
}
"""

TEMPLATE_REPOSITORY = """\
package @REPO_PACKAGE@

import @API_PACKAGE@.@CLASS@ServiceApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @NAME@ 域仓库（脚手架骨架）：封装 @CLASS@ServiceApi 调用与领域逻辑。
 */
interface @CLASS@Repository

@Singleton
class @CLASS@RepositoryImpl @Inject constructor(
    private val api: @CLASS@ServiceApi,
) : @CLASS@Repository {
    // TODO: 实现领域方法（api 为 Retrofit 契约实例）
}
"""

TEMPLATE_VIEWMODEL = """\
package @PAGE_PACKAGE@

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * @NAME@ 页 ViewModel（脚手架骨架）。
 * 异步状态统一 RequestState + StateFlow（见 AGENTS.md「核心架构模式」）。
 */
@HiltViewModel
class @CLASS@ViewModel @Inject constructor() : ViewModel() {
    // TODO: 注入 Repository，暴露 StateFlow<RequestState<...>>
}
"""

TEMPLATE_PAGE = """\
package @PAGE_PACKAGE@

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import @RS_PACKAGE@.R

/**
 * @NAME@ 主页（脚手架骨架，TODO 替换为真实实现）。
 * 文案一律走 strings.xml（规范 §9）。
 */
@Composable
fun @CLASS@Page(navController: NavHostController) {
    // TODO: val viewModel: @CLASS@ViewModel = hiltViewModel()
    Text(text = stringResource(R.string.@NAME@_title))
}
"""

TEMPLATE_STRINGS = """\
<?xml version="1.0" encoding="utf-8"?>
<!-- @NAME@ 域文案（脚手架生成，TODO 按页面补全）。 -->
<resources>
    <string name="@NAME@_title">TODO 页面标题</string>
</resources>
"""

TEMPLATE_COMMON_SOURCE = """\
package com.smartwash.common.@NAME@

/**
 * @NAME@ 通用能力入口（脚手架骨架，TODO 替换为真实实现）。
 */
object @CLASS@ {
    const val TAG = "@CLASS@"
}
"""


# ============================================================
#  模板: AGENTS.md
# ============================================================

TEMPLATE_AGENTS_SINGLE = """\
# AGENTS.md — :feature:@NAME@（@NAME@ 域）

> 脚手架生成骨架：实现完成后请补全本文件并删除本行提示。

## 1. 模块身份

- **Gradle 坐标**: `:feature:@NAME@`
- **包名**: `com.smartwash.feature.@NAME@`
- **职责**: TODO（一句话说明域职责）
- **入口**: 深链 `smartwash://@HOST@`（`di/@CLASS@DeepLinkModule` 经 `@IntoSet` 贡献）

## 2. 包结构

```
com.smartwash.feature.@NAME@/
├── @CLASS@Route.kt              # 路由常量（@CLASS@Home）
├── @CLASS@ServiceApi.kt         # Retrofit 网络接口
├── di/
│   ├── @CLASS@Module.kt         # Hilt 网络供给
│   └── @CLASS@DeepLinkModule.kt # 深链 host "@HOST@"
├── repository/@CLASS@Repository.kt
└── ui/
    ├── @NAME_GRAPH@.kt          # NavGraphBuilder 扩展
    └── @NAME@/@CLASS@Page.kt + @CLASS@ViewModel.kt
```

## 3. 依赖关系

- **此模块依赖**: `:common:utils` / `:common:network` / `:common:ui`
- **谁依赖此模块**: `:app`（NavHost 注册 + `@NAME_GRAPH@` 聚合）

## 4. 内部约定

- 路由常量 `@CLASS@Route` 为单一事实来源；需登录接口加 `@RequireAuthorization`。
- 异步状态统一 `RequestState` + `StateFlow`；用户可见文案一律 `strings.xml`。
- 遵循 `.claude/ui-design-spec.md` 令牌/间距/圆角/动效（含 reduced motion 分支）。

## 5. 已知坑

- TODO
"""

TEMPLATE_AGENTS_API = """\
# AGENTS.md — :feature:@NAME@:api

> 脚手架生成骨架：实现完成后请补全本文件并删除本行提示。

## 1. 模块身份

- **Gradle 坐标**: `:feature:@NAME@:api`
- **包名**: `com.smartwash.feature.@NAME@.api`
- **职责**: `@NAME@` 域对外契约——`@CLASS@Route` 路由常量 + `@CLASS@ServiceApi` 网络接口。

## 2. 包结构

```
com.smartwash.feature.@NAME@.api/
├── @CLASS@Route.kt
└── @CLASS@ServiceApi.kt
```

## 3. 依赖关系

- **此模块依赖**: `:common:utils` + `:common:network`（铁律：api 不依赖任何 feature）
- **谁依赖此模块**: `:feature:@NAME@:impl`（域内实现）、`:app`（NavHost 注册）

## 4. 内部约定

- 零 Hilt 运行时、零 Compose；仅契约与模型。
- 需登录接口加 `@RequireAuthorization`；返回统一 `ApiResult<T>`。

## 5. 已知坑

- TODO
"""

TEMPLATE_AGENTS_IMPL = """\
# AGENTS.md — :feature:@NAME@:impl

> 脚手架生成骨架：实现完成后请补全本文件并删除本行提示。

## 1. 模块身份

- **Gradle 坐标**: `:feature:@NAME@:impl`
- **包名**: `com.smartwash.feature.@NAME@`（与模块路径解耦，同 coupon/points 先例）
- **职责**: `@NAME@` 域实现——仓库 + 页面 + 深链贡献。

## 2. 包结构

```
com.smartwash.feature.@NAME@/
├── di/
│   ├── @CLASS@Module.kt         # Hilt 网络供给
│   └── @CLASS@DeepLinkModule.kt # 深链 host "@HOST@"
├── repository/@CLASS@Repository.kt
└── ui/
    ├── @NAME_GRAPH@.kt          # NavGraphBuilder 扩展
    └── @NAME@/@CLASS@Page.kt + @CLASS@ViewModel.kt
```

## 3. 依赖关系

- **此模块依赖**: `:feature:@NAME@:api` + `:common:utils` / `:common:network` / `:common:ui`
- **谁依赖此模块**: `:app`（NavHost 注册 + `@NAME_GRAPH@` 聚合）

## 4. 内部约定

- 路由常量 `@CLASS@Route` 为单一事实来源；需登录接口加 `@RequireAuthorization`。
- 异步状态统一 `RequestState` + `StateFlow`；用户可见文案一律 `strings.xml`。
- 遵循 `.claude/ui-design-spec.md` 令牌/间距/圆角/动效（含 reduced motion 分支）。

## 5. 已知坑

- TODO
"""

TEMPLATE_AGENTS_COMMON = """\
# AGENTS.md — :common:@NAME@

> 脚手架生成骨架：实现完成后请补全本文件并删除本行提示。

## 1. 模块身份

- **Gradle 坐标**: `:common:@NAME@`
- **包名**: `com.smartwash.common.@NAME@`
- **职责**: TODO（通用能力，禁止放业务模型）

## 2. 包结构

```
com.smartwash.common.@NAME@/
└── @CLASS@.kt
```

## 3. 依赖关系

- **此模块依赖**: 仅 `common` 互相依赖与 `:common:init`（铁律 1/4：common 不依赖 feature）
- **谁依赖此模块**: 各 `feature` 域 impl

## 4. 内部约定

- 只放真共享物（工具/契约）；业务模型跟各自 `feature:<域>:api`。

## 5. 已知坑

- TODO
"""


# ============================================================
#  创建: feature（单模块 / api+impl）
# ============================================================

def create_feature_single(name: str, host: str, force: bool, app_register: bool) -> None:
    module_dir = PROJECT_ROOT / "feature" / name
    package = "com.smartwash.feature.%s" % name
    class_name = to_class_name(name)
    graph_name = "%sGraph" % name

    print("\n[创建 feature 单模块] feature/%s" % name)
    print("  包名: %s" % package)
    print("  深链 host: %s" % host)

    ensure_module_dir_available(module_dir, "feature")

    src = module_dir / "src" / "main" / "java" / package_path(package)
    res = module_dir / "src" / "main" / "res" / "values"

    mapping = {
        "NAME": name, "CLASS": class_name, "HOST": host,
        "PACKAGE": package, "API_PACKAGE": package,
        "DI_PACKAGE": package + ".di",
        "UI_PACKAGE": package + ".ui",
        "REPO_PACKAGE": package + ".repository",
        "PAGE_PACKAGE": package + ".ui." + name,
        "NAME_GRAPH": graph_name, "RS_PACKAGE": package,
        "EXTRA_APPLY": "",
    }

    write_file(module_dir / "build.gradle",
               render(TEMPLATE_FEATURE_BUILD, mapping), force=force)
    write_file(module_dir / "src" / "main" / "AndroidManifest.xml",
               render(TEMPLATE_MANIFEST, mapping), force=force)
    write_file(src / ("%sRoute.kt" % class_name),
               render(TEMPLATE_ROUTE, mapping), force=force)
    write_file(src / ("%sServiceApi.kt" % class_name),
               render(TEMPLATE_SERVICE_API, mapping), force=force)
    write_file(src / "di" / ("%sModule.kt" % class_name),
               render(TEMPLATE_HILT_MODULE, mapping), force=force)
    write_file(src / "di" / ("%sDeepLinkModule.kt" % class_name),
               render(TEMPLATE_DEEPLINK_MODULE, mapping), force=force)
    write_file(src / "repository" / ("%sRepository.kt" % class_name),
               render(TEMPLATE_REPOSITORY, mapping), force=force)
    write_file(src / "ui" / ("%sGraph.kt" % name),
               render(TEMPLATE_GRAPH, mapping), force=force)
    write_file(src / "ui" / name / ("%sPage.kt" % class_name),
               render(TEMPLATE_PAGE, mapping), force=force)
    write_file(src / "ui" / name / ("%sViewModel.kt" % class_name),
               render(TEMPLATE_VIEWMODEL, mapping), force=force)
    write_file(res / "strings.xml",
               render(TEMPLATE_STRINGS, mapping), force=force)
    write_file(module_dir / "AGENTS.md",
               render(TEMPLATE_AGENTS_SINGLE, mapping), force=force)

    register_in_settings([":feature:%s" % name])
    if app_register:
        register_in_app(":feature:%s" % name,
                        "%s 域（脚手架生成）：TODO 补注释；经 MainActivity NavHost 一行 %s 聚合"
                        % (name, graph_name))
    git_add_files(CREATED_FILES)
    print("[完成] feature/%s 单模块创建成功" % name)


def create_feature_split(name: str, host: str, force: bool, app_register: bool) -> None:
    module_dir = PROJECT_ROOT / "feature" / name
    api_package = "com.smartwash.feature.%s.api" % name
    impl_package = "com.smartwash.feature.%s" % name
    class_name = to_class_name(name)
    graph_name = "%sGraph" % name

    print("\n[创建 feature api/impl 拆分] feature/%s/{api,impl}" % name)
    print("  api 包名: %s" % api_package)
    print("  impl 包名: %s" % impl_package)
    print("  深链 host: %s" % host)

    ensure_module_dir_available(module_dir, "feature")

    api_dir = module_dir / "api"
    impl_dir = module_dir / "impl"

    api_src = api_dir / "src" / "main" / "java" / package_path(api_package)
    impl_src = impl_dir / "src" / "main" / "java" / package_path(impl_package)
    impl_res = impl_dir / "src" / "main" / "res" / "values"

    api_mapping = {
        "NAME": name, "CLASS": class_name, "HOST": host,
        "PACKAGE": api_package, "API_PACKAGE": api_package,
        "DI_PACKAGE": api_package + ".di",
        "UI_PACKAGE": api_package + ".ui",
        "REPO_PACKAGE": api_package + ".repository",
        "PAGE_PACKAGE": api_package + ".ui",
        "NAME_GRAPH": graph_name, "RS_PACKAGE": impl_package,
        "EXTRA_APPLY": "",
    }
    impl_mapping = {
        "NAME": name, "CLASS": class_name, "HOST": host,
        "PACKAGE": impl_package, "API_PACKAGE": api_package,
        "DI_PACKAGE": impl_package + ".di",
        "UI_PACKAGE": impl_package + ".ui",
        "REPO_PACKAGE": impl_package + ".repository",
        "PAGE_PACKAGE": impl_package + ".ui." + name,
        "NAME_GRAPH": graph_name, "RS_PACKAGE": impl_package,
        "EXTRA_APPLY": "",
    }

    # ---- api ----
    write_file(api_dir / "build.gradle",
               render(TEMPLATE_API_BUILD, api_mapping), force=force)
    write_file(api_dir / "src" / "main" / "AndroidManifest.xml",
               render(TEMPLATE_MANIFEST, api_mapping), force=force)
    write_file(api_src / ("%sRoute.kt" % class_name),
               render(TEMPLATE_ROUTE, api_mapping), force=force)
    write_file(api_src / ("%sServiceApi.kt" % class_name),
               render(TEMPLATE_SERVICE_API, api_mapping), force=force)
    write_file(api_dir / "AGENTS.md",
               render(TEMPLATE_AGENTS_API, api_mapping), force=force)

    # ---- impl ----
    write_file(impl_dir / "build.gradle",
               render(TEMPLATE_IMPL_BUILD, impl_mapping), force=force)
    write_file(impl_dir / "src" / "main" / "AndroidManifest.xml",
               render(TEMPLATE_MANIFEST, impl_mapping), force=force)
    write_file(impl_src / "di" / ("%sModule.kt" % class_name),
               render(TEMPLATE_HILT_MODULE, impl_mapping), force=force)
    write_file(impl_src / "di" / ("%sDeepLinkModule.kt" % class_name),
               render(TEMPLATE_DEEPLINK_MODULE, impl_mapping), force=force)
    write_file(impl_src / "repository" / ("%sRepository.kt" % class_name),
               render(TEMPLATE_REPOSITORY, impl_mapping), force=force)
    write_file(impl_src / "ui" / ("%sGraph.kt" % name),
               render(TEMPLATE_GRAPH, impl_mapping), force=force)
    write_file(impl_src / "ui" / name / ("%sPage.kt" % class_name),
               render(TEMPLATE_PAGE, impl_mapping), force=force)
    write_file(impl_src / "ui" / name / ("%sViewModel.kt" % class_name),
               render(TEMPLATE_VIEWMODEL, impl_mapping), force=force)
    write_file(impl_res / "strings.xml",
               render(TEMPLATE_STRINGS, impl_mapping), force=force)
    write_file(impl_dir / "AGENTS.md",
               render(TEMPLATE_AGENTS_IMPL, impl_mapping), force=force)

    register_in_settings([":feature:%s:api" % name, ":feature:%s:impl" % name])
    if app_register:
        register_in_app(":feature:%s:impl" % name,
                        "%s 域实现（脚手架生成）：TODO 补注释；经 MainActivity NavHost 一行 %s 聚合"
                        % (name, graph_name))
    git_add_files(CREATED_FILES)
    print("[完成] feature/%s api/impl 拆分创建成功" % name)


# ============================================================
#  创建: common
# ============================================================

def create_common(name: str, force: bool, with_compose: bool,
                  with_hilt: bool, with_serialization: bool) -> None:
    module_dir = PROJECT_ROOT / "common" / name
    package = "com.smartwash.common.%s" % name
    class_name = to_class_name(name)

    print("\n[创建 common 模块] common/%s" % name)
    print("  包名: %s" % package)

    ensure_module_dir_available(module_dir, "common")

    extra_apply = ""
    if with_compose:
        extra_apply += 'apply from: "$rootDir/config/compose.gradle"\n'
    if with_hilt:
        extra_apply += 'apply from: "$rootDir/config/hilt.gradle"\n'
    if with_serialization:
        extra_apply += 'apply from: "$rootDir/config/serialization.gradle"\n'
    extra_apply = extra_apply.rstrip("\n")

    mapping = {
        "NAME": name, "CLASS": class_name, "EXTRA_APPLY": extra_apply,
        "PACKAGE": package,
    }

    write_file(module_dir / "build.gradle",
               render(TEMPLATE_COMMON_BUILD, mapping), force=force)
    write_file(module_dir / "src" / "main" / "java" / package_path(package)
               / ("%s.kt" % class_name),
               render(TEMPLATE_COMMON_SOURCE, mapping), force=force)
    write_file(module_dir / "AGENTS.md",
               render(TEMPLATE_AGENTS_COMMON, mapping), force=force)

    register_in_settings([":common:%s" % name])
    git_add_files(CREATED_FILES)
    print("[完成] common/%s 模块创建成功" % name)


# ============================================================
#  删除
# ============================================================

def _delete_dir_checked(module_dir: Path) -> None:
    if not module_dir.exists():
        print("  目录不存在: %s" % module_dir.relative_to(PROJECT_ROOT))
        return
    if DRY_RUN:
        print("  [dry-run] 将删除目录: %s" % module_dir.relative_to(PROJECT_ROOT))
        return
    git_rm_files([module_dir])
    shutil.rmtree(module_dir)
    print("  已删除目录: %s" % module_dir.relative_to(PROJECT_ROOT))


def delete_feature(name: str) -> None:
    module_dir = PROJECT_ROOT / "feature" / name
    print("\n[删除 feature] feature/%s" % name)
    # 先判定结构（删除后 api/impl 子目录不复存在）
    is_split = (module_dir / "api").exists() or (module_dir / "impl").exists()
    _delete_dir_checked(module_dir)
    if is_split:
        unregister_from_settings([":feature:%s:api" % name, ":feature:%s:impl" % name])
        unregister_from_app(":feature:%s:impl" % name)
    else:
        unregister_from_settings([":feature:%s" % name])
        unregister_from_app(":feature:%s" % name)
    print("[完成] feature/%s 已删除" % name)


def delete_common(name: str) -> None:
    module_dir = PROJECT_ROOT / "common" / name
    print("\n[删除 common] common/%s" % name)
    _delete_dir_checked(module_dir)
    unregister_from_settings([":common:%s" % name])
    print("[完成] common/%s 已删除" % name)


# ============================================================
#  主函数
# ============================================================

def main() -> None:
    global DRY_RUN

    parser = argparse.ArgumentParser(
        description="SmartWash-Android 模块脚手架",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python scripts/scaffold-module.py --type feature --name game
  python scripts/scaffold-module.py --type feature --name order --split
  python scripts/scaffold-module.py --type common --name util --with-compose
  python scripts/scaffold-module.py --type feature --name game --delete --yes
  python scripts/scaffold-module.py --type feature --name demo --dry-run
        """,
    )
    parser.add_argument("--type", required=True, choices=["common", "feature"],
                        help="模块类型: common(通用库) / feature(业务域)")
    parser.add_argument("--name", required=True, help="模块名，如 game / order / util")
    parser.add_argument("--split", action="store_true", default=False,
                        help="feature 拆分为 api/impl（默认单模块）")
    parser.add_argument("--host", default=None,
                        help="深链 host（默认等于模块名；落点 = {Class}Route.Home）")
    parser.add_argument("--no-app-register", action="store_true", default=False,
                        help="不向 app/build.gradle 注入实现模块依赖（默认注入）")
    parser.add_argument("--with-compose", action="store_true", default=False, help="common: 追加 compose convention")
    parser.add_argument("--with-hilt", action="store_true", default=False, help="common: 追加 hilt convention")
    parser.add_argument("--with-serialization", action="store_true", default=False, help="common: 追加 serialization convention")
    parser.add_argument("--delete", action="store_true", default=False, help="删除模块")
    parser.add_argument("--force", action="store_true", default=False, help="目标文件已存在时允许覆盖")
    parser.add_argument("--yes", action="store_true", default=False, help="删除时跳过交互确认")
    parser.add_argument("--dry-run", action="store_true", default=False, help="只打印不落盘")

    args = parser.parse_args()
    DRY_RUN = args.dry_run

    if not re.match(r"^[a-z][a-z0-9_-]*$", args.name):
        print("[ERROR] 模块名格式不合法: %s" % args.name)
        print("  规则: 小写字母开头，只包含小写字母、数字、下划线、连字符")
        sys.exit(1)

    if not SETTINGS.exists():
        print("[ERROR] 找不到项目根目录（settings.gradle）: %s" % SETTINGS)
        sys.exit(1)

    if args.split and args.type != "feature":
        print("[ERROR] --split 仅适用于 --type feature")
        sys.exit(1)

    host = args.host or args.name

    print("=" * 60)
    print("  SmartWash-Android 模块脚手架")
    print("=" * 60)
    print("  类型: %s%s" % (args.type, "（api/impl 拆分）" if args.split else ""))
    print("  名称: %s" % args.name)
    print("  host: %s" % host)
    print("  操作: %s" % ("删除" if args.delete else "创建"))
    if DRY_RUN:
        print("  模式: dry-run（不落盘、不改 git）")

    if args.delete:
        if not args.yes:
            answer = input("确认删除 %s/%s？[y/N] " % (args.type, args.name))
            if answer.strip().lower() not in ("y", "yes"):
                print("已取消删除")
                return
        if args.type == "feature":
            delete_feature(args.name)
        else:
            delete_common(args.name)
    else:
        if args.type == "feature":
            if args.split:
                create_feature_split(args.name, host, args.force, not args.no_app_register)
            else:
                create_feature_single(args.name, host, args.force, not args.no_app_register)
        else:
            create_common(args.name, args.force, args.with_compose,
                          args.with_hilt, args.with_serialization)
        print_manual_steps(args)

    print("\n" + "=" * 60)
    print("  全部完成！")
    print("=" * 60)


def print_manual_steps(args) -> None:
    """打印脚手架无法代劳的收尾步骤"""
    if args.type != "feature":
        return
    graph = "%sGraph" % args.name
    print("\n[后续手工步骤]")
    print("  1. app/src/main/java/com/smartwash/ui/activity/MainActivity.kt:")
    print("     - import com.smartwash.feature.%s.ui.%s" % (args.name, graph))
    print("     - NavHost 内加一行: %s(navController)" % graph)
    print("  2. 与后端对齐 ServiceApi 的路径/参数/返回（返回统一 ApiResult<T>）。")
    print("  3. 补全各模块 AGENTS.md（职责/依赖/已知坑）。")
    print("  4. 运行 ./scripts/check-deps.sh 校验依赖铁律。")


if __name__ == "__main__":
    main()
