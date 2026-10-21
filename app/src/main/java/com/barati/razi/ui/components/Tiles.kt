package com.barati.razi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.theme.brandColor

/** یک کاشی صفحه اول: آیکون در دایره، عنوان، توضیح کوتاه و شمارنده اختیاری. */
class TileItem(
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val badge: Int = 0,
    val onClick: () -> Unit,
)

/** کاشی‌ها در دو ستون؛ اگر تعداد فرد باشد، خانه آخر خالی می‌ماند. */
@Composable
fun TileGrid(tiles: List<TileItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            // ارتفاع هر ردیف را بلندترین کاشی تعیین می‌کند تا همه هم‌اندازه باشند.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> ActionTile(t, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ActionTile(t: TileItem, modifier: Modifier = Modifier) {
    Card(
        modifier.heightIn(min = 120.dp).clickable(onClick = t.onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // دایره پر رنگ برند با آیکون سفید؛ کاشی‌ها زنده‌تر و از دور شناختنی‌اند.
                Box(
                    Modifier.size(44.dp).background(brandColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(t.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary) }
                Spacer(Modifier.weight(1f))
                if (t.badge > 0) Badge { Text(toPersianDigits(if (t.badge > 99) "99+" else t.badge.toString())) }
            }
            Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            t.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** کارت تمام‌عرض با آیکون، عنوان و توضیح. */
@Composable
fun WideCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surface,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(48.dp).background(tint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, Modifier.size(28.dp), tint = tint) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
