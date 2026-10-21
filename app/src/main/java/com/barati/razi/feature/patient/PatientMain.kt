package com.barati.razi.feature.patient

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.BuildConfig
import com.barati.razi.R
import com.barati.razi.core.HospitalInfo
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.PatientAppointmentDto
import com.barati.razi.core.network.PatientDto
import com.barati.razi.core.network.Result
import com.barati.razi.feature.appointments.DoctorsTab
import com.barati.razi.feature.appointments.FeaturedDoctorCard
import com.barati.razi.feature.appointments.MyAppointmentsTab
import com.barati.razi.feature.club.ClubTab
import com.barati.razi.feature.results.ResultsTab
import com.barati.razi.feature.appointments.SpecialtyTile
import com.barati.razi.feature.publicinfo.FeedbackBody
import com.barati.razi.feature.publicinfo.UnitsList
import com.barati.razi.notify.SystemNotifier
import com.barati.razi.ui.components.Load
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.ThemeModePicker
import com.barati.razi.ui.components.TileGrid
import com.barati.razi.ui.components.TileItem
import com.barati.razi.ui.components.WideCard
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.ui.i18n.LanguageAction
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors
import kotlinx.coroutines.launch
import java.time.Instant

/** صفحه‌های اصلی مراجع؛ چهار تای اول در نوار پایین‌اند و بخش‌ها و انتقادها از منوی کناری باز می‌شوند. */
private enum class PatientTab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector, val inBar: Boolean = true) {
    HOME(R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    DOCTORS(R.string.nav_doctors, Icons.Outlined.MedicalServices, Icons.Filled.MedicalServices),
    APPOINTMENTS(R.string.nav_appointments, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    PROFILE(R.string.nav_profile, Icons.Outlined.Person, Icons.Filled.Person),
    UNITS(R.string.drawer_units, Icons.Outlined.LocalHospital, Icons.Outlined.LocalHospital, inBar = false),
    CLUB(R.string.nav_club, Icons.Outlined.CardGiftcard, Icons.Outlined.CardGiftcard, inBar = false),
    RESULTS(R.string.nav_results, Icons.Outlined.Science, Icons.Outlined.Science, inBar = false),
    FEEDBACK(R.string.drawer_feedback, Icons.Outlined.Feedback, Icons.Outlined.Feedback, inBar = false),
}

/** صفحه اول مراجعان: منوی کناری، نوار پایین (خانه، پزشکان، نوبت‌های من، پروفایل). */
@Composable
fun PatientHomeScreen(
    container: AppContainer,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    patient: PatientDto?,
    staffLoggedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onFaq: () -> Unit,
    onNews: () -> Unit,
    onContact: () -> Unit,
    onInternational: () -> Unit,
    onAbout: () -> Unit,
    onAdmission: () -> Unit,
    onTrackIncident: () -> Unit,
    onStaff: () -> Unit,
    onDoctor: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var specialtyFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val current = PatientTab.entries[tab]
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && current != PatientTab.HOME) { tab = PatientTab.HOME.ordinal }

    // لمس یادآور نوبت: زبانه «نوبت‌های من» باز می‌شود.
    val pending by container.pendingOpen.collectAsState()
    LaunchedEffect(pending) {
        if (pending?.linkType == SystemNotifier.LINK_MY_APPOINTMENTS) {
            container.pendingOpen.value = null
            tab = PatientTab.APPOINTMENTS.ordinal
        }
    }

    fun go(t: PatientTab) {
        tab = t.ordinal
        scope.launch { drawer.close() }
    }
    fun openDrawerItem(action: () -> Unit) {
        scope.launch { drawer.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DrawerHeader(patient)
                    Spacer(Modifier.height(8.dp))
                    DrawerItem(Icons.Outlined.MedicalServices, stringResource(R.string.nav_doctors)) { go(PatientTab.DOCTORS) }
                    DrawerItem(Icons.Outlined.CalendarMonth, stringResource(R.string.nav_appointments)) { go(PatientTab.APPOINTMENTS) }
                    DrawerItem(Icons.Outlined.Science, stringResource(R.string.nav_results)) { go(PatientTab.RESULTS) }
                    DrawerItem(Icons.Outlined.CardGiftcard, stringResource(R.string.nav_club)) { go(PatientTab.CLUB) }
                    DrawerItem(Icons.Outlined.LocalHospital, stringResource(R.string.drawer_units)) { go(PatientTab.UNITS) }
                    DrawerItem(Icons.AutoMirrored.Outlined.Assignment, stringResource(R.string.admission_title)) { openDrawerItem(onAdmission) }
                    DrawerItem(Icons.Outlined.AccessTime, stringResource(R.string.tile_visiting)) { openDrawerItem(onFaq) }
                    DrawerItem(Icons.Outlined.Campaign, stringResource(R.string.tile_news)) { openDrawerItem(onNews) }
                    DrawerItem(Icons.Outlined.Public, stringResource(R.string.international_title)) { openDrawerItem(onInternational) }
                    DrawerItem(Icons.Outlined.Feedback, stringResource(R.string.drawer_feedback)) { go(PatientTab.FEEDBACK) }
                    DrawerItem(Icons.Outlined.Search, stringResource(R.string.tile_track)) { openDrawerItem(onTrackIncident) }
                    DrawerItem(Icons.Outlined.Place, stringResource(R.string.tile_contact)) { openDrawerItem(onContact) }
                    DrawerItem(Icons.Outlined.Info, stringResource(R.string.about_title)) { openDrawerItem(onAbout) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    DrawerItem(
                        Icons.Outlined.Badge,
                        stringResource(if (staffLoggedIn) R.string.tile_staff_logged_in else R.string.tile_staff),
                    ) { openDrawerItem(onStaff) }
                    Row(Modifier.padding(horizontal = 28.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.drawer_language), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        LanguageAction(language, onLanguage)
                    }
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.theme_label), style = MaterialTheme.typography.labelLarge)
                        val themeMode by container.themeRepository.mode.collectAsState()
                        ThemeModePicker(themeMode) { scope.launch { container.themeRepository.set(it) } }
                    }
                    Text(
                        stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME),
                        Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        RaziScaffold(
            title = if (current == PatientTab.HOME) stringResource(R.string.hospital_full_name) else stringResource(current.label),
            onMenu = { scope.launch { drawer.open() } },
            actions = {
                if (patient == null && current == PatientTab.HOME) {
                    TextButton(onClick = onSignIn) { Text(stringResource(R.string.signin_action)) }
                }
                LanguageAction(language, onLanguage)
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    PatientTab.entries.filter { it.inBar }.forEach { t ->
                        NavigationBarItem(
                            selected = t == current,
                            onClick = { tab = t.ordinal },
                            icon = { Icon(if (t == current) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                indicatorColor = ValiasrColors.Brand,
                                selectedTextColor = ValiasrColors.Link,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            when (current) {
                PatientTab.HOME -> HomeTab(
                    container,
                    padding,
                    language,
                    patient,
                    onSearch = { specialtyFilter = null; tab = PatientTab.DOCTORS.ordinal },
                    onSpecialty = { specialtyFilter = it; tab = PatientTab.DOCTORS.ordinal },
                    onDoctor = onDoctor,
                    onAppointments = { tab = PatientTab.APPOINTMENTS.ordinal },
                    onFaq = onFaq,
                    onNews = onNews,
                    onContact = onContact,
                    onFeedback = { tab = PatientTab.FEEDBACK.ordinal },
                    onInternational = onInternational,
                    onAbout = onAbout,
                    onAdmission = onAdmission,
                )
                PatientTab.DOCTORS -> DoctorsTab(container, padding, specialtyFilter, { specialtyFilter = it }, onDoctor)
                PatientTab.APPOINTMENTS -> MyAppointmentsTab(
                    container,
                    padding,
                    signedIn = patient != null,
                    onSignIn = onSignIn,
                    onFindDoctor = { tab = PatientTab.DOCTORS.ordinal },
                )
                PatientTab.PROFILE -> ProfileTab(padding, language, onLanguage, patient, staffLoggedIn, onSignIn, onSignOut, onTrackIncident, onStaff)
                PatientTab.CLUB -> ClubTab(container, padding, signedIn = patient != null, onSignIn = onSignIn)
                PatientTab.RESULTS -> ResultsTab(container, padding, signedIn = patient != null, onSignIn = onSignIn)
                PatientTab.UNITS -> UnitsList(container, padding)
                PatientTab.FEEDBACK -> FeedbackBody(container, padding)
            }
        }
    }
}

@Composable
private fun DrawerHeader(patient: PatientDto?) {
    Column(
        Modifier.fillMaxWidth().background(ValiasrColors.BrandSoft).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.logo_mark), contentDescription = null, Modifier.size(width = 34.dp, height = 40.dp))
        }
        Text(
            stringResource(R.string.hospital_full_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ValiasrColors.PrimaryPressed,
        )
        patient?.fullName?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = ValiasrColors.Link)
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null, tint = ValiasrColors.Brand) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun HomeTab(
    container: AppContainer,
    padding: PaddingValues,
    language: AppLanguage,
    patient: PatientDto?,
    onSearch: () -> Unit,
    onSpecialty: (String) -> Unit,
    onDoctor: (String) -> Unit,
    onAppointments: () -> Unit,
    onFaq: () -> Unit,
    onNews: () -> Unit,
    onContact: () -> Unit,
    onFeedback: () -> Unit,
    onInternational: () -> Unit,
    onAbout: () -> Unit,
    onAdmission: () -> Unit,
) {
    val repo = container.appointmentRepository
    val specialties = rememberLoader { repo.specialties() }
    val featured = rememberLoader { repo.doctors(featured = true) }
    val mine = rememberLoader(patient?.id) {
        if (patient == null) Result.Ok(emptyList<PatientAppointmentDto>()) else repo.myAppointments()
    }
    // مراجع غیرفارسی‌زبان به احتمال زیاد بیمار بین‌الملل است؛ کارت مربوط بالاتر می‌آید.
    val internationalFirst = language != AppLanguage.FA
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val name = patient?.fullName?.takeIf { it.isNotBlank() }
            Text(
                if (name != null) stringResource(R.string.home_greeting_name, name) else stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            SearchBox(onSearch)
            val next = (mine.state as? Load.Data)?.value?.let { AppointmentLogic.upcoming(it, Instant.now()).firstOrNull() }
            if (next != null) NextVisitCard(next, onAppointments) else HeroCard(onSearch)
            if (internationalFirst) InternationalCard(onInternational)
        }

        val specs = (specialties.state as? Load.Data)?.value.orEmpty()
        if (specs.isNotEmpty()) {
            SectionHeader(stringResource(R.string.specialties_title), onSearch)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                specs.take(6).chunked(3).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { c, s -> SpecialtyTile(s, r * 3 + c, Modifier.weight(1f)) { onSpecialty(s.id) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        val docs = (featured.state as? Load.Data)?.value.orEmpty()
        if (docs.isNotEmpty()) {
            SectionHeader(stringResource(R.string.featured_doctors), onSearch)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(docs, key = { it.id }) { d -> FeaturedDoctorCard(d, container::doctorPhotoUrl) { onDoctor(d.id) } }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TileGrid(
                listOf(
                    tile(R.string.tile_visiting, R.string.tile_visiting_sub, Icons.Outlined.AccessTime, onFaq),
                    tile(R.string.tile_contact, R.string.tile_contact_sub, Icons.Outlined.Place, onContact),
                    tile(R.string.tile_news, R.string.tile_news_sub, Icons.Outlined.Campaign, onNews),
                    tile(R.string.tile_feedback_short, R.string.tile_feedback_sub, Icons.Outlined.Feedback, onFeedback),
                ),
            )
            WideCard(Icons.AutoMirrored.Outlined.Assignment, stringResource(R.string.admission_title), stringResource(R.string.admission_sub), onAdmission)
            if (!internationalFirst) InternationalCard(onInternational)
            WideCard(Icons.Outlined.Info, stringResource(R.string.about_title), stringResource(R.string.about_sub), onAbout)
            SupportCard()
        }
    }
}

@Composable
private fun SearchBox(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = ValiasrColors.Brand)
        Text(
            stringResource(R.string.home_search_hint),
            Modifier.padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).background(ValiasrColors.Brand, RoundedCornerShape(2.dp)))
        Text(title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAll) { Text(stringResource(R.string.see_all), color = ValiasrColors.Link) }
    }
}

/** کارت اصلی با شیب رنگ برند: دعوت به گرفتن نوبت از پزشکان بیمارستان. */
@Composable
private fun HeroCard(onBook: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(ValiasrColors.GradientStart, ValiasrColors.Primary, ValiasrColors.GradientEnd)),
                RoundedCornerShape(24.dp),
            ),
    ) {
        // دایره‌های تزئینی
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(96.dp).background(Color.White.copy(alpha = 0.08f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).padding(12.dp).size(56.dp).background(ValiasrColors.GoldDeco.copy(alpha = 0.18f), CircleShape))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Event, contentDescription = null, tint = ValiasrColors.GoldDeco, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.home_hero_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Text(stringResource(R.string.home_hero_sub), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            Button(
                onClick = onBook,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ValiasrColors.Primary),
            ) { Text(stringResource(R.string.home_book), fontWeight = FontWeight.Bold) }
        }
    }
}

