package com.github.diogocerqueiralima.kotasql.processors

import com.github.diogocerqueiralima.kotasql.annotations.*
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.math.log

class DaoProcessor(

    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger

) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val symbols = resolver.getSymbolsWithAnnotation(Dao::class.java.name)
        val ret = symbols.filter { !it.validate() }.toList()

        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(DaoVisitor(resolver), Unit) }

        return ret
    }

    inner class DaoVisitor(

        private val resolver: Resolver

    ) : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            logger.info("Processing ${classDeclaration.simpleName.asString()}")

            val packageName = classDeclaration.packageName.asString()
            val className = "${classDeclaration.simpleName.asString()}Impl"
            val fileBuilder = FileSpec.builder(packageName, className)
                .addImport("java.sql", "Statement")
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
                        ParameterSpec.builder(parameter.name?.asString() ?: "", parameter.type.toTypeName())
                            .addModifiers(if (parameter.isVararg) listOf(KModifier.VARARG) else emptyList())
                            .build()
                    }
                )
                .addCode(getImplementationContent(function))
                .build()

        @OptIn(KspExperimental::class)
        private fun getImplementationContent(function: KSFunctionDeclaration) =
            when {
                function.isAnnotationPresent(Insert::class) -> getInsertImplementationContent(function)
                function.isAnnotationPresent(Delete::class) -> getDeleteImplementationContent(function)
                function.isAnnotationPresent(Query::class) -> getQueryImplementationContent(function)
                else -> throw IllegalStateException("There is no SQL operation")
            }

        @OptIn(KspExperimental::class)
        private fun getQueryImplementationContent(function: KSFunctionDeclaration): String {

            val returnType = function.returnType!!.resolve()
            val queryAnnotation = function.getAnnotationsByType(Query::class).first()
            val queryFromAnnotation = queryAnnotation.value
            val query = queryFromAnnotation.replace(Regex(":\\w+"), "?")
            val queryParameters = extractParametersFromQuery(queryFromAnnotation)
            val listType = resolver.getClassDeclarationByName(List::class.qualifiedName!!)!!.asStarProjectedType()

            if (listType.isAssignableFrom(returnType))
                return getQueryManyImplementationContent(function, query, queryParameters)

            return getQueryOneImplementationContent(function, query, queryParameters)
        }

        private fun getQueryManyImplementationContent(function: KSFunctionDeclaration, query: String, queryParameters: List<String>): String {

            val listType = function.returnType!!.resolve()
            val classDeclaration = listType.arguments.first().type!!.resolve().declaration as KSClassDeclaration
            val className = classDeclaration.simpleName.asString()

            val propertiesMap = classDeclaration.getDeclaredProperties().map { property ->
                property.type.resolve().declaration.simpleName.asString()
            }

            return """
                |
                |dataSource.getConnection().use { connection ->
                |
                |   connection.prepareStatement("$query").use { preparedStatement ->
                |   
                |       ${queryParameters.mapIndexed { index, parameter -> "preparedStatement.setObject(${index + 1}, $parameter)" }.joinToString("\n\t   ")}
                |       
                |       preparedStatement.executeQuery().use { resultSet -> 
                |       
                |           val items = mutableListOf<$className>()
                |           
                |           while (resultSet.next()) {
                |           
                |               items.add(
                |                   $className(
                |                       ${propertiesMap.mapIndexed { index, s -> "resultSet.getObject(${index + 1}) as $s" }.joinToString(",\n\t\t\t\t\t   ")}
                |                   )
                |               )
                |               
                |           }
                |       
                |           return items
                |       }
                |   
                |   }
                |
                |}
                |
            """.trimMargin()

        }

        private fun getQueryOneImplementationContent(function: KSFunctionDeclaration, query: String, queryParameters: List<String>): String {

            val classDeclaration = function.returnType!!.resolve().declaration as KSClassDeclaration
            val className = classDeclaration.simpleName.asString()

            val propertiesMap = classDeclaration.getDeclaredProperties().map { property ->
                property.type.resolve().declaration.simpleName.asString()
            }

            return """
                |
                |dataSource.getConnection().use { connection ->
                |
                |   connection.prepareStatement("$query").use { preparedStatement ->
                |   
                |       ${queryParameters.mapIndexed { index, parameter -> "preparedStatement.setObject(${index + 1}, $parameter)" }.joinToString("\n\t   ")}
                |       
                |       preparedStatement.executeQuery().use { resultSet -> 
                |           
                |           if (!resultSet.next()) return null
                |           
                |           return $className(
                |               ${propertiesMap.mapIndexed { index, s -> "resultSet.getObject(${index + 1}) as $s" }.joinToString(",\n\t\t\t   ")}
                |           )
                |       
                |       }
                |   
                |   }
                |
                |}
                |
            """.trimMargin()
        }

        private fun extractParametersFromQuery(query: String): List<String> {
            val regex = Regex(":([a-zA-Z_][a-zA-Z0-9_]*)")
            return regex.findAll(query).map { it.groupValues[1] }.toList()
        }

        @OptIn(KspExperimental::class)
        private fun getDeleteImplementationContent(function: KSFunctionDeclaration): String {

            val parameters = function.parameters

            if (parameters.size != 1)
                throw IllegalStateException("Delete operation must have exactly one parameter.")

            val parameter = parameters.first()
            val parameterName = parameter.name?.asString() ?: ""
            val classDeclaration = parameter.type.resolve().declaration as KSClassDeclaration
            val tableName = classDeclaration.getAnnotationsByType(Entity::class).first().tableName
            val primaryKeyName = classDeclaration.getDeclaredProperties()
                .first { it.isAnnotationPresent(PrimaryKey::class) }
                .simpleName
                .asString()

            if (parameter.isVararg)
                return getDeleteManyImplementationContent(tableName, primaryKeyName, parameterName)

            return getDeleteOneImplementationContent(tableName, primaryKeyName, parameterName)
        }

        private fun getDeleteOneImplementationContent(tableName: String, primaryKeyName: String, parameterName: String) =
            """
                |
                |dataSource.getConnection().use { connection ->
                |
                |   connection.prepareStatement("DELETE FROM $tableName WHERE $primaryKeyName = ?").use { preparedStatement ->
                |   
                |       preparedStatement.setObject(1, ${parameterName}.$primaryKeyName)
                |       preparedStatement.executeUpdate()
                |    
                |   }
                |
                |}
                |
            """.trimMargin()

        private fun getDeleteManyImplementationContent(tableName: String, primaryKeyName: String, parameterName: String) =
            """
                |
                |dataSource.getConnection().use { connection ->
                |
                |   connection.prepareStatement("DELETE FROM $tableName WHERE $primaryKeyName = ?").use { preparedStatement ->
                |   
                |       for (item in $parameterName) {
                |           preparedStatement.setObject(1, item.$primaryKeyName)
            |               preparedStatement.addBatch()
                |       }
                |       
                |       preparedStatement.executeBatch()
                |    
                |   }
                |
                |}
                |
            """.trimMargin()

        @OptIn(KspExperimental::class)
        private fun getInsertImplementationContent(function: KSFunctionDeclaration): String {

            val parameters = function.parameters

            if (parameters.size != 1)
                throw IllegalStateException("Insert operation must have exactly one parameter.")

            val returnTypeReference = function.returnType
            val parameter = parameters.first()
            val parameterName = parameter.name?.asString() ?: ""
            val classDeclaration = parameter.type.resolve().declaration as KSClassDeclaration

            if (returnTypeReference?.resolve() != parameter.type.resolve() && !parameter.isVararg)
                throw IllegalStateException("Insert operation must return type ${parameter.type}")

            val properties = mutableMapOf<String, String>()
            val tableName = classDeclaration.getAnnotationsByType(Entity::class).first().tableName
            val primaryKeyProperty = classDeclaration.getDeclaredProperties().first { it.isAnnotationPresent(PrimaryKey::class) }
            val primaryKey = primaryKeyProperty.getAnnotationsByType(PrimaryKey::class).first()
            val primaryKeyName = primaryKeyProperty.simpleName.asString()

            classDeclaration.getDeclaredProperties().forEach { property ->

                val columnAnnotation = property.getAnnotationsByType(Column::class).firstOrNull()

                if (columnAnnotation != null) {
                    val fieldName = columnAnnotation.name.let { it.ifBlank { property.simpleName.asString() } }
                    properties[fieldName] = "$fieldName = EXCLUDED.$fieldName"
                }

            }

            if (parameter.isVararg)
                return if (primaryKey.autoGenerate)
                    getInsertManyAutoGenerateImplementation(tableName, primaryKeyName, parameterName, parameter.type.resolve().toString(), properties, classDeclaration)
                else getInsertManyImplementation(tableName, primaryKeyName, parameterName, parameter.type.resolve().toString(), properties, classDeclaration)

            return if (primaryKey.autoGenerate)
                getInsertOneAutoGenerateImplementation(tableName, primaryKeyName, parameterName, properties, classDeclaration)
            else getInsertOneImplementation(tableName, primaryKeyName, parameterName, properties, classDeclaration)
        }

        @OptIn(KspExperimental::class)
        private fun getInsertManyAutoGenerateImplementation(tableName: String, primaryKeyName: String, parameterName: String, parameterTypeName: String, properties: Map<String, String>, classDeclaration: KSClassDeclaration) =
            """
                
                |dataSource.getConnection().use { connection ->
                |    
                |    connection.prepareStatement("INSERT INTO $tableName (${properties.keys.joinToString(", ")}) VALUES(${"?, ".repeat(properties.size - 1)}?) ON CONFLICT ($primaryKeyName) DO UPDATE SET ${properties.values.joinToString(", ")}", Statement.RETURN_GENERATED_KEYS).use { preparedStatement ->
                |    
                |       for (item in $parameterName) {
                |       
                |           ${classDeclaration.getDeclaredProperties().filter { !it.isAnnotationPresent(PrimaryKey::class) }.mapIndexed() { index, property -> "preparedStatement.setObject(${index + 1}, item.${property.simpleName.asString()})" }.joinToString("\n\t\t   ")}   
                |           
                |           preparedStatement.addBatch()
                |       }
                |       
                |       preparedStatement.executeBatch()
                |       
                |       val generatedItems = mutableListOf<$parameterTypeName>()
                |       
                |       preparedStatement.generatedKeys.use { resultSet ->
                |       
                |           var index = 0
                |           
                |           while (resultSet.next()) {
                |               val id = resultSet.getLong(1)
                |               val item = ${parameterName}[index++]
                |               generatedItems.add(item.copy($primaryKeyName = id))
                |           }
                |       
                |           return generatedItems
                |       }
                |       
                |    }
                |    
                |}
                
            """.trimMargin()

        private fun getInsertManyImplementation(tableName: String, primaryKeyName: String, parameterName: String, parameterTypeName: String, properties: Map<String, String>, classDeclaration: KSClassDeclaration) =
            """
                
                |dataSource.getConnection().use { connection ->
                |    
                |    connection.prepareStatement("INSERT INTO $tableName ($primaryKeyName, ${properties.keys.joinToString(", ")}) VALUES(${"?, ".repeat(properties.size)}?) ON CONFLICT ($primaryKeyName) DO UPDATE SET ${properties.values.joinToString(", ")}", Statement.RETURN_GENERATED_KEYS).use { preparedStatement ->
                |    
                |       for (item in $parameterName) {
                |       
                |           ${classDeclaration.getDeclaredProperties().mapIndexed() { index, property -> "preparedStatement.setObject(${index + 1}, item.${property.simpleName.asString()})" }.joinToString("\n\t\t   ")}   
                |           
                |           preparedStatement.addBatch()
                |       }
                |       
                |       preparedStatement.executeBatch()
                |       
                |       return ${parameterName}.toList()
                |       
                |    }
                |    
                |}
                
            """.trimMargin()

            @OptIn(KspExperimental::class)
        private fun getInsertOneAutoGenerateImplementation(tableName: String, primaryKeyName: String, parameterName: String, properties: Map<String, String>, classDeclaration: KSClassDeclaration) =
            """
                
                |dataSource.getConnection().use { connection ->
                |    
                |    connection.prepareStatement("INSERT INTO $tableName (${properties.keys.joinToString(", ")}) VALUES(${"?, ".repeat(properties.size - 1)}?) ON CONFLICT ($primaryKeyName) DO UPDATE SET ${properties.values.joinToString(", ")}", Statement.RETURN_GENERATED_KEYS).use { preparedStatement ->
                |    
                |       ${classDeclaration.getDeclaredProperties().filter { !it.isAnnotationPresent(PrimaryKey::class) }.mapIndexed() { index, property -> "preparedStatement.setObject(${index + 1}, ${parameterName}.${property.simpleName.asString()})" }.joinToString("\n\t   ")}
                |       
                |       val rowsAffected = preparedStatement.executeUpdate()
                |       
                |       if (rowsAffected > 0) {
                |       
                |           val generatedKeys = preparedStatement.generatedKeys
                |           
                |           if (generatedKeys.next()) {
                |           
                |               val id = generatedKeys.getLong("$primaryKeyName")
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

    private fun getInsertOneImplementation(tableName: String, primaryKeyName: String, parameterName: String, properties: Map<String, String>, classDeclaration: KSClassDeclaration) =
        """
                
                |dataSource.getConnection().use { connection ->
                |    
                |    connection.prepareStatement("INSERT INTO $tableName ($primaryKeyName, ${properties.keys.joinToString(", ")}) VALUES(${"?, ".repeat(properties.size)}?) ON CONFLICT ($primaryKeyName) DO UPDATE SET ${properties.values.joinToString(", ")}", Statement.RETURN_GENERATED_KEYS).use { preparedStatement ->
                |    
                |       ${classDeclaration.getDeclaredProperties().mapIndexed() { index, property -> "preparedStatement.setObject(${index + 1}, ${parameterName}.${property.simpleName.asString()})" }.joinToString("\n\t   ")}
                |       
                |       preparedStatement.executeUpdate()
                |     
                |       return $parameterName
                |    }
                |    
                |}
                
            """.trimMargin()


}

class DaoProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment) =
        DaoProcessor(environment.codeGenerator, environment.logger)

}