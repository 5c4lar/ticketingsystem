/*
 * Backoff.java
 *
 * Created on November 19, 2006, 5:43 PM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */
package ticketingsystem.lock

import java.util.*

/**
 * Adaptive exponential backoff class. Encapsulates back-off code
 * common to many locking classes.
 * @author Maurice Herlihy
 */
/**
 * Prepare to pause for random duration.
 * @param min smallest back-off
 * @param max largest back-off
 */
class Backoff(min: Int, max: Int) {
    private val minDelay: Int
    private val maxDelay: Int
    private var limit // wait between limit and 2*limit
            : Int
    private val random // add randomness to wait
            : Random

    init {
        require(max >= min) { "max must be greater than min" }
        minDelay = MIN_DELAY ?: min
        maxDelay = MAX_DELAY ?: max
        limit = minDelay
        random = Random()
    }

    /**
     * Backoff for random duration.
     * @throws java.lang.InterruptedException
     */
    @Throws(InterruptedException::class)
    fun backoff() {
        val delay = random.nextInt(limit)
        if (limit < maxDelay) { // double limit if less than max
            limit *= 2
        }
//        Thread.sleep(delay.toLong())
        Thread.sleep(0, delay)
    }

    companion object {
        var MIN_DELAY: Int? = null
        var MAX_DELAY: Int? = null
            get() {
                return if (MIN_DELAY == null) null else MIN_DELAY!! * 128
            }
            private set
    }
}