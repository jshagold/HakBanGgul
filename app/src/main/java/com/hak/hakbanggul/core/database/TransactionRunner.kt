package com.hak.hakbanggul.core.database

import androidx.room.withTransaction

// 여러 DB 작업을 한 트랜잭션으로 돌린다. 인터페이스라 테스트에서 가짜를 끼울 수 있다.
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

// Room 구현. 도중에 앱이 꺼지면 전부 들어가거나 하나도 들어가지 않는다.
class RoomTransactionRunner(private val database: AppDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
