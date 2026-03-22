package core

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import scraper.PlaywrightPool

const val DEFAULT_SCRAPER_WORKERS = 4

fun main(args: Array<String>) {
    EngineMain.main(args)
}


fun Application.module() {
    configureSerialization()

    val maxWorkers = System.getenv("MAX_SCRAPER_WORKERS")?.toIntOrNull() ?: DEFAULT_SCRAPER_WORKERS

    monitor.subscribe(ApplicationStarted) {
        runBlocking {
            PlaywrightPool.initPool(maxWorkers)
        }
    }

    monitor.subscribe(ApplicationStopping) {
        PlaywrightPool.closePool()
    }

    configureMonitoring()
    configureRouting()
}
