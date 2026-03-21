package models.student

import kotlinx.serialization.Serializable

@Serializable
data class StudentCredentials(
    val email: String,
    val password: String
)