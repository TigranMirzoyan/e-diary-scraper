package scraper

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import org.slf4j.LoggerFactory

object PlaywrightManager {
    private val logger = LoggerFactory.getLogger(PlaywrightManager::class.java)
    private var playwright: Playwright? = null
    var browser: Browser? = null
        private set

    fun startBrowser() {
        logger.info("Starting Browser")
        playwright = Playwright.create()
        browser = playwright?.chromium()?.launch(
            BrowserType.LaunchOptions().setHeadless(true),
        )
        logger.info("Browser is ready!")
    }

    fun stopBrowser() {
        logger.info("Shutting down browser...")
        browser?.close()
        playwright?.close()
    }
}