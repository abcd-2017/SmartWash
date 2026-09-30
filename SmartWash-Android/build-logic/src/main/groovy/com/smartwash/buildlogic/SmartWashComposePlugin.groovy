package com.smartwash.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Compose 模块配置：kotlin compose 编译器插件 + buildFeatures + Compose BOM 与基础依赖。
 *
 * BOM 版本（2025.03.00）与依赖坐标取自主项目 libs.versions.toml（版本单一来源）。
 * navigation-compose、material-icons-extended 等非通用依赖由各模块自行声明。
 *
 * 注意：插件类中嵌套 DSL 用具名对象点式赋值，不用闭包（闭包 delegate 不指向扩展对象）。
 */
class SmartWashComposePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply('org.jetbrains.kotlin.plugin.compose')

        // CommonExtension 为 library / application 共同父接口，两类模块均可使用本插件
        def android = project.extensions.getByType(CommonExtension)
        android.buildFeatures.compose = true

        def libs = project.extensions.getByType(VersionCatalogsExtension).named('libs')
        def bom = libs.findLibrary('androidx-compose-bom').get()
        def dependencies = project.dependencies
        dependencies.add('implementation', dependencies.platform(bom))
        dependencies.add('androidTestImplementation', dependencies.platform(bom))
        dependencies.add('implementation', libs.findLibrary('androidx-ui').get())
        dependencies.add('implementation', libs.findLibrary('androidx-ui-graphics').get())
        dependencies.add('implementation', libs.findLibrary('androidx-ui-tooling-preview').get())
        dependencies.add('implementation', libs.findLibrary('androidx-material3').get())
        dependencies.add('debugImplementation', libs.findLibrary('androidx-ui-tooling').get())
        dependencies.add('debugImplementation', libs.findLibrary('androidx-ui-test-manifest').get())
    }
}
