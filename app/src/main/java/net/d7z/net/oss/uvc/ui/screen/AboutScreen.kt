package net.d7z.net.oss.uvc.ui.screen

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import net.d7z.net.oss.uvc.R

data class AboutNoticePayloads(
    val projectLicenseText: String,
    val libusbNoticeText: String,
    val libuvcLicenseText: String
)

private data class LicenseListEntry(
    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val licenseLabel: String,
    val sourceUrl: String,
    val noticeText: String,
    val actionLabel: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    noticePayloads: AboutNoticePayloads?,
    onBack: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenProjectLicense: () -> Unit,
    onOpenLibraryLicense: (title: String, text: String, sourceUrl: String, noticeType: String) -> Unit
) {
    val context = LocalContext.current
    val versionLabel = remember(context) {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionName = packageInfo.versionName ?: "1.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        "$versionName ($versionCode)"
    }
    val aboutLibraries by produceLibraries()
    val manualEntries = remember(noticePayloads) {
        noticePayloads?.let {
            listOf(
                LicenseListEntry(
                    id = "manual:libusb",
                    name = "libusb",
                    version = "bundled subtree",
                    description = "Native USB access layer linked into the application runtime.",
                    licenseLabel = "LGPL-2.1",
                    sourceUrl = "https://libusb.info",
                    noticeText = it.libusbNoticeText,
                    actionLabel = "View License"
                ),
                LicenseListEntry(
                    id = "manual:libuvc",
                    name = "libuvc",
                    version = "bundled subtree",
                    description = "Native UVC capture layer used by the streaming pipeline.",
                    licenseLabel = "BSD",
                    sourceUrl = "https://github.com/libuvc/libuvc",
                    noticeText = it.libuvcLicenseText,
                    actionLabel = "View License"
                )
            )
        }.orEmpty()
    }
    val scannedEntries = remember(aboutLibraries) {
        aboutLibraries?.libraries
            ?.mapNotNull { it.toLicenseListEntry() }
            .orEmpty()
    }
    val allEntries = remember(manualEntries, scannedEntries) {
        manualEntries + scannedEntries
    }
    val isLoading = noticePayloads == null || aboutLibraries == null

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.about_title),
                        fontFamily = FontFamily.Serif
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ProjectInfoCard(
                        versionLabel = versionLabel,
                        noticesReady = noticePayloads != null,
                        onOpenRepository = onOpenRepository,
                        onOpenProjectLicense = onOpenProjectLicense
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.about_open_source_notices),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = stringResource(R.string.about_open_source_notices_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(allEntries, key = { it.id }) { entry ->
                    LicenseEntryCard(
                        entry = entry,
                        enabled = !isLoading,
                        onOpen = {
                            onOpenLibraryLicense(
                                entry.name,
                                entry.noticeText,
                                entry.sourceUrl,
                                context.getString(R.string.about_notice_type_library)
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.about_loading),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = stringResource(R.string.about_loading_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Library.toLicenseListEntry(): LicenseListEntry? {
    val noticeText = strippedLicenseContent.takeIf { it.isNotBlank() } ?: return null
    val licenseLabel = licenses.joinToString(", ") { it.spdxId ?: it.name }.ifBlank { "Unknown" }
    val sourceUrl = scm?.url ?: website ?: licenses.firstOrNull()?.url.orEmpty()
    return LicenseListEntry(
        id = artifactId,
        name = name,
        version = artifactVersion ?: "unspecified",
        description = description,
        licenseLabel = licenseLabel,
        sourceUrl = sourceUrl,
        noticeText = noticeText,
        actionLabel = "View License"
    )
}

@Composable
private fun ProjectInfoCard(
    versionLabel: String,
    noticesReady: Boolean,
    onOpenRepository: () -> Unit,
    onOpenProjectLicense: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_project_heading),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.about_project_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Serif
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = versionLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = stringResource(R.string.about_project_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MetaRow(
                label = stringResource(R.string.about_project_repository),
                value = stringResource(R.string.about_project_repository_url),
                trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = onOpenRepository
            )
            MetaRow(
                label = stringResource(R.string.about_project_license),
                value = stringResource(R.string.about_project_license_value),
                trailingIcon = Icons.Rounded.Description,
                onClick = if (noticesReady) onOpenProjectLicense else null
            )
            MetaRow(
                label = stringResource(R.string.about_project_copyright),
                value = stringResource(R.string.about_project_copyright_value)
            )
            Text(
                text = stringResource(R.string.about_project_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenRepository,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Text(
                        text = stringResource(R.string.about_project_repository),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onOpenProjectLicense,
                    enabled = noticesReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null)
                    Text(
                        text = stringResource(R.string.about_view_project_license),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseEntryCard(
    entry: LicenseListEntry,
    enabled: Boolean,
    onOpen: () -> Unit
) {
    Card(
        onClick = onOpen,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = entry.version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    entry.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text(entry.licenseLabel) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }


        }
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = if (onClick != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeTextScreen(
    title: String,
    sourceUrl: String,
    noticeType: String,
    noticeText: String,
    onBack: () -> Unit,
    onOpenSource: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Text(title, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = false,
                                onClick = {},
                                enabled = false,
                                label = { Text(noticeType.uppercase()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        if (sourceUrl.isNotBlank()) {
                            MetaRow(
                                label = stringResource(R.string.about_dependency_source),
                                value = sourceUrl,
                                trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                                onClick = onOpenSource
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = noticeText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
