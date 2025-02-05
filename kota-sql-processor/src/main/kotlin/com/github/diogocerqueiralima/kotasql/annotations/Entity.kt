package com.github.diogocerqueiralima.kotasql.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Entity(val tableName: String)
