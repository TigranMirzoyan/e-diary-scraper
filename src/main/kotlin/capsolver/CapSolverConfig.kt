package capsolver

import io.github.cdimascio.dotenv.dotenv

data class CapSolverConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.capsolver.com",
    val defaultTaskType: String = "ReCaptchaV2EnterpriseTaskProxyLess",
    val initialDelayMs: Long = 15_000L,
    val pollIntervalMs: Long = 3_000L,
    val maxPollingAttempts: Int = 30
) {
    companion object {
        fun fromEnv(): CapSolverConfig {
            val env = dotenv { ignoreIfMissing = true }
            val key = env["CAPSOLVER_API_KEY"] ?: System.getenv("CAPSOLVER_API_KEY")
            ?: throw IllegalStateException("CRITICAL: CAPSOLVER_API_KEY is missing!")
            return CapSolverConfig(apiKey = key)
        }
    }
}
