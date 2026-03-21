package core

import service.EDiaryService
import scraper.PlaywrightManager
import capsolver.CaptchaSolver
import models.student.StudentCredentials
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.configureRouting() {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }

    val captchaSolver = CaptchaSolver(client = httpClient)

    routing {
        get("/health") {
            application.log.info("Health check")
            call.respond(HttpStatusCode.OK, PlaywrightManager.browser != null)
        }

        post("/api/student-data") {
            val credentials = call.receive<StudentCredentials>()

            application.log.info("API Request: Fetching students data for {}", credentials.email)

            val browser = PlaywrightManager.browser
            if (browser == null) {
                application.log.error("Request failed: Browser instance is null")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Browser not initialized"))
                return@post
            }

            val eDiaryService = EDiaryService(browser, captchaSolver)
            val result = eDiaryService.fetchStudentData(credentials.email, credentials.password)

            result.fold(onSuccess = { profiles ->
                application.log.info("Successfully returned {} profiles for {}", profiles.size, credentials.email)
                call.respond(HttpStatusCode.OK, profiles)
            }, onFailure = { exception ->
                application.log.warn("Failed to fulfill request for {}: {}", credentials.email, exception.message)
                call.respond(
                    HttpStatusCode.Unauthorized, mapOf("error" to (exception.message ?: "Scraping failed"))
                )
            })
        }
    }
}
