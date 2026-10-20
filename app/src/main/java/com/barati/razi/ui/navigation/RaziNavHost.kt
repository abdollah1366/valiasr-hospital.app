package com.barati.razi.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.barati.razi.AppContainer
import com.barati.razi.core.data.SessionState
import com.barati.razi.core.data.can
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.notifications.NotificationLogic
import com.barati.razi.core.notifications.NotificationTarget
import com.barati.razi.feature.announcements.AnnouncementsScreen
import com.barati.razi.feature.auth.ChangePasswordScreen
import com.barati.razi.feature.chat.ChatInfoScreen
import com.barati.razi.feature.chat.ChatListScreen
import com.barati.razi.feature.chat.ChatScreen
import com.barati.razi.feature.chat.NewChatScreen
import com.barati.razi.feature.chat.NewGroupScreen
import com.barati.razi.feature.auth.LoginScreen
import com.barati.razi.feature.home.StaffHomeScreen
import com.barati.razi.feature.incident.IncidentDetailScreen
import com.barati.razi.feature.incident.IncidentFormScreen
import com.barati.razi.feature.incident.MyIncidentsScreen
import com.barati.razi.feature.incident.ReviewListScreen
import com.barati.razi.feature.incident.TrackIncidentScreen
import com.barati.razi.feature.notifications.NotificationsScreen
import com.barati.razi.feature.publicinfo.FaqScreen
import com.barati.razi.feature.publicinfo.FeedbackScreen
import com.barati.razi.feature.publicinfo.PublicHomeScreen
import com.barati.razi.feature.publicinfo.PublicNewsScreen
import com.barati.razi.feature.publicinfo.UnitsScreen
import com.barati.razi.feature.requests.NewRequestScreen
import com.barati.razi.feature.requests.RequestDetailScreen
import com.barati.razi.feature.requests.RequestsHomeScreen
import com.barati.razi.feature.training.AttemptScreen
import com.barati.razi.feature.training.CourseItemScreen
import com.barati.razi.feature.training.CourseScreen
import com.barati.razi.feature.training.ExamTakeScreen
import com.barati.razi.feature.schedule.MyScheduleScreen
import com.barati.razi.feature.schedule.OnDutyScreen
import com.barati.razi.feature.schedule.ScheduleRosterScreen
import com.barati.razi.feature.schedule.ScheduleSwapsScreen
import com.barati.razi.feature.schedule.ScheduleUnitsScreen
import com.barati.razi.feature.training.TrainingHomeScreen
import com.barati.razi.ui.components.LoadingBox
import com.barati.razi.ui.i18n.LocalizedContent
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val PUBLIC_HOME = "public"
    const val UNITS = "units"
    const val FAQ = "faq"
    const val NEWS = "news"
    const val FEEDBACK = "feedback"
    const val TRACK = "track?code={code}&secret={secret}"
    const val LOGIN = "login"
    const val CHANGE_PASSWORD = "change_password"
    const val STAFF_HOME = "staff"
    const val INCIDENT_NEW = "incident_new"
    const val MY_INCIDENTS = "my_incidents"
    const val INCIDENT_DETAIL = "incident/{id}"
    const val ANNOUNCEMENTS = "announcements"
    const val REVIEW = "review"
    const val TRAINING = "training"
    const val COURSE = "course/{id}"
    const val COURSE_ITEM = "course/{courseId}/item/{itemId}"
    const val EXAM_TAKE = "exam/{id}/take"
    const val ATTEMPT = "attempt/{id}"
    const val REQUESTS = "requests"
    const val REQUEST_NEW = "request_new"
    const val REQUEST_DETAIL = "request/{id}"
    const val NOTIFICATIONS = "notifications"
    const val CHATS = "chats"
    const val CHAT_NEW = "chat_new"
    const val CHAT_GROUP_NEW = "chat_group_new"
    const val CHAT = "chat/{id}"
    const val CHAT_INFO = "chat/{id}/info"
    const val SCHEDULE = "schedule"
    const val SCHEDULE_UNITS = "schedule_units"
    const val SCHEDULE_ROSTER = "schedule/{id}"
    const val SCHEDULE_SWAPS = "schedule_swaps"
    const val ON_DUTY = "on_duty"

    /** صفحه‌هایی که بدون نشست معتبر باز نمی‌مانند. */
    val staffOnly = setOf(
        CHANGE_PASSWORD, STAFF_HOME, MY_INCIDENTS, INCIDENT_DETAIL, ANNOUNCEMENTS, REVIEW,
        TRAINING, COURSE, COURSE_ITEM, EXAM_TAKE, ATTEMPT, REQUESTS, REQUEST_DETAIL, NOTIFICATIONS,
        CHATS, CHAT_NEW, CHAT_GROUP_NEW, CHAT, CHAT_INFO,
        SCHEDULE, SCHEDULE_UNITS, SCHEDULE_ROSTER, SCHEDULE_SWAPS, ON_DUTY,
    )

    fun track(code: String = "", secret: String = "") = "track?code=${Uri.encode(code)}&secret=${Uri.encode(secret)}"
    fun incident(id: String) = "incident/$id"
    fun course(id: String) = "course/$id"
    fun courseItem(courseId: String, itemId: String) = "course/$courseId/item/$itemId"
    fun examTake(id: String) = "exam/$id/take"
    fun attempt(id: String) = "attempt/$id"
    fun request(id: String) = "request/$id"
    fun chat(id: String) = "chat/$id"
    fun chatInfo(id: String) = "chat/$id/info"
    fun scheduleRoster(id: String) = "schedule/$id"

    fun of(target: NotificationTarget): String = when (target) {
        is NotificationTarget.Request -> request(target.id)
        is NotificationTarget.Incident -> incident(target.id)
        is NotificationTarget.Course -> course(target.id)
        NotificationTarget.Announcements -> ANNOUNCEMENTS
        NotificationTarget.Training -> TRAINING
        is NotificationTarget.Chat -> chat(target.id)
        is NotificationTarget.Schedule -> target.id?.let(::scheduleRoster) ?: SCHEDULE
    }
}

