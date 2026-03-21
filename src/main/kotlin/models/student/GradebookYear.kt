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
    val subjects: List<SubjectGrades>
)

@Serializable
data class SubjectGrades(
    val subject: String,
    val records: List<GradeRecord>
)
@Serializable
data class GradeRecord(
    val date: String,
    val dayOfWeek: String,
    val period: Int,
    val grade: Int
)
