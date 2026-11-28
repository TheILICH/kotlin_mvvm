package com.example.problemlog.data

import com.example.problemlog.data.local.AttemptEntity
import com.example.problemlog.data.local.ProblemDao
import com.example.problemlog.data.local.toEntity
import com.example.problemlog.data.local.toProblem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// the only class that talks to the database, screens and viewmodel never see room types
class ProblemRepository(private val dao: ProblemDao) {

    val problems: Flow<List<Problem>> = dao.observeAll()
        .map { rows -> rows.map { it.toProblem() } }

    suspend fun upsert(problem: Problem) = dao.upsertProblem(problem.toEntity())

    suspend fun delete(id: String) = dao.deleteProblem(id)

    suspend fun logAttempt(id: String, verdict: Verdict, minutes: Int) =
        dao.insertAttempt(
            AttemptEntity(
                problemId = id,
                verdict = verdict,
                minutes = minutes,
                timestamp = System.currentTimeMillis()
            )
        )

    suspend fun undoLastAttempt(id: String) = dao.deleteLastAttempt(id)

    suspend fun loadSamples() {
        val samples = sampleProblems()
        dao.insertSamplesIfEmpty(
            problems = samples.map { it.toEntity() },
            attempts = samples.flatMap { p -> p.attempts.map { it.toEntity(p.id) } }
        )
    }
}
