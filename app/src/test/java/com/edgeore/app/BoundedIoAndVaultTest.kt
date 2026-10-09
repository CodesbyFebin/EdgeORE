package com.edgeore.app

import com.edgeore.app.io.AtomicFiles
import com.edgeore.app.io.BoundedInput
import com.edgeore.app.io.InputTooLargeException
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.VaultException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** P0 bounded reads + atomic vault publication; P1 vault version/AAD. Software AES key stands in for Keystore. */
class BoundedIoAndVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    /** An endless provider stream that counts what was pulled from it. */
    private class Endless : InputStream() {
        var pulled = 0L
        override fun read(): Int { pulled++; return 1 }
        override fun read(b: ByteArray, off: Int, len: Int): Int { java.util.Arrays.fill(b, off, off + len, 1); pulled += len; return len }
    }

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun vault(dir: File = File(tmp.root, "vault"), k: () -> SecretKey = { key }, free: Long = Long.MAX_VALUE) = LocalVault(dir, k, freeSpace = { free })
    private fun onlyObject(v: File) = v.listFiles { f -> f.name.endsWith(LocalVault.SUFFIX) }!!.single()

    @Test fun oversizeRejectedBeforeUnboundedAllocation() {
        val s = Endless()
        val e = runCatching { BoundedInput.readAtMost(s, 8_000_000) }.exceptionOrNull()
        assertTrue(e is InputTooLargeException)
        assertTrue("pulled ${s.pulled}", s.pulled <= 8_000_000 + 1)
    }

    @Test fun exactLimitAcceptedOneMoreRejected() {
        assertEquals(1000, BoundedInput.readAtMost(ByteArrayInputStream(ByteArray(1000)), 1000).size)
        assertTrue(runCatching { BoundedInput.readAtMost(ByteArrayInputStream(ByteArray(1001)), 1000) }.exceptionOrNull() is InputTooLargeException)
        assertEquals(0, BoundedInput.readAtMost(ByteArrayInputStream(ByteArray(0)), 0).size)
    }

    @Test fun textReadsAreByteBoundedStrictUtf8AndCharLimited() {
        val r = BoundedInput.readUtf8Text(ByteArrayInputStream("héllo wörld".toByteArray()), 1024, 5)
        assertTrue(r is BoundedInput.TextResult.Ok && r.text == "héllo" && r.truncatedChars)
        val bad = BoundedInput.readUtf8Text(ByteArrayInputStream(byteArrayOf(0x68, 0xC3.toByte(), 0x28)), 1024, 100)
        assertTrue(bad is BoundedInput.TextResult.Refused && bad.reason.contains("UTF-8"))
        val big = BoundedInput.readUtf8Text(Endless(), 256 * 1024, 100)
        assertTrue(big is BoundedInput.TextResult.Refused)
    }

    @Test fun streamingDigestHasNoBuffer() {
        val d = BoundedInput.sha256Hex(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", d)
    }

    @Test fun atomicWriteFailureKeepsOldContentAndNoTemp() {
        val f = File(tmp.root, "x/state.json")
        AtomicFiles.write(f, "old".toByteArray())
        val e = runCatching { AtomicFiles.write(f) { out -> out.write("partial".toByteArray()); throw java.io.IOException("disk full") } }.exceptionOrNull()
        assertTrue(e is java.io.IOException)
        assertEquals("old", f.readText())
        assertTrue(AtomicFiles.abandonedTemps(f.parentFile!!).isEmpty())
    }

    @Test fun roundTripAndSameNameTwiceKeepsBothObjects() {
        val v = vault()
        val a = v.put("report.pdf", "first".toByteArray())
        val b = v.put("report.pdf", "second".toByteArray())
        assertNotEquals(a.id, b.id)
        assertEquals(2, v.entries().size)
        assertArrayEquals("first".toByteArray(), v.read(a.id))
        assertArrayEquals("second".toByteArray(), v.read(b.id))
        assertTrue(v.entries().all { it.displayName == "report.pdf" && !it.legacy })
    }

    @Test fun interruptedWriteIsNeverAnObjectAndIsReclaimed() {
        val dir = File(tmp.root, "vault")
        val v = vault(dir)
        val kept = v.put("keep.txt", "keep".toByteArray())
        // A process that died mid-write leaves only a temp file.
        File(dir, "0123456789abcdef0123456789abcdef.eov2.1a2b3c4d.part").writeBytes(ByteArray(100) { 7 })
        val restarted = vault(dir)
        assertEquals(listOf(kept.id), restarted.entries().map { it.id })
        assertEquals(1, restarted.recoverAbandonedWrites())
        assertTrue(AtomicFiles.abandonedTemps(dir).isEmpty())
        assertArrayEquals("keep".toByteArray(), restarted.read(kept.id))
    }

    @Test fun oversizeAllowanceAndDiskFullAreSpecificAndLeaveNothing() {
        val dir = File(tmp.root, "vault")
        assertTrue(runCatching { vault(dir).put("big", ByteArray(8_000_001)) }.exceptionOrNull() is VaultException.TooLarge)
        assertTrue(runCatching { vault(dir).put("a", ByteArray(2000), allowanceBytes = 1000) }.exceptionOrNull() is VaultException.OverAllowance)
        assertTrue(runCatching { vault(dir, free = 10).put("a", ByteArray(2000)) }.exceptionOrNull() is VaultException.NoSpace)
        assertTrue(vault(dir).entries().isEmpty())
        assertTrue(dir.listFiles()!!.isEmpty())
        val v = vault(dir)
        v.put("a", ByteArray(500), allowanceBytes = 2000)
        assertTrue(runCatching { v.put("b", ByteArray(1500), allowanceBytes = 2000) }.exceptionOrNull() is VaultException.OverAllowance)
        assertEquals(1, v.entries().size)
    }

    private fun flipByte(f: File, pos: Long) = RandomAccessFile(f, "rw").use { it.seek(pos); val b = it.read(); it.seek(pos); it.write(b xor 0x01) }

    @Test fun tamperedIvCiphertextAndMetadataFailAuthentication() {
        val dir = File(tmp.root, "vault")
        for (offset in listOf(7L /* IV */, -1L /* last tag byte */, 40L /* inside name (AAD) */)) {
            dir.deleteRecursively()
            val v = vault(dir)
            val e = v.put("abcdefgh.txt", "secret payload".toByteArray())
            val f = onlyObject(dir)
            flipByte(f, if (offset < 0) f.length() - 1 else offset)
            assertTrue("offset $offset", runCatching { v.read(e.id) }.exceptionOrNull() is VaultException.Tampered)
        }
    }

    @Test fun truncatedTrailingAndForeignObjectsAreCorrupt() {
        val dir = File(tmp.root, "vault")
        val v = vault(dir)
        val e = v.put("t.txt", "0123456789".toByteArray())
        val f = onlyObject(dir)
        val full = f.readBytes()
        f.writeBytes(full.copyOf(full.size - 3))
        assertTrue(runCatching { v.read(e.id) }.exceptionOrNull() is VaultException.Corrupt)
        f.writeBytes(full + byteArrayOf(1, 2))
        assertTrue(runCatching { v.read(e.id) }.exceptionOrNull() is VaultException.Corrupt)
        f.writeBytes(ByteArray(10))
        assertTrue(runCatching { v.read(e.id) }.exceptionOrNull() is VaultException.Corrupt)
        // Renamed object: id in header must match the file identity.
        f.writeBytes(full)
        val other = File(dir, "ffffffffffffffffffffffffffffffff" + LocalVault.SUFFIX)
        f.copyTo(other)
        assertTrue(runCatching { v.read("ffffffffffffffffffffffffffffffff") }.exceptionOrNull() is VaultException.Corrupt)
    }

    @Test fun wrongOrMissingKeyIsReportedNotEmpty() {
        val dir = File(tmp.root, "vault")
        val e = vault(dir).put("k.txt", "data".toByteArray())
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertTrue(runCatching { vault(dir, { otherKey }).read(e.id) }.exceptionOrNull() is VaultException.Tampered)
        assertTrue(runCatching { vault(dir, { throw java.security.KeyStoreException("gone") }).read(e.id) }.exceptionOrNull() is VaultException.KeyUnavailable)
        assertEquals(1, vault(dir).entries().size)
    }

    @Test fun pathsAreValidatedAtTheBoundary() {
        val v = vault()
        for (bad in listOf("../x", "/etc/passwd", "a/b.vault", "", "ABCDEF0123456789ABCDEF0123456789", "x.vault/../../y.vault"))
            assertTrue(bad, runCatching { v.read(bad) }.exceptionOrNull() is VaultException.InvalidId)
        assertFalse(v.delete("../../outside"))
    }

    @Test fun legacyObjectsStayReadable() {
        val dir = File(tmp.root, "vault").apply { mkdirs() }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, ByteArray(12) { 5 })) }
        File(dir, "old_note.txt.vault").writeBytes(ByteArray(12) { 5 } + c.doFinal("legacy".toByteArray()))
        val v = vault(dir)
        val e = v.entries().single()
        assertTrue(e.legacy)
        assertArrayEquals("legacy".toByteArray(), v.read(e.id))
        File(dir, "short.vault").writeBytes(ByteArray(20))
        assertTrue(runCatching { v.read("short.vault") }.exceptionOrNull() is VaultException.Corrupt)
    }

    @Test fun deleteRemovesOnlyThatObject() {
        val v = vault()
        val a = v.put("a", "1".toByteArray()); val b = v.put("b", "2".toByteArray())
        assertTrue(v.delete(a.id))
        assertEquals(listOf(b.id), v.entries().map { it.id })
        assertTrue(runCatching { v.read(a.id) }.exceptionOrNull() is VaultException.NotFound)
    }
}
