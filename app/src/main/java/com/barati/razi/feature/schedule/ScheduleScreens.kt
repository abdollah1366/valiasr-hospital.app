package com.barati.razi.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barati.razi.AppContainer
import com.barati.razi.core.data.CachedSchedule
import com.barati.razi.core.data.can
import com.barati.razi.core.network.MeDto
import com.barati.razi.core.network.MyShiftDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.ScheduleCellDto
import com.barati.razi.core.network.ScheduleDetailDto
import com.barati.razi.core.network.SwapBody
import com.barati.razi.core.network.SwapDto
import com.barati.razi.core.schedule.ScheduleLogic
import com.barati.razi.core.schedule.ShiftTone
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.feature.training.EmptyState
import com.barati.razi.feature.training.Pill
import com.barati.razi.feature.training.TrainingColors
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.LoadingBox
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.coroutines.launch

private data class Palette(val fg: Color, val bg: Color)

private fun palette(tone: ShiftTone) = when (tone) {
    ShiftTone.Morning -> Palette(TrainingColors.amber, TrainingColors.amberBg)
    ShiftTone.Evening -> Palette(TrainingColors.blue, TrainingColors.blueBg)
    ShiftTone.Night -> Palette(Color(0xFF4527A0), Color(0xFFEDE7F6))
    ShiftTone.Off -> Palette(TrainingColors.gray, TrainingColors.grayBg)
    ShiftTone.Leave -> Palette(TrainingColors.green, TrainingColors.greenBg)
}

private fun monthTitle(jy: Int, jm: Int) = "${Jalali.MONTH_NAMES[jm - 1]} ${toPersianDigits(jy.toString())}"

private fun todayJalali() = Jalali.fromGregorian(LocalDate.now())

@Composable
private fun MonthBar(title: String, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, enabled = canPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "ماه قبل")
        }
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "ماه بعد")
        }
    }
}

// ---------------------------------------------------------------- برنامه من

@Composable
fun MyScheduleScreen(
    container: AppContainer,
    me: MeDto,
    onBack: () -> Unit,
    onUnits: () -> Unit,
    onOnDuty: () -> Unit,
    onSwaps: () -> Unit,
) {
    val repo = container.scheduleRepository
    val today = remember { todayJalali() }
    var version by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<CachedSchedule?>(null) }
    var offline by remember { mutableStateOf(false) }
    var jy by remember { mutableIntStateOf(today.year) }
    var jm by remember { mutableIntStateOf(today.month) }
    var selected by remember { mutableStateOf<MyShiftDto?>(null) }

    LaunchedEffect(version) {
        loading = true
        when (val r = repo.mine(me.id)) {
            is Result.Ok -> {
                data = repo.cachedMine(me.id)
                offline = false
                error = null
            }
            is Result.Err -> {
                val cached = repo.cachedMine(me.id)
                data = cached
                offline = cached != null
                error = if (cached == null) r.error.message else null
            }
        }
        loading = false
    }

    RaziScaffold(title = "برنامه شیفت", onBack = onBack) { padding ->
        val d = data
        when {
            loading && d == null -> LoadingBox(Modifier.padding(padding))
            d == null -> com.barati.razi.ui.components.MessageBox(error ?: "برنامه‌ای دریافت نشد", Modifier.padding(padding), "تلاش دوباره") { version++ }
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (offline) {
                    Text(
                        "بدون اینترنت؛ آخرین برنامه ذخیره‌شده نمایش داده می‌شود.",
                        color = TrainingColors.amber,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                d.data.nextShift?.let { NextShiftCard(it) }
                val next = ScheduleLogic.nextMonth(today.year, today.month)
                MonthBar(
                    monthTitle(jy, jm),
                    canPrev = jy != today.year || jm != today.month,
                    canNext = jy == today.year && jm == today.month,
                    onPrev = { jy = today.year; jm = today.month },
                    onNext = { jy = next.first; jm = next.second },
                )
                CalendarGrid(jy, jm, d.data.items) { selected = it }
                if (!ScheduleLogic.hasShifts(jy, jm, d.data.items)) {
                    Text("برنامه‌ای برای این ماه منتشر نشده است.", style = MaterialTheme.typography.bodyMedium)
                }
                Legend()
                OutlinedButton(onClick = onSwaps, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.SwapHoriz, null)
                    Spacer(Modifier.width(8.dp))
                    Text("درخواست‌های جابجایی")
                }
                OutlinedButton(onClick = onUnits, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Groups, null)
                    Spacer(Modifier.width(8.dp))
                    Text("برنامه بخش‌ها")
                }
                if (me.can("schedule.read.all")) {
                    OutlinedButton(onClick = onOnDuty, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CalendarMonth, null)
                        Spacer(Modifier.width(8.dp))
                        Text("کشیک امروز")
                    }
                }
            }
        }
    }

    selected?.let { s ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("بستن") } },
            title = { Text(s.jalali.let { toPersianDigits(it) }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.title, fontWeight = FontWeight.Bold)
                    ScheduleLogic.timeRange(s.startsAt, s.endsAt)?.let { Text(toPersianDigits(it)) }
                    Text(s.unitName)
                    if (s.kind == "work" && s.workHours > 0) Text(toPersianDigits("ساعت کار: ${"%.1f".format(s.workHours)}"))
                }
            },
        )
    }
}

