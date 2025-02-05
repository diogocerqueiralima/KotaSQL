package com.github.diogocerqueiralima.kotasql.processors

import com.github.diogocerqueiralima.kotasql.annotations.Database
import com.google.devtools.ksp.getDeclaredFunctions
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

class DatabaseProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Database::class.java.name)
        val ret = symbols.filter { !it.validate() }.toList()

        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(DatabaseVisitor(), Unit) }

        return ret
    }

    inner class DatabaseVisitor : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            logger.info("Processing ${classDeclaration.simpleName.asString()}")

            val packageName = classDeclaration.packageName.asString()
            val className = "${classDeclaration.simpleName.asString()}Impl"
            val fileBuilder = FileSpec.builder(packageName, className)
                .addImport("java.io", "File")
            val typeSpec = TypeSpec.classBuilder(className)
                .superclass(classDeclaration.toClassName())
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("url", String::class)
                        .addParameter("username", String::class)
                        .addParameter("password", String::class)
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("dataSource", ClassName("com.zaxxer.hikari", "HikariDataSource"))
                        .initializer("HikariDataSource()")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )
                .addInitializerBlock(
                    CodeBlock.builder()
                        .addStatement("dataSource.jdbcUrl = url")
                        .addStatement("dataSource.username = username")
                        .addStatement("dataSource.password = password")
                        .addStatement("dataSource.driverClassName = \"org.postgresql.Driver\"")
                        .addStatement("initializeDatabase()")
                        .build()
                )
                .addFunctions(
                    classDeclaration.getDeclaredFunctions()
                        .filter { it.simpleName.asString() != "<init>" }
                        .map { logger.info("Processing: ${it.simpleName.asString()} function"); getFunctionImplementation(it) }
                        .toList()
                ).addFunction(initializeDatabase())
                .build()

            fileBuilder.addType(typeSpec)

            val file = fileBuilder.build()
            file.writeTo(codeGenerator, true)
        }

        private fun getFunctionImplementation(function: KSFunctionDeclaration) =
            FunSpec.builder(function.simpleName.asString())
                .addModifiers(KModifier.OVERRIDE)
                .returns(function.returnType?.toTypeName() ?: Unit::class.asClassName())
                .addStatement("return ${function.returnType?.resolve()?.toString()}Impl(dataSource)")
                .build()

        private fun initializeDatabase() =
            FunSpec.builder("initializeDatabase")
                .addModifiers(KModifier.PRIVATE)
                .addCode(
                    """
                        |   
                        |   val folder = this.javaClass.classLoader.getResource("generated/sql")
                        |   val files = File(folder.toURI()).listFiles()
                        |   
                        |   files
                        |       .filter { it.extension == "sql" }
                        |       .forEach { file -> 
                        |           
                        |           val command = file.readText()
                        |           
                        |           dataSource.getConnection().use { connection ->
                        |           
                        |               connection.prepareStatement(command).use { preparedStatement ->
                        |               
                        |                   preparedStatement.executeUpdate()
                        |               
                        |               }
                        |           
                        |           }
                        |           
                        |       }
                        |
                    """.trimMargin()
                )
                .build()

    }

}

class DatabaseProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment) =
        DatabaseProcessor(environment.codeGenerator, environment.logger)

}
