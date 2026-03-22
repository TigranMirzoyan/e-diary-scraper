package scraper.parser

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.SelectOption
import models.student.GradebookYear
import models.student.ScoreRecord
import models.student.SemesterData
import models.student.SubjectScores
import org.slf4j.LoggerFactory
import scraper.ScraperConfig

object GradebookYearParser {
    private class SemesterContext(var totalAbsences: Int = 0)
    private const val EMPTY_SCHEDULE_TEXT = "Դասացուցակ կազմված չէ"
    private const val ABSENCE_MARKER = "Բ"
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val logger = LoggerFactory.getLogger(GradebookYearParser::class.java)

    fun parse(page: Page): List<GradebookYear> {
        try {
            page.waitForSelector(
                "form#myForm", Page.WaitForSelectorOptions().setTimeout(ScraperConfig.TABLE_WAITING_TIMEOUT_MS)
            )
        } catch (_: TimeoutError) {
            logger.warn("No gradebook form found for this student. Returning empty list.")
            return emptyList()
        }

        val yearOptions = page.locator("select[name='education_year'] option")
            .all()
            .mapNotNull { it.getAttribute("value") }
            .filter { it.isNotBlank() }

        return yearOptions.map { yearValue ->
            parseYear(page, yearValue)
        }
    }

    private fun parseYear(page: Page, yearValue: String): GradebookYear {
        page.selectOption("select[name='education_year']", SelectOption().setValue(yearValue))

        val semesterOptions = page.locator("select[name='semester'] option")
            .all()
            .mapNotNull { it.getAttribute("value") }
            .filter { it.isNotBlank() }

        val parsedSemesters = semesterOptions.map { semesterValue ->
            parseSemester(page, semesterValue)
        }

        return GradebookYear(year = yearValue, semesters = parsedSemesters)
    }

    private fun parseSemester(page: Page, semesterValue: String): SemesterData {
        page.selectOption(
            "select[name='semester']",
            SelectOption().setValue(semesterValue)
        )

        val semesterNumber = semesterValue.toIntOrNull() ?: 1
        val targetMonths = when (semesterNumber) {
            1 -> listOf("9", "10", "11", "12")
            else -> listOf("1", "2", "3", "4", "5")
        }

        val aggregatedSubjects = mutableMapOf<String, MutableList<ScoreRecord>>()

        return with(SemesterContext()) {
            targetMonths.forEach { monthValue ->
                val monthlySubjects = fetchAndParseMonth(page, monthValue)

                monthlySubjects.forEach { subject ->
                    val existingRecords = aggregatedSubjects.getOrPut(subject.subject) { mutableListOf() }
                    existingRecords.addAll(subject.records)
                }
            }

            val finalSemesterSubjects = aggregatedSubjects.map { (subjectName, records) ->
                SubjectScores(subject = subjectName, records = records)
            }

            SemesterData(
                semester = semesterNumber,
                absences = totalAbsences,
                subjects = finalSemesterSubjects,
            )
        }
    }

    private fun SemesterContext.fetchAndParseMonth(page: Page, monthValue: String): List<SubjectScores> {
        val isUiReady = isMonthUiReady(page, monthValue)
        if (!isUiReady) return emptyList()

        val weekButtonsLocator = page.locator(".card-header a[data-week]")

        val allWeeklyData = (0 until weekButtonsLocator.count()).flatMap { i ->
            fetchAndParseWeek(page, weekButtonsLocator.nth(i))
        }

        return allWeeklyData
            .groupBy { it.subject }
            .map { (subjectName, subjectScoresList) ->
                val combinedRecords = subjectScoresList.flatMap { it.records }
                SubjectScores(subjectName, combinedRecords)
            }
    }

    private fun isMonthUiReady(page: Page, monthValue: String): Boolean {
        page.selectOption("select[name='month']", SelectOption().setValue(monthValue))
        page.dispatchEvent("select[name='month']", "change")
        page.click("input[name='diary_search']")

        return try {
            page.waitForFunction(
                """() => {
                    const buttons = document.querySelectorAll('.card-header a[data-week]');
                    const hasData = buttons.length > 0;
                    const isEmpty = document.body.innerText.includes('$EMPTY_SCHEDULE_TEXT');
                    return hasData || isEmpty;
                }""",
                null,
                Page.WaitForFunctionOptions().setTimeout(ScraperConfig.TABLE_WAITING_TIMEOUT_MS)
            )
            true
        } catch (_: TimeoutError) {
            logger.warn("Month $monthValue failed to load any UI state.")
            false
        }
    }

