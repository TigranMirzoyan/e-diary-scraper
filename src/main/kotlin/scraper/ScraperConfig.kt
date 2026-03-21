package scraper

object ScraperConfig {
    const val BASE_URL = "https://e-diary.emis.am"
    const val AUTH_URL = "$BASE_URL/auth"
    const val CHILDREN_URL = "$BASE_URL/children"
    const val SITE_KEY = "6LcOJiUpAAAAABAahHFpfsamoiTJy2b5p-8dt_Tm"
    const val FETCHING_TIMEOUT_MS = 90_000L
    const val PAGE_WAITING_TIMEOUT_MS = 15_000.0
    const val TABLE_WAITING_TIMEOUT_MS = 3_000.0
}