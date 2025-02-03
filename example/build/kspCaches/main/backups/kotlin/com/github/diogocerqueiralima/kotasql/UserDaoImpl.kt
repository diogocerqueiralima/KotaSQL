package com.github.diogocerqueiralima.kotasql

import com.zaxxer.hikari.HikariDataSource

public class UserDaoImpl(
  private val dataSource: HikariDataSource,
) : UserDao {
  override fun insert(user: User) {
  }
}
