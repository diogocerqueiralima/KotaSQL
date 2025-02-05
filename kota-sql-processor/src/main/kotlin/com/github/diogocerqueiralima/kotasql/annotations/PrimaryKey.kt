package com.github.diogocerqueiralima.kotasql.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class PrimaryKey(val autoGenerate: Boolean, val name: String = "")
