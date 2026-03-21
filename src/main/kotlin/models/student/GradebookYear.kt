package models.student

import kotlinx.serialization.Serializable

@Serializable
data class GradebookYear(
    val year: String,
    val semesters: List<SemesterData>
)

@Serializable
data class SemesterData(
    val semester: Int,
    val subjects: List<SubjectScores>
)

@Serializable
data class SubjectScores(
    val subject: String,
    val records: List<ScoreRecord>
)
@Serializable
data class ScoreRecord(
    val date: String,
    val dayOfWeek: String,
    val score: Int
)
