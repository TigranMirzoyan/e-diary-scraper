package scraper.parser

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitUntilState
import models.student.AcademicRecord
import models.student.SemesterReport
import models.student.StudentProfile
import org.slf4j.LoggerFactory
import scraper.ScraperConfig

object StudentProfileParser {
    private val logger = LoggerFactory.getLogger(StudentProfileParser::class.java)
    private val fastNavOptions = Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)

    fun extractProfiles(page: Page): List<StudentProfile> {
        val metadataList = collectStudentsMetadata(page)
        logger.info("Found {} student(s). Extracting profiles...", metadataList.size)

        return metadataList.map { meta ->
            processFullProfile(page, meta)
        }
    }

    private fun collectStudentsMetadata(page: Page): List<StudentMetadata> {
        page.waitForSelector(".users-card")
        val allCards = page.locator(".user-card").all()

        val validCards = if (allCards.isNotEmpty()) allCards.dropLast(1) else emptyList()

        return validCards.map { card ->
            val h6Lines = card.locator("h6").first().innerText().trim().split("\n")
            val semesterButton = card.locator("a:has-text('Կիսամյակներ')")
            val gradebookButton = card.locator("a:has-text('Օրագիր')")

            StudentMetadata(
                name = card.locator("h4").innerText().trim(),
                photoUrl = normalizePhotoUrl(card.locator(".card-header-img img").getAttribute("src")),
                schoolName = h6Lines.getOrNull(0)?.trim().orEmpty(),
                gradeName = h6Lines.getOrNull(1)?.trim().orEmpty(),
                semesterUrl = if (semesterButton.count() > 0) semesterButton.first().getAttribute("href") else null,
                gradebookUrl = if (gradebookButton.count() > 0) gradebookButton.first().getAttribute("href") else null
            )
        }
    }

    private fun processFullProfile(page: Page, meta: StudentMetadata): StudentProfile {
        logger.info("Processing data for student: {}", meta.name)

        val semesterData = meta.semesterUrl?.let { url ->
            page.navigate(url, fastNavOptions)
            SemesterReportParser.parse(page)
        } ?: SemesterReport(emptyList(), emptyList())
        logger.info("Processed semester data")

        val gradebooksData = meta.gradebookUrl?.let { url ->
            page.navigate(url, fastNavOptions)
            GradebookYearParser.parse(page)
        } ?: emptyList()
        logger.info("Processed gradebook data")

        return StudentProfile(
            name = meta.name,
            photoUrl = meta.photoUrl,
            schoolName = meta.schoolName,
            gradeName = meta.gradeName,
            academicRecord = AcademicRecord(gradebooks = gradebooksData, semesterReport = semesterData)
        )
    }

    private fun normalizePhotoUrl(src: String): String = when {
        src.isEmpty() -> ""
        src.startsWith("data:image") || src.startsWith("http") -> src
        else -> "${ScraperConfig.BASE_URL}$src"
    }
}

private data class StudentMetadata(
    val name: String,
    val photoUrl: String,
    val schoolName: String,
    val gradeName: String,
    val semesterUrl: String?,
    val gradebookUrl: String?
)