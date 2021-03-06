package io.jentz.winter.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import javax.lang.model.element.TypeElement

class ComponentModel {
    val factories = mutableListOf<FactoryModel>()
    val injectors = mutableMapOf<TypeElement, InjectorModel>()

    fun generate(packageName: String, generatedAnnotationAvailable: Boolean): FileSpec {
        val grouped = factories.groupBy { it.scope ?: "__prototype__" }

        val componentBuilder = CodeBlock.builder().beginControlFlow("component")

        injectors.forEach { (_, injector) ->
            val code = CodeBlock.of(
                "membersInjector<%T> { %T() }\n",
                injector.typeName,
                escapeGeneratedClassName(injector.generatedClassName)
            )
            componentBuilder.add(code)
        }

        grouped.forEach { (scope, factories) ->
            when (scope) {
                "__prototype__" -> {
                    factories.forEach { componentBuilder.add(generatePrototype(it)) }
                }
                "javax.inject.Singleton" -> {
                    factories.forEach { componentBuilder.add(generateSingleton(it)) }
                }
                else -> {
                    componentBuilder
                        .beginControlFlow("subcomponent($scope::class)")
                        .also { subcomponentBlock ->
                            factories.forEach { subcomponentBlock.add(generateSingleton(it)) }
                        }
                        .endControlFlow()
                }
            }
        }

        componentBuilder.endControlFlow()

        return FileSpec.builder(packageName, "generatedComponent")
            .addStaticImport("io.jentz.winter", "component")
            .addProperty(
                PropertySpec.builder("generatedComponent", COMPONENT_CLASS_NAME)
                    .also {
                        if (generatedAnnotationAvailable) {
                            it.addAnnotation(generatedAnnotation())
                        } else {
                            it.addKdoc(generatedComment())
                        }
                    }
                    .initializer(componentBuilder.build())
                    .build()
            )
            .build()
    }

    fun isEmpty() = factories.isEmpty() && injectors.isEmpty()

    private fun generatePrototype(model: FactoryModel) = CodeBlock.of(
        "prototype<%T> { %T().invoke(this) }\n",
        model.typeName,
        escapeGeneratedClassName(model.generatedClassName)
    )

    private fun generateSingleton(model: FactoryModel) = CodeBlock.of(
        "singleton<%T> { %T().invoke(this) }\n",
        model.typeName,
        escapeGeneratedClassName(model.generatedClassName)
    )

    private fun escapeGeneratedClassName(className: ClassName) =
        ClassName(className.packageName(), "`${className.simpleName()}`")

}
