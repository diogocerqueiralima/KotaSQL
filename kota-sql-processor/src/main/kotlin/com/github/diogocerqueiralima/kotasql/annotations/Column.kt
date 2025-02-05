package com.github.diogocerqueiralima.kotasql.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Column(
    val name: String = "",
    val nullable: Boolean = false,
    val unique: Boolean = false
)
