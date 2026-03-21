package io.github

import core.module
import io.ktor.server.testing.*
import kotlin.test.Test

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
    }
}
