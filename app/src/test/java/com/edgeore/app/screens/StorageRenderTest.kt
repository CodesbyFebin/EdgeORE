package com.edgeore.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edgeore.app.StorageState
import com.edgeore.app.storage.BackupJobStore
import com.edgeore.app.storage.LocalVault
import com.edgeore.app.storage.NotConfiguredBackupProvider
import com.edgeore.app.storage.StorageController
import com.edgeore.app.ui.screens.StorageVaultSection
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import javax.crypto.KeyGenerator

private val out: String = System.getProperty("screens.out") ?: "build/screenshots"

/**
 * JVM RENDERS (Robolectric, API 28), NOT DEVICE SCREENSHOTS. The Storage vault section is rendered
 * with two real objects encrypted into a temporary vault by a SOFTWARE AES key on the build machine
 * (Robolectric has no Android Keystore). Remote backup is the shipped NotConfiguredBackupProvider.
 * Device free space is not observable here and renders as "Not observed". Runs only with -Pscreens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h2000dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class StorageRenderTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun state(withFiles: Boolean): StorageState = runBlocking {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = LocalVault(tmp.newFolder("vault"), { key }, freeSpace = { Long.MAX_VALUE })
        val c = StorageController(vault, BackupJobStore(tmp.root.resolve("jobs.json")), NotConfiguredBackupProvider)
        if (withFiles) {
            c.import("field-notes.txt", 0) { ByteArrayInputStream(ByteArray(18_432) { (it % 26 + 97).toByte() }) }
            c.import("site-photo.jpg", 0) { ByteArrayInputStream(ByteArray(1_250_000) { it.toByte() }) }
        }
        c.refresh()
        val v = c.view.value
        StorageState(files = v.files.map { it.entry }, views = v.files, usedBytes = v.usedBytes, backup = v.backup, allocationMb = if (withFiles) 64 else 0,
            note = v.message ?: "Vault is empty until you encrypt a file.")
    }

    private fun render(st: StorageState) = rule.setContent {
        EdgeOreTheme {
            Column(Modifier.fillMaxSize().background(EdgeColors.background).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageVaultSection(st, onImport = {}, onExport = { _, _ -> }, onDelete = {}, onAllowance = {})
            }
        }
    }

    private val options = RoborazziOptions()

    @Test fun empty() { render(state(false)); rule.onRoot().captureRoboImage("$out/storage/jvm-render-storage-empty.png", roborazziOptions = options) }

    @Test fun withFiles() { render(state(true)); rule.onRoot().captureRoboImage("$out/storage/jvm-render-storage-files.png", roborazziOptions = options) }

    @Test fun exportWarning() {
        render(state(true))
        rule.onAllNodes(hasText("Export", substring = false) and hasClickAction())[0].performScrollTo().performClick()
        rule.waitForIdle()
        captureScreenRoboImage("$out/storage/jvm-render-storage-export-warning.png", roborazziOptions = options)
    }

    @Test fun deleteConfirmation() {
        render(state(true))
        rule.onAllNodes(hasText("Delete", substring = false) and hasClickAction())[0].performScrollTo().performClick()
        rule.waitForIdle()
        captureScreenRoboImage("$out/storage/jvm-render-storage-delete-confirm.png", roborazziOptions = options)
    }

    @Test fun backupStatus() {
        render(state(true))
        rule.onAllNodes(hasText("Backup status") and hasClickAction())[0].performScrollTo().performClick()
        rule.waitForIdle()
        captureScreenRoboImage("$out/storage/jvm-render-storage-backup-status.png", roborazziOptions = options)
    }

    @Test fun configureRemoteBackup() {
        render(state(false))
        rule.onNode(hasText("Configure remote backup") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        captureScreenRoboImage("$out/storage/jvm-render-storage-configure-backup.png", roborazziOptions = options)
    }
}
