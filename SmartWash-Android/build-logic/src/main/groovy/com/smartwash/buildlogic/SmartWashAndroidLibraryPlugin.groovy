package com.smartwash.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * android library 模块通用配置，与 app/build.gradle 对齐：
 * compileSdk 35 / minSdk 30 / Java 17 / Kotlin jvmTarget 17。
 *
 * namespace 属于模块差异，由各模块 build.gradle 自行声明；
 * testInstrumentationRunner、lint 等非通用项同样留给模块。
 *
 * 注意：插件类中嵌套 DSL 一律用具名对象点式赋值（getByType 后直接访问嵌套属性），
 * 不用闭包——普通 .groovy 类里闭包 delegate 不会指向扩展对象（区别于 build.gradle 脚本）。
 */
class SmartWashAndroidLibraryPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // 插件本体已在根 build.gradle 声明（apply false），此处按 id 应用到模块
        project.pluginManager.apply('com.android.library')
        project.pluginManager.apply('org.jetbrains.kotlin.android')

        def android = project.extensions.getByType(LibraryExtension)
        android.compileSdk = 35
        android.defaultConfig.minSdk = 30
        android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        android.compileOptions.targetCompatibility = JavaVersion.VERSION_17

        // 与 app 的 kotlinOptions { jvmTarget = '17' } 等价；
        // KGP 后续大版本可迁 compilerOptions DSL
        project.tasks.withType(KotlinCompile).configureEach { task ->
            task.kotlinOptions.jvmTarget = '17'
        }
    }
}
