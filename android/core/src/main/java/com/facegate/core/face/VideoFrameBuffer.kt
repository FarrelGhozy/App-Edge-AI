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

        /** Minimum IoU between consecutive frames — a lower overlap means the
         *  face jumped / was replaced (different person) → collection aborted
         *  to avoid fusing faces of different people (issue #80). */
        const val MIN_FACE_IOU = 0.35f
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
    private var lastFaceRect: Rect? = null

    /** Start a new collection cycle. */
    fun start() {
        clear()
        startTime = System.currentTimeMillis()
        collecting = true
        Log.d(TAG, "Collection started: max=$maxFrames duration=${collectDurationMs}ms")
    }

    /**
     * Add a frame to the buffer. Returns true if added.
     * Returns false (and ABORTS the collection) if the face box jumped
     * dramatically from the previous frame — signals a different person or the
     * user leaving the frame. Caller must reset its scan state.
     */
    fun add(frame: FrameEntry): Boolean {
        if (!collecting) return false
        if (frames.size >= maxFrames) return false

        // Face consistency check (issue #80): same face must stay in frame.
        val prev = lastFaceRect
        if (prev != null && frame.faceRect != null) {
            val cur = frame.faceRect
            val iou = computeIoU(
                prev.left, prev.top, prev.right, prev.bottom,
                cur.left, cur.top, cur.right, cur.bottom
            )
            if (iou < MIN_FACE_IOU) {
                Log.w(TAG, "Face box jumped (IoU=${"%.2f".format(iou)}) — aborting collection")
                clear()
                return false
            }
        }
        lastFaceRect = frame.faceRect
        frames.add(frame)
        Log.d(TAG, "Frame added: ${frames.size}/$maxFrames (qs=${"%.3f".format(frame.qualityScore)})")
        return true
    }

    /** Intersection-over-union of two rects; 0 if either is empty. Pure
     *  geometry (no Rect methods) so it can be unit-tested without Robolectric. */
    internal fun computeIoU(
        aLeft: Int, aTop: Int, aRight: Int, aBottom: Int,
        bLeft: Int, bTop: Int, bRight: Int, bBottom: Int
    ): Float {
        val left = maxOf(aLeft, bLeft)
        val top = maxOf(aTop, bTop)
        val right = minOf(aRight, bRight)
        val bottom = minOf(aBottom, bBottom)
        if (right <= left || bottom <= top) return 0f
        val interArea = (right - left) * (bottom - top)
        val aArea = (aRight - aLeft) * (aBottom - aTop)
        val bArea = (bRight - bLeft) * (bBottom - bTop)
        val union = aArea + bArea - interArea
        return if (union <= 0) 0f else interArea.toFloat() / union
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
