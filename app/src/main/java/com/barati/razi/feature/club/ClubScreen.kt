package com.barati.razi.feature.club

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.RewardDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.TokensDto
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.rememberErrorText
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors
import kotlinx.coroutines.launch

fun tokenText(n: Int, language: AppLanguage): String =
    if (language == AppLanguage.FA) toPersianDigits(n.toString()) else n.toString()

/** بیمه ایمنی: خرج فقط وقتی موجودی به بهای امتیاز برسد فعال است. */
fun canAfford(balance: Int, reward: RewardDto) = balance >= reward.cost

/** باشگاه مراجعان: موجودی ژتون، معرفی اپ، امتیازهای قابل دریافت و کدهای استفاده. */
@Composable
fun ClubTab(container: AppContainer, padding: PaddingValues, signedIn: Boolean, onSignIn: () -> Unit) {
    if (!signedIn) {
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.CardGiftcard, contentDescription = null, Modifier.size(64.dp), tint = ValiasrColors.Brand)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.club_signin_needed), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSignIn, Modifier.height(48.dp)) { Text(stringResource(R.string.signin_action)) }
        }
        return
    }
    val repo = container.tokensRepository
    val language = LocalAppLanguage.current
    val loader = rememberLoader(language) { repo.overview() }
    LoadContent(loader, Modifier.padding(padding)) { t ->
        ClubContent(container, t, padding, onChanged = { loader.reload() })
    }
}

@Composable
private fun ClubContent(container: AppContainer, t: TokensDto, padding: PaddingValues, onChanged: () -> Unit) {
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val errorText = rememberErrorText()
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<RewardDto?>(null) }
    var referral by remember { mutableStateOf("") }
    val invite = stringResource(R.string.club_share_text, t.referralCode)

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = ValiasrColors.Primary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.club_balance), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                    Text(
                        tokenText(t.balance, language),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = ValiasrColors.GoldDeco,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.club_invite_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.club_invite_text, tokenText(t.referralReward, language), tokenText(t.referredBonus, language)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "⁦${t.referralCode}⁩",
                        Modifier.fillMaxWidth().background(ValiasrColors.BrandSoft, RoundedCornerShape(12.dp)).padding(12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, invite)
                            context.startActivity(Intent.createChooser(send, null))
                        }) { Text(stringResource(R.string.club_share)) }
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(t.referralCode)) }) { Text(stringResource(R.string.club_copy)) }
                    }
                }
            }
        }
        if (!t.referred) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ValiasrColors.BrandSoft)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.club_have_code), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = referral,
                            onValueChange = { referral = it.uppercase().take(12) },
                            label = { Text(stringResource(R.string.club_code_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(enabled = referral.trim().length >= 4, onClick = {
                            scope.launch {
                                when (val r = container.tokensRepository.enterReferral(referral)) {
                                    is Result.Ok -> {
                                        error = null
                                        onChanged()
                                    }
                                    is Result.Err -> error = errorText(r.error)
                                }
                            }
                        }) { Text(stringResource(R.string.club_code_submit)) }
                    }
                }
            }
        }
        item { ErrorText(error) }
        if (t.rewards.isNotEmpty()) {
            item { Text(stringResource(R.string.club_rewards), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(t.rewards, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.Bold)
                            r.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Button(enabled = canAfford(t.balance, r), onClick = { confirm = r }) {
                            Text(stringResource(R.string.club_cost, tokenText(r.cost, language)))
                        }
                    }
                }
            }
        }
        if (t.vouchers.isNotEmpty()) {
            item {
                Column {
                    Text(stringResource(R.string.club_vouchers), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.club_voucher_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(t.vouchers, key = { it.code }) { v ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(v.rewardTitle, fontWeight = FontWeight.Bold)
                        Text("⁦${v.code}⁩", style = MaterialTheme.typography.titleMedium, color = ValiasrColors.Link, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                when (v.status) {
                                    "used" -> R.string.club_voucher_used
                                    "cancelled" -> R.string.club_voucher_cancelled
                                    else -> R.string.club_voucher_issued
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (t.history.isNotEmpty()) {
            item { Text(stringResource(R.string.club_history), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(t.history, key = { it.id }) { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.note ?: "", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            AppointmentLogic.dateTimeText(h.createdAt, language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        (if (h.amount > 0) "+" else "") + tokenText(h.amount, language),
                        fontWeight = FontWeight.Bold,
                        color = if (h.amount > 0) ValiasrColors.Success else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    confirm?.let { r ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(r.title) },
            text = { Text(stringResource(R.string.club_confirm, tokenText(r.cost, language))) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        when (val res = container.tokensRepository.redeem(r.id)) {
                            is Result.Ok -> {
                                error = null
                                onChanged()
                            }
                            is Result.Err -> error = errorText(res.error)
                        }
                    }
                }) { Text(stringResource(R.string.club_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.keep)) } },
        )
    }
}
