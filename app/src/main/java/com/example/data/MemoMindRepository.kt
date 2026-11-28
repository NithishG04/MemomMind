package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.local.LocalAudioDao
import com.example.local.LocalAudioEntity
import com.example.model.ExtractedInfo
import com.example.model.MemoryMessage
import com.example.model.Recording
import com.example.model.ReminderItem
import com.example.model.TranscriptSegment
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

class MemoMindRepository(
    private val db: FirebaseFirestore,
    private val localAudioDao: LocalAudioDao,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context, localAudioDao: LocalAudioDao) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localAudioDao = localAudioDao
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing MemoMind data.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun recordingsCol(uid: String) = userDoc(uid).collection("recordings")
    private fun segmentsCol(uid: String, recordingId: String) =
        recordingsCol(uid).document(recordingId).collection("segments")
    private fun extractedInfoCol(uid: String) = userDoc(uid).collection("extractedInfo")
    private fun remindersCol(uid: String) = userDoc(uid).collection("reminders")
    private fun memoryMessagesCol(uid: String) = userDoc(uid).collection("memoryMessages")

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    logAndRethrowStreamError(error, OperationType.GET, path, uid)
                }
        )
    }

    fun observeRecordings(uid: String): Flow<List<Recording>> = flow {
        val path = "users/$uid/recordings"
        emitAll(
            recordingsCol(uid)
                .orderBy("recordedAtMs", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Recording::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeSegments(uid: String, recordingId: String): Flow<List<TranscriptSegment>> = flow {
        val path = "users/$uid/recordings/$recordingId/segments"
        emitAll(
            segmentsCol(uid, recordingId)
                .orderBy("startMs", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TranscriptSegment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeExtractedInfo(uid: String): Flow<List<ExtractedInfo>> = flow {
        val path = "users/$uid/extractedInfo"
        emitAll(
            extractedInfoCol(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ExtractedInfo::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeReminders(uid: String): Flow<List<ReminderItem>> = flow {
        val path = "users/$uid/reminders"
        emitAll(
            remindersCol(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReminderItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeMemoryMessages(uid: String): Flow<List<MemoryMessage>> = flow {
        val path = "users/$uid/memoryMessages"
        emitAll(
            memoryMessagesCol(uid)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MemoryMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrowStreamError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeLocalAudioFiles(uid: String): Flow<List<LocalAudioEntity>> =
        localAudioDao.observeForUser(uid)

    suspend fun ensureUserProfile(
        displayName: String,
        email: String
    ): Result<UserProfile> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            docPath = ref.path
            val snap = ref.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    return Result.success(existing)
                }
            }
            val safeName = displayName.trim().ifBlank { "MemoMind User" }.take(120)
            val safeEmail = email.trim().take(200)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "email" to safeEmail,
                "consentAccepted" to false,
                "aiProcessingConsent" to true,
                "defaultLanguage" to "Auto",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            val fresh = ref.get().await().toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserProfile(
                id = uid,
                userId = uid,
                displayName = safeName,
                email = safeEmail,
                consentAccepted = false,
                aiProcessingConsent = true,
                defaultLanguage = "Auto"
            )
            Result.success(fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(
        displayName: String? = null,
        consentAccepted: Boolean? = null,
        aiProcessingConsent: Boolean? = null,
        defaultLanguage: String? = null
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            docPath = ref.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            displayName?.let { updates["displayName"] = it.trim().take(120) }
            consentAccepted?.let { updates["consentAccepted"] = it }
            aiProcessingConsent?.let { updates["aiProcessingConsent"] = it }
            defaultLanguage?.let { updates["defaultLanguage"] = it.trim().ifBlank { "Auto" }.take(40) }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createRecording(
        recordingId: String,
        title: String,
        category: String,
        tags: List<String>,
        durationMs: Long,
        recordedAtMs: Long,
        language: String,
        localAudioFile: File,
        waveformPeaks: List<Int>
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recordings/$recordingId"
        return try {
            val uid = requireUserId()
            val safeId = recordingId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
            val ref = recordingsCol(uid).document(safeId)
            docPath = ref.path
            val safeTitle = title.trim().ifBlank { "Conversation" }.take(180)
            val safeCategory = category.trim().ifBlank { "General" }.take(60)
            val safeTags = tags.map { it.trim().take(40) }.filter { it.isNotBlank() }.take(10)
            val safePeaks = waveformPeaks.map { it.coerceIn(0, 100) }.take(80)

            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "notes" to "",
                "category" to safeCategory,
                "tags" to safeTags,
                "durationMs" to durationMs.coerceIn(0L, 86400000L),
                "recordedAtMs" to recordedAtMs.coerceAtLeast(0L),
                "language" to language.trim().take(40),
                "transcriptStatus" to "PENDING",
                "fullTranscript" to "",
                "summaryTopics" to "",
                "summaryDecisions" to "",
                "summaryTasks" to "",
                "summaryLocations" to "",
                "summaryFinancials" to "",
                "summaryDates" to "",
                "localAudioFileName" to localAudioFile.name.take(260),
                "waveformPeaks" to safePeaks,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()

            localAudioDao.upsert(
                LocalAudioEntity(
                    recordingId = safeId,
                    userId = uid,
                    fileName = localAudioFile.name,
                    filePath = localAudioFile.absolutePath,
                    durationMs = durationMs,
                    sizeBytes = localAudioFile.length(),
                    createdAtMs = recordedAtMs,
                    syncedToCloud = true
                )
            )
            Result.success(safeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRecordingTranscriptionStatus(
        recordingId: String,
        status: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recordings/$recordingId"
        return try {
            val uid = requireUserId()
            val ref = recordingsCol(uid).document(recordingId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "transcriptStatus" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveRecordingAnalysis(
        recordingId: String,
        title: String,
        language: String,
        fullTranscript: String,
        summaryTopics: String,
        summaryDecisions: String,
        summaryTasks: String,
        summaryLocations: String,
        summaryFinancials: String,
        summaryDates: String,
        segments: List<TranscriptSegment>,
        extractedItems: List<ExtractedInfo>
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recordings/$recordingId"
        return try {
            val uid = requireUserId()
            val recRef = recordingsCol(uid).document(recordingId)
            docPath = recRef.path

            val safeTitle = title.trim().ifBlank { "Conversation" }.take(180)
            recRef.update(
                mapOf(
                    "title" to safeTitle,
                    "language" to language.trim().ifBlank { "English" }.take(40),
                    "transcriptStatus" to "COMPLETED",
                    "fullTranscript" to fullTranscript.take(50000),
                    "summaryTopics" to summaryTopics.take(4000),
                    "summaryDecisions" to summaryDecisions.take(4000),
                    "summaryTasks" to summaryTasks.take(4000),
                    "summaryLocations" to summaryLocations.take(4000),
                    "summaryFinancials" to summaryFinancials.take(4000),
                    "summaryDates" to summaryDates.take(4000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Clear any old segments before writing new ones on re-transcribe
            val existingSegs = segmentsCol(uid, recordingId).get().await()
            if (!existingSegs.isEmpty) {
                val delBatch = db.batch()
                existingSegs.documents.forEach { delBatch.delete(it.reference) }
                delBatch.commit().await()
            }

            if (segments.isNotEmpty()) {
                val segBatch = db.batch()
                segments.forEachIndexed { idx, seg ->
                    val segId = "seg_${idx}_${System.currentTimeMillis()}"
                    val segRef = segmentsCol(uid, recordingId).document(segId)
                    val safeText = seg.text.trim().ifBlank { "..." }.take(5000)
                    val safeStart = seg.startMs.coerceAtLeast(0L)
                    val safeEnd = seg.endMs.coerceAtLeast(safeStart)
                    segBatch.set(
                        segRef,
                        mapOf(
                            "userId" to uid,
                            "recordingId" to recordingId,
                            "startMs" to safeStart,
                            "endMs" to safeEnd,
                            "text" to safeText,
                            "speaker" to seg.speaker.trim().ifBlank { "Speaker 1" }.take(80),
                            "confidence" to seg.confidence.coerceIn(0.0, 1.0),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                segBatch.commit().await()
            }

            if (extractedItems.isNotEmpty()) {
                val infoBatch = db.batch()
                extractedItems.forEach { item ->
                    val infoRef = extractedInfoCol(uid).document()
                    infoBatch.set(
                        infoRef,
                        mapOf(
                            "userId" to uid,
                            "recordingId" to recordingId,
                            "recordingTitle" to safeTitle,
                            "segmentId" to item.segmentId.take(128),
                            "timestampMs" to item.timestampMs.coerceAtLeast(0L),
                            "category" to item.category.trim().ifBlank { "OTHER" }.take(40),
                            "label" to item.label.trim().ifBlank { "Detail" }.take(120),
                            "value" to item.value.trim().ifBlank { "-" }.take(1000),
                            "verified" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                infoBatch.commit().await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRecordingMetadata(
        recordingId: String,
        title: String,
        notes: String,
        category: String,
        tags: List<String>
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recordings/$recordingId"
        return try {
            val uid = requireUserId()
            val ref = recordingsCol(uid).document(recordingId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "title" to title.trim().ifBlank { "Untitled Conversation" }.take(180),
                    "notes" to notes.trim().take(4000),
                    "category" to category.trim().ifBlank { "General" }.take(60),
                    "tags" to tags.map { it.trim().take(40) }.filter { it.isNotBlank() }.take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRecordingSummary(
        recordingId: String,
        summaryTopics: String,
        summaryDecisions: String,
        summaryTasks: String,
        summaryLocations: String,
        summaryFinancials: String,
        summaryDates: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recordings/$recordingId"
        return try {
            val uid = requireUserId()
            val ref = recordingsCol(uid).document(recordingId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "summaryTopics" to summaryTopics.trim().take(4000),
                    "summaryDecisions" to summaryDecisions.trim().take(4000),
                    "summaryTasks" to summaryTasks.trim().take(4000),
                    "summaryLocations" to summaryLocations.trim().take(4000),
                    "summaryFinancials" to summaryFinancials.trim().take(4000),
                    "summaryDates" to summaryDates.trim().take(4000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSegmentText(
        recordingId: String,
        segmentId: String,
        newText: String,
        newSpeaker: String,
        rebuiltFullTranscript: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recordings/$recordingId/segments/$segmentId"
        return try {
            val uid = requireUserId()
            val segRef = segmentsCol(uid, recordingId).document(segmentId)
            docPath = segRef.path
            val safeText = newText.trim().ifBlank { "..." }.take(5000)
            val safeSpeaker = newSpeaker.trim().ifBlank { "Speaker 1" }.take(80)
            segRef.update(
                mapOf(
                    "text" to safeText,
                    "speaker" to safeSpeaker,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordingsCol(uid).document(recordingId).update(
                mapOf(
                    "fullTranscript" to rebuiltFullTranscript.take(50000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRecording(recordingId: String, recordingsDir: File): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/recordings/$recordingId"
        return try {
            val uid = requireUserId()
            val recRef = recordingsCol(uid).document(recordingId)
            docPath = recRef.path
            val recSnap = recRef.get().await()
            val fileName = recSnap.getString("localAudioFileName")

            // Delete subcollection segments
            val segSnaps = segmentsCol(uid, recordingId).get().await()
            if (!segSnaps.isEmpty) {
                val batch = db.batch()
                segSnaps.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            // Delete linked extracted info
            val infoSnaps = extractedInfoCol(uid).whereEqualTo("recordingId", recordingId).get().await()
            if (!infoSnaps.isEmpty) {
                val batch = db.batch()
                infoSnaps.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            recRef.delete().await()
            localAudioDao.deleteByRecordingId(recordingId)
            if (!fileName.isNullOrBlank()) {
                val audioFile = File(recordingsDir, fileName)
                if (audioFile.exists()) audioFile.delete()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun getAllSegmentsForUser(uid: String, recordings: List<Recording>): Map<String, List<TranscriptSegment>> {
        val result = mutableMapOf<String, List<TranscriptSegment>>()
        for (rec in recordings.take(25)) {
            try {
                val snap = segmentsCol(uid, rec.id)
                    .orderBy("startMs", Query.Direction.ASCENDING)
                    .get()
                    .await()
                result[rec.id] = snap.toObjects(
                    TranscriptSegment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "users/$uid/recordings/${rec.id}/segments")
            }
        }
        return result
    }

    suspend fun updateExtractedInfo(
        infoId: String,
        label: String,
        value: String,
        verified: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/extractedInfo/$infoId"
        return try {
            val uid = requireUserId()
            val ref = extractedInfoCol(uid).document(infoId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "label" to label.trim().ifBlank { "Detail" }.take(120),
                    "value" to value.trim().ifBlank { "-" }.take(1000),
                    "verified" to verified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteExtractedInfo(infoId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/extractedInfo/$infoId"
        return try {
            val uid = requireUserId()
            val ref = extractedInfoCol(uid).document(infoId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createConfirmedReminder(
        title: String,
        dueDateTimeText: String,
        dueTimestampMs: Long,
        recordingId: String = "",
        recordingTitle: String = "",
        timestampMs: Long = 0L
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders"
        return try {
            val uid = requireUserId()
            val ref = remindersCol(uid).document()
            docPath = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "recordingId" to recordingId.take(128),
                    "recordingTitle" to recordingTitle.take(180),
                    "timestampMs" to timestampMs.coerceAtLeast(0L),
                    "title" to title.trim().ifBlank { "Follow up" }.take(300),
                    "dueDateTimeText" to dueDateTimeText.trim().ifBlank { "Soon" }.take(120),
                    "dueTimestampMs" to dueTimestampMs.coerceAtLeast(0L),
                    "completed" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReminder(
        reminderId: String,
        title: String,
        dueDateTimeText: String,
        dueTimestampMs: Long,
        completed: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            val ref = remindersCol(uid).document(reminderId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "title" to title.trim().ifBlank { "Follow up" }.take(300),
                    "dueDateTimeText" to dueDateTimeText.trim().ifBlank { "Soon" }.take(120),
                    "dueTimestampMs" to dueTimestampMs.coerceAtLeast(0L),
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(reminderId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            val ref = remindersCol(uid).document(reminderId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMemoryMessage(message: MemoryMessage): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memoryMessages"
        return try {
            val uid = requireUserId()
            val ref = memoryMessagesCol(uid).document()
            docPath = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "role" to if (message.role == "ASSISTANT") "ASSISTANT" else "USER",
                    "text" to message.text.trim().ifBlank { "..." }.take(12000),
                    "citedRecordingId" to message.citedRecordingId.take(128),
                    "citedRecordingTitle" to message.citedRecordingTitle.take(180),
                    "citedDateText" to message.citedDateText.take(80),
                    "citedTimestampMs" to message.citedTimestampMs.coerceAtLeast(0L),
                    "citedExcerpt" to message.citedExcerpt.take(2000),
                    "evidenceConfidence" to message.evidenceConfidence.take(30),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearMemoryChatHistory(): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memoryMessages"
        return try {
            val uid = requireUserId()
            val snaps = memoryMessagesCol(uid).get().await()
            if (!snaps.isEmpty) {
                val batch = db.batch()
                snaps.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserData(recordingsDir: File): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val recSnaps = recordingsCol(uid).get().await()
            for (recDoc in recSnaps.documents) {
                val segSnaps = segmentsCol(uid, recDoc.id).get().await()
                if (!segSnaps.isEmpty) {
                    val segBatch = db.batch()
                    segSnaps.documents.forEach { segBatch.delete(it.reference) }
                    segBatch.commit().await()
                }
                val fileName = recDoc.getString("localAudioFileName")
                if (!fileName.isNullOrBlank()) {
                    val f = File(recordingsDir, fileName)
                    if (f.exists()) f.delete()
                }
                recDoc.reference.delete().await()
            }

            listOf(extractedInfoCol(uid), remindersCol(uid), memoryMessagesCol(uid)).forEach { col ->
                val snaps = col.get().await()
                if (!snaps.isEmpty) {
                    val batch = db.batch()
                    snaps.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }

            userDoc(uid).delete().await()
            localAudioDao.deleteAllForUser(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun logAndRethrowStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        if (auth.currentUser?.uid == uid) {
            handleFirestoreError(firestoreError ?: error, operationType, path)
        }
        throw error
    }
}
