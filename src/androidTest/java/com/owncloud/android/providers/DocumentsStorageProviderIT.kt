package com.owncloud.android.providers

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.test.espresso.intent.rule.IntentsTestRule
import com.owncloud.android.AbstractOnServerIT
import com.owncloud.android.R
import com.owncloud.android.providers.DocumentsProviderUtils.assertListFilesEquals
import com.owncloud.android.providers.DocumentsProviderUtils.assertReadEquals
import com.owncloud.android.providers.DocumentsProviderUtils.assertRegularFile
import com.owncloud.android.providers.DocumentsProviderUtils.grantUriPermission
import com.owncloud.android.providers.DocumentsProviderUtils.listFilesBlocking
import kotlinx.coroutines.runBlocking
import net.bytebuddy.utility.RandomString
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

class DocumentsStorageProviderIT : AbstractOnServerIT() {

    @get:Rule
    val intentsTestRule = IntentsTestRule(TestActivity::class.java)

    private val context = targetContext
    private val contentResolver = context.contentResolver
    private val authority = context.getString(R.string.document_provider_authority)
    private val rootUri = DocumentsContract.buildRootUri(authority, account.name)

    private lateinit var uri: Uri
    private val rootDir get() = DocumentFile.fromTreeUri(context, uri)!!

    @Before
    // @BeforeClass needs a static context where we can't access the needed IntentsTestRule
    fun before() {
        uri = grantUriPermission(intentsTestRule.activity, rootUri)
        assertTrue(rootDir.exists())
        assertTrue(rootDir.isDirectory)
    }

    /**
     * Delete all files in [rootDir] after each test.
     *
     * We can't use [AbstractOnServerIT.after] as this is only deleting remote files.
     */
    @After
    override fun after() {
        rootDir.listFiles().forEach {
            Log.e("TEST", "Deleting ${it.name}...")
            it.delete()
        }
    }

    @Test
    fun testCreateDeleteFiles() = runBlocking {
        // no files in root initially
        assertTrue(rootDir.listFilesBlocking(context).isEmpty())

        // create first file
        val name1 = RandomString.make()
        val type1 = "text/html"
        val file1 = rootDir.createFile(type1, name1)!!

        // check assumptions
        file1.assertRegularFile(name1, 0L, null/* FIXME: type1 */, rootDir)
        assertTrue(file1.canRead())
        assertTrue(file1.canWrite())
        assertTrue(System.currentTimeMillis() - file1.lastModified() < 5000)

        // file is found in root
        assertListFilesEquals(listOf(file1), rootDir.listFilesBlocking(context).toList())

        // create second long file with long file name
        val name2 = RandomString.make(225)
        val type2 = "application/octet-stream"
        val file2 = rootDir.createFile(type2, name2)!!

        // check assumptions
        file2.assertRegularFile(name2, 0L, type2, rootDir)
        assertTrue(file2.canRead())
        assertTrue(file2.canWrite())
        assertTrue(System.currentTimeMillis() - file2.lastModified() < 5000)

        // both files get listed in root
        assertListFilesEquals(listOf(file1, file2), rootDir.listFiles().toList())

        // delete first file
        assertTrue(file1.delete())
        assertFalse(file1.exists())

        // only second file gets listed in root
        assertListFilesEquals(listOf(file2), rootDir.listFiles().toList())

        // delete also second file
        assertTrue(file2.delete())
        assertFalse(file2.exists())

        // no more files in root
        assertTrue(rootDir.listFiles().isEmpty())
    }

    @Test
    fun testReadWriteFiles() {
        // create random file
        val file1 = rootDir.createFile("application/octet-stream", RandomString.make())!!
        file1.assertRegularFile(size = 0L)

        // write random bytes to file
        val data1 = Random.nextBytes(5 * 1024)
        contentResolver.openOutputStream(file1.uri, "wt").use {
            it!!.write(data1)
        }

        // read back random bytes
        assertReadEquals(data1, contentResolver.openInputStream(file1.uri))

        // file size was updated correctly
        file1.assertRegularFile(size = data1.size.toLong())

    }

}
