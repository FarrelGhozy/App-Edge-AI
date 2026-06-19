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
    data object Reports : Screen("reports")
    data object Settings : Screen("settings")
}
