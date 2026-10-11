package io.github.pxgray.clasp.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportInboxTest {
    @Test
    fun `content uris are taken`() {
        val inbox = ImportInbox()
        val uri = Uri.parse("content://mail/attachments/ticket.pkpass")
        inbox.offer(uri)
        assertEquals(uri, inbox.pending.value)
    }

    @Test
    fun `other schemes are ignored`() {
        val inbox = ImportInbox()
        listOf(
            "file:///data/data/io.github.pxgray.clasp/databases/wallet.db",
            "https://example.com/ticket.pkpass",
            "ticket.pkpass",
        ).forEach { inbox.offer(Uri.parse(it)) }
        assertNull(inbox.pending.value)
    }
}
