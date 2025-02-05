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

data class FirstName(

    @Column(name = "first_name")
    val value: String

)

@Dao
interface UserDao {

    @Insert
    fun insert(user: User): User

    @Insert
    fun insertMany(vararg users: User): List<User>

    @Delete
    fun delete(user: User)

    @Delete
    fun deleteMany(vararg users: User)

    @Query("SELECT * FROM users WHERE id = :id")
    fun findById(id: Long): User?

    @Query("SELECT * FROM users ORDER BY id DESC")
    fun findAll(): List<User>

    @Query("SELECT * FROM users WHERE age < :age")
    fun findAllByAgeLowerThan(age: Int): List<User>

    @Query("SELECT first_name FROM users")
    fun findAllFirstName(): List<FirstName>

}

fun main() {

    val userDao = db.userDao()

    println(userDao.findAll())

    userDao.deleteMany(
        User(id = 1, firstName = "Diogo", age = 20),
        User(id = 2, firstName = "Joana", age = 21)
    )

    println(userDao.findAll())
}