package com.github.diogocerqueiralima.kotasql.processors

import com.github.diogocerqueiralima.kotasql.annotations.Dao
import com.github.diogocerqueiralima.kotasql.annotations.Insert
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class DaoProcessor(

    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger

) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val symbols = resolver.getSymbolsWithAnnotation(Dao::class.java.name)
        val ret = symbols.filter { !it.validate() }.toList()

        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(DaoVisitor(), Unit) }

        return ret
    }

    inner class DaoVisitor : KSVisitorVoid() {

        @OptIn(KspExperimental::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            logger.info("Processing ${classDeclaration.simpleName.asString()}")

            val packageName = classDeclaration.packageName.asString()
            val className = "${classDeclaration.simpleName.asString()}Impl"
            val fileBuilder = FileSpec.builder(packageName, className)
            val typeSpecBuilder = TypeSpec.classBuilder(className)
                .addSuperinterface(classDeclaration.toClassName())
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter(
                            name = "dataSource",
                            type = ClassName("com.zaxxer.hikari", "HikariDataSource")
                        )
                        .build()
                )
                .addProperty(
                    PropertySpec.builder(
                        name = "dataSource",
                        type = ClassName("com.zaxxer.hikari", "HikariDataSource")
                    )
                        .initializer("dataSource")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )
                .addFunctions(
                    classDeclaration.getDeclaredFunctions().map { function ->

                        logger.info("Processing ${function.simpleName.asString()}")

                        if (function.isAnnotationPresent(Insert::class)) {
                            insert(function)
                        }else throw IllegalStateException("There is no SQL operation")

                    }.toList()
                )

            val typeSpec = typeSpecBuilder.build()
            fileBuilder.addType(typeSpec)

            val file = fileBuilder.build()
            file.writeTo(codeGenerator, true)
        }

        private fun insert(function: KSFunctionDeclaration) =
            FunSpec.builder(function.simpleName.asString())
                .addModifiers(KModifier.OVERRIDE)
                .addParameters(
                    function.parameters.map { parameter ->
                        ParameterSpec.builder(parameter.name?.asString() ?: "", parameter.type.toTypeName()).build()
                    }
                )
                .build()

    }

}

class DaoProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment) =
        DaoProcessor(environment.codeGenerator, environment.logger)

}