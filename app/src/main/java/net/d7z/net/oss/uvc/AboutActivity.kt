package net.d7z.net.oss.uvc

import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.d7z.net.oss.uvc.ui.screen.AboutNoticePayloads
import net.d7z.net.oss.uvc.ui.screen.AboutScreen
import net.d7z.net.oss.uvc.ui.theme.UvcComposeTheme
import java.util.zip.GZIPInputStream

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UvcComposeTheme {
                AboutRoute(
                    onBack = ::finish,
                    onOpenRepository = { openExternalUrl(getString(R.string.about_project_repository_url)) },
                    onOpenNotice = { title, text, sourceUrl, noticeType ->
                        startActivity(
                            NoticeTextActivity.createIntent(
                                context = this,
                                title = title,
                                noticeText = text,
                                sourceUrl = sourceUrl,
                                noticeType = noticeType
                            )
                        )
                    }
                )
            }
        }
    }

    private fun openExternalUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun AboutRoute(
    onBack: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenNotice: (title: String, text: String, sourceUrl: String, noticeType: String) -> Unit
) {
    val context = LocalContext.current
    val payloads by produceState<AboutNoticePayloads?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            AboutNoticePayloads(
                projectLicenseText = context.resources.readCompressedRawText(R.raw.project_license),
                libusbNoticeText = context.resources.readCompressedRawText(R.raw.libusb_notice),
                libuvcLicenseText = context.resources.readCompressedRawText(R.raw.libuvc_license)
            )
        }
    }

    AboutScreen(
        noticePayloads = payloads,
        onBack = onBack,
        onOpenRepository = onOpenRepository,
        onOpenProjectLicense = {
            payloads?.let {
                onOpenNotice(
                    context.getString(R.string.about_notice_project_license_title),
                    it.projectLicenseText,
                    context.getString(R.string.about_project_repository_url),
                    context.getString(R.string.about_notice_type_project)
                )
            }
        },
        onOpenLibraryLicense = onOpenNotice
    )
}

private fun Resources.readCompressedRawText(resId: Int): String {
    return openRawResource(resId).use { input ->
        GZIPInputStream(input).bufferedReader().use { it.readText() }
    }
}
