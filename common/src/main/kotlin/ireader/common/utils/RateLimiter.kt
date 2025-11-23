package ireader.common.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limiter for controlling request frequency to sources.
 * Prevents overwhelming servers and getting blocked.
 */
class RateLimiter(
    private val permits: Int = 1,
    private val periodMillis: Long = 1000
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>(permits)
    
    /**
     * Acquires a permit, waiting if necessary.
     * Suspends until a permit is available.
     */
    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            
            // Remove old timestamps outside the time window
            while (timestamps.isNotEmpty() && now - timestamps.first() >= periodMillis) {
                timestamps.removeFirst()
            }
            
            // If we've hit the limit, wait
            if (timestamps.size >= permits) {
                val oldestTimestamp = timestamps.first()
                val waitTime = periodMillis - (now - oldestTimestamp)
                if (waitTime > 0) {
                    delay(waitTime)
                }
                // Remove the oldest after waiting
                timestamps.removeFirst()
            }
            
            // Add current timestamp
            timestamps.addLast(System.currentTimeMillis())
        }
    }
    
    /**
     * Executes a block with rate limiting.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        acquire()
        return block()
    }
}

/**
 * Global rate limiter manager for managing per-source rate limits.
 */
object RateLimiterManager {
    private val limiters = ConcurrentHashMap<String, RateLimiter>()
    
    /**
     * Gets or creates a rate limiter for a source.
     * 
     * @param sourceId Unique identifier for the source
     * @param permits Number of permits per period
     * @param periodMillis Time period in milliseconds
     */
    fun getOrCreate(
        sourceId: String,
        permits: Int = 2,
        periodMillis: Long = 1000
    ): RateLimiter {
        return limiters.getOrPut(sourceId) {
            RateLimiter(permits, periodMillis)
        }
    }
    
    /**
     * Removes a rate limiter for a source.
     */
    fun remove(sourceId: String) {
        limiters.remove(sourceId)
    }
    
    /**
     * Clears all rate limiters.
     */
    fun clear() {
        limiters.clear()
    }
}
