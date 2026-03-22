package core

import capsolver.CaptchaSolver
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
import models.student.StudentCredentials
import service.EDiaryService

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
    val eDiaryService = EDiaryService(captchaSolver)

    routing {
        get("/health") {
            application.log.info("Health check")
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        post("/api/student-data") {
            val credentials = call.receive<StudentCredentials>()

            application.log.info("API Request: Fetching students data for {}", credentials.email)
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