@Composable
fun RaziNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    val session by container.authRepository.session.collectAsState()
    val scope = rememberCoroutineScope()
    val back: () -> Unit = { nav.popBackStack() }

    fun goStaffHome() = nav.navigate(Routes.STAFF_HOME) { popUpTo(Routes.PUBLIC_HOME) }

    // لمس اعلان سیستمی: وقتی کاربر واردشده در بخش کارکنان است (پس از صفحه آغاز)، صفحه همان مورد باز و اعلان خوانده می‌شود.
    val pendingOpen by container.pendingOpen.collectAsState()
    val current by nav.currentBackStackEntryAsState()
    LaunchedEffect(pendingOpen, session, current?.destination?.route) {
        val open = pendingOpen ?: return@LaunchedEffect
        val here = current?.destination?.route ?: return@LaunchedEffect
        val s = session
        if (s !is SessionState.LoggedIn || here !in Routes.staffOnly || here == Routes.CHANGE_PASSWORD) return@LaunchedEffect
        container.pendingOpen.value = null
        open.notificationId?.let { id -> scope.launch { container.notificationRepository.markRead(id) } }
        val route = if (open.linkType == "notifications") {
            Routes.NOTIFICATIONS
        } else {
            NotificationLogic.target(open.linkType, open.linkId)?.let(Routes::of) ?: Routes.NOTIFICATIONS
        }
        nav.navigate(route)
    }

    // اگر نشست منقضی شد، کاربر از صفحه‌های کارکنان به صفحه ورود برده می‌شود.
    LaunchedEffect(session) {
        val route = nav.currentDestination?.route
        if (session is SessionState.LoggedOut && route != null && route in Routes.staffOnly) {
            nav.navigate(Routes.LOGIN) { popUpTo(Routes.PUBLIC_HOME) }
        }
    }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            LoadingBox()
            LaunchedEffect(session) {
                val s = session
                if (s is SessionState.Unknown) return@LaunchedEffect
                nav.navigate(Routes.PUBLIC_HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                if (s is SessionState.LoggedIn) {
                    nav.navigate(if (s.me.mustChangePassword) Routes.CHANGE_PASSWORD else Routes.STAFF_HOME)
                }
            }
        }
        composable(Routes.PUBLIC_HOME) {
            PatientContent(container) { language ->
                PublicHomeScreen(
                    isLoggedIn = session is SessionState.LoggedIn,
                    language = language,
                    onLanguage = { scope.launch { container.languageRepository.set(it) } },
                    onUnits = { nav.navigate(Routes.UNITS) },
                    onFaq = { nav.navigate(Routes.FAQ) },
                    onNews = { nav.navigate(Routes.NEWS) },
                    onFeedback = { nav.navigate(Routes.FEEDBACK) },
                    onTrack = { nav.navigate(Routes.track()) },
                    onStaff = {
                        val s = session
                        if (s is SessionState.LoggedIn && !s.me.mustChangePassword) goStaffHome() else nav.navigate(Routes.LOGIN)
                    },
                )
            }
        }
        composable(Routes.UNITS) { PatientContent(container) { UnitsScreen(container, back) } }
        composable(Routes.FAQ) { PatientContent(container) { FaqScreen(container, back) } }
        composable(Routes.NEWS) { PatientContent(container) { PublicNewsScreen(container, back) } }
        composable(Routes.FEEDBACK) { PatientContent(container) { FeedbackScreen(container, back) } }
        composable(
            Routes.TRACK,
            arguments = listOf(
                navArgument("code") { type = NavType.StringType; defaultValue = "" },
                navArgument("secret") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            TrackIncidentScreen(
                container,
                entry.arguments?.getString("code").orEmpty(),
                entry.arguments?.getString("secret").orEmpty(),
                back,
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(container, onBack = back, onLoggedIn = { mustChange ->
                nav.navigate(if (mustChange) Routes.CHANGE_PASSWORD else Routes.STAFF_HOME) {
                    popUpTo(Routes.PUBLIC_HOME)
                }
            })
        }
        composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(container, onDone = { goStaffHome() }) }
        composable(Routes.STAFF_HOME) {
            val s = session
            if (s is SessionState.LoggedIn) {
                StaffHomeScreen(
                    container,
                    s.me,
                    onNewIncident = { nav.navigate(Routes.INCIDENT_NEW) },
                    onMyIncidents = { nav.navigate(Routes.MY_INCIDENTS) },
                    onAnnouncements = { nav.navigate(Routes.ANNOUNCEMENTS) },
                    onReview = { nav.navigate(Routes.REVIEW) },
                    onTraining = { nav.navigate(Routes.TRAINING) },
                    onRequests = { nav.navigate(Routes.REQUESTS) },
                    onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    onChat = { nav.navigate(Routes.CHATS) },
                    onSchedule = { nav.navigate(Routes.SCHEDULE) },
                    onPublic = { nav.popBackStack(Routes.PUBLIC_HOME, inclusive = false) },
                    onLogout = { scope.launch { container.authRepository.logout() } },
                )
            } else {
                LoadingBox()
            }
        }
        // فرم گزارش عمداً در staffOnly نیست تا با منقضی‌شدن نشست، اطلاعات واردشده از دست نرود.
        composable(Routes.INCIDENT_NEW) {
            IncidentFormScreen(container, onBack = back, onDone = { nav.popBackStack(Routes.STAFF_HOME, inclusive = false) })
        }
        composable(Routes.MY_INCIDENTS) {
            MyIncidentsScreen(
                container,
                onBack = back,
                onOpen = { nav.navigate(Routes.incident(it)) },
                onTrack = { code, secret -> nav.navigate(Routes.track(code, secret)) },
            )
        }
        composable(Routes.INCIDENT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            IncidentDetailScreen(container, entry.arguments?.getString("id").orEmpty(), back)
        }
        composable(Routes.ANNOUNCEMENTS) { AnnouncementsScreen(container, back) }
        composable(Routes.REVIEW) { ReviewListScreen(container, back, onOpen = { nav.navigate(Routes.incident(it)) }) }
        composable(Routes.TRAINING) {
            TrainingHomeScreen(
                container,
                onBack = back,
                onCourse = { nav.navigate(Routes.course(it)) },
                onTakeExam = { nav.navigate(Routes.examTake(it)) },
                onAttempt = { nav.navigate(Routes.attempt(it)) },
            )
        }
        composable(Routes.COURSE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val courseId = entry.arguments?.getString("id").orEmpty()
            CourseScreen(
                container,
                courseId,
                onBack = back,
                onItem = { nav.navigate(Routes.courseItem(courseId, it)) },
                onTakeExam = { nav.navigate(Routes.examTake(it)) },
                onAttempt = { nav.navigate(Routes.attempt(it)) },
            )
        }
        composable(
            Routes.COURSE_ITEM,
            arguments = listOf(
                navArgument("courseId") { type = NavType.StringType },
                navArgument("itemId") { type = NavType.StringType },
            ),
        ) { entry ->
            val courseId = entry.arguments?.getString("courseId").orEmpty()
            CourseItemScreen(
                container,
                courseId,
                entry.arguments?.getString("itemId").orEmpty(),
                onBack = back,
                // محتوای قبلی/بعدی جای صفحه فعلی را می‌گیرد تا «بازگشت» مستقیم به دوره برگردد.
                onOpenItem = { nav.navigate(Routes.courseItem(courseId, it)) { popUpTo(Routes.COURSE_ITEM) { inclusive = true } } },
            )
        }
        composable(Routes.EXAM_TAKE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ExamTakeScreen(container, entry.arguments?.getString("id").orEmpty(), back)
        }
        composable(Routes.REQUESTS) {
            val s = session
            if (s is SessionState.LoggedIn) {
                RequestsHomeScreen(
                    container,
                    s.me,
                    onBack = back,
                    onNew = { nav.navigate(Routes.REQUEST_NEW) },
                    onOpen = { nav.navigate(Routes.request(it)) },
                )
            } else {
                LoadingBox()
            }
        }
        // مثل فرم گزارش، در staffOnly نیست تا با منقضی‌شدن نشست، متن واردشده از دست نرود.
        composable(Routes.REQUEST_NEW) {
            NewRequestScreen(
                container,
                onBack = back,
                onCreated = { nav.navigate(Routes.request(it)) { popUpTo(Routes.REQUEST_NEW) { inclusive = true } } },
            )
        }
        composable(Routes.REQUEST_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            RequestDetailScreen(container, entry.arguments?.getString("id").orEmpty(), back)
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(container, onBack = back, onOpen = { nav.navigate(Routes.of(it)) })
        }
        composable(Routes.SCHEDULE) {
            val s = session
            if (s is SessionState.LoggedIn) {
                MyScheduleScreen(
                    container,
                    s.me,
                    onBack = back,
                    onUnits = { nav.navigate(Routes.SCHEDULE_UNITS) },
                    onOnDuty = { nav.navigate(Routes.ON_DUTY) },
                    onSwaps = { nav.navigate(Routes.SCHEDULE_SWAPS) },
                )
            } else {
                LoadingBox()
            }
        }
        composable(Routes.SCHEDULE_UNITS) {
            ScheduleUnitsScreen(container, onBack = back, onOpen = { nav.navigate(Routes.scheduleRoster(it)) })
        }
        composable(Routes.SCHEDULE_ROSTER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val s = session
            if (s is SessionState.LoggedIn) {
                ScheduleRosterScreen(container, s.me, entry.arguments?.getString("id").orEmpty(), back)
            } else {
                LoadingBox()
            }
        }
        composable(Routes.SCHEDULE_SWAPS) { ScheduleSwapsScreen(container, back) }
        composable(Routes.ON_DUTY) { OnDutyScreen(container, back) }
        composable(Routes.CHATS) {
            ChatListScreen(container, onBack = back, onOpen = { nav.navigate(Routes.chat(it)) }, onNew = { nav.navigate(Routes.CHAT_NEW) })
        }
        composable(Routes.CHAT_NEW) {
            val s = session
            NewChatScreen(
                container,
                canCreateGroup = s is SessionState.LoggedIn && s.me.can("chat.group.create"),
                onBack = back,
                onOpened = { nav.navigate(Routes.chat(it)) { popUpTo(Routes.CHAT_NEW) { inclusive = true } } },
                onNewGroup = { nav.navigate(Routes.CHAT_GROUP_NEW) { popUpTo(Routes.CHAT_NEW) { inclusive = true } } },
            )
        }
        composable(Routes.CHAT_GROUP_NEW) {
            NewGroupScreen(
                container,
                onBack = back,
                onCreated = { nav.navigate(Routes.chat(it)) { popUpTo(Routes.CHAT_GROUP_NEW) { inclusive = true } } },
            )
        }
        composable(Routes.CHAT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val s = session
            val id = entry.arguments?.getString("id").orEmpty()
            if (s is SessionState.LoggedIn) {
                ChatScreen(container, s.me.id, id, onBack = back, onInfo = { nav.navigate(Routes.chatInfo(id)) })
            } else {
                LoadingBox()
            }
        }
        composable(Routes.CHAT_INFO, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val s = session
            if (s is SessionState.LoggedIn) {
                ChatInfoScreen(
                    container,
                    s.me.id,
                    entry.arguments?.getString("id").orEmpty(),
                    onBack = back,
                    // پس از خروج از گروه، صفحه گروه هم بسته می‌شود
                    onLeft = { nav.popBackStack(Routes.CHATS, inclusive = false) },
                    onOpenChat = { nav.navigate(Routes.chat(it)) },
                )
            } else {
                LoadingBox()
            }
        }
        composable(Routes.ATTEMPT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            AttemptScreen(container, entry.arguments?.getString("id").orEmpty(), back)
        }
    }
}

/** صفحه‌های مراجعان به زبان انتخابی کاربر نمایش داده می‌شوند؛ بقیه اپ فارسی است. */
@Composable
private fun PatientContent(container: AppContainer, content: @Composable (AppLanguage) -> Unit) {
    val language by container.languageRepository.language.collectAsState()
    LocalizedContent(language) { content(language) }
}
