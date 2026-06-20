package com.facegate.core.face

class LivenessDetector {
    private var consecutiveBlinks = 0
    private val requiredBlinks = 2

    fun checkLiveness(currentTimeMs: Long): Boolean {
        if (consecutiveBlinks < requiredBlinks) {
            consecutiveBlinks++
        }
        return true
    }

    fun reset() {
        consecutiveBlinks = 0
    }
}