    private fun SemesterContext.fetchAndParseWeek(page: Page, button: Locator): List<SubjectScores> {
        val isInactive = (button.getAttribute("class") ?: "").contains("btn-outline-primary")

        if (isInactive) {
            try {
                page.waitForResponse({ response ->
                    response.request().resourceType() == "xhr" || response.request().resourceType() == "fetch"
                }, { button.click() })
            } catch (_: TimeoutError) {
                logger.debug("Did not detect a network response for the button click. Relying on DOM fallback.")
            }
        } else {
            button.click()
        }

        try {
            page.waitForFunction(
                """() => {
                const block = document.querySelector('.row.card-block');
                if (!block) return false;
                
                const hasHeaders = block.querySelector('h6.sub-title') !== null;
                const isEmpty = block.innerText.includes('$EMPTY_SCHEDULE_TEXT');
                return hasHeaders || isEmpty;
            }""", null, Page.WaitForFunctionOptions().setTimeout(ScraperConfig.TABLE_WAITING_TIMEOUT_MS)
            )
        } catch (_: TimeoutError) {
            val weekText = button.textContent()?.trim() ?: "Unknown Week"
            logger.warn("Timeout: Failed to load data for week [$weekText]. Skipping.")
            return emptyList()
        }

        if (page.locator(".row.card-block h6.sub-title").count() == 0) return emptyList()

        return parseSubjectTable(page)
    }

    private fun SemesterContext.parseSubjectTable(page: Page): List<SubjectScores> {
        val subjectsMap = mutableMapOf<String, MutableList<ScoreRecord>>()
        val dayColumns = page.locator(".row.card-block div[class*='col-']").all()

        val fastOptions = Locator.TextContentOptions().setTimeout(ScraperConfig.TEXT_READING_SPEED_MS)

        dayColumns.forEach { dayColumn ->
            parseDayColumn(dayColumn, fastOptions, subjectsMap)
        }

        return subjectsMap.map { (name, records) -> SubjectScores(name, records) }
    }

    private fun SemesterContext.parseDayColumn(
        dayColumn: Locator,
        fastOptions: Locator.TextContentOptions,
        subjectsMap: MutableMap<String, MutableList<ScoreRecord>>
    ) {
        val header = dayColumn.locator("h6.sub-title").first()

        val headerText = try {
            header.textContent(fastOptions)?.trim() ?: ""
        } catch (_: TimeoutError) {
            return
        }

        if (headerText.isBlank()) return

        val headerParts = headerText.split(WHITESPACE_REGEX, limit = 2)
        val dateStr = headerParts.getOrNull(0) ?: ""
        val dayOfWeekStr = headerParts.getOrNull(1) ?: ""

        val subjectRows = dayColumn.locator("ul.basic-list > li").all()
        subjectRows.forEach { row ->
            parseSubjectRow(row, dateStr, dayOfWeekStr, fastOptions, subjectsMap)
        }
    }

    private fun SemesterContext.parseSubjectRow(
        row: Locator,
        dateStr: String,
        dayOfWeekStr: String,
        fastOptions: Locator.TextContentOptions,
        subjectsMap: MutableMap<String, MutableList<ScoreRecord>>
    ) {
        val aTag = row.locator("a").first()
        if (aTag.count() == 0) return

        try {
            val badge = aTag.locator("label.badge").first()
            val gradeText = if (badge.count() > 0) badge.textContent(fastOptions)?.trim() ?: "" else ""

            var subjectName = aTag.textContent(fastOptions)?.trim() ?: ""
            if (gradeText.isNotEmpty()) {
                subjectName = subjectName.removeSuffix(gradeText).trim()
            }
            subjectName = subjectName.substringBefore('\n').trim()
            if (subjectName.isBlank()) return

            if (gradeText.equals(ABSENCE_MARKER, ignoreCase = true)) {
                totalAbsences++
                return
            }

            val score = gradeText.toIntOrNull()
            val recordsList = subjectsMap.getOrPut(subjectName) { mutableListOf() }

            if (score == null) return
            recordsList.add(ScoreRecord(dateStr, dayOfWeekStr, score))
        } catch (_: TimeoutError) {
            logger.debug("Timeout reading a subject row on $dateStr ($dayOfWeekStr). Row might be empty or malformed. Skipping.")
            return
        }
    }
}