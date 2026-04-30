package net.d7z.net.oss.uvc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import net.d7z.net.oss.uvc.ui.screen.NoticeTextScreen
import net.d7z.net.oss.uvc.ui.theme.UvcComposeTheme

class NoticeTextActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL).orEmpty()
        val noticeType = intent.getStringExtra(EXTRA_NOTICE_TYPE).orEmpty()
        val noticeText = intent.getStringExtra(EXTRA_NOTICE_TEXT).orEmpty()

        setContent {
            UvcComposeTheme {
                NoticeTextScreen(
                    title = title,
                    sourceUrl = sourceUrl,
                    noticeType = noticeType,
                    noticeText = noticeText,
                    onBack = ::finish,
                    onOpenSource = {
                        if (sourceUrl.isNotBlank()) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
                        }
                    }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SOURCE_URL = "source_url"
        private const val EXTRA_NOTICE_TYPE = "notice_type"
        private const val EXTRA_NOTICE_TEXT = "notice_text"

        fun createIntent(
            context: Context,
            title: String,
            noticeText: String,
            sourceUrl: String,
            noticeType: String
        ): Intent = Intent(context, NoticeTextActivity::class.java).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
            putExtra(EXTRA_NOTICE_TYPE, noticeType)
            putExtra(EXTRA_NOTICE_TEXT, noticeText)
        }
    }
}