/** اگر مراجع نوبت پیش رو دارد، جای کارت اصلی را می‌گیرد. */
@Composable
private fun NextVisitCard(a: PatientAppointmentDto, onClick: () -> Unit) {
    val language = LocalAppLanguage.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(ValiasrColors.GradientStart, ValiasrColors.Primary)), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = ValiasrColors.GoldDeco, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_next_visit), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                Text(a.doctorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(AppointmentLogic.dateTimeText(a.startsAt, language), color = Color.White)
            }
        }
    }
}

@Composable
private fun SupportCard() {
    val uri = LocalUriHandler.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ValiasrColors.Cream),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(ValiasrColors.GoldDeco, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = ValiasrColors.Gold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.home_support_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ValiasrColors.Text)
                Text(stringResource(R.string.home_support_body), style = MaterialTheme.typography.bodySmall, color = ValiasrColors.Text2)
            }
            Button(
                onClick = { uri.tryOpen("tel:${HospitalInfo.PHONE}") },
                colors = ButtonDefaults.buttonColors(containerColor = ValiasrColors.Brand, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, Modifier.size(18.dp))
                Text(stringResource(R.string.call), Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun tile(title: Int, subtitle: Int, icon: ImageVector, onClick: () -> Unit) =
    TileItem(stringResource(title), stringResource(subtitle), icon, onClick = onClick)

@Composable
private fun InternationalCard(onClick: () -> Unit) {
    WideCard(
        Icons.Outlined.Public,
        stringResource(R.string.international_title),
        stringResource(R.string.international_sub),
        onClick,
        container = MaterialTheme.colorScheme.secondaryContainer,
        tint = MaterialTheme.colorScheme.secondary,
    )
}
