package service

import capsolver.CaptchaException
import capsolver.CaptchaSolver
import com.microsoft.playwright.Page
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import models.student.StudentProfile
import org.slf4j.LoggerFactory
import scraper.ScraperConfig
import scraper.PlaywrightPool
import scraper.parser.StudentProfileParser

class EDiaryService(private val captchaSolver: CaptchaSolver) {
    private val logger = LoggerFactory.getLogger(EDiaryService::class.java)

    suspend fun fetchStudentData(email: String, password: String): Result<List<StudentProfile>> =
        PlaywrightPool.withBrowser { browser ->
            val browserContext = browser.newContext()
            val page = browserContext.newPage()

            return@withBrowser try {
                withTimeout(ScraperConfig.FETCHING_TIMEOUT_MS) {
                    val deferredToken = async {
                        captchaSolver.solveReCaptcha(ScraperConfig.SITE_KEY, ScraperConfig.AUTH_URL)
                    }

                    logIntoDiary(page, email, password, deferredToken.await())
                    val profiles = StudentProfileParser.extractProfiles(page)

                    logger.info("Profiles have successfully been extracted.")
                    Result.success(profiles)
                }
            } catch (_: TimeoutCancellationException) {
                logger.error("Scraping timed out for user: {} (Network or Captcha delay)", email)
                Result.failure(Exception("Operation timed out. Please try again later."))
            } catch (e: IllegalArgumentException) {
                Result.failure(e)
            } catch (e: CaptchaException) {
                logger.error("Captcha bypass failed: {}", e.message)
                Result.failure(Exception("Failed to verify human identity. Please try again."))
            } catch (e: Exception) {
                logger.error("Scraping failed with an unexpected system error", e)
                Result.failure(e)
            } finally {
                browserContext.close()
            }
        }

    private fun logIntoDiary(page: Page, user: String, pass: String, token: String) {
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
            page.waitForFunction(
                """
                () => {
                    const span = document.querySelector('.loginFaild');
                    const hasText = span && span.innerText.trim().length > 0;
                    return hasText || window.location.href.includes('${ScraperConfig.CHILDREN_URL}');
                }
                """.trimIndent(),
                null,
                Page.WaitForFunctionOptions().setTimeout(ScraperConfig.PAGE_WAITING_TIMEOUT_MS)
            )

            val errorLocator = page.locator(".loginFaild")

            if (errorLocator.isVisible && errorLocator.innerText().trim().isNotEmpty()) {
                val errorText = errorLocator.innerText().trim()
                logger.error("Auth failed with message: {}", errorText)
                throw IllegalArgumentException("Invalid email or password.")
            }

            logger.info("Logged in successfully")
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw IllegalStateException("Login timed out. The site might be extremely slow or down.", e)
        }
    }
}