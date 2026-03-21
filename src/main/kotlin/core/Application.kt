package core

import scraper.PlaywrightManager
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()

    monitor.subscribe(ApplicationStarted) {
        PlaywrightManager.startBrowser()
    }

    monitor.subscribe(ApplicationStopping) {
        PlaywrightManager.stopBrowser()
    }

    configureRouting()
}
