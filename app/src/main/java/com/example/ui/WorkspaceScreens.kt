package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.R

internal enum class WorkspaceRole(
    val title: String,
    val initials: String,
    val summary: String,
    val color: Color,
    val tint: Color,
    val homeRoute: String
) {
    SUPER_ADMIN(
        "Super Admin", "SA", "Institution governance and platform health",
        Color(0xFF1456A0), Color(0xFFE7F0FC), "admin-dashboard"
    ),
    HOD(
        "HOD", "HD", "Department academics, roster and reports",
        Color(0xFFBA6415), Color(0xFFFFF0E1), "admin-dashboard"
    ),
    TEACHER(
        "Teacher", "TE", "Assigned classes and attendance review",
        Color(0xFF167A55), Color(0xFFE3F5EC), "dashboard"
    ),
    STUDENT(
        "Student", "ST", "Your profile, timetable and attendance",
        Color(0xFF7540B5), Color(0xFFF0E9FA), "student-dashboard"
    ),
    GUARDIAN(
        "Guardian", "GU", "Verified ward links and notifications",
        Color(0xFFC3313E), Color(0xFFFCE9EB), "timetable-notifications"
    );

    companion object {
        fun fromServerValue(value: String?): WorkspaceRole? =
            when (value?.trim()?.lowercase()?.replace('_', ' ')?.replace('-', ' ')) {
                "super admin", "superadmin", "admin", "administrator" -> SUPER_ADMIN
                "hod", "department admin", "department administrator" -> HOD
                "teacher", "faculty" -> TEACHER
                "student" -> STUDENT
                "guardian", "parent" -> GUARDIAN
                else -> null
            }
    }
}

internal data class WorkspaceScreen(
    val number: Int,
    val route: String,
    val title: String,
    val subtitle: String,
    val roles: Set<WorkspaceRole>,
    val nextRoute: String
)

internal data class WorkspaceModule(
    val number: Int,
    val title: String,
    val route: String,
    val roles: Set<WorkspaceRole>
)

private val everyone = WorkspaceRole.values().toSet()
private val administrators = setOf(WorkspaceRole.SUPER_ADMIN, WorkspaceRole.HOD)
private val staff = administrators + WorkspaceRole.TEACHER

internal val workspaceScreens = listOf(
    WorkspaceScreen(5, "student-dashboard", "Student dashboard",
        "A personal view of attendance, classes and profile status.", setOf(WorkspaceRole.STUDENT), "student-history"),
    WorkspaceScreen(6, "dashboard", "Faculty dashboard",
        "Assigned classes, attendance sessions and reports.", setOf(WorkspaceRole.TEACHER), "class-session-selection"),
    WorkspaceScreen(7, "admin-dashboard", "Administrator dashboard",
        "Institution or department operations and service status.", administrators, "student-roster"),
    WorkspaceScreen(8, "student-roster", "Student roster & enrollment",
        "Authorized roster and enrollment workspace.", administrators, "admin-dashboard"),
    WorkspaceScreen(9, "enrollment", "Face profile enrollment",
        "Guided student identity and face-sample enrollment.", setOf(WorkspaceRole.STUDENT) + administrators, "weekly-face-check"),
    WorkspaceScreen(10, "weekly-face-check", "Weekly profile check",
        "Review profile status and capture a new sample when requested.", setOf(WorkspaceRole.STUDENT), "enrollment"),
    WorkspaceScreen(11, "class-session-selection", "Class & session selection",
        "Select an assigned class before opening an attendance session.", setOf(WorkspaceRole.TEACHER), "dashboard"),
    WorkspaceScreen(12, "four-photo-workflow", "Four-photo capture",
        "Capture classroom evidence from four coverage zones.", setOf(WorkspaceRole.TEACHER), "ai-processing"),
    WorkspaceScreen(13, "ai-processing", "AI processing status",
        "View the status of a real evidence-processing job.", setOf(WorkspaceRole.TEACHER), "recognition-results"),
    WorkspaceScreen(14, "recognition-results", "Recognition results",
        "Review candidates and uncertainty categories before attendance decisions.", setOf(WorkspaceRole.TEACHER), "teacher-review"),
    WorkspaceScreen(15, "teacher-review", "Faculty verification",
        "Resolve candidates and verify the eligible roster before finalization.", setOf(WorkspaceRole.TEACHER), "attendance-saved-report"),
    WorkspaceScreen(16, "attendance-saved-report", "Attendance saved & evidence",
        "Open a finalized session summary and its actual evidence.", staff + WorkspaceRole.STUDENT + WorkspaceRole.GUARDIAN, "reports"),
    WorkspaceScreen(17, "student-history", "Student attendance history",
        "Personal attendance records and dispute status.", setOf(WorkspaceRole.STUDENT), "timetable-notifications"),
    WorkspaceScreen(18, "reports", "Reports & analytics",
        "Authorized attendance reports and evidence access.", staff, "attendance-saved-report"),
    WorkspaceScreen(19, "timetable-notifications", "Timetable & notifications",
        "Schedules and notification history for your authorized account.", everyone, "profile-settings"),
    WorkspaceScreen(20, "profile-settings", "Profile, settings & security",
        "Account, session and role-appropriate security controls.", everyone, "role-portal")
)

