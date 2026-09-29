package com.smartwash.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Hilt + KSP：应用 dagger.hilt / ksp 插件，并注入 hilt-android 依赖与 KSP 注解处理器。
 *
 * 插件版本（Hilt 2.51 / KSP 2.0.0-1.0.24）由根 build.gradle 管理；
 * 依赖坐标取自主项目 libs.versions.toml。
 * androidx.hilt:hilt-compiler（HiltWorker 等 androidx 扩展的处理器）非通用，由需要的模块自行添加。
 */
class SmartWashHiltPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // 插件本体已在根 build.gradle 声明（apply false），此处按 id 应用到模块
        project.pluginManager.apply('com.google.dagger.hilt.android')
        project.pluginManager.apply('com.google.devtools.ksp')

        def libs = project.extensions.getByType(VersionCatalogsExtension).named('libs')
        // Hilt 注解处理统一走 KSP（与 Room 一致），Hilt 2.51 起官方支持
        project.dependencies.add('implementation', libs.findLibrary('hilt-android').get())
        project.dependencies.add('ksp', libs.findLibrary('hilt-compiler').get())
    }
}
