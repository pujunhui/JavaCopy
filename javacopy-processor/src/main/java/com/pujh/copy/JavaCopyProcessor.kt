package com.pujh.copy

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

class JavaCopyProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
    private val options = environment.options
    private val logger = environment.logger
    private val codeGenerator = environment.codeGenerator

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(Copyable::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .forEach(::generateCopyableFile)

        return emptyList()
    }

    private fun generateCopyableFile(
        copyableClass: KSClassDeclaration
    ) {
        val packageName = copyableClass.qualifiedName?.getQualifier().orEmpty()
        val className = copyableClass.simpleName.getShortName()
        val copierClassName = className + "Copier"

        val fileSpec = buildCopyableFile(
            packageName,
            copierClassName,
            copyableClass
        )

        val dependencies = Dependencies(
            aggregating = false,
            copyableClass.containingFile!!
        )
        fileSpec.writeTo(codeGenerator, dependencies)
    }


    private fun buildCopyableFile(
        packageName: String,
        copierClassName: String,
        copyableClass: KSClassDeclaration,
    ): FileSpec = FileSpec
        .builder(packageName, copierClassName)
        .addType(buildCopyableClass(copierClassName, copyableClass))
        .build()

    private fun buildCopyableClass(
        copierClassName: String,
        copyableClass: KSClassDeclaration
    ): TypeSpec = TypeSpec.classBuilder(copierClassName)
        .addSuperinterface(
            ICopier::class.asClassName().parameterizedBy(copyableClass.toClassName())
        )
        .addFunction(buildCopyMethod(copyableClass))
        .build()

    private fun buildCopyMethod(
        copyableClass: KSClassDeclaration
    ): FunSpec = FunSpec.builder("copy")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("old", copyableClass.toClassName())
        .returns(copyableClass.toClassName())
        .addStatement("return old")
        .build()
}