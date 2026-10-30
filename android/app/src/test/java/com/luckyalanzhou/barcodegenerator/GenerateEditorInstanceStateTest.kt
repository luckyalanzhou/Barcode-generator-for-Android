package com.luckyalanzhou.barcodegenerator

import android.os.Bundle
import android.app.Application
import com.luckyalanzhou.barcodegenerator.ui.app.GenerateEditorDraftStore
import com.luckyalanzhou.barcodegenerator.ui.app.GenerateEditorDraftAutosaver
import com.luckyalanzhou.barcodegenerator.ui.app.GenerateEditorDraftSnapshot
import com.luckyalanzhou.barcodegenerator.presentation.GenerateEditorState
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateEditorStateHolder
import com.luckyalanzhou.barcodegenerator.ui.app.GenerateEditorInstanceState
import java.io.File
import java.io.RandomAccessFile
import java.util.AbstractList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GenerateEditorInstanceStateTest {
    private val draftIdsCreated = mutableSetOf<String>()

    private val application: Application
        get() = RuntimeEnvironment.getApplication()

    @After
    fun removePrivateDraftFixture() {
        GenerateEditorDraftStore(application.noBackupFilesDir).delete()
        draftIdsCreated.forEach { GenerateEditorDraftStore(application.noBackupFilesDir, it).delete() }
    }

    private fun storeFor(savedState: Bundle): GenerateEditorDraftStore {
        val draftId = savedState.getString(GenerateEditorInstanceState.FILE_ID_KEY)
        draftId?.let(draftIdsCreated::add)
        return GenerateEditorDraftStore(application.noBackupFilesDir, draftId)
    }

    @Test
    fun restoresInputExactlyIncludingWhitespaceBlankRowsAndLeadingZeros() {
        val saved = Bundle()
        val expected = listOf(" 00123 ", "", "零件-04") to "QR Code"
        assertTrue(
            GenerateEditorInstanceState.save(
                application,
                GenerateEditorState(expected.first, formatName = expected.second),
                saved,
            ),
        )
        assertTrue(saved.containsKey(GenerateEditorInstanceState.INPUTS_KEY))
        assertFalse(saved.containsKey(GenerateEditorInstanceState.FILE_SAVED_KEY))

        var restored: Pair<List<String>, String>? = null
        assertTrue(
            GenerateEditorInstanceState.restore(application, saved) { inputs, format ->
                restored = inputs to format
            },
        )
        assertEquals(expected, restored)
    }

    @Test
    fun oversizedDraftUsesPrivateFileAndRestoresExactly() {
        val saved = Bundle()
        val beforeRecreation = GenerateEditorStateHolder().apply {
            updateDraft(listOf("x".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES + 1), " 02-A "))
            updateFormat("Code 128-B")
        }

        assertTrue(GenerateEditorInstanceState.save(application, beforeRecreation.state.value, saved))
        assertFalse(saved.containsKey(GenerateEditorInstanceState.INPUTS_KEY))
        assertTrue(saved.getBoolean(GenerateEditorInstanceState.FILE_SAVED_KEY))
        assertTrue(storeFor(saved).draftFile.isFile)

        val afterRecreation = GenerateEditorStateHolder()
        assertTrue(GenerateEditorInstanceState.restore(application, saved, afterRecreation::restoreDraft))
        assertEquals(beforeRecreation.state.value.inputDraft, afterRecreation.state.value.inputDraft)
        assertEquals("Code 128-B", afterRecreation.state.value.formatName)
        assertEquals(null, afterRecreation.state.value.pendingFormat)
    }

    @Test
    fun largeDraftRestoreReadsOnIoAndReturnsTheSavedFileIdentity() = runBlocking {
        val expected = GenerateEditorState(
            inputDraft = listOf("async-restore-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 14)),
            formatName = "Code 128-B",
        )
        val savedState = Bundle()
        assertTrue(GenerateEditorInstanceState.save(application, expected, savedState))
        val expectedDraftId = savedState.getString(GenerateEditorInstanceState.FILE_ID_KEY)
        draftIdsCreated += expectedDraftId.orEmpty()

        val callerThread = Thread.currentThread()
        val ioThread = AtomicReference<Thread?>()
        val executor = Executors.newSingleThreadExecutor()
        val delegate = executor.asCoroutineDispatcher()
        val recordingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                delegate.dispatch(context) {
                    ioThread.compareAndSet(null, Thread.currentThread())
                    block.run()
                }
            }
        }

        try {
            val restored = GenerateEditorInstanceState.restoreLargeDraft(
                application,
                savedState,
                recordingDispatcher,
            )

            assertNotNull(restored)
            assertEquals(expected.inputDraft, restored?.inputs)
            assertEquals(expected.formatName, restored?.formatName)
            assertEquals(expectedDraftId, restored?.draftId)
            assertNotSame("Large draft file reads must not run on the Activity thread", callerThread, ioThread.get())
        } finally {
            delegate.close()
        }
    }

    @Test
    fun legacyDraftRestoreKeepsItsSourceAndPendingStateCanBeSavedAgain() = runBlocking {
        val expected = GenerateEditorState(
            inputDraft = listOf("legacy-async-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 13)),
            formatName = "QR Code",
        )
        val legacyStore = GenerateEditorDraftStore(application.noBackupFilesDir)
        assertTrue(legacyStore.save(expected))
        val pendingState = Bundle().apply {
            putBoolean(GenerateEditorInstanceState.FILE_SAVED_KEY, true)
        }

        val restored = GenerateEditorInstanceState.restoreLargeDraft(application, pendingState)
        val savedAgain = Bundle()
        GenerateEditorInstanceState.copyPendingDraftState(pendingState, savedAgain)

        assertNotNull(restored)
        assertEquals(expected.inputDraft, restored?.inputs)
        assertNull(restored?.draftId)
        assertTrue("The legacy source must remain available until the new Activity saves a replacement", legacyStore.draftFile.isFile)
        assertTrue(savedAgain.getBoolean(GenerateEditorInstanceState.FILE_SAVED_KEY))
        assertNull(savedAgain.getString(GenerateEditorInstanceState.FILE_ID_KEY))
    }

    @Test
    fun alreadyPersistedLargeDraftIsReferencedWithoutRewritingIt() {
        val state = GenerateEditorState(
            inputDraft = listOf("persisted-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 10)),
            formatName = "Code 128-B",
        )
        val draftId = GenerateEditorDraftStore.newDraftId()
        draftIdsCreated += draftId
        val store = GenerateEditorDraftStore(application.noBackupFilesDir, draftId)
        assertTrue(store.save(state))
        val originalTimestamp = System.currentTimeMillis() + 60_000L
        assertTrue(store.draftFile.setLastModified(originalTimestamp))
        val saved = Bundle()

        assertTrue(
            GenerateEditorInstanceState.save(
                application,
                state,
                saved,
                prePersistedDraft = GenerateEditorDraftSnapshot(
                    draftId,
                    state.inputDraft,
                    state.formatName,
                    GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES + 1L,
                ),
            ),
        )

        assertTrue(saved.getBoolean(GenerateEditorInstanceState.FILE_SAVED_KEY))
        assertEquals(draftId, saved.getString(GenerateEditorInstanceState.FILE_ID_KEY))
        assertEquals("A prewritten snapshot must not be synchronously rewritten during state saving", originalTimestamp, store.draftFile.lastModified())
        assertEquals(state.inputDraft, store.restore()?.inputs)
    }

    @Test
    fun largeDraftAutosavesOnIoAndProvidesAReusableSnapshot() = runBlocking {
        val state = GenerateEditorState(
            inputDraft = listOf("async-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 6)),
            formatName = "QR Code",
        )
        val executor = Executors.newSingleThreadExecutor()
        val delegate = executor.asCoroutineDispatcher()
        val ioThread = AtomicReference<Thread?>()
        val recordingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                delegate.dispatch(context) {
                    ioThread.compareAndSet(null, Thread.currentThread())
                    block.run()
                }
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val autosaver = GenerateEditorDraftAutosaver(
            application.noBackupFilesDir,
            ioDispatcher = recordingDispatcher,
            debounceMillis = 1L,
        )
        val states = MutableStateFlow(state)
        val observation = autosaver.observe(scope, states)

        try {
            val snapshot = withTimeout(5_000L) {
                var saved = autosaver.snapshotFor(state)
                while (saved == null) {
                    delay(10L)
                    saved = autosaver.snapshotFor(state)
                }
                saved
            }
            draftIdsCreated += snapshot.draftId
            assertTrue(GenerateEditorDraftStore(application.noBackupFilesDir, snapshot.draftId).draftFile.isFile)
            assertNotSame(Thread.currentThread(), ioThread.get())

            val savedState = Bundle()
            assertTrue(
                GenerateEditorInstanceState.save(
                    application,
                    state,
                    savedState,
                    prePersistedDraft = snapshot,
                ),
            )
            assertEquals(snapshot.draftId, savedState.getString(GenerateEditorInstanceState.FILE_ID_KEY))

            val newerState = state.copy(inputDraft = listOf("new-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 4)))
            states.value = newerState
            assertNull("A changed draft must not reuse a snapshot while its file is being replaced", autosaver.snapshotFor(state))
            val newerSnapshot = withTimeout(5_000L) {
                var saved = autosaver.snapshotFor(newerState)
                while (saved == null) {
                    delay(10L)
                    saved = autosaver.snapshotFor(newerState)
                }
                saved
            }
            assertEquals(snapshot.draftId, newerSnapshot.draftId)
            assertEquals(
                "The autosave file must contain the newest snapshot after replacement completes",
                newerState.inputDraft,
                GenerateEditorDraftStore(application.noBackupFilesDir, newerSnapshot.draftId).restore()?.inputs,
            )
        } finally {
            observation.cancel()
            scope.cancel()
            delegate.close()
        }
    }

    @Test
    fun smallDraftSizeCheckRunsOnIoDispatcher() = runBlocking {
        val callerThread = Thread.currentThread()
        val listReadThread = AtomicReference<Thread?>()
        val state = GenerateEditorState(
            inputDraft = CallerReadTrackingList("small draft", listReadThread),
            formatName = "QR Code",
        )
        val executor = Executors.newSingleThreadExecutor()
        val delegate = executor.asCoroutineDispatcher()
        val ioThread = AtomicReference<Thread?>()
        val recordingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                delegate.dispatch(context) {
                    ioThread.compareAndSet(null, Thread.currentThread())
                    block.run()
                }
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val autosaver = GenerateEditorDraftAutosaver(
            application.noBackupFilesDir,
            ioDispatcher = recordingDispatcher,
            debounceMillis = 1L,
        )
        val observation = autosaver.observe(scope, MutableStateFlow(state))

        try {
            withTimeout(5_000L) {
                while (listReadThread.get() == null) delay(10L)
            }
            assertNotSame("Draft size calculation must not iterate inputs on the caller thread", callerThread, listReadThread.get())
            assertNotSame(callerThread, ioThread.get())
            assertNull(autosaver.snapshotFor(state))
        } finally {
            observation.cancel()
            scope.cancel()
            delegate.close()
        }
    }

    private class CallerReadTrackingList(
        private val value: String,
        private val readThread: AtomicReference<Thread?>,
    ) : AbstractList<String>() {
        override val size: Int = 1

        override fun get(index: Int): String {
            check(index == 0)
            readThread.compareAndSet(null, Thread.currentThread())
            return value
        }
    }

    @Test
    fun largeDraftsFromSeparateActivityInstancesDoNotOverwriteEachOther() {
        val firstInputs = listOf("A".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES + 1))
        val secondInputs = listOf("B".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES + 1))
        val firstSavedState = Bundle()
        val secondSavedState = Bundle()

        assertTrue(GenerateEditorInstanceState.save(
            application,
            GenerateEditorState(firstInputs, formatName = "QR Code"),
            firstSavedState,
        ))
        assertTrue(GenerateEditorInstanceState.save(
            application,
            GenerateEditorState(secondInputs, formatName = "Code 128-B"),
            secondSavedState,
        ))
        val firstStore = storeFor(firstSavedState)
        val secondStore = storeFor(secondSavedState)
        assertNotEquals(firstStore.draftFile, secondStore.draftFile)

        var restoredFirst: Pair<List<String>, String>? = null
        assertTrue(GenerateEditorInstanceState.restore(application, firstSavedState) { inputs, format ->
            restoredFirst = inputs to format
        })
        assertEquals(firstInputs to "QR Code", restoredFirst)

        var restoredSecond: Pair<List<String>, String>? = null
        assertTrue(GenerateEditorInstanceState.restore(application, secondSavedState) { inputs, format ->
            restoredSecond = inputs to format
        })
        assertEquals(secondInputs to "Code 128-B", restoredSecond)
    }

    @Test
    fun activityDraftIdSurvivesRecreationAndReusesItsOwnFile() {
        val firstInputs = listOf("before-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 7))
        val updatedInputs = listOf("after-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 6))
        val firstSavedState = Bundle()
        assertTrue(GenerateEditorInstanceState.save(
            application,
            GenerateEditorState(firstInputs, formatName = "QR Code"),
            firstSavedState,
        ))

        var restoredDraftId: String? = null
        assertTrue(GenerateEditorInstanceState.restore(
            application,
            firstSavedState,
            { _, _ -> },
            { restoredDraftId = it },
        ))
        assertEquals(firstSavedState.getString(GenerateEditorInstanceState.FILE_ID_KEY), restoredDraftId)
        restoredDraftId?.let(draftIdsCreated::add)

        val nextSavedState = Bundle()
        assertTrue(GenerateEditorInstanceState.save(
            application,
            GenerateEditorState(updatedInputs, formatName = "Code 128-B"),
            nextSavedState,
            restoredDraftId,
        ))
        assertEquals(restoredDraftId, nextSavedState.getString(GenerateEditorInstanceState.FILE_ID_KEY))
        val reusedStore = storeFor(nextSavedState)
        assertTrue("draft file missing: ${reusedStore.draftFile}", reusedStore.draftFile.isFile)
        assertFalse("AtomicFile backup remained: ${File(reusedStore.draftFile.path + ".bak")}", File(reusedStore.draftFile.path + ".bak").exists())
        assertFalse("AtomicFile staging file remained: ${File(reusedStore.draftFile.path + ".new")}", File(reusedStore.draftFile.path + ".new").exists())
        val updatedDraft = storeFor(nextSavedState).restore()
        assertEquals("Code 128-B", updatedDraft?.formatName)
        assertEquals(updatedInputs.single().length, updatedDraft?.inputs?.singleOrNull()?.length)
        assertEquals(updatedInputs.single().take(6), updatedDraft?.inputs?.singleOrNull()?.take(6))

        var restoredUpdated: Pair<List<String>, String>? = null
        assertTrue(GenerateEditorInstanceState.restore(application, nextSavedState) { inputs, format ->
            restoredUpdated = inputs to format
        })
        assertEquals(updatedInputs to "Code 128-B", restoredUpdated)
    }

    @Test
    fun smallerBundleDraftKeepsPreviousFileUntilSuccessfulRestore() {
        val largeSavedState = Bundle()
        val largeDraft = GenerateEditorState(
            inputDraft = listOf("large-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 6)),
            formatName = "QR Code",
        )
        assertTrue(GenerateEditorInstanceState.save(application, largeDraft, largeSavedState))
        val previousStore = storeFor(largeSavedState)
        val previousDraftId = largeSavedState.getString(GenerateEditorInstanceState.FILE_ID_KEY)

        val smallSavedState = Bundle()
        val smallDraft = GenerateEditorState(listOf("small"), formatName = "EAN-13")
        assertTrue(GenerateEditorInstanceState.save(application, smallDraft, smallSavedState, previousDraftId))

        assertFalse(smallSavedState.getBoolean(GenerateEditorInstanceState.FILE_SAVED_KEY))
        assertEquals(previousDraftId, smallSavedState.getString(GenerateEditorInstanceState.FILE_ID_KEY))
        assertTrue(previousStore.draftFile.isFile)

        var restored: Pair<List<String>, String>? = null
        assertTrue(GenerateEditorInstanceState.restore(application, smallSavedState) { inputs, format ->
            restored = inputs to format
        })

        assertEquals(listOf("small") to "EAN-13", restored)
        assertFalse(previousStore.draftFile.exists())
    }

    @Test
    fun legacySharedDraftMigratesToAnInstanceScopedFile() {
        val expected = GenerateEditorState(
            inputDraft = listOf("legacy-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 6)),
            formatName = "QR Code",
        )
        val legacyStore = GenerateEditorDraftStore(application.noBackupFilesDir)
        val savedState = Bundle().apply { putBoolean(GenerateEditorInstanceState.FILE_SAVED_KEY, true) }
        assertTrue(legacyStore.save(expected))

        var restored: Pair<List<String>, String>? = null
        var restoredDraftId: String? = null
        assertTrue(GenerateEditorInstanceState.restore(
            application,
            savedState,
            { inputs, format -> restored = inputs to format },
            { restoredDraftId = it },
        ))

        assertEquals(expected.inputDraft to expected.formatName, restored)
        assertTrue(GenerateEditorDraftStore.isValidDraftId(restoredDraftId.orEmpty()))
        restoredDraftId?.let(draftIdsCreated::add)
        assertTrue(GenerateEditorDraftStore(application.noBackupFilesDir, restoredDraftId).draftFile.isFile)
        assertFalse(legacyStore.draftFile.exists())
    }

    @Test
    fun missingOrIncompleteInstanceStateDoesNotReplaceEditorState() {
        assertFalse(GenerateEditorInstanceState.restore(application, null) { _, _ -> error("must not restore") })
        assertFalse(
            GenerateEditorInstanceState.restore(
                application,
                Bundle().apply { putString(GenerateEditorInstanceState.FORMAT_KEY, "QR Code") },
            ) { _, _ -> error("must not restore") },
        )
    }

    @Test
    fun configurationRecreationRestoresDraftAndSelectedFormatIntoANewEditor() {
        val beforeRecreation = GenerateEditorStateHolder()
        beforeRecreation.updateDraft(listOf("00123", " second "))
        beforeRecreation.updateFormat("QR Code")
        val savedState = Bundle()
        assertTrue(GenerateEditorInstanceState.save(application, beforeRecreation.state.value, savedState))

        val afterRecreation = GenerateEditorStateHolder()
        assertTrue(GenerateEditorInstanceState.restore(application, savedState, afterRecreation::restoreDraft))

        assertEquals(listOf("00123", " second "), afterRecreation.state.value.inputDraft)
        assertEquals("QR Code", afterRecreation.state.value.formatName)
        assertEquals(null, afterRecreation.state.value.pendingFormat)
    }

    @Test
    fun resultEditDraftAndFormatSurviveRecreationWithoutRestoringTransientEditSignal() {
        val beforeRecreation = GenerateEditorStateHolder()
        beforeRecreation.beginEditing(listOf("edit-01", "edit-02"), "Code 39")
        val savedState = Bundle()
        assertTrue(GenerateEditorInstanceState.save(application, beforeRecreation.state.value, savedState))

        val afterRecreation = GenerateEditorStateHolder()
        assertTrue(GenerateEditorInstanceState.restore(application, savedState, afterRecreation::restoreDraft))

        assertEquals(listOf("edit-01", "edit-02"), afterRecreation.state.value.inputDraft)
        assertEquals("Code 39", afterRecreation.state.value.formatName)
        assertEquals(null, afterRecreation.state.value.pendingFormat)
    }

    @Test
    fun explicitlyClearedDraftRestoresAsOneEmptyInputRow() {
        val saved = Bundle()
        assertTrue(
            GenerateEditorInstanceState.save(
                application,
                GenerateEditorState(listOf(""), formatName = "EAN-13"),
                saved,
            ),
        )

        var restored: GenerateEditorState? = null
        assertTrue(
            GenerateEditorInstanceState.restore(application, saved) { inputs, format ->
                restored = GenerateEditorState(inputs, formatName = format)
            },
        )

        assertEquals(listOf(""), restored?.inputDraft)
        assertEquals("EAN-13", restored?.formatName)
    }

    @Test
    fun expiredPrivateDraftIsRemovedAndCannotBeRestored() {
        val largeDraft = GenerateEditorState(
            inputDraft = listOf("x".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES + 1)),
            formatName = "QR Code",
        )
        val saved = Bundle()

        assertTrue(GenerateEditorInstanceState.save(application, largeDraft, saved))
        val store = storeFor(saved)
        assertTrue(store.draftFile.setLastModified(
            System.currentTimeMillis() - GenerateEditorDraftStore.EXPIRATION_MILLIS - 1_000L,
        ))
        assertFalse(GenerateEditorInstanceState.restore(application, saved) { _, _ -> error("must not restore") })
        assertFalse("restoring an expired draft must delete its own file", store.draftFile.exists())
    }

    @Test
    fun savingDraftDoesNotSynchronouslySweepOtherExpiredActivityDrafts() {
        val expiredState = GenerateEditorState(
            inputDraft = listOf("expired-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 8)),
            formatName = "QR Code",
        )
        val expiredBundle = Bundle()
        assertTrue(GenerateEditorInstanceState.save(application, expiredState, expiredBundle))
        val expiredStore = storeFor(expiredBundle)
        assertTrue(expiredStore.draftFile.setLastModified(
            System.currentTimeMillis() - GenerateEditorDraftStore.EXPIRATION_MILLIS - 1_000L,
        ))

        val currentState = GenerateEditorState(
            inputDraft = listOf("current-".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES / 8)),
            formatName = "Code 128-B",
        )
        val currentBundle = Bundle()

        assertTrue(GenerateEditorInstanceState.save(application, currentState, currentBundle))

        assertTrue("saving one Activity draft must not scan/delete another expired draft on the caller thread", expiredStore.draftFile.exists())
    }

    @Test
    fun corruptPrivateDraftIsDeletedWithoutReplacingEditorState() {
        val saved = Bundle()
        val largeDraft = GenerateEditorState(
            inputDraft = listOf("x".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES + 1)),
            formatName = "QR Code",
        )

        assertTrue(GenerateEditorInstanceState.save(application, largeDraft, saved))
        val store = storeFor(saved)
        store.draftFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02))

        assertFalse(GenerateEditorInstanceState.restore(application, saved) { _, _ -> error("must not restore") })
        assertFalse(store.draftFile.exists())
    }

    @Test
    fun validAtomicBackupIsRecoveredWhenInterruptedBaseExceedsDraftLimit() {
        val expected = GenerateEditorState(
            inputDraft = listOf("x".repeat(GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES + 1)),
            formatName = "QR Code",
        )
        val saved = Bundle()
        assertTrue(GenerateEditorInstanceState.save(application, expected, saved))
        val store = storeFor(saved)
        val backup = File(store.draftFile.path + ".bak")

        store.draftFile.copyTo(backup, overwrite = true)
        RandomAccessFile(store.draftFile, "rw").use { it.setLength(GenerateEditorDraftStore.MAX_DRAFT_FILE_BYTES + 1L) }

        val restored = store.restore()

        assertEquals(expected.inputDraft, restored?.inputs)
        assertEquals(expected.formatName, restored?.formatName)
        assertTrue(store.draftFile.length() <= GenerateEditorDraftStore.MAX_DRAFT_FILE_BYTES)
    }

    @Test
    fun draftBeyondPrivateFileLimitIsNotAddedToBundleOrDisk() {
        val saved = Bundle()
        val beyondFileLimit = GenerateEditorState(
            inputDraft = listOf("x".repeat(GenerateEditorDraftStore.MAX_DRAFT_FILE_BYTES + 1)),
            formatName = "QR Code",
        )
        val store = GenerateEditorDraftStore(application.noBackupFilesDir)

        assertFalse(GenerateEditorInstanceState.save(application, beyondFileLimit, saved))
        assertFalse(saved.containsKey(GenerateEditorInstanceState.INPUTS_KEY))
        assertFalse(saved.containsKey(GenerateEditorInstanceState.FILE_SAVED_KEY))
        assertFalse(store.draftFile.exists())
    }
}
