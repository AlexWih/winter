package io.jentz.winter.compiler

import com.squareup.kotlinpoet.FileSpec
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Scope
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.tools.Diagnostic

class WinterProcessor : AbstractProcessor() {

    private var generatedComponentPackage: String? = null
    private lateinit var componentModel: ComponentModel

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        generatedComponentPackage = processingEnv.options[optionGeneratedComponentPackage]
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(Inject::class.java.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun getSupportedOptions(): Set<String> = setOf(optionGeneratedComponentPackage)

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (generatedComponentPackage.isNullOrBlank()) {
            warn("Skipping annotation processing: Package to generate component to is not configured. Set option `$optionGeneratedComponentPackage`")
            return true
        }

        componentModel = ComponentModel()

        roundEnv.getElementsAnnotatedWith(Inject::class.java).forEach { element ->
            try {
                when (element.kind) {
                    ElementKind.CONSTRUCTOR -> {
                        val execuatable = element as ExecutableElement
                        componentModel.factories += FactoryModel(execuatable)
                    }
                    ElementKind.FIELD -> {
                        val field = element as VariableElement
                        field.getAnnotationsByType(Named::class.java)
                        getOrCreateInjector(element).targets += InjectTargetModel.FieldInjectTarget(field)
                    }
                    ElementKind.METHOD -> {
                        val method = element as ExecutableElement
                        getOrCreateInjector(element).targets += InjectTargetModel.SetterInjectTarget(method)
                    }
                    else -> {
                        error(element, "Inject annotation is only supported for constructor, method or field.")
                        return true
                    }
                }
            } catch (t: Throwable) {
                error(element, t.message ?: "Unknown error")
                return true
            }
        }

        if (componentModel.isEmpty()) return true

        buildInjectors()
        buildFactories()
        buildRegistry()

        return true
    }

    private fun buildInjectors() {
        componentModel.injectors.forEach { (_, injector) ->
            info("Create injector for ${injector.typeName}")
            val kCode = injector.generate()
            info(kCode.toString())
            write(kCode)
        }
    }

    private fun buildFactories() {
        componentModel.factories.forEach { factory ->
            info("Create factory for ${factory.typeName}")
            val kCode = factory.generate(componentModel.injectors[factory.typeElement])
            info(kCode.toString())
            write(kCode)
        }
    }

    private fun buildRegistry() {
        val kCode = componentModel.generate(generatedComponentPackage!!)
        info(kCode.toString())
        write(kCode)
    }

    private fun write(fileSpec: FileSpec) {
        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        val file = File(kaptKotlinGeneratedDir)
        fileSpec.writeTo(file)
    }

    private fun getOrCreateInjector(fieldOrSetter: Element): InjectorModel {
        val typeElement = fieldOrSetter.enclosingElement as? TypeElement ?: throw IllegalArgumentException("Enclosing constructor for $fieldOrSetter must be a class")
        return componentModel.injectors.getOrPut(typeElement) { InjectorModel(typeElement) }
    }

    private fun info(element: Element, message: String, vararg args: Any) {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, String.format(message, *args), element)
    }

    private fun info(message: String, vararg args: Any) {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, String.format(message, *args))
    }

    private fun warn(message: String, vararg args: Any) {
        processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, String.format(message, *args))
    }

    private fun error(element: Element, message: String, vararg args: Any) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, String.format(message, *args), element)
    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }

}