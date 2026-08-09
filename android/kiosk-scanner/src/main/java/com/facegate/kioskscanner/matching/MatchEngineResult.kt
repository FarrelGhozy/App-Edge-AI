package com.facegate.kioskscanner.matching

import com.facegate.core.engine.ToggleAction
import com.facegate.core.face.MatchDecision

// #103: MatchEngineResult dipindah dari MatchEngine.kt (class TFLite lama dihapus)
// karena hanya hasil panduan yang dipakai oleh ScannerViewModel / VideoMatchEngine.
sealed class MatchEngineResult {
    data class Matched(
        val studentId: String,
        val studentName: String,
        val action: ToggleAction,
        val isViolation: Boolean = false,
        val violationMessage: String? = null,
        val confidence: Float = 1f,
        // #91: decision level CONFIDENTIAL/MEDIUM/WEAK dipetakan ke UX
        val decision: MatchDecision? = null
    ) : MatchEngineResult()

    data class Unknown(val confidence: Float) : MatchEngineResult()
    data object LivenessFailed : MatchEngineResult()
    data object NoFace : MatchEngineResult()
    data class QualityFailed(val reason: String) : MatchEngineResult()

    /**
     * #135: Mode verifikasi izin — wajah dikenali tapi BUKAN orang yang sedang
     * diverifikasi. Scanner harus menampilkan pesan yang jelas ("ini bukan X").
     */
    data class WrongPerson(val matchedName: String) : MatchEngineResult()
}