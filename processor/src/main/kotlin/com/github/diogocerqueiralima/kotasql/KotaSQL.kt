package com.github.diogocerqueiralima.kotasql

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

abstract class KotaSQL {

    companion object {
        fun <T : KotaSQL> build(kclass: KClass<T>, url: String, username: String, password: String): T {

            val implementationClass = Class.forName(kclass.qualifiedName + "Impl").kotlin
            val constructor = implementationClass.primaryConstructor
                ?: throw IllegalArgumentException("This class should have a valid constructor")

            return constructor.call(url, username, password) as T
        }
    }
}