package com.ChronosFlow.VBCR.feature.focus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ChronosFlow.VBCR.core.ai.findNextFocusBlock
import com.ChronosFlow.VBCR.core.data.assist.ProactiveAssistCache
import com.ChronosFlow.VBCR.core.data.dao.FocusSessionDao
import com.ChronosFlow.VBCR.core.data.focus.ManualMissedBlockRegistry
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.data.privacy.PrivacyPreferences
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSource
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.planner.FocusSessionReducer
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.usecase.LogActualTimeUseCase
import com.ChronosFlow.VBCR.core.notifications.CurrentBlockNotificationCoordinator
import com.ChronosFlow.VBCR.core.notifications.FocusCompletionNotifier
import com.ChronosFlow.VBCR.core.notifications.FocusNotificationContent
import com.ChronosFlow.VBCR.core.notifications.FocusNotificationManager
import com.ChronosFlow.VBCR.core.notifications.FocusPhaseSegment
import com.ChronosFlow.VBCR.core.notifications.FocusProgressNotificationRenderer
import com.ChronosFlow.VBCR.core.notifications.buildFocusNotificationContentIntent
import com.ChronosFlow.VBCR.core.notifications.parseFocusPhasePlan
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class FocusService : Service() {
    @Inject lateinit var notificationRenderer: FocusProgressNotificationRenderer
    @Inject lateinit var focusSessionDao: FocusSessionDao
    @Inject lateinit var focusSessionReducer: FocusSessionReducer
    @Inject lateinit var timeBlockRepository: TimeBlockRepository
    @Inject lateinit var plannerService: PlannerService
    @Inject lateinit var logActualTimeUseCase: LogActualTimeUseCase
    @Inject lateinit var privacyPreferences: PrivacyPreferences
    @Inject lateinit var manualMissedBlockRegistry: ManualMissedBlockRegistry
    @Inject lateinit var proactiveAssistCache: ProactiveAssistCache
    @Inject lateinit var wearFocusBridge: WearFocusBridge
    @Inject lateinit var currentBlockNotificationCoordinator: CurrentBlockNotificationCoordinator

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private val runtime by lazy { FocusSessionRuntime(focusSessionReducer) }

    private var currentSessionId: String? = null
    private var currentBlockId: String? = null
    // @Volatile so the write from refreshBlockTitle's IO coroutine (outside the mutex) is
    // immediately visible to the ticker loop which reads it via updateForegroundNotification (TS-003).
    @Volatile private var currentBlockTitle: String? = null
    private var loadedTitleForBlockId: String? = null
    private var currentTotalSeconds: Int = DEFAULT_FOCUS_SECONDS
    // False while mirroring an intermediate phase of a split session: reaching
    // zero must not log actual time or fire the "session complete" notification.
    private var currentTerminal: Boolean = true
    // For a non-terminal phase, the prompt to post when its timer runs out (e.g.
    // "Time for a 5m break — tap to continue"). Null on the final split phase.
    private var currentBoundaryLabel: String? = null
    // The full work/break plan of a split session ("F25,B5,…" decoded) and which phase is current,
    // so the live notification can draw the whole segmented bar. Empty for a flat session.
    private var currentPhaseSegments: List<FocusPhaseSegment> = emptyList()
    private var currentPhaseIndex: Int = 0
    private var timerJob: Job? = null
    private var blockTitleJob: Job? = null
    private var sessionStartElapsedRealtime: Long = 0L
    private var sessionStartTimeLeftSeconds: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FocusNotificationManager.createFocusNotificationChannel(this, notificationManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Synchronously promote to foreground BEFORE any I/O in the coroutine.
        // ForegroundServiceDidNotStartInTimeException (API 26+) fires at the OS level if
        // startForeground() is delayed past ~5 seconds — the broad catch below cannot catch it.
        // The coroutine calls updateForegroundNotification() to replace this placeholder.
        val placeholder = NotificationCompat.Builder(
            this, com.ChronosFlow.VBCR.core.notifications.FocusNotificationManager.FOCUS_CHANNEL_ID
        )
            .setSmallIcon(com.ChronosFlow.VBCR.core.notifications.R.drawable.ic_chronosflow_notification)
            .setContentTitle(getString(com.ChronosFlow.VBCR.core.notifications.R.string.focus_notification_default_title))
            .setOngoing(true)
            .setSilent(true)
            .build()
        try {
            ServiceCompat.startForeground(
                this,
                com.ChronosFlow.VBCR.core.notifications.FocusNotificationManager.FOCUS_NOTIFICATION_ID,
                placeholder,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            // A background start that Android disallows (ForegroundServiceStartNotAllowedException)
            // reaches here before the coroutine's catch exists. Stop cleanly instead of crashing
            // the process that a widget/wear/agent command woke up.
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }
        serviceScope.launch {
            commandMutex.withLock {
                try {
                    handleStartCommand(intent)
                } catch (e: Exception) {
                    // The service could not be promoted to the foreground. This happens when
                    // the start is requested while the app is in the background (e.g. headlessly
                    // by an AppFunction/agent), which Android disallows for a specialUse FGS and
                    // surfaces as ForegroundServiceStartNotAllowedException here in the coroutine.
                    // Without this guard the exception is uncaught and the OS kills the process.
                    // Stop cleanly instead of crashing.
                    stopTicker()
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (runtime.snapshot().isRunning || runtime.snapshot().isPaused) {
            markCurrentSessionRecoverable()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopTicker()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleStartCommand(intent: Intent?) {
        currentSessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: currentSessionId
        currentBlockId = intent?.getStringExtra(EXTRA_BLOCK_ID) ?: currentBlockId
        ensureRuntimeLoaded(intent)

        when (intent?.action) {
            ACTION_PAUSE -> {
                if (!runtime.snapshot().isRunning) {
                    stopIfIdle()
                    return
                }
                val snapshot = runtime.pause()
                sessionStartElapsedRealtime = 0L
                persistSession(snapshot.state)
                updateForegroundNotification(snapshot)
            }

            ACTION_RESUME -> {
                if (!runtime.snapshot().isPaused) {
                    stopIfIdle()
                    return
                }
                val snapshot = runtime.resume()
                sessionStartElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                sessionStartTimeLeftSeconds = snapshot.timeLeftSeconds
                persistSession(snapshot.state)
                updateForegroundNotification(snapshot)
                startTicker()
            }

            ACTION_SYNC -> {
                val snapshot = runtime.snapshot()
                if (!snapshot.isRunning && !snapshot.isPaused) {
                    stopIfIdle()
                    return
                }
                persistSession(snapshot.state)
                updateForegroundNotification(snapshot)
                if (snapshot.isRunning) {
                    if (sessionStartElapsedRealtime == 0L) {
                        sessionStartElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                        sessionStartTimeLeftSeconds = snapshot.timeLeftSeconds
                    }
                    startTicker()
                } else {
                    sessionStartElapsedRealtime = 0L
                    stopTicker()
                }
            }

            ACTION_EXTEND -> {
                if (!runtime.snapshot().isRunning && !runtime.snapshot().isPaused) {
                    stopIfIdle()
                    return
                }
                val adjustSeconds = intent.getIntExtra(EXTRA_ADJUST_SECONDS, EXTEND_SECONDS)
                val snapshot = runtime.adjustSeconds(adjustSeconds)
                if (snapshot.isRunning) {
                    sessionStartElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                    sessionStartTimeLeftSeconds = snapshot.timeLeftSeconds
                }
                persistSession(snapshot.state)
                updateForegroundNotification(snapshot)
                if (snapshot.isRunning) {
                    startTicker()
                }
            }

            ACTION_STOP -> {
                stopTicker()
                sessionStartElapsedRealtime = 0L
                val snapshot = runtime.snapshot()
                val wasSkip = intent.getBooleanExtra(EXTRA_WAS_SKIP, false)
                val logActualOnStop = intent.getBooleanExtra(EXTRA_LOG_ACTUAL_ON_STOP, true) && !wasSkip
                if (logActualOnStop && snapshot.blockId != null) {
                    logLinkedBlockProgress(snapshot, clearMissed = true)
                }
                val archiveOnStop = intent.getBooleanExtra(EXTRA_ARCHIVE_ON_STOP, true)
                if (archiveOnStop) {
                    persistSession(runtime.archive().state)
                }
                wearFocusBridge.clear()
                // A real stop hands the live surface back to the "now" block notification; an
                // intermediate split-phase stop (archiveOnStop=false) keeps it suppressed so the
                // next phase resumes the focus notification cleanly.
                if (archiveOnStop) {
                    currentBlockNotificationCoordinator.onFocusEnded()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_START,
            null -> {
                val requestedSessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
                val loaded = runtime.snapshot()
                // A split's next phase arrives as a START with a fresh session id while the
                // just-finished phase may still be loaded (same service instance, not yet torn
                // down). Honor the new id by starting that phase fresh instead of mirroring the
                // elapsed one.
                val supersedesLoaded = requestedSessionId != null &&
                    loaded.sessionId != null &&
                    requestedSessionId != loaded.sessionId
                val snapshot = when {
                    loaded.isRunning && !supersedesLoaded -> loaded
                    loaded.isPaused && !supersedesLoaded -> runtime.resume()
                    else -> startNewSession(intent)
                }
                if (snapshot.isRunning) {
                    sessionStartElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                    sessionStartTimeLeftSeconds = snapshot.timeLeftSeconds
                }
                persistSession(snapshot.state)
                // A split session persists one row per phase under a fresh session id; drop the
                // earlier phases' rows so a completed split can't leave a stale RUNNING row behind
                // that resurfaces as a phantom "recoverable" session on next launch.
                snapshot.sessionId?.let { focusSessionDao.deleteSessionsExcept(it) }
                updateForegroundNotification(snapshot)
                startTicker()
            }
        }
    }

    /**
     * A command that found nothing to act on (a stray PAUSE/SYNC racing a stop, delivered by a
     * widget or watch) must not leave the service sitting in the foreground with its placeholder
     * notification. When no session is live, stand down cleanly.
     */
    private fun stopIfIdle() {
        val snapshot = runtime.snapshot()
        if (!snapshot.isRunning && !snapshot.isPaused) {
            stopTicker()
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
    }

    private suspend fun startNewSession(intent: Intent?): FocusSessionSnapshot {
        // Phase metadata is a property of the session being started, so it is
        // read here (not on every command) — a periodic SYNC must never flip it.
        currentTerminal = intent?.getBooleanExtra(EXTRA_TERMINAL, true) ?: true
        currentBoundaryLabel = intent?.getStringExtra(EXTRA_BOUNDARY_LABEL)
        currentPhaseSegments = parseFocusPhasePlan(intent?.getStringExtra(EXTRA_PHASE_PLAN))
        currentPhaseIndex = intent?.getIntExtra(EXTRA_PHASE_INDEX, 0) ?: 0
        val requestedTotalSeconds = resolveRequestedTotalSeconds(intent)
        val requestedTimeLeft = resolveRequestedTimeLeft(intent, requestedTotalSeconds)
        return runtime.start(
            sessionId = currentSessionId ?: UUID.randomUUID().toString(),
            blockId = currentBlockId,
            timeLeftSeconds = requestedTimeLeft,
            totalSeconds = requestedTotalSeconds
        )
    }

    private fun buildNotification(snapshot: FocusSessionSnapshot): Notification {
        val redactSensitiveTitles = privacyPreferences.redactSensitiveNotifications()
        val title = currentBlockTitle ?: getString(com.ChronosFlow.VBCR.core.notifications.R.string.focus_notification_default_title)
        val text = if (snapshot.isPaused) {
            FocusNotificationContent.pausedBody(this, snapshot.timeLeftSeconds, redactSensitiveTitles)
        } else {
            FocusNotificationContent.runningBody(this, snapshot.timeLeftSeconds, redactSensitiveTitles)
        }
        val contentIntent = buildFocusNotificationContentIntent(
            context = this,
            blockId = currentBlockId
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FocusService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_SESSION_ID, snapshot.sessionId)
                .putExtra(EXTRA_ARCHIVE_ON_STOP, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FocusService::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_SESSION_ID, snapshot.sessionId)
                .putExtra(EXTRA_TIME_LEFT_SECONDS, snapshot.timeLeftSeconds)
                .putExtra(EXTRA_TOTAL_SECONDS, snapshot.totalSeconds),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, FocusService::class.java)
                .setAction(ACTION_RESUME)
                .putExtra(EXTRA_SESSION_ID, snapshot.sessionId)
                .putExtra(EXTRA_TIME_LEFT_SECONDS, snapshot.timeLeftSeconds)
                .putExtra(EXTRA_TOTAL_SECONDS, snapshot.totalSeconds),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val extendIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, FocusService::class.java)
                .setAction(ACTION_EXTEND)
                .putExtra(EXTRA_SESSION_ID, snapshot.sessionId)
                .putExtra(EXTRA_TIME_LEFT_SECONDS, snapshot.timeLeftSeconds)
                .putExtra(EXTRA_TOTAL_SECONDS, snapshot.totalSeconds),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return notificationRenderer.build(
            channelId = FocusNotificationManager.FOCUS_CHANNEL_ID,
            title = title,
            text = text,
            timeLeftSeconds = snapshot.timeLeftSeconds,
            totalSeconds = snapshot.totalSeconds,
            isPaused = snapshot.isPaused,
            redactSensitiveTitles = redactSensitiveTitles,
            contentIntent = contentIntent,
            pauseIntent = if (snapshot.isPaused) null else pauseIntent,
            resumeIntent = if (snapshot.isPaused) resumeIntent else null,
            stopIntent = stopIntent,
            extendIntent = extendIntent,
            phaseSegments = currentPhaseSegments,
            currentPhaseIndex = currentPhaseIndex,
            subText = phaseIndicatorSubText()
        )
    }

    /** Header label naming the current split phase (e.g. "Focus 2 of 4"); null for a flat session. */
    private fun phaseIndicatorSubText(): String? {
        val segments = currentPhaseSegments
        if (segments.size <= 1) return null
        val idx = currentPhaseIndex.coerceIn(0, segments.lastIndex)
        val resId = if (segments[idx].isBreak) {
            com.ChronosFlow.VBCR.core.notifications.R.string.focus_notification_phase_break
        } else {
            com.ChronosFlow.VBCR.core.notifications.R.string.focus_notification_phase_focus
        }
        return getString(resId, idx + 1, segments.size)
    }

    private fun refreshBlockTitle(blockId: String?) {
        if (blockId == null) {
            blockTitleJob?.cancel()
            currentBlockTitle = null
            loadedTitleForBlockId = null
            return
        }
        if (blockId == loadedTitleForBlockId && currentBlockTitle != null) {
            return
        }
        blockTitleJob?.cancel()
        blockTitleJob = serviceScope.launch {
            val title = timeBlockRepository.getTimeBlockById(blockId)?.title
            currentBlockTitle = title
            loadedTitleForBlockId = blockId
            val snapshot = runtime.snapshot()
            if (snapshot.isRunning || snapshot.isPaused) {
                updateForegroundNotification(snapshot)
            }
        }
    }

    // The live notification renders the thick ProgressStyle bar on every capable device, and that
    // bar only advances when the notification is re-posted — so while running we refresh on the
    // notification cadence rather than every clock tick.
    private fun focusTickerDelayMs(snapshot: FocusSessionSnapshot): Long =
        if (snapshot.isRunning) FOCUS_NOTIFICATION_REFRESH_INTERVAL_MS else 1_000L

    private suspend fun ensureRuntimeLoaded(intent: Intent?) {
        val snapshot = runtime.snapshot()
        if (snapshot.isRunning || snapshot.isPaused) return

        // Only fall back to "the latest recoverable session" when the caller did not name one. A
        // START/SYNC that explicitly carries a session id (e.g. the next phase of a split) must load
        // exactly that session — never resurrect a different, already-finished phase that still
        // lingers as RUNNING in the table, which would make the live notification mirror the wrong
        // phase and overwrite currentSessionId.
        val explicitSessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        val byId = currentSessionId?.let { focusSessionDao.getFocusSession(it) }
        val restoredEntity = when {
            byId != null -> byId
            explicitSessionId != null -> return
            else -> focusSessionDao.observeRecoverableSession().first()
        } ?: return

        val restoredState = restoredEntity.toDomain()
        if (restoredState !is FocusSessionState.Running && restoredState !is FocusSessionState.Paused) {
            return
        }

        val restoredTotalSeconds = restoredEntity.totalSeconds
            ?: resolveRequestedTotalSeconds(intent, restoredState)
        runtime.restore(restoredState, restoredTotalSeconds)
        currentTotalSeconds = restoredTotalSeconds
        currentSessionId = runtime.snapshot().sessionId
        currentBlockId = runtime.snapshot().blockId ?: currentBlockId
        val currentSnapshot = runtime.snapshot()
        if (currentSnapshot.isRunning) {
            sessionStartElapsedRealtime = android.os.SystemClock.elapsedRealtime()
            sessionStartTimeLeftSeconds = currentSnapshot.timeLeftSeconds
        }
    }

    private suspend fun resolveRequestedTotalSeconds(
        intent: Intent?,
        restoredState: FocusSessionState? = null
    ): Int {
        val hintedTotal = intent?.getIntExtra(EXTRA_TOTAL_SECONDS, 0) ?: 0
        if (hintedTotal > 0) {
            currentTotalSeconds = hintedTotal
            return hintedTotal
        }

        val restoredBlockId = when (restoredState) {
            is FocusSessionState.Running -> restoredState.blockId
            is FocusSessionState.Paused -> restoredState.blockId
            else -> currentBlockId
        }
        val blockDuration = restoredBlockId?.let { blockId ->
            timeBlockRepository.getTimeBlockById(blockId)?.durationMinutes?.times(60)
        }

        currentTotalSeconds = blockDuration ?: currentTotalSeconds
        return currentTotalSeconds
    }

    private fun resolveRequestedTimeLeft(intent: Intent?, requestedTotalSeconds: Int): Int {
        val hintedTimeLeft = intent?.getIntExtra(EXTRA_TIME_LEFT_SECONDS, 0) ?: 0
        return when {
            hintedTimeLeft > 0 -> hintedTimeLeft
            runtime.snapshot().isRunning || runtime.snapshot().isPaused -> runtime.snapshot().timeLeftSeconds
            else -> requestedTotalSeconds
        }
    }

    private fun updateForegroundNotification(snapshot: FocusSessionSnapshot) {
        // The boundary "tap to continue" prompt reuses the live notification's id, so re-posting the
        // running notification below (startForeground) replaces it in place — no separate nudge to
        // cancel. The focus notification is the single live surface — stand the block "now"
        // notification down so the two never show at once.
        if (snapshot.isRunning || snapshot.isPaused) {
            currentBlockNotificationCoordinator.onFocusStarted()
        }
        currentSessionId = snapshot.sessionId
        currentBlockId = snapshot.blockId ?: currentBlockId
        refreshBlockTitle(currentBlockId)
        currentTotalSeconds = snapshot.totalSeconds
        ServiceCompat.startForeground(
            this,
            FocusNotificationManager.FOCUS_NOTIFICATION_ID,
            buildNotification(snapshot),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
        publishWearState(snapshot)
    }

    private fun publishWearState(snapshot: FocusSessionSnapshot) {
        if (!snapshot.isRunning && !snapshot.isPaused) return
        val defaultTitle = getString(com.ChronosFlow.VBCR.core.notifications.R.string.focus_notification_default_title)
        val title = if (privacyPreferences.redactSensitiveNotifications()) {
            defaultTitle
        } else {
            currentBlockTitle ?: defaultTitle
        }
        if (snapshot.isPaused) {
            wearFocusBridge.publishPaused(title, snapshot.timeLeftSeconds, snapshot.totalSeconds)
        } else {
            val plannedEndAtMillis = snapshot.plannedEndAt?.toEpochMilli()
                ?: (System.currentTimeMillis() + snapshot.timeLeftSeconds * 1_000L)
            wearFocusBridge.publishRunning(title, plannedEndAtMillis, snapshot.totalSeconds)
        }
    }

    private fun startTicker() {
        stopTicker()
        timerJob = serviceScope.launch {
            while (isActive) {
                // Clock drift & wall-clock shift alignment
                if (sessionStartElapsedRealtime > 0L) {
                    val elapsedRealtimeSeconds = ((android.os.SystemClock.elapsedRealtime() - sessionStartElapsedRealtime) / 1000).toInt()
                    val expectedTimeLeftSeconds = (sessionStartTimeLeftSeconds - elapsedRealtimeSeconds).coerceAtLeast(0)
                    val currentSnapshot = runtime.snapshot()
                    val drift = currentSnapshot.timeLeftSeconds - expectedTimeLeftSeconds
                    if (java.lang.Math.abs(drift) >= 2) {
                        commandMutex.withLock {
                            val adjustedSnapshot = runtime.adjustSeconds(-drift)
                            persistSession(adjustedSnapshot.state)
                            updateForegroundNotification(adjustedSnapshot)
                        }
                    }
                }

                val snapshot = runtime.snapshot()
                if (!snapshot.isRunning) {
                    break
                }
                if (snapshot.timeLeftSeconds <= 0 && commandMutex.withLock { runtime.completeIfFinished() }) {
                    persistSession(runtime.snapshot().state)
                    val redactSensitiveTitles = privacyPreferences.redactSensitiveNotifications()
                    val boundaryLabel = currentBoundaryLabel
                    val completed = currentTerminal || boundaryLabel == null
                    // DETACH (never REMOVE): keep the live notification (id 4201) on screen so the
                    // completion celebration AND the phase-boundary "tap to continue" prompt both
                    // update it in place — one live notification on one channel, no separate nudge.
                    stopForeground(STOP_FOREGROUND_DETACH)
                    when {
                        // Legacy / final-phase completion: log actual time and announce.
                        currentTerminal -> {
                            completeAndLogCurrentSession()
                            FocusCompletionNotifier.show(
                                context = this@FocusService,
                                blockTitle = currentBlockTitle,
                                redactSensitiveTitles = redactSensitiveTitles,
                                nextStepLine = buildNextBlockLine(redactSensitiveTitles)
                            )
                        }
                        // Intermediate split phase: the in-app layer owns completion,
                        // so just nudge the user to continue (works while backgrounded).
                        boundaryLabel != null -> FocusCompletionNotifier.showPhaseBoundary(
                            context = this@FocusService,
                            blockTitle = currentBlockTitle,
                            body = boundaryLabel,
                            redactSensitiveTitles = redactSensitiveTitles
                        )
                        // Final split phase reached zero while backgrounded: announce
                        // completion, but leave actual-time logging to the in-app layer.
                        else -> FocusCompletionNotifier.show(
                            context = this@FocusService,
                            blockTitle = currentBlockTitle,
                            redactSensitiveTitles = redactSensitiveTitles,
                            nextStepLine = buildNextBlockLine(redactSensitiveTitles)
                        )
                    }
                    // Completion (terminal or final split phase) returns the live surface to the
                    // "now" block notification; an intermediate phase boundary keeps it suppressed.
                    if (completed) {
                        currentBlockNotificationCoordinator.onFocusEnded()
                    }
                    wearFocusBridge.clear()
                    stopSelf()
                    break
                }
                updateForegroundNotification(snapshot)
                delay(focusTickerDelayMs(snapshot))
            }
        }
    }

    private fun stopTicker() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * "Next up" line for the completion notification. Uses the deterministic
     * local picker (no GenAI in a background service); when the foreground app
     * pre-generated an AI line for the same block today, that line wins.
     */
    private suspend fun buildNextBlockLine(redactSensitiveTitles: Boolean): String? {
        if (redactSensitiveTitles) return null
        val today = LocalDate.now()
        val blocks = runCatching { timeBlockRepository.getTimeBlocksByDate(today).first() }
            .getOrDefault(emptyList())
            .filter { it.id != currentBlockId }
        val now = LocalTime.now()
        val pick = findNextFocusBlock(blocks, now.hour * 60 + now.minute) ?: return null
        proactiveAssistCache.focusNextBlockLine(today, pick.id)?.let { return it }
        return getString(
            com.ChronosFlow.VBCR.core.notifications.R.string.notification_next_up,
            pick.title,
            pick.startMinuteOfDay / 60,
            pick.startMinuteOfDay % 60
        )
    }

    private suspend fun completeAndLogCurrentSession() {
        val snapshot = runtime.snapshot()
        if (snapshot.blockId != null) {
            logLinkedBlockProgress(snapshot, clearMissed = true)
        }
    }

    private suspend fun logLinkedBlockProgress(
        snapshot: FocusSessionSnapshot,
        clearMissed: Boolean
    ) {
        val blockId = snapshot.blockId ?: currentBlockId ?: return
        val block = timeBlockRepository.getTimeBlockById(blockId) ?: return
        val elapsedMinutes = focusElapsedMinutes(
            totalSeconds = snapshot.totalSeconds,
            timeLeftSeconds = snapshot.timeLeftSeconds,
            plannedDurationMinutes = block.durationMinutes
        )
        val actualEnd = (block.startMinuteOfDay + elapsedMinutes).coerceIn(0, 1440)
        plannerService.logActualWindow(block.id, block.startMinuteOfDay, actualEnd)
        logActualTimeUseCase(
            ActualTimeSegment(
                id = UUID.randomUUID().toString(),
                blockId = block.id,
                date = block.date,
                startInstant = block.date.atStartOfDay(ZoneId.systemDefault())
                    .plusMinutes(block.startMinuteOfDay.toLong())
                    .toInstant(),
                endInstant = block.date.atStartOfDay(ZoneId.systemDefault())
                    .plusMinutes(actualEnd.toLong())
                    .toInstant(),
                source = ActualTimeSource.FOCUS_SESSION,
                confidence = 1f
            )
        )
        if (clearMissed) {
            manualMissedBlockRegistry.clearMissed(block.id, block.date)
        }
    }

    private fun persistSession(state: FocusSessionState) {
        serviceScope.launch {
            focusSessionDao.insertFocusSession(state.toEntity(now = Instant.now(), totalSeconds = currentTotalSeconds))
        }
    }

    private fun markCurrentSessionRecoverable() {
        val snapshot = runtime.snapshot()
        when (val state = snapshot.state) {
            is FocusSessionState.Running, is FocusSessionState.Paused -> persistSession(state)
            else -> Unit
        }
    }

    companion object {
        const val ACTION_START = "com.ChronosFlow.VBCR.feature.focus.START"
        const val ACTION_SYNC = "com.ChronosFlow.VBCR.feature.focus.SYNC"
        const val ACTION_PAUSE = "com.ChronosFlow.VBCR.feature.focus.PAUSE"
        const val ACTION_RESUME = "com.ChronosFlow.VBCR.feature.focus.RESUME"
        const val ACTION_EXTEND = "com.ChronosFlow.VBCR.feature.focus.EXTEND"
        const val ACTION_STOP = "com.ChronosFlow.VBCR.feature.focus.STOP"
        const val EXTRA_TIME_LEFT_SECONDS = "time_left_seconds"
        const val EXTRA_TOTAL_SECONDS = "total_seconds"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_BLOCK_ID = "block_id"
        const val EXTRA_ARCHIVE_ON_STOP = "archive_on_stop"
        const val EXTRA_LOG_ACTUAL_ON_STOP = "log_actual_on_stop"
        const val EXTRA_WAS_SKIP = "was_skip"
        const val EXTRA_ADJUST_SECONDS = "adjust_seconds"
        const val EXTRA_TERMINAL = "terminal"
        const val EXTRA_BOUNDARY_LABEL = "boundary_label"
        // Full split plan ("F25,B5,…") and the index of the phase this session mirrors, so the live
        // notification can draw the whole work/break bar.
        const val EXTRA_PHASE_PLAN = "phase_plan"
        const val EXTRA_PHASE_INDEX = "phase_index"

        private const val DEFAULT_FOCUS_SECONDS = 25 * 60
        private const val EXTEND_SECONDS = 15 * 60
        private const val FOCUS_NOTIFICATION_REFRESH_INTERVAL_MS = 5_000L
    }
}
