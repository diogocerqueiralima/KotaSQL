package com.github.diogocerqueiralima.kotasql.processors

import com.github.diogocerqueiralima.kotasql.annotations.Column
import com.github.diogocerqueiralima.kotasql.annotations.Dao
import com.github.diogocerqueiralima.kotasql.annotations.Entity
import com.github.diogocerqueiralima.kotasql.annotations.PrimaryKey
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import kotlin.math.log

class EntityProcessor(

    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger

) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val symbols = resolver.getSymbolsWithAnnotation(Entity::class.java.name)
        val ret = symbols.filter { !it.validate() }.toList()

        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(EntityVisitor(), Unit) }

        return ret
    }

    inner class EntityVisitor : KSVisitorVoid() {

        @OptIn(KspExperimental::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            logger.info("Processing ${classDeclaration.simpleName.asString()}")

            val tableName = classDeclaration.getAnnotationsByType(Entity::class).first().tableName
            val properties = classDeclaration.getAllProperties()
            val columns = mutableListOf<String>()

            val primaryKeyProperty = properties.firstOrNull { it.isAnnotationPresent(PrimaryKey::class) }

            if (primaryKeyProperty == null) {
                logger.exception(IllegalStateException("There is no Primary Key for ${classDeclaration.simpleName.asString()} Entity"))
                return
            }

            val primaryKeyAnnotation = primaryKeyProperty.getAnnotationsByType(PrimaryKey::class).first()
            val autoGenerate = primaryKeyAnnotation.autoGenerate
            val primaryKeyColumnName = primaryKeyAnnotation.name.let { it.ifBlank { primaryKeyProperty.simpleName.asString() } }

            columns.add("$primaryKeyColumnName ${if (autoGenerate) "BIGSERIAL" else ""} PRIMARY KEY")

            properties.forEach { property ->

                logger.info("Processing property ${property.simpleName.asString()}")

                val columnAnnotation = property.getAnnotationsByType(Column::class).firstOrNull() ?: return@forEach

                val columnName = columnAnnotation.name.let { it.ifBlank { property.simpleName.asString() } }
                val columnType = getDataType(property.type.resolve())
                val columnNullable = columnAnnotation.nullable
                val columnUnique = columnAnnotation.unique

                columns.add("$columnName $columnType ${if (columnUnique) "UNIQUE" else ""} ${if (columnNullable) "" else "NOT NULL"}")
            }

            val sql = "CREATE TABLE IF NOT EXISTS $tableName (\n  ${columns.joinToString(",\n  ")}\n);"

            val file = codeGenerator.createNewFile(
                dependencies = Dependencies(false),
                packageName = "generated/sql",
                fileName = "${tableName}_schema",
                extensionName = "sql"
            )

            file.write(sql.toByteArray())
            file.close()
        }

        private fun getDataType(type: KSType): DataType =
            when (type.declaration.qualifiedName?.asString()) {
                "kotlin.Int" -> DataType.INTEGER
                "kotlin.Long" -> DataType.BIGINT
                "kotlin.Double" -> DataType.DOUBLE_PRECISION
                "kotlin.Boolean" -> DataType.BOOLEAN
                else -> DataType.TEXT
            }

    }

}

class EntityProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment) =
        EntityProcessor(environment.codeGenerator, environment.logger)

}

enum class DataType {

    TEXT,
    INTEGER,
    BIGINT,
    DOUBLE_PRECISION,
    BOOLEAN

}