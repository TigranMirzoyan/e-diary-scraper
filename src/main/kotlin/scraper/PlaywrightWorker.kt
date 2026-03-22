package scraper

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import java.util.concurrent.Executors

class PlaywrightWorker(val id: Int) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var playwright: Playwright? = null
    var browser: Browser? = null
        private set

    suspend fun start() = withContext(dispatcher) {
        playwright = Playwright.create()
        browser = playwright?.chromium()?.launch(
            BrowserType.LaunchOptions().setHeadless(true),
        )
    }

    suspend fun <T> execute(block: suspend (Browser) -> T): T {
        val currentMdc = MDC.getCopyOfContextMap() ?: emptyMap()
        val mergedContext = currentMdc + mapOf("workerId" to id.toString())

        return withContext(dispatcher + MDCContext(mergedContext)) {
            val currentBrowser = browser ?: throw IllegalStateException("Browser not initialized on worker $id")
            block(currentBrowser)
        }
    }

    fun stop() {
        browser?.close()
        playwright?.close()
        dispatcher.close()
    }
}