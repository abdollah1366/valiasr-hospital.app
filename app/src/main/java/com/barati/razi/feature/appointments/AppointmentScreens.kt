package com.barati.razi.feature.appointments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.network.DoctorDto
import com.barati.razi.core.network.PatientAppointmentDto
import com.barati.razi.core.network.PatientDto
import com.barati.razi.core.network.PublicReviewDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.SlotDto
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberErrorText
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

private fun digits(n: Int, language: AppLanguage) = if (language == AppLanguage.FA) toPersianDigits(n.toString()) else n.toString()

/** زبانه پزشکان: جستجو، تراشه‌های تخصص و فهرست پزشکان. */
@Composable
fun DoctorsTab(
    container: AppContainer,
    padding: PaddingValues,
    specialtyId: String?,
    onSpecialty: (String?) -> Unit,
    onOpen: (String) -> Unit,
) {
    val repo = container.appointmentRepository
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        delay(300)
        debounced = query
    }
    val language = LocalAppLanguage.current
    val specialties = rememberLoader(language) { repo.specialties() }
    val doctors = rememberLoader(specialtyId, debounced, language) { repo.doctors(specialtyId, debounced) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            placeholder = { Text(stringResource(R.string.search_doctor_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
        )
        val list = (specialties.state as? com.barati.razi.ui.components.Load.Data)?.value.orEmpty()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                BrightChip(stringResource(R.string.all_specialties), specialtyId == null) { onSpecialty(null) }
            }
            items(list, key = { it.id }) { s ->
                BrightChip(s.name, specialtyId == s.id) { onSpecialty(if (specialtyId == s.id) null else s.id) }
            }
        }
        LoadContent(doctors) { items ->
            if (items.isEmpty()) {
                MessageBox(stringResource(R.string.no_doctors))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val about = list.firstOrNull { it.id == specialtyId }?.description
                    if (about != null) {
                        item { Text(about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(items, key = { it.id }) { d -> DoctorRowCard(d, container::doctorPhotoUrl) { onOpen(d.id) } }
                }
            }
        }
    }
}

@Composable
private fun BrightChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** صفحه پزشک: معرفی، انتخاب روز و ساعت و ثبت نوبت. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorDetailScreen(
    container: AppContainer,
    doctorId: String,
    patient: PatientDto?,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onMyAppointments: () -> Unit,
) {
    val repo = container.appointmentRepository
    val language = LocalAppLanguage.current
    val loader = rememberLoader<Pair<DoctorDto, List<SlotDto>>>(doctorId, language) {
        when (val d = repo.doctor(doctorId)) {
            is Result.Err -> d
            is Result.Ok -> when (val s = repo.slots(doctorId)) {
                is Result.Err -> s
                is Result.Ok -> Result.Ok(d.value to s.value)
            }
        }
    }
    RaziScaffold(title = stringResource(R.string.doctor_about), onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { (doctor, slots) ->
            val days = remember(slots) { AppointmentLogic.groupByDay(slots) }
            var day by rememberSaveable { mutableStateOf<String?>(null) }
            val selectedDay = day?.let { LocalDate.parse(it) }?.takeIf { it in days } ?: days.keys.firstOrNull()
            var slotId by rememberSaveable { mutableStateOf<String?>(null) }
            var name by rememberSaveable { mutableStateOf(patient?.fullName.orEmpty()) }
            var note by rememberSaveable { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var booked by remember { mutableStateOf<PatientAppointmentDto?>(null) }
            val scope = rememberCoroutineScope()
            val errorText = rememberErrorText()
            val fullError = stringResource(R.string.booking_failed_full)
            var reviews by remember { mutableStateOf<List<PublicReviewDto>>(emptyList()) }
            LaunchedEffect(doctorId) {
                (repo.doctorReviews(doctorId) as? Result.Ok)?.let { reviews = it.value.items }
            }

            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DoctorHeader(doctor, container::doctorPhotoUrl)
                if (doctor.ratingCount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        RatingSummary(doctor.ratingAverage, doctor.ratingCount)
                    }
                }
                doctor.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.doctor_about), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(bio, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                ReviewList(reviews)
                if (!doctor.bookingEnabled) {
                    Text(stringResource(R.string.booking_closed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (days.isEmpty()) {
                    Text(stringResource(R.string.no_free_slot), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.choose_day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(days.keys.toList(), key = { it.toString() }) { d ->
                            BrightChip(AppointmentLogic.dayText(d, language), d == selectedDay) {
                                day = d.toString()
                                slotId = null
                            }
                        }
                    }
                    Text(stringResource(R.string.choose_time), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedDay?.let { days[it] }.orEmpty().forEach { s: SlotDto ->
                            BrightChip(AppointmentLogic.timeText(s.startsAt, language), s.id == slotId) { slotId = s.id }
                        }
                    }
                    if (patient == null) {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ValiasrColors.BrandSoft)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.booking_signin_needed))
                                Button(onClick = onSignIn, Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.signin_action)) }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.patient_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { if (it.length <= 500) note = it },
                            label = { Text(stringResource(R.string.note_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                        ErrorText(error)
                        PrimaryButton(
                            stringResource(R.string.confirm_booking),
                            enabled = slotId != null && name.trim().length >= 2,
                            loading = busy,
                            onClick = {
                                val id = slotId ?: return@PrimaryButton
                                busy = true
                                error = null
                                scope.launch {
                                    when (val r = repo.book(id, name, note)) {
                                        is Result.Ok -> booked = r.value
                                        is Result.Err -> {
                                            error = if (r.error.code == "slot_full" && language != AppLanguage.FA) fullError else errorText(r.error)
                                            if (r.error.code == "slot_full") loader.reload()
                                        }
                                    }
                                    busy = false
                                }
                            },
                        )
                    }
                }
            }
            booked?.let { BookedDialog(it, onDone = { booked = null; onMyAppointments() }) }
        }
    }
}

@Composable
private fun DoctorHeader(doctor: DoctorDto, photoUrl: (DoctorDto) -> String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(ValiasrColors.GradientStart, ValiasrColors.Primary)))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.background(Color.White, CircleShape).padding(3.dp)) { DoctorAvatar(doctor, photoUrl, 96.dp) }
            Text(doctor.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Text(doctor.title ?: doctor.specialtyName, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            val place = listOfNotNull(doctor.clinicName, doctor.clinicLocation).joinToString(" · ")
            if (place.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, contentDescription = null, Modifier.size(16.dp), tint = ValiasrColors.GoldDeco)
                    Text(place, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
        }
    }
}

