package com.github.diogocerqueiralima.kotasql

import com.github.diogocerqueiralima.kotasql.annotations.*

@Entity(tableName = "users")
data class User(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @Column(name = "first_name")
    val firstName: String,

    @Column(nullable = true, unique = true)
    val age: Int

)

@Dao
interface UserDao {

    @Insert
    fun insert(user: User)

}

fun main() {

}