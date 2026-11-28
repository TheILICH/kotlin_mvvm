package com.example.problemlog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProblemDao {

    // flow sends a new list every time problems or attempts table changes
    @Transaction
    @Query("SELECT * FROM problems")
    fun observeAll(): Flow<List<ProblemWithAttempts>>

    // insert or update, does not delete the row, so attempts stay
    @Upsert
    suspend fun upsertProblem(problem: ProblemEntity)

    // attempts go away too (CASCADE)
    @Query("DELETE FROM problems WHERE id = :id")
    suspend fun deleteProblem(id: String)

    @Insert
    suspend fun insertAttempt(attempt: AttemptEntity)

    // newest attempt of this problem = biggest id
    @Query("DELETE FROM attempts WHERE id = (SELECT MAX(id) FROM attempts WHERE problemId = :problemId)")
    suspend fun deleteLastAttempt(problemId: String)

    @Query("SELECT COUNT(*) FROM problems")
    suspend fun countProblems(): Int

    @Insert
    suspend fun insertProblems(problems: List<ProblemEntity>)

    @Insert
    suspend fun insertAttempts(attempts: List<AttemptEntity>)

    // all or nothing, and only when the table is empty
    @Transaction
    suspend fun insertSamplesIfEmpty(problems: List<ProblemEntity>, attempts: List<AttemptEntity>) {
        if (countProblems() > 0) return
        insertProblems(problems)
        insertAttempts(attempts)
    }
}
