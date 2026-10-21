package com.barati.razi.feature.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.barati.razi.R
import com.barati.razi.ui.theme.ValiasrColors

/** صفحه آغاز: شیب سبز برند به آبی نفتی، نشان بیمارستان در دایره سفید و سه نقطه طلایی در حال بارگذاری. */
@Composable
fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ValiasrColors.GradientStart, ValiasrColors.Primary, ValiasrColors.GradientEnd))),
        contentAlignment = Alignment.Center,
    ) {
        // دایره‌های کم‌رنگ پس‌زمینه، برای عمق
        Box(Modifier.size(320.dp).background(Color.White.copy(alpha = 0.05f), CircleShape))
        Box(Modifier.size(220.dp).background(Color.White.copy(alpha = 0.07f), CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                Modifier.size(132.dp).scale(pulse).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.logo_mark), contentDescription = null, Modifier.size(width = 72.dp, height = 84.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.hospital_full_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            LoadingDots()
        }
    }
}

@Composable
private fun LoadingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = i * 200, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(Modifier.size(10.dp).alpha(a).background(ValiasrColors.GoldDeco, CircleShape))
        }
    }
}
