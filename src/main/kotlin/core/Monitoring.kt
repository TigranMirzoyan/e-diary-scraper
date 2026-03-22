package core

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallId) {
        generate(8,"abcdefghijklmnopqrstuvwxyz0123456789")
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("reqId")
    }
}