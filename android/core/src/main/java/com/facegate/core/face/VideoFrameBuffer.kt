package com.facegate.core.face

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log

/**
 * Ring buffer for collecting video frames during scan.
 *
 * Collects frames from camera preview for a fixed duration or until max count.
 * Each frame carries metadata: quality score, face rect, and (later) embedding.
 */
class VideoFrameBuffer(
    val maxFrames: Int = 15,
    val collectDurationMs: Long = 1500L
) {
    companion object {
        private const val TAG = "VideoFrameBuffer"
    }

    data class FrameEntry(
        val bitmap: Bitmap,
        val timestamp: Long,
        val faceRect: Rect? = null,
        val qualityScore: Float = 0f,
        val embedding: FloatArray? = null
    ) {
        /** Compare by quality score descending (for K-best selection). */
        fun qualityDescending(other: FrameEntry): Int =
            other.qualityScore.compareTo(qualityScore)
    }

    private val frames = mutableListOf<FrameEntry>()
    private var startTime: Long = 0L
    private var collecting = false

    /** Start a new collection cycle. */
    fun start() {
        clear()
        startTime = System.currentTimeMillis()
        collecting = true
        Log.d(TAG, "Collection started: max=$maxFrames duration=${collectDurationMs}ms")
    }

    /** Add a frame to the buffer. Returns true if added, false if buffer full. */
    fun add(frame: FrameEntry): Boolean {
        if (!collecting) return false
        if (frames.size >= maxFrames) return false
        frames.add(frame)
        Log.d(TAG, "Frame added: ${frames.size}/$maxFrames (qs=${"%.3f".format(frame.qualityScore)})")
        return true
    }

    /** True if buffer reached max capacity. */
    fun isFull(): Boolean = frames.size >= maxFrames

    /** True if collection duration has expired. */
    fun isExpired(): Boolean {
        if (!collecting) return true
        return System.currentTimeMillis() - startTime > collectDurationMs
    }

    /** Progress as float 0.0..1.0 (based on whichever fills first: count or time). */
    fun getProgress(): Float {
        if (!collecting) return 0f
        val byCount = frames.size.toFloat() / maxFrames
        val byTime = (System.currentTimeMillis() - startTime).toFloat() / collectDurationMs
        return minOf(byCount, byTime, 1.0f)
    }

    /** Get all collected frames so far. */
    fun getFrames(): List<FrameEntry> = frames.toList()

    /** Number of frames collected. */
    fun size(): Int = frames.size

    /** Returns true if at least one frame was collected and collection ended. */
    fun hasFrames(): Boolean = frames.isNotEmpty()

    /** Reset buffer. */
    fun clear() {
        // Note: bitmaps are owned by caller — we don't recycle here
        frames.clear()
        startTime = 0L
        collecting = false
    }

    /** Stop collecting (called when ready to process). */
    fun stop() {
        collecting = false
    }

    fun isCollecting(): Boolean = collecting
}
