package com.barati.razi.feature.appointments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.barati.razi.R
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.network.DoctorDto
import com.barati.razi.core.network.SpecialtyDto
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors

/** آیکون هر کلید تخصص (همان فهرست SPECIALTY_ICONS سرور). */
fun specialtyIcon(key: String): ImageVector = when (key) {
    "heart" -> Icons.Filled.Favorite
    "bone" -> Icons.Filled.AccessibilityNew
    "brain" -> Icons.Filled.Psychology
    "psychology" -> Icons.Filled.SelfImprovement
    "baby" -> Icons.Filled.ChildFriendly
    "female" -> Icons.Filled.PregnantWoman
    "child" -> Icons.Filled.ChildCare
    "eye" -> Icons.Filled.Visibility
    "ear" -> Icons.Filled.Hearing
    "tooth" -> Icons.Filled.SentimentSatisfiedAlt
    "lungs" -> Icons.Filled.Air
    "stomach" -> Icons.Filled.Restaurant
    "kidney" -> Icons.Filled.WaterDrop
    "skin" -> Icons.Filled.Spa
    "scalpel" -> Icons.Filled.ContentCut
    "blood" -> Icons.Filled.Opacity
    "ribbon" -> Icons.Filled.VolunteerActivism
    "radiology" -> Icons.Filled.Scanner
    "lab" -> Icons.Filled.Science
    else -> Icons.Filled.MedicalServices
}

/** رنگ ملایم پس‌زمینه کاشی هر تخصص؛ چرخشی تا شبکه تخصص‌ها یکنواخت نباشد. */
private val SPECIALTY_TONES = listOf(
    Color(0xFFE0F2F0) to Color(0xFF017A6B),
    Color(0xFFE3EEF8) to Color(0xFF2F6DB5),
    Color(0xFFFDF3E3) to Color(0xFF8A4B08),
    Color(0xFFF1E9F7) to Color(0xFF5E3A87),
    Color(0xFFFBECE8) to Color(0xFFB9341C),
    Color(0xFFE7F3EA) to Color(0xFF2E7D3E),
)

fun specialtyTone(index: Int): Pair<Color, Color> = SPECIALTY_TONES[index % SPECIALTY_TONES.size]

/** عکس پزشک؛ اگر عکس نداشته باشد، حرف اول نام روی شیب رنگ برند. */
@Composable
fun DoctorAvatar(doctor: DoctorDto, photoUrl: (DoctorDto) -> String, size: Dp) {
    val shape = CircleShape
    if (doctor.hasPhoto) {
        AsyncImage(
            model = photoUrl(doctor),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape).background(ValiasrColors.BrandSoft),
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(ValiasrColors.GradientStart, ValiasrColors.Primary))),
            contentAlignment = Alignment.Center,
        ) {
            val initial = doctor.fullName.removePrefix("دکتر").trim().take(1)
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** کارت پزشک در فهرست: عکس، نام، تخصص و نزدیک‌ترین نوبت. */
@Composable
fun DoctorRowCard(doctor: DoctorDto, photoUrl: (DoctorDto) -> String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DoctorAvatar(doctor, photoUrl, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(doctor.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    doctor.title ?: doctor.specialtyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RatingSummary(doctor.ratingAverage, doctor.ratingCount)
                NextFreeChip(doctor)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NextFreeChip(doctor: DoctorDto) {
    val language = LocalAppLanguage.current
    val next = doctor.nextFreeAt
    val (bg, fg) = if (next != null && doctor.bookingEnabled) ValiasrColors.SuccessSoft to ValiasrColors.Success else ValiasrColors.SurfaceVariant to ValiasrColors.Text2
    Row(
        Modifier.padding(top = 4.dp).background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Event, contentDescription = null, Modifier.size(14.dp), tint = fg)
        Text(
            if (next != null && doctor.bookingEnabled) {
                stringResource(R.string.next_free, AppointmentLogic.dateTimeText(next, language))
            } else {
                stringResource(R.string.no_free_slot)
            },
            Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

/** کارت عمودی پزشک برگزیده در نوار افقی صفحه خانه. */
@Composable
fun FeaturedDoctorCard(doctor: DoctorDto, photoUrl: (DoctorDto) -> String, onClick: () -> Unit) {
    Card(
        Modifier.width(150.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DoctorAvatar(doctor, photoUrl, 72.dp)
            Text(
                doctor.fullName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                doctor.specialtyName,
                style = MaterialTheme.typography.bodySmall,
                color = ValiasrColors.Link,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** کاشی تخصص با آیکون تصویری در دایره رنگی. */
@Composable
fun SpecialtyTile(specialty: SpecialtyDto, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (bg, fg) = specialtyTone(index)
    Card(
        modifier.heightIn(min = 112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(52.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(specialtyIcon(specialty.icon), contentDescription = null, Modifier.size(28.dp), tint = fg)
            }
            Text(
                specialty.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** نشان وضعیت نوبت. */
@Composable
fun StatusChip(status: String, label: String) {
    val (bg, fg) = when (status) {
        "booked" -> ValiasrColors.InfoSoft to ValiasrColors.Info
        "attended" -> ValiasrColors.SuccessSoft to ValiasrColors.Success
        "no_show" -> ValiasrColors.WarningSoft to ValiasrColors.WarningText
        else -> ValiasrColors.ErrorSoft to ValiasrColors.Error
    }
    Text(
        label,
        Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun statusLabel(status: String): String = stringResource(
    when (status) {
        "booked" -> R.string.status_booked
        "attended" -> R.string.status_attended
        "no_show" -> R.string.status_no_show
        else -> R.string.status_cancelled
    },
)
