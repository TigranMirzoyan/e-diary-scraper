package models.student

import kotlinx.serialization.Serializable

@Serializable
data class SemesterReport(
    val subjects: List<SemesterSubjectScores>,
    val finalExams: List<FinalExamScore>
)

@Serializable
data class SemesterSubjectScores(
    val subject: String,
    val firstSemesterScore: Int,
    val secondSemesterScore: Int,
    val averageScore: Int
)

@Serializable
data class FinalExamScore(
    val subject: String,
    val examScore: String,
    val retakeScore: String
)