internal val workspaceModules = listOf(
    WorkspaceModule(1, "Foundation", "admin-dashboard", administrators),
    WorkspaceModule(2, "Identity, sessions & RBAC", "profile-settings", everyone),
    WorkspaceModule(3, "Academic engine", "student-roster", administrators),
    WorkspaceModule(4, "Timetable", "timetable-notifications", everyone),
    WorkspaceModule(5, "Student self-enrollment", "enrollment", setOf(WorkspaceRole.STUDENT) + administrators),
    WorkspaceModule(6, "Face profile lifecycle", "weekly-face-check", setOf(WorkspaceRole.STUDENT)),
    WorkspaceModule(7, "Attendance state machine", "teacher-review", setOf(WorkspaceRole.TEACHER)),
    WorkspaceModule(8, "Four-photo capture", "four-photo-workflow", setOf(WorkspaceRole.TEACHER)),
    WorkspaceModule(9, "AI evidence pipeline", "ai-processing", setOf(WorkspaceRole.TEACHER)),
    WorkspaceModule(10, "Teacher review & finalization", "teacher-review", setOf(WorkspaceRole.TEACHER)),
    WorkspaceModule(11, "Reports & photo evidence", "reports", staff),
    WorkspaceModule(12, "Guardian notifications", "timetable-notifications", setOf(WorkspaceRole.GUARDIAN, WorkspaceRole.STUDENT)),
    WorkspaceModule(13, "Role portals & analytics", "role-portal", everyone),
    WorkspaceModule(14, "Security & resilience", "profile-settings", administrators),
    WorkspaceModule(15, "Testing & pilot", "admin-dashboard", administrators),
    WorkspaceModule(16, "Production deployment", "admin-dashboard", setOf(WorkspaceRole.SUPER_ADMIN))
)

