package com.facegate.adminapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.facegate.adminapp.auth.LoginScreen
import com.facegate.adminapp.dashboard.DashboardScreen
import com.facegate.adminapp.students.StudentDetailScreen
import com.facegate.adminapp.students.StudentFormScreen
import com.facegate.adminapp.students.StudentListScreen
import com.facegate.adminapp.attendance.AttendanceScreen
import com.facegate.adminapp.permits.PermitDetailScreen
import com.facegate.adminapp.permits.PermitFormScreen
import com.facegate.adminapp.permits.PermitListScreen
import com.facegate.adminapp.rules.RuleFormScreen
import com.facegate.adminapp.rules.RuleListScreen
import com.facegate.adminapp.settings.SettingsScreen
import com.facegate.adminapp.devices.DeviceListScreen
import com.facegate.adminapp.devices.DeviceDetailScreen
import com.facegate.adminapp.notifications.NotificationListScreen
import com.facegate.adminapp.violations.ViolationDetailScreen
import com.facegate.adminapp.violations.ViolationListScreen
import com.facegate.adminapp.reports.DailyReportScreen
import com.facegate.adminapp.reports.MonthlyReportScreen
import com.facegate.adminapp.reports.ReportScreen
import com.facegate.adminapp.reports.ViolationReportScreen
import com.facegate.adminapp.reports.OutsideHoursReportScreen
import com.facegate.adminapp.monitor.OutsideNowScreen
import com.facegate.adminapp.register.FaceRegisterScreen
import com.facegate.adminapp.students.ImportCsvScreen
import com.facegate.adminapp.holiday.HolidayListScreen
import com.facegate.adminapp.holiday.HolidayFormScreen
import com.facegate.adminapp.sync.SyncScreen
import com.facegate.adminapp.permits.PendingApprovalScreen
import com.facegate.adminapp.monitor.ToggleStatusScreen

@Composable
fun AppNavigator(
    startDestination: String = Screen.Splash.route,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(navController)
        }
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.Students.route) {
            StudentListScreen(navController = navController)
        }
        composable(
            Screen.StudentDetail.route,
            arguments = listOf(navArgument("studentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getString("studentId") ?: return@composable
            StudentDetailScreen(studentId = studentId, navController = navController)
        }
        composable(
            Screen.StudentForm.route,
            arguments = listOf(navArgument("studentId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getString("studentId")
            StudentFormScreen(studentId = studentId, navController = navController)
        }
        composable(Screen.ImportCsv.route) {
            ImportCsvScreen(navController = navController)
        }
        composable(Screen.Attendance.route) {
            AttendanceScreen(navController = navController)
        }
        composable(Screen.Permits.route) {
            PermitListScreen(navController = navController)
        }
        composable(Screen.PermitForm.route) {
            PermitFormScreen(navController = navController)
        }
        composable(
            Screen.PermitDetail.route,
            arguments = listOf(navArgument("permitId") { type = NavType.StringType })
        ) { backStackEntry ->
            val permitId = backStackEntry.arguments?.getString("permitId") ?: return@composable
            PermitDetailScreen(permitId = permitId, navController = navController)
        }
        composable(Screen.Rules.route) {
            RuleListScreen(navController = navController)
        }
        composable(
            Screen.RuleForm.route,
            arguments = listOf(navArgument("ruleId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getString("ruleId")
            RuleFormScreen(ruleId = ruleId, navController = navController)
        }
        composable(Screen.Devices.route) {
            DeviceListScreen(navController = navController)
        }
        composable(
            Screen.DeviceDetail.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            DeviceDetailScreen(deviceId = deviceId, navController = navController)
        }
        composable(Screen.Notifications.route) {
            NotificationListScreen(navController = navController)
        }
        composable(Screen.Violations.route) {
            ViolationListScreen(navController = navController)
        }
        composable(
            Screen.ViolationDetail.route,
            arguments = listOf(navArgument("violationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val violationId = backStackEntry.arguments?.getString("violationId") ?: return@composable
            ViolationDetailScreen(violationId = violationId, navController = navController)
        }
        composable(Screen.Reports.route) {
            ReportScreen(navController = navController)
        }
        composable(Screen.DailyReport.route) {
            DailyReportScreen(navController = navController)
        }
        composable(Screen.MonthlyReport.route) {
            MonthlyReportScreen(navController = navController)
        }
        composable(Screen.ViolationReport.route) {
            ViolationReportScreen(navController = navController)
        }
        composable(Screen.OutsideHoursReport.route) {
            OutsideHoursReportScreen(navController = navController)
        }
        composable(Screen.OutsideNow.route) {
            OutsideNowScreen(navController = navController)
        }
        composable(
            Screen.FaceRegister.route,
            arguments = listOf(navArgument("studentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getString("studentId") ?: return@composable
            FaceRegisterScreen(studentId = studentId, navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Holidays.route) {
            HolidayListScreen(navController = navController)
        }
        composable(
            Screen.HolidayForm.route,
            arguments = listOf(navArgument("holidayId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { backStackEntry ->
            val holidayId = backStackEntry.arguments?.getString("holidayId")
            HolidayFormScreen(holidayId = holidayId, navController = navController)
        }
        composable(Screen.Sync.route) {
            SyncScreen(navController = navController)
        }
        composable(Screen.PendingApproval.route) {
            PendingApprovalScreen(navController = navController)
        }
        composable(Screen.ToggleStatus.route) {
            ToggleStatusScreen(navController = navController)
        }
    }
}
