package scraper

import com.microsoft.playwright.Browser
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory

object PlaywrightPool {
    private val logger = LoggerFactory.getLogger(PlaywrightPool::class.java)
    private lateinit var workerChannel: Channel<PlaywrightWorker>
    private val workers = mutableListOf<PlaywrightWorker>()

    suspend fun initPool(poolSize: Int) {
        logger.info("Initializing Playwright pool with {} workers...", poolSize)
        workerChannel = Channel(poolSize)

        for (i in 1..poolSize) {
            logger.info("Starting worker {} ...", i)
            val worker = PlaywrightWorker(i)
            worker.start()
            workers.add(worker)
            workerChannel.send(worker)
            logger.info("Worker {} is ready.", i)
        }
        logger.info("Playwright pool fully initialized!")
    }

    suspend fun <T> withBrowser(block: suspend (Browser) -> T): T {
        val worker = workerChannel.receive()
        return try {
            logger.info("Task successfully received and starting executing on worker {}", worker.id)
            worker.execute(block)
        } finally {
            workerChannel.send(worker)
            logger.info("Worker {} successfully returner into the pool.", worker.id)
        }
    }

    fun closePool() {
        logger.info("Shutting down Playwright pool...")
        workers.forEach { it.stop() }
        workerChannel.close()
    }
}