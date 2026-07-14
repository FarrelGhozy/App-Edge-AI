package com.facegate.adminapp.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Students : Screen("students")
    data object StudentDetail : Screen("students/{studentId}") {
        fun createRoute(studentId: String) = "students/$studentId"
    }
    data object StudentForm : Screen("students/form?studentId={studentId}") {
        fun createRoute(studentId: String? = null) =
            if (studentId != null) "students/form?studentId=$studentId" else "students/form"
    }
    data object ImportCsv : Screen("students/import")
    data object Attendance : Screen("attendance")
    data object Permits : Screen("permits")
    data object PermitForm : Screen("permits/form")
    data object PermitDetail : Screen("permits/{permitId}") {
        fun createRoute(permitId: String) = "permits/$permitId"
    }
    data object Rules : Screen("rules")
    data object RuleForm : Screen("rules/form?ruleId={ruleId}") {
        fun createRoute(ruleId: String? = null) =
            if (ruleId != null) "rules/form?ruleId=$ruleId" else "rules/form"
    }
    data object Devices : Screen("devices")
    data object DeviceDetail : Screen("devices/{deviceId}") {
        fun createRoute(deviceId: String) = "devices/$deviceId"
    }
    data object Notifications : Screen("notifications")
    data object Violations : Screen("violations")
    data object ViolationDetail : Screen("violations/{violationId}") {
        fun createRoute(violationId: String) = "violations/$violationId"
    }
    data object Reports : Screen("reports")
    data object DailyReport : Screen("reports/daily")
    data object MonthlyReport : Screen("reports/monthly")
    data object ViolationReport : Screen("reports/violations")
    data object OutsideHoursReport : Screen("reports/outside-hours")
    data object OutsideNow : Screen("reports/outside-now")
    data object FaceRegister : Screen("students/{studentId}/register-face") {
        fun createRoute(studentId: String) = "students/$studentId/register-face"
    }
    data object Settings : Screen("settings")
    data object Holidays : Screen("holidays")
    data object HolidayForm : Screen("holidays/form?holidayId={holidayId}") {
        fun createRoute(holidayId: String? = null) =
            if (holidayId != null) "holidays/form?holidayId=$holidayId" else "holidays/form"
    }
    data object Sync : Screen("sync")
    data object PendingApproval : Screen("permits/pending")
    data object ToggleStatus : Screen("toggle-status")
}
