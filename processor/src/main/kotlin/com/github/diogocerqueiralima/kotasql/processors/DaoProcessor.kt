package com.github.diogocerqueiralima.kotasql.processors

import com.github.diogocerqueiralima.kotasql.annotations.Dao
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate

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

        }

    }

}

class DaoProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment) =
        DaoProcessor(environment.codeGenerator, environment.logger)

}