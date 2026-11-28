package com.example.problemlog.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.problemlog.data.Attempt
import com.example.problemlog.data.Problem
import com.example.problemlog.data.Verdict
import com.example.problemlog.logic.withAttempt

// one row per problem, status is not stored, it comes from the attempts
@Entity(tableName = "problems")
data class ProblemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val rating: Int,
    val tags: String,      // "dp,math"
    val notes: String
)

// one row per attempt, deleted together with its problem
@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("problemId")]
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val problemId: String,
    val verdict: Verdict,
    val minutes: Int,
    val timestamp: Long
)

// problem row + all its attempt rows, room fills it with one query
data class ProblemWithAttempts(
    @Embedded val problem: ProblemEntity,
    @Relation(parentColumn = "id", entityColumn = "problemId")
    val attempts: List<AttemptEntity>
)

// ---------- database rows <-> app models ----------

fun ProblemWithAttempts.toProblem(): Problem {
    val base = Problem(
        id = problem.id,
        title = problem.title,
        rating = problem.rating,
        tags = problem.tags.split(",").filter { it.isNotEmpty() },
        notes = problem.notes
    )
    // replay history in the order it was logged, so status follows the same rule as before
    return attempts
        .sortedBy { it.id }
        .map { Attempt(it.verdict, it.minutes, it.timestamp, it.id) }
        .fold(base) { p, attempt -> p.withAttempt(attempt) }
}

fun Problem.toEntity() = ProblemEntity(
    id = id,
    title = title,
    rating = rating,
    tags = tags.joinToString(","),
    notes = notes
)

fun Attempt.toEntity(problemId: String) = AttemptEntity(
    problemId = problemId,
    verdict = verdict,
    minutes = minutes,
    timestamp = timestamp
)