@Composable
private fun NextShiftCard(s: MyShiftDto) {
    val p = palette(ScheduleLogic.tone(s.kind, s.startsAt))
    Card(colors = CardDefaults.cardColors(containerColor = p.bg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("شیفت بعدی شما", style = MaterialTheme.typography.labelMedium, color = p.fg)
            Text(toPersianDigits("${s.jalali}  ·  ${s.label}"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = p.fg)
            ScheduleLogic.timeRange(s.startsAt, s.endsAt)?.let { Text(toPersianDigits(it)) }
            Text(s.unitName, style = MaterialTheme.typography.bodySmall)
            ScheduleLogic.untilText(s.startsAt, s.endsAt)?.let { Text(toPersianDigits(it), fontWeight = FontWeight.Bold, color = p.fg) }
        }
    }
}

@Composable
private fun CalendarGrid(jy: Int, jm: Int, items: List<MyShiftDto>, onPick: (MyShiftDto) -> Unit) {
    val rows = remember(jy, jm, items) { ScheduleLogic.monthRows(jy, jm, items) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            ScheduleLogic.WEEKDAY_HEADERS.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    Box(Modifier.weight(1f).aspectRatio(0.8f), contentAlignment = Alignment.Center) {
                        if (cell.day != 0) {
                            val shift = cell.shift
                            val p = shift?.let { palette(ScheduleLogic.tone(it.kind, it.startsAt)) }
                            val base = Modifier.fillMaxSize().background(p?.bg ?: Color.Transparent, RoundedCornerShape(8.dp))
                            Column(
                                if (shift != null) base.clickable { onPick(shift) } else base,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(toPersianDigits(cell.day.toString()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (shift != null) {
                                    Text(shift.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p!!.fg, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "صبح" to ShiftTone.Morning, "عصر" to ShiftTone.Evening, "شب" to ShiftTone.Night,
            "استراحت" to ShiftTone.Off, "مرخصی" to ShiftTone.Leave,
        ).forEach { (name, tone) -> palette(tone).let { Pill(name, it.fg, it.bg) } }
    }
}

// ---------------------------------------------------------------- برنامه بخش‌ها

@Composable
fun ScheduleUnitsScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val today = remember { todayJalali() }
    var jy by remember { mutableIntStateOf(today.year) }
    var jm by remember { mutableIntStateOf(today.month) }
    val loader = rememberLoader(jy, jm) { container.scheduleRepository.units(jy, jm) }
    RaziScaffold(title = "برنامه بخش‌ها", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            MonthBar(
                monthTitle(jy, jm), true, true,
                onPrev = { ScheduleLogic.prevMonth(jy, jm).let { jy = it.first; jm = it.second } },
                onNext = { ScheduleLogic.nextMonth(jy, jm).let { jy = it.first; jm = it.second } },
            )
            LoadContent(loader) { rows ->
                if (rows.isEmpty()) {
                    EmptyState(Icons.Filled.Groups, "بخشی برای نمایش نیست", "برنامه بخشی که در آن هستید پس از انتشار اینجا دیده می‌شود.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                        items(rows, key = { it.unitId }) { row ->
                            val s = row.schedule
                            Card(Modifier.fillMaxWidth().let { if (s != null) it.clickable { onOpen(s.id) } else it }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.unitName, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (s == null) "برنامه‌ای ثبت یا منتشر نشده" else toPersianDigits("${s.memberCount} نفر"),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (s != null) {
                                        val ok = s.status == "published"
                                        Pill(
                                            ScheduleLogic.statusName(s.status),
                                            if (ok) TrainingColors.green else TrainingColors.amber,
                                            if (ok) TrainingColors.greenBg else TrainingColors.amberBg,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- جدول یک بخش

private val CELL_W = 40.dp
private val CELL_H = 40.dp
private val NAME_W = 110.dp

@Composable
fun ScheduleRosterScreen(container: AppContainer, me: MeDto, id: String, onBack: () -> Unit) {
    val repo = container.scheduleRepository
    val scope = rememberCoroutineScope()
    var updated by remember { mutableStateOf<ScheduleDetailDto?>(null) }
    val loader = rememberLoader(id) { repo.detail(id) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var returning by remember { mutableStateOf(false) }
    var swapTarget by remember { mutableStateOf<Pair<String, ScheduleCellDto>?>(null) } // (partnerId, cell)
    var swapSent by remember { mutableStateOf(false) }

    RaziScaffold(title = "برنامه ماهانه", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { loaded ->
            val d = updated ?: loaded
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val ok = d.status == "published"
                    Pill(ScheduleLogic.statusName(d.status), if (ok) TrainingColors.green else TrainingColors.amber, if (ok) TrainingColors.greenBg else TrainingColors.amberBg)
                }
                d.returnNote?.takeIf { d.status == "draft" }?.let {
                    Text("برگشت‌خورده: $it", color = TrainingColors.red, style = MaterialTheme.typography.bodySmall)
                }
                if (swapSent) Text("درخواست جابجایی ارسال شد.", color = TrainingColors.green)
                ErrorText(error)
                RosterTable(d, me.id) { member, cell ->
                    if ("swap" in d.actions && member.userId != me.id && ScheduleLogic.isSwappable(cell)) swapTarget = member.userId to cell
                }
                if ("swap" in d.actions) {
                    Text("برای درخواست جابجایی، روی شیفت همکار خود بزنید.", style = MaterialTheme.typography.bodySmall)
                }
                if ("approve" in d.actions || "return" in d.actions) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if ("approve" in d.actions) {
                            Button(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    when (val r = repo.approve(d.id)) {
                                        is Result.Ok -> { updated = r.value; error = null }
                                        is Result.Err -> error = r.error.message
                                    }
                                    busy = false
                                }
                            }) { Text("تأیید و انتشار") }
                        }
                        if ("return" in d.actions) {
                            OutlinedButton(enabled = !busy, onClick = { returning = true }) { Text("برگشت برای اصلاح") }
                        }
                    }
                }
                if (d.warnings.isNotEmpty()) {
                    Text("هشدارها", fontWeight = FontWeight.Bold)
                    d.warnings.take(30).forEach {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Warning, null, tint = TrainingColors.amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(toPersianDigits(it.message), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("ویرایش جدول فعلاً از پنل وب انجام می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (returning) {
                var note by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { returning = false },
                    title = { Text("دلیل برگشت") },
                    text = { OutlinedTextField(note, { note = it }, label = { Text("توضیح برای مسئول بخش") }, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = {
                        TextButton(enabled = note.isNotBlank() && !busy, onClick = {
                            scope.launch {
                                busy = true
                                when (val r = repo.returnToDraft(d.id, note.trim())) {
                                    is Result.Ok -> { updated = r.value; error = null; returning = false }
                                    is Result.Err -> error = r.error.message
                                }
                                busy = false
                            }
                        }) { Text("برگشت") }
                    },
                    dismissButton = { TextButton(onClick = { returning = false }) { Text("انصراف") } },
                )
            }
            swapTarget?.let { (partnerId, theirCell) ->
                SwapDialog(
                    detail = d,
                    myId = me.id,
                    partnerId = partnerId,
                    theirCell = theirCell,
                    onDismiss = { swapTarget = null },
                    onSend = { myDay, note ->
                        scope.launch {
                            val r = repo.requestSwap(SwapBody(d.id, myDay, partnerId, theirCell.day, note.ifBlank { null }))
                            when (r) {
                                is Result.Ok -> { swapSent = true; error = null; swapTarget = null }
                                is Result.Err -> { error = r.error.message; swapTarget = null }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun cellColors(cell: ScheduleCellDto?): Palette = when {
    cell == null || cell.code == null -> Palette(MaterialTheme.colorScheme.onSurfaceVariant, Color.Transparent)
    cell.kind == "off" -> palette(ShiftTone.Off)
    cell.kind == "leave" -> palette(ShiftTone.Leave)
    else -> palette(ShiftTone.Evening)
}

@Composable
private fun RosterTable(d: ScheduleDetailDto, myId: String, onCell: (com.barati.razi.core.network.ScheduleMemberDto, ScheduleCellDto) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.width(NAME_W).height(28.dp))
            d.members.forEach { m ->
                Box(Modifier.width(NAME_W).height(CELL_H), contentAlignment = Alignment.CenterStart) {
                    Text(
                        m.fullName,
                        maxLines = 2,
                        fontSize = 12.sp,
                        fontWeight = if (m.userId == myId) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                d.days.forEach { day ->
                    Box(Modifier.width(CELL_W).height(28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            toPersianDigits(day.day.toString()),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (day.isHoliday || day.weekday == 4) TrainingColors.red else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            d.members.forEach { m ->
                val byDay = m.cells.associateBy { it.day }
                Row {
                    d.days.forEach { day ->
                        val cell = byDay[day.date]
                        val p = cellColors(cell)
                        Box(
                            Modifier.width(CELL_W).height(CELL_H).padding(1.dp)
                                .background(p.bg, RoundedCornerShape(6.dp))
                                .clickable(enabled = cell != null) { cell?.let { onCell(m, it) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cell?.code != null) Text(cell.label, fontSize = 11.sp, color = p.fg, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapDialog(
    detail: ScheduleDetailDto,
    myId: String,
    partnerId: String,
    theirCell: ScheduleCellDto,
    onDismiss: () -> Unit,
    onSend: (myDay: String, note: String) -> Unit,
) {
    val mine = detail.members.firstOrNull { it.userId == myId }?.cells.orEmpty().filter(ScheduleLogic::isSwappable)
    val partner = detail.members.firstOrNull { it.userId == partnerId }
    val days = detail.days.associateBy { it.date }
    var picked by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("درخواست جابجایی") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    toPersianDigits("شیفت ${theirCell.label} ${partner?.fullName.orEmpty()} در ${days[theirCell.day]?.jalali.orEmpty()}"),
                    fontWeight = FontWeight.Bold,
                )
                Text("را با کدام یک از شیفت‌های خودتان عوض می‌کنید؟")
                if (mine.isEmpty()) Text("شیفت کاری برای جابجایی ندارید.", color = TrainingColors.red)
                mine.forEach { c ->
                    val selected = picked == c.day
                    TextButton(onClick = { picked = c.day }) {
                        Text(
                            toPersianDigits("${if (selected) "✓ " else ""}${days[c.day]?.jalali.orEmpty()}  ·  ${c.label}"),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("توضیح (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = picked != null, onClick = { onSend(picked!!, note.trim()) }) { Text("ارسال") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

// ---------------------------------------------------------------- کشیک

@Composable
fun OnDutyScreen(container: AppContainer, onBack: () -> Unit) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    val loader = rememberLoader(day) { container.scheduleRepository.onDuty(day.toString()) }
    RaziScaffold(title = "کشیک", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            MonthBar(
                toPersianDigits(Jalali.formatLong(day)), true, true,
                onPrev = { day = day.minusDays(1) },
                onNext = { day = day.plusDays(1) },
            )
            LoadContent(loader) { d ->
                if (d.units.isEmpty()) {
                    EmptyState(Icons.Filled.Groups, "واحدی برای نمایش نیست", "برنامه منتشرشده‌ای برای این روز پیدا نشد.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                        items(d.units, key = { it.unitId }) { u ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(u.unitName, fontWeight = FontWeight.Bold)
                                    if (!u.published) Text("برنامه این ماه منتشر نشده", style = MaterialTheme.typography.bodySmall, color = TrainingColors.amber)
                                    if (u.published && u.people.isEmpty()) Text("کسی برنامه ندارد", style = MaterialTheme.typography.bodySmall)
                                    u.people.forEach { p ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(p.person.fullName)
                                                Text(
                                                    toPersianDigits(
                                                        "${p.label}  ·  " + (ScheduleLogic.timeRange(p.startsAt, p.endsAt) ?: ""),
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            if (p.onDutyNow) Pill("سر کار", TrainingColors.green, TrainingColors.greenBg)
                                            else if (p.oncallNow) Pill("آنکال", TrainingColors.blue, TrainingColors.blueBg)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- جابجایی‌ها

@Composable
fun ScheduleSwapsScreen(container: AppContainer, onBack: () -> Unit) {
    val repo = container.scheduleRepository
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val loader = rememberLoader(version) { repo.swaps() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun act(s: SwapDto, action: String) {
        scope.launch {
            busy = true
            when (val r = repo.answerSwap(s.id, action)) {
                is Result.Ok -> { error = null; version++ }
                is Result.Err -> error = r.error.message
            }
            busy = false
        }
    }

    RaziScaffold(title = "جابجایی شیفت", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { list ->
            if (list.isEmpty()) {
                EmptyState(Icons.Filled.SwapHoriz, "درخواستی نیست", "برای جابجایی، در جدول ماهانه روی شیفت همکارتان بزنید.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    item { ErrorText(error) }
                    items(list, key = { it.id }) { s ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.monthTitle + " · " + s.unitName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    val done = s.status == "approved"
                                    val bad = s.status == "rejected" || s.status == "cancelled"
                                    Pill(
                                        ScheduleLogic.swapStatusName(s.status),
                                        if (done) TrainingColors.green else if (bad) TrainingColors.gray else TrainingColors.amber,
                                        if (done) TrainingColors.greenBg else if (bad) TrainingColors.grayBg else TrainingColors.amberBg,
                                    )
                                }
                                Text(toPersianDigits("${s.requester.fullName}: ${s.requesterDay.jalali} (${s.requesterDay.label ?: "—"})"))
                                Text(toPersianDigits("${s.partner.fullName}: ${s.partnerDay.jalali} (${s.partnerDay.label ?: "—"})"))
                                s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                s.decisionNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TrainingColors.red) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    s.actions.forEach { a ->
                                        val name = ScheduleLogic.swapActionName(a) ?: return@forEach
                                        if (a == "accept" || a == "approve") Button(enabled = !busy, onClick = { act(s, a) }) { Text(name) }
                                        else OutlinedButton(enabled = !busy, onClick = { act(s, a) }) { Text(name) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
