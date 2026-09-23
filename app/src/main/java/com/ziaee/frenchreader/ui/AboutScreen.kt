package com.ziaee.frenchreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.components.EditorialSectionHeader
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign

private const val DEVELOPER_NAME = "Abolfazl Ziaeemehr"
private const val GITHUB_URL = "https://github.com/Ziaeemehr"
private const val LINKEDIN_URL = "https://www.linkedin.com/in/ziaeemehr/"
private const val EMAIL_ADDRESS = "a.ziaeemehr@gmail.com"
private const val PROJECT_URL = "https://github.com/Ziaeemehr/FrenchReaderSpike"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) { readAppVersion(context) }
    val openFailedMessage = stringResource(R.string.about_open_link_failed)
    val openUrl: (String) -> Unit = { url ->
        launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), openFailedMessage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FrenchReaderDesign.spacing.medium, vertical = FrenchReaderDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = FrenchReaderDesign.spacing.small),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The launcher mipmap is an adaptive-icon XML, which painterResource can't load;
                // draw its vector foreground on the launcher background instead.
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(22.dp))
                        .background(colorResource(R.color.ic_launcher_background))
                )
                Spacer(Modifier.height(FrenchReaderDesign.spacing.xSmall))
                Text(
                    text = stringResource(R.string.app_name),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.about_version, version.name, version.code),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(FrenchReaderDesign.spacing.small))
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            AboutSection(stringResource(R.string.about_developer_section)) {
                Text(
                    text = DEVELOPER_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.xSmall)
                )
                AboutLinkRow(Icons.Default.Code, stringResource(R.string.about_github), GITHUB_URL) { openUrl(GITHUB_URL) }
                AboutLinkRow(Icons.Default.Person, stringResource(R.string.about_linkedin), LINKEDIN_URL) { openUrl(LINKEDIN_URL) }
                AboutLinkRow(Icons.Default.Email, stringResource(R.string.about_email), EMAIL_ADDRESS) {
                    val emailUri = Uri.parse("mailto:$EMAIL_ADDRESS").buildUpon()
                        .appendQueryParameter("subject", "French Reader")
                        .build()
                    launchIntent(context, Intent(Intent.ACTION_SENDTO, emailUri), openFailedMessage)
                }
            }

            AboutSection(stringResource(R.string.about_project_section)) {
                AboutLinkRow(Icons.Default.Code, stringResource(R.string.about_source_code), PROJECT_URL) { openUrl(PROJECT_URL) }
                AboutLinkRow(Icons.Default.Update, stringResource(R.string.about_check_updates), "$PROJECT_URL/releases/latest") { openUrl("$PROJECT_URL/releases/latest") }
                AboutLinkRow(Icons.Default.BugReport, stringResource(R.string.about_report_bug), "$PROJECT_URL/issues/new") { openUrl("$PROJECT_URL/issues/new") }
                AboutLinkRow(Icons.Default.Policy, stringResource(R.string.about_privacy_policy), "PRIVACY.md") { openUrl("$PROJECT_URL/blob/main/PRIVACY.md") }
                AboutLinkRow(Icons.Default.Description, stringResource(R.string.about_license), "MIT License") { openUrl("$PROJECT_URL/blob/main/LICENSE") }
            }

            AboutSection(stringResource(R.string.about_credits_section)) {
                AboutLinkRow(Icons.Default.Code, "edge-tts (LGPL-3.0)", stringResource(R.string.about_edge_tts_credit)) {
                    openUrl("https://github.com/rany2/edge-tts")
                }
                AboutLinkRow(Icons.Default.Code, "Chaquopy", stringResource(R.string.about_chaquopy_credit)) {
                    openUrl("https://chaquo.com/chaquopy/")
                }
                AboutLinkRow(Icons.Default.Description, "Vazirmatn (SIL OFL 1.1)", stringResource(R.string.about_vazirmatn_credit)) {
                    openUrl("https://github.com/rastikerdar/vazirmatn")
                }
                CreditText(stringResource(R.string.about_software_credits))
                CreditText(stringResource(R.string.about_content_credits))
            }

            Text(
                text = stringResource(R.string.about_footer),
                modifier = Modifier.fillMaxWidth().padding(vertical = FrenchReaderDesign.spacing.medium),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = FrenchReaderDesign.spacing.medium)) {
        EditorialSectionHeader(title = title, modifier = Modifier.padding(bottom = FrenchReaderDesign.spacing.xSmall))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(FrenchReaderDesign.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = FrenchReaderDesign.spacing.small)
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CreditText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(FrenchReaderDesign.spacing.small),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private data class AppVersion(val name: String, val code: Long)

@Suppress("DEPRECATION")
private fun readAppVersion(context: Context): AppVersion {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    return AppVersion(info.versionName.orEmpty(), versionCode)
}

private fun launchIntent(context: Context, intent: Intent, failureMessage: String) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
    }
}