/** پیام ثبت نوبت، با کد پیگیری؛ در اندروید ۱۳ به بالا اجازه اعلان برای یادآور گرفته می‌شود. */
@Composable
private fun BookedDialog(a: PatientAppointmentDto, onDone: () -> Unit) {
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ValiasrColors.Success, modifier = Modifier.size(40.dp)) },
        title = { Text(stringResource(R.string.booked_title), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(a.doctorName, fontWeight = FontWeight.Bold)
                Text(AppointmentLogic.dateTimeText(a.startsAt, language))
                Text(stringResource(R.string.tracking_code, "⁦${a.trackingCode}⁩"), fontWeight = FontWeight.Bold, color = ValiasrColors.Link)
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, Modifier.size(18.dp), tint = ValiasrColors.Warning)
                    Text(
                        stringResource(if (needsPermission) R.string.notif_body else R.string.booked_reminder_note),
                        Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (needsPermission) {
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.notif_allow)) }
            } else {
                Button(onClick = onDone) { Text(stringResource(R.string.nav_appointments)) }
            }
        },
        dismissButton = {
            if (needsPermission) TextButton(onClick = onDone) { Text(stringResource(R.string.later)) }
        },
    )
}

/** زبانه نوبت‌های من: نوبت‌های پیش رو و گذشته، با امکان لغو. */
@Composable
fun MyAppointmentsTab(
    container: AppContainer,
    padding: PaddingValues,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onFindDoctor: () -> Unit,
) {
    if (!signedIn) {
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Event, contentDescription = null, Modifier.size(64.dp), tint = ValiasrColors.Brand)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.booking_signin_needed), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSignIn, Modifier.height(48.dp)) { Text(stringResource(R.string.signin_action)) }
        }
        return
    }
    val repo = container.appointmentRepository
    val language = LocalAppLanguage.current
    val loader = rememberLoader(language) { repo.myAppointments() }
    val scope = rememberCoroutineScope()
    val errorText = rememberErrorText()
    var cancelling by remember { mutableStateOf<PatientAppointmentDto?>(null) }
    var reviewing by remember { mutableStateOf<PatientAppointmentDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LoadContent(loader, Modifier.padding(padding)) { items ->
        val now = Instant.now()
        val upcoming = AppointmentLogic.upcoming(items, now)
        val past = items.filter { it !in upcoming }.sortedByDescending { AppointmentLogic.instant(it.startsAt) }
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Event, contentDescription = null, Modifier.size(64.dp), tint = ValiasrColors.Brand)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.my_appointments_empty))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onFindDoctor, Modifier.height(48.dp)) { Text(stringResource(R.string.find_doctor)) }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { item { ErrorText(it) } }
                if (upcoming.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.appt_upcoming)) }
                    items(upcoming, key = { it.id }) { a -> AppointmentCard(a, onCancel = { cancelling = a }, onReview = null) }
                }
                if (past.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.appt_past)) }
                    items(past, key = { it.id }) { a -> AppointmentCard(a, onCancel = null, onReview = { reviewing = a }) }
                }
            }
        }
    }
    reviewing?.let { a ->
        ReviewDialog(container, a, onClose = { submitted ->
            reviewing = null
            if (submitted) loader.reload()
        })
    }
    cancelling?.let { a ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text(stringResource(R.string.cancel_confirm)) },
            text = { Text(a.doctorName + "\n" + AppointmentLogic.dateTimeText(a.startsAt, LocalAppLanguage.current)) },
            confirmButton = {
                TextButton(onClick = {
                    cancelling = null
                    scope.launch {
                        when (val r = repo.cancel(a.id)) {
                            is Result.Ok -> {
                                error = null
                                loader.reload()
                            }
                            is Result.Err -> error = errorText(r.error)
                        }
                    }
                }) { Text(stringResource(R.string.yes_cancel), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text(stringResource(R.string.keep)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun AppointmentCard(a: PatientAppointmentDto, onCancel: (() -> Unit)?, onReview: (() -> Unit)?) {
    val language = LocalAppLanguage.current
    val active = a.status == "booked" && onCancel != null
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 2.dp else 0.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // نوار رنگی کنار کارت نوبت فعال
            if (active) Box(Modifier.width(6.dp).fillMaxHeight().background(ValiasrColors.Brand))
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.doctorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(a.specialtyName, style = MaterialTheme.typography.bodySmall, color = ValiasrColors.Link)
                    }
                    StatusChip(a.status, statusLabel(a.status))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Event, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        AppointmentLogic.dateTimeText(a.startsAt, language) + " · " + stringResource(R.string.minutes_short, digits(a.minutes, language)),
                        Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                listOfNotNull(a.clinicName, a.clinicLocation).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(it, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    stringResource(R.string.tracking_code, "⁦${a.trackingCode}⁩"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (a.status == "cancelled" && a.cancelledBy == "staff") {
                    Text(stringResource(R.string.cancelled_by_hospital), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (a.canReview && onReview != null) {
                    Button(onClick = onReview, Modifier.height(44.dp)) { Text(stringResource(R.string.review_rate_action)) }
                }
                a.myReview?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.review_your_rating), style = MaterialTheme.typography.bodySmall)
                        StarRow(r.stars.toDouble())
                        if (r.status == "pending") {
                            Text(stringResource(R.string.review_pending_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (active && a.canCancel) {
                    OutlinedButton(
                        onClick = onCancel!!,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.cancel_appt)) }
                }
            }
        }
    }
}
