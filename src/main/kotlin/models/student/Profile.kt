package models.student

import kotlinx.serialization.Serializable

@Serializable
data class StudentProfile(
    val name: String,
    val photoUrl: String,
    val schoolName: String,
    val gradeName: String,
    val academicRecord: AcademicRecord
)

@Serializable
data class AcademicRecord   (
    val gradebooks: List<GradebookYear>,
    val semesterReport: SemesterReport
)
