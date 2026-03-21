package service

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import scraper.ScraperConfig
import scraper.parser.StudentProfileParser
import capsolver.CaptchaSolver
import models.student.StudentProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

class EDiaryService(
    private val browser: Browser,
    private val captchaSolver: CaptchaSolver
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun fetchStudentData(email: String, password: String): Result<List<StudentProfile>> =
        withContext(Dispatchers.IO) {
            val browserContext = browser.newContext()
            val page = browserContext.newPage()

            return@withContext try {
                withTimeout(ScraperConfig.FETCHING_TIMEOUT_MS) {
                    val deferredToken = async {
                        captchaSolver.solveReCaptcha(ScraperConfig.SITE_KEY, ScraperConfig.AUTH_URL)
                    }

                    login(page, email, password, deferredToken.await())
                    val profiles = StudentProfileParser.extractProfiles(page)

                    logger.info("Profiles have successfully been extracted.")
                    Result.success(profiles)
                }
            } catch (_: TimeoutCancellationException) {
                logger.error("Scraping timed out for user: {} (Network or Captcha delay)", email)
                Result.failure(Exception("Operation timed out. Please try again later."))
            } catch (e: Exception) {
                logger.error("Scraping failed", e)
                Result.failure(e)
            } finally {
                browserContext.close()
            }
        }

    private fun login(page: Page, user: String, pass: String, token: String) {
        page.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})")

        logger.info("Navigating to login page...")
        page.navigate(ScraperConfig.AUTH_URL)

        logger.info("Filling out credentials...")
        page.fill("input[name='email']", user)
        page.fill("input[name='password']", pass)

        logger.info("Token received! Injecting and submitting...")
        page.evaluate("document.getElementById('g-recaptcha-response').innerHTML = '$token';")
        page.click("button[type='submit']")

        logger.info("Logging in and waiting for redirect...")
        try {
            page.waitForURL(
                ScraperConfig.CHILDREN_URL,
                Page.WaitForURLOptions().setTimeout(ScraperConfig.PAGE_WAITING_TIMEOUT_MS)
            )
            logger.info("Logged in successfully")
        } catch (e: Exception) {
            throw IllegalStateException("Login failed: Did not reach the success URL in time.", e)
        }
    }
}