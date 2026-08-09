package com.facegate.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WIB = ZoneId.of("Asia/Jakarta")

fun formatWib(iso: String?, withSeconds: Boolean = false): String {
    if (iso.isNullOrBlank()) return "-"
    return try {
        val pattern = if (withSeconds) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd HH:mm"
        Instant.parse(iso)
            .atZone(WIB)
            .format(DateTimeFormatter.ofPattern(pattern))
    } catch (_: Exception) {
        iso
    }
}

fun formatWibDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return try {
        Instant.parse(iso).atZone(WIB).toLocalDate().toString()
    } catch (_: Exception) {
        try {
            LocalDate.parse(iso).toString()
        } catch (_: Exception) {
            iso
        }
    }
}
