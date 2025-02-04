package com.github.diogocerqueiralima.kotasql

import com.github.diogocerqueiralima.kotasql.annotations.*

val db = KotaSQL.build(MyDatabase::class, "jdbc:postgresql://localhost:5432/test", "postgres", "")

@Database
abstract class MyDatabase : KotaSQL() {

    abstract fun userDao(): UserDao

}

@Entity(tableName = "users")
data class User(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @Column(name = "first_name", unique = true)
    val firstName: String,

    @Column(nullable = true, unique = false)
    val age: Int

)

@Dao
interface UserDao {

    @Insert
    fun insert(user: User): User

    @Insert
    fun insertMany(vararg users: User): List<User>

}

fun main() {

    val userDao = db.userDao()
    val user = userDao.insert(User(firstName = "João", age = 20))

    val users = userDao.insertMany(
        User(firstName = "Maria", age = 21),
        User(firstName = "Joana", age = 21)
    )

    println(user)
    println(users)
}