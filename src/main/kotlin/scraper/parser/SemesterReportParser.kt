package scraper.parser

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.TimeoutError
import models.student.FinalExamScore
import models.student.SemesterReport
import models.student.SemesterSubjectScores
import org.slf4j.LoggerFactory
import scraper.ScraperConfig

object SemesterReportParser {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun parse(page: Page): SemesterReport {

        try {
            page.waitForSelector(
                ".table-responsive",
                Page.WaitForSelectorOptions().setTimeout(ScraperConfig.TABLE_WAITING_TIMEOUT_MS)
            )
        } catch (_: TimeoutError) {
            logger.warn("No grade tables found for this student. Returning empty report.")
            return SemesterReport(emptyList(), emptyList())
        }

        val tables = page.locator(".table-responsive").all()

        val subjectScores = parseTable(tables.getOrNull(0)) { cells ->
            SemesterSubjectScores(
                subject = cells[0].innerText().trim(),
                firstSemesterScore = cells[1].parseInt(),
                secondSemesterScore = cells[2].parseInt(),
                averageScore = cells[3].parseInt()
            )
        }

        val finalExams = parseTable(tables.getOrNull(1)) { cells ->
            FinalExamScore(
                subject = cells[0].innerText().trim(),
                examScore = cells[1].innerText().trim(),
                retakeScore = cells[2].innerText().trim(),
            )
        }
        return SemesterReport(subjects = subjectScores, finalExams = finalExams)
    }

    private fun <T> parseTable(tableLocator: Locator?, mapper: (List<Locator>) -> T): List<T> {
        if (tableLocator == null) return emptyList()

        val rows = tableLocator.locator("tbody tr").all()

        return rows.mapNotNull { row ->

            val cells = row.locator("td").all()

            if (cells.isEmpty() || cells[0].innerText().isBlank()) {
                null
            } else {
                mapper(cells)
            }
        }
    }

    private fun Locator.parseInt(): Int {
        val text = this.innerText().trim()
        return text.toIntOrNull() ?: 0
    }
}