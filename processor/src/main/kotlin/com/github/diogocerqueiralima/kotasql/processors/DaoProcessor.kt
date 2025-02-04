package com.github.diogocerqueiralima.kotasql.processors

import com.github.diogocerqueiralima.kotasql.annotations.*
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
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
                    classDeclaration.getDeclaredFunctions().map { getImplementation(it) }.toList()
                )

            val typeSpec = typeSpecBuilder.build()
            fileBuilder.addType(typeSpec)

            val file = fileBuilder.build()
            file.writeTo(codeGenerator, true)
        }

        private fun getImplementation(function: KSFunctionDeclaration) =
            FunSpec.builder(function.simpleName.asString())
                .addModifiers(KModifier.OVERRIDE)
                .returns(function.returnType?.toTypeName() ?: Unit::class.asClassName())
                .addParameters(
                    function.parameters.map { parameter ->
                        ParameterSpec.builder(parameter.name?.asString() ?: "", parameter.type.toTypeName()).build()
                    }
                )
                .addCode(getImplementationContent(function))
                .build()

        @OptIn(KspExperimental::class)
        private fun getImplementationContent(function: KSFunctionDeclaration): String {

            if (function.isAnnotationPresent(Insert::class))
                return getInsertImplementationContent(function)

            throw IllegalStateException("There is no SQL operation")
        }

        @OptIn(KspExperimental::class)
        private fun getInsertImplementationContent(function: KSFunctionDeclaration): String {

            val parameters = function.parameters

            if (parameters.size != 1)
                throw IllegalStateException("Insert operation must have exactly one parameter.")

            val parameter = parameters.first()
            val parameterName = parameter.name?.asString()
            val classDeclaration = parameter.type.resolve().declaration as KSClassDeclaration

            if (function.returnType?.resolve() != parameter.type.resolve())
                throw IllegalStateException("Insert operation must return type ${parameter.type}")

            if (!classDeclaration.isAnnotationPresent(Entity::class))
                throw IllegalStateException("You should insert an Entity on database")

            val properties = mutableMapOf<String, String>()
            val tableName = classDeclaration.getAnnotationsByType(Entity::class).first().tableName
            val primaryKeyName = classDeclaration.getDeclaredProperties()
                .firstOrNull { it.isAnnotationPresent(PrimaryKey::class) }
                ?.simpleName
                ?.asString()

            classDeclaration.getDeclaredProperties().forEach { property ->

                val columnAnnotation = property.getAnnotationsByType(Column::class).firstOrNull()

                if (columnAnnotation != null) {
                    val fieldName = columnAnnotation.name.let { it.ifBlank { property.simpleName.asString() } }
                    properties[fieldName] = "$fieldName = EXCLUDED.$fieldName"
                }

            }

            return """
                
                |dataSource.getConnection().use { connection ->
                |    
                |    connection.prepareStatement("INSERT INTO $tableName ($primaryKeyName, ${properties.keys.joinToString(", ")}) VALUES(${"?, ".repeat(properties.size)}?) ON CONFLICT ($primaryKeyName) DO UPDATE SET ${properties.values.joinToString(", ")}").use { preparedStatement ->
                |    
                |       ${classDeclaration.getDeclaredProperties().mapIndexed() { index, property -> "preparedStatement.setObject($index, ${parameterName}.${property.simpleName.asString()})" }.joinToString("\n\t   ")}
                |       
                |       val rowsAffected = preparedStatement.executeUpdate()
                |       
                |       if (rowsAffected > 0) {
                |       
                |           val generatedKeys = preparedStatement.generatedKeys
                |           
                |           if (generatedKeys.next()) {
                |           
                |               val id = generatedKeys.getLong(1)
                |           
                |               return ${parameterName}.copy($primaryKeyName = id)
                |           }
                |       
                |       }
                |     
                |      throw IllegalStateException("There is no primary key")
                |    }
                |    
                |}
                
            """.trimMargin()
        }

    }

}

class DaoProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment) =
        DaoProcessor(environment.codeGenerator, environment.logger)

}