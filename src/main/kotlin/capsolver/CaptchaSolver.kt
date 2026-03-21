package capsolver

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class CaptchaSolver(
    private val config: CapSolverConfig = CapSolverConfig.fromEnv(),
    private val client: HttpClient
) {
    private val logger = LoggerFactory.getLogger(CaptchaSolver::class.java)

    suspend fun solveReCaptcha(
        siteKey: String,
        websiteUrl: String,
        taskType: String = config.defaultTaskType
    ): String {
        logger.info("Initializing Captcha task for siteKey: {}", siteKey)

        try {
            val taskId = createTask(siteKey, websiteUrl, taskType)
                ?: throw CaptchaException("Failed to create Captcha task on CapSolver.")

            logger.info("Task created (ID: {}). Waiting {}ms...", taskId, config.initialDelayMs)
            delay(config.initialDelayMs)

            return pollForSolution(taskId)
                ?: throw CaptchaException("Captcha solving timed out or failed.")

        } catch (e: Exception) {
            logger.error("Unexpected error during Captcha solving process", e)
            throw CaptchaException("Unexpected error solving captcha", e)
        }
    }

    private suspend fun createTask(siteKey: String, websiteUrl: String, taskType: String): String? {
        val response: CreateTaskResponse = client.post("${config.baseUrl}/createTask") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    clientKey = config.apiKey,
                    task = CapSolverTask(taskType, websiteUrl, siteKey, false)
                )
            )
        }.body()

        if (response.errorId != 0 || response.taskId == null) {
            logger.error("CapSolver Task Creation Failed! Error Code: {}", response.errorCode)
            return null
        }
        return response.taskId
    }

    private suspend fun pollForSolution(taskId: String): String? {
        repeat(config.maxPollingAttempts) { attempt ->
            val response: GetTaskResultResponse = client.post("${config.baseUrl}/getTaskResult") {
                contentType(ContentType.Application.Json)
                setBody(GetTaskResultRequest(clientKey = config.apiKey, taskId = taskId))
            }.body()

            when (val state = evaluateStatus(response, taskId, attempt + 1)) {
                is PollingState.Solved -> return state.token
                is PollingState.Failed -> return null
                is PollingState.Processing -> delay(config.pollIntervalMs)
            }
        }

        logger.error("CapSolver timeout: Task ID {} took too long.", taskId)
        return null
    }

    private fun evaluateStatus(
        response: GetTaskResultResponse,
        taskId: String,
        attempt: Int
    ): PollingState {
        return when (response.status) {
            "ready" -> {
                logger.info("Captcha solved successfully for Task ID: {}", taskId)
                val token = response.solution?.gRecaptchaResponse
                if (token != null) PollingState.Solved(token) else PollingState.Failed
            }

            "processing" -> {
                logger.debug("Task {} processing... (Attempt {}/{})", taskId, attempt, config.maxPollingAttempts)
                PollingState.Processing
            }

            else -> {
                logger.warn("CapSolver returned error during status check: {}", response.errorCode)
                PollingState.Failed
            }
        }
    }
}