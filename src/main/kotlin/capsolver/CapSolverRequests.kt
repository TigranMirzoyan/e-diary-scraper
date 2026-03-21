package capsolver

import kotlinx.serialization.Serializable

@Serializable
data class CreateTaskRequest(
    val clientKey: String,
    val task: CapSolverTask
)

@Serializable
data class CapSolverTask(
    val type: String,
    val websiteURL: String,
    val websiteKey: String,
    val isInvisible: Boolean
)

@Serializable
data class CreateTaskResponse(
    val errorId: Int,
    val errorCode: String? = null,
    val taskId: String? = null,
)

@Serializable
data class GetTaskResultRequest(
    val clientKey: String,
    val taskId: String
)

@Serializable
data class Solution(
    val gRecaptchaResponse: String? = null
)

@Serializable
data class GetTaskResultResponse(
    val errorId: Int,
    val errorCode: String? = null,
    val status: String? = null,
    val solution: Solution? = null
)