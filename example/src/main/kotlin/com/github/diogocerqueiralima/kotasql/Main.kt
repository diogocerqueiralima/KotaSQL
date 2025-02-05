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

    @Delete
    fun delete(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    fun findById(id: Long): User?

    @Query("SELECT * FROM users ORDER BY id DESC")
    fun findAll(): List<User>

    @Query("SELECT * FROM users WHERE age < :age")
    fun findAllByAgeLowerThan(age: Int): List<User>

}

fun main() {

    val userDao = db.userDao()
    val users = userDao.findAllByAgeLowerThan(23)

    println(users)
}