@Composable
internal fun RolePortalScreen(
    role: WorkspaceRole?,
    onOpen: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F7FC))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BrandHeader(onSignOut = onSignOut)

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF103A70))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("YOUR SVIT WORKSPACE", color = Color(0xFFB8D7FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(role?.title ?: "Role not available", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    role?.summary ?: "Sign in again or contact your college administrator to verify your account role.",
                    color = Color.White.copy(alpha = 0.84f), fontSize = 14.sp
                )
                if (role != null) {
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = { onOpen(role.homeRoute) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = role.color),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text("Open my dashboard", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SectionHeading("ROLE PORTALS", "Five role-based workspaces")
        WorkspaceRole.values().forEach { item ->
            val isCurrent = role == item
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(
                    if (isCurrent) 1.5.dp else 1.dp,
                    if (isCurrent) item.color.copy(alpha = 0.55f) else Color(0xFFE0E8F1)
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoleBadge(item)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.title, color = Color(0xFF173252), fontWeight = FontWeight.Bold)
                        Text(item.summary, color = Color(0xFF66768A), fontSize = 12.sp)
                    }
                    if (isCurrent) {
                        TextButton(onClick = { onOpen(item.homeRoute) }) {
                            Text("Open", color = item.color, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Restricted", color = Color(0xFF7A8797), fontSize = 11.sp)
                    }
                }
            }
        }
        Text(
            "Role access comes from the signed-in account. Choosing a different portal does not grant permissions.",
            color = Color(0xFF65758B), fontSize = 12.sp
        )

        SectionHeading("YOUR SCREENS", "20-screen experience • role-scoped shortcuts")
        val availableScreens = workspaceScreens.filter { role != null && role in it.roles }
        if (availableScreens.isEmpty()) {
            EmptyState("No role-specific screens are available until your server-verified role is loaded.")
        } else {
            availableScreens.forEach { screen ->
                NavigationCard(
                    number = screen.number,
                    title = screen.title,
                    subtitle = screen.subtitle,
                    accent = role?.color ?: Color(0xFF1769AA),
                    onClick = { onOpen(screen.route) }
                )
            }
        }

        SectionHeading("16 PROJECT MODULES", "Mapped to the SVIT project guides")
        workspaceModules.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { module ->
                    val enabled = role != null && role in module.roles
                    ModuleCard(
                        module = module,
                        enabled = enabled,
                        onClick = { onOpen(module.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun WorkspacePageScreen(
    screen: WorkspaceScreen,
    role: WorkspaceRole?,
    nav: NavHostController,
    onSignOut: () -> Unit
) {
    val allowed = role != null && role in screen.roles
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F7FC))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandHeader(actionText = "Workspace") { nav.navigate("role-portal") }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { nav.popBackStack() }) {
                Text("‹  Back", color = Color(0xFF1769AA), fontWeight = FontWeight.SemiBold)
            }
            Text("SCREEN ${screen.number} / 20", color = Color(0xFF65758B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(screen.title, color = Color(0xFF12345A), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(screen.subtitle, color = Color(0xFF53657A), fontSize = 14.sp)

        if (!allowed) {
            EmptyState("This screen is not available for the signed-in role. Role permissions are also enforced by the server.")
            OutlinedButton(onClick = { nav.navigate("role-portal") }, modifier = Modifier.fillMaxWidth()) {
                Text("Return to my workspace")
            }
        } else {
            ScreenBody(screen, role!!, nav, onSignOut)
        }
    }
}

@Composable
private fun ScreenBody(
    screen: WorkspaceScreen,
    role: WorkspaceRole,
    nav: NavHostController,
    onSignOut: () -> Unit
) {
    when (screen.route) {
        "student-dashboard" -> {
            StatusPanel("Personal campus overview", "Attendance history, timetable and face-profile status load only from your authorized account.")
            NavigationCard(17, "Attendance history", "View your own records and disputes.", role.color) { nav.navigate("student-history") }
            NavigationCard(19, "Timetable & alerts", "Open your schedule and notification history.", role.color) { nav.navigate("timetable-notifications") }
            NavigationCard(10, "Weekly profile check", "Check whether a profile review is due.", role.color) { nav.navigate("weekly-face-check") }
        }
        "admin-dashboard" -> {
            StatusPanel("Operations overview", "Live department, enrollment and service metrics appear only when returned by the authorized API.")
            NavigationCard(8, "Student roster", "Open authorized roster and enrollment tools.", role.color) { nav.navigate("student-roster") }
            NavigationCard(18, "Reports & analytics", "Load attendance reports for your authorized scope.", role.color) { nav.navigate("reports") }
            NavigationCard(20, "Security & settings", "Review role-appropriate account and service controls.", role.color) { nav.navigate("profile-settings") }
        }
        "student-roster" -> {
            StatusPanel("Roster data is not loaded", "This app does not show sample student records. Connect an authorized roster endpoint to populate this screen.")
            DetailPanel("Import workflow", "Validate a roster and preview errors before committing changes. No import action is claimed until the roster API is connected.")
        }
        "weekly-face-check" -> {
            StatusPanel("Profile check status unavailable", "A weekly check must not replace a trusted profile automatically. The previous valid version must remain available until a new version passes policy checks.")
            NavigationCard(9, "Capture profile samples", "Continue to the existing guided enrollment flow.", role.color) { nav.navigate("enrollment") }
        }
        "class-session-selection" -> {
            StatusPanel("No class selected", "Choose an assigned, scheduled class before starting attendance. Class assignments are not fabricated on this screen.")
            NavigationCard(6, "Open faculty dashboard", "Use the existing dashboard to select an attendance session.", role.color) { nav.navigate("dashboard") }
        }
        "ai-processing" -> {
            StatusPanel("No processing job selected", "Processing progress and retry status are shown only when the AI service returns a job.")
            DetailPanel("Attendance safety", "AI detections remain candidates. UNKNOWN, REVIEW, LOW QUALITY, NOT DETECTED and OTHER CLASS never mean automatic absence.")
        }
        "recognition-results" -> {
            StatusPanel("No recognition results loaded", "No candidate matches or confidence values are displayed without a real processing response.")
            DetailPanel("Review categories", "PRESENT candidate • REVIEW • UNKNOWN • OTHER CLASS • LOW QUALITY • NOT DETECTED")
            NavigationCard(15, "Faculty verification", "Continue to the teacher-review workspace.", role.color) { nav.navigate("teacher-review") }
        }
        "teacher-review" -> {
            StatusPanel("No attendance session loaded", "A teacher must review the eligible roster and resolve uncertainty before official attendance can be finalized.")
            DetailPanel("Safe finalization", "Absence is derived only from the eligible roster after review. Corrections need authorization, a reason and an audit record.")
            NavigationCard(12, "Open four-photo capture", "Capture classroom evidence for an authorized session.", role.color) { nav.navigate("four-photo-workflow") }
        }
        "attendance-saved-report" -> {
            StatusPanel("No finalized session selected", "Official status, counts and classroom-photo links appear after an authorized finalization response.")
            NavigationCard(18, "Open reports", "Load a report from the connected attendance service.", role.color) { nav.navigate("reports") }
        }
        "student-history" -> {
            StatusPanel("Attendance history is empty", "Your authorized personal records and dispute status will appear here when the history API is connected.")
            DetailPanel("Privacy", "This view is intended for the signed-in student's own attendance. It does not display other students' records.")
        }
        "timetable-notifications" -> {
            StatusPanel("No schedule or notifications loaded", "Timetable entries and delivery states are shown only when returned by the service.")
            if (role == WorkspaceRole.GUARDIAN) {
                DetailPanel("Verified guardian link required", "Ward details and confirmed-absence alerts require an active, verified guardian-to-student link.")
            } else {
                DetailPanel("Notification safety", "Guardian absence notifications are sent only after authorized faculty finalization.")
            }
        }
        "profile-settings" -> {
            StatusPanel("Account settings", "Account details and session controls are tied to the signed-in identity.")
            DetailPanel("Signed-in role", role.title)
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12345A)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Sign out securely", fontWeight = FontWeight.Bold)
            }
        }
        else -> {
            StatusPanel("Workspace ready", "Open the connected workflow for this role.")
            NavigationCard(screen.number, screen.title, screen.subtitle, role.color) {
                nav.navigate(screen.nextRoute)
            }
        }
    }
}

@Composable
private fun BrandHeader(actionText: String = "Sign out", onSignOut: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.svit_logo),
            contentDescription = "Sai Vidya Institute of Technology logo",
            modifier = Modifier.size(44.dp)
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("SVIT  |  EDULENS", color = Color(0xFF1769AA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Smart Attendance", color = Color(0xFF12345A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onSignOut) {
            Text(actionText, color = Color(0xFF1769AA), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RoleBadge(role: WorkspaceRole) {
    Box(
        Modifier
            .size(46.dp)
            .background(role.tint, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(role.initials, color = role.color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color(0xFF12345A), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
        Text(subtitle, color = Color(0xFF6A788A), fontSize = 12.sp)
    }
}

@Composable
private fun NavigationCard(
    number: Int,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3EAF2))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).background(accent.copy(alpha = 0.1f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(number.toString().padStart(2, '0'), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = Color(0xFF173252), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = Color(0xFF66768A), fontSize = 12.sp)
            }
            Text("›", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModuleCard(
    module: WorkspaceModule,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color.White else Color(0xFFF8FAFC),
            disabledContainerColor = Color(0xFFF8FAFC)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3EAF2))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(module.number.toString().padStart(2, '0'), color = Color(0xFF1769AA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(module.title, color = if (enabled) Color(0xFF173252) else Color(0xFF7C8998), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(if (enabled) "Open module" else "Not in your role", color = if (enabled) Color(0xFF1769AA) else Color(0xFF7C8998), fontSize = 10.sp)
        }
    }
}

@Composable
private fun StatusPanel(title: String, message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3EAF2))
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Color(0xFF1769AA), RoundedCornerShape(50)))
                Text("  LIVE DATA STATE", color = Color(0xFF1769AA), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
            Text(title, color = Color(0xFF173252), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color(0xFF5E6F82), fontSize = 13.sp)
        }
    }
}

@Composable
private fun DetailPanel(title: String, message: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FC))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color(0xFF12345A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(message, color = Color(0xFF53657A), fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3EAF2))
    ) {
        Text(message, Modifier.padding(16.dp), color = Color(0xFF5E6F82), fontSize = 13.sp)
    }
}

@Composable
internal fun AccountRecoveryScreen(onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F7FC))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandHeader(actionText = "Back", onSignOut = onBack)
        Text("Account recovery", color = Color(0xFF12345A), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Enter your registered college email to begin a secure password reset.", color = Color(0xFF53657A), fontSize = 14.sp)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("College email") },
            placeholder = { Text("name@saividya.ac.in") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Send secure reset link")
        }
        DetailPanel(
            "Recovery service not connected",
            "Password-reset requests are unavailable until the approved recovery API is connected. No email has been sent."
        )
    }
}

@Composable
internal fun WorkspaceAccessDeniedScreen(onReturn: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F7FC))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandHeader(actionText = "Workspace", onSignOut = onReturn)
        Text("Access unavailable", color = Color(0xFF12345A), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        EmptyState("This workspace is not available for the signed-in role. Account permissions are enforced by the server.")
        OutlinedButton(onClick = onReturn, modifier = Modifier.fillMaxWidth()) {
            Text("Return to my workspace")
        }
    }
}
