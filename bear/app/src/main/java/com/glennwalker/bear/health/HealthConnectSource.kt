package com.glennwalker.bear.health

import android.content.Context
import android.content.pm.PackageManager
import android.health.connect.AggregateRecordsRequest
import android.health.connect.AggregateRecordsResponse
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.HealthPermissions
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.StepsRecord
import android.os.OutcomeReceiver
import com.glennwalker.bear.core.HealthReading
import com.glennwalker.bear.ui.HealthSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads your sleep and steps from Health Connect, which is built into Android 14 and up.
 * Read on the phone only; nothing is sent anywhere.
 */
class HealthConnectSource private constructor(private val context: Context) : HealthSource {
    /** Shows the permission prompt; set by whichever activity is on screen. */
    var ask: ((Array<String>) -> Unit)? = null

    private val manager: HealthConnectManager? = context.getSystemService(HealthConnectManager::class.java)

    override var onChanged: (() -> Unit)? = null

    override val available: Boolean get() = manager != null

    override fun granted(): Boolean =
        PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun requestAccess() {
        ask?.invoke(PERMISSIONS)
    }

    override suspend fun read(): HealthReading? {
        val hc = manager ?: return null
        if (!granted()) return null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = Instant.now()
        val midnight = today.atStartOfDay(zone).toInstant()
        val steps = runCatching { steps(hc, midnight, now) }.getOrNull()
        // Last night: sleep sessions between 6 pm yesterday and 2 pm today.
        val nightStart = today.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant()
        val nightEnd = today.atTime(LocalTime.of(14, 0)).atZone(zone).toInstant()
        val sleep = runCatching { sleepMinutes(hc, nightStart, nightEnd) }.getOrNull()
        return HealthReading(day = today.toEpochDay(), sleepMinutes = sleep, steps = steps)
    }

    private suspend fun steps(hc: HealthConnectManager, start: Instant, end: Instant): Int? {
        val request = AggregateRecordsRequest.Builder<Long>(range(start, end))
            .addAggregationType(StepsRecord.STEPS_COUNT_TOTAL)
            .build()
        val response = suspendCancellableCoroutine<AggregateRecordsResponse<Long>> { cont ->
            hc.aggregate(request, context.mainExecutor, receiver(cont))
        }
        return response.get(StepsRecord.STEPS_COUNT_TOTAL)?.toInt()
    }

    /** Time asleep: each session's length, less any stages marked awake. Null when there's no session. */
    private suspend fun sleepMinutes(hc: HealthConnectManager, start: Instant, end: Instant): Int? {
        val request = ReadRecordsRequestUsingFilters.Builder(SleepSessionRecord::class.java)
            .setTimeRangeFilter(range(start, end))
            .build()
        val response = suspendCancellableCoroutine<ReadRecordsResponse<SleepSessionRecord>> { cont ->
            hc.readRecords(request, context.mainExecutor, receiver(cont))
        }
        val sessions = response.records
        if (sessions.isEmpty()) return null
        val asleep = sessions.sumOf { session ->
            val total = Duration.between(session.startTime, session.endTime)
            val awake = session.stages
                .filter { it.type in AWAKE_STAGES }
                .fold(Duration.ZERO) { sum, stage -> sum + Duration.between(stage.startTime, stage.endTime) }
            (total - awake).toMinutes().coerceAtLeast(0)
        }
        return asleep.toInt()
    }

    private fun range(start: Instant, end: Instant) =
        TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build()

    private fun <T> receiver(cont: kotlinx.coroutines.CancellableContinuation<T>) =
        object : OutcomeReceiver<T, HealthConnectException> {
            override fun onResult(result: T) = cont.resume(result)
            override fun onError(error: HealthConnectException) = cont.resumeWithException(error)
        }

    companion object {
        @Volatile private var instance: HealthConnectSource? = null

        /** One source for the whole app, so it outlives screen rotations like the view model does. */
        fun get(context: Context): HealthConnectSource =
            instance ?: synchronized(this) { instance ?: HealthConnectSource(context.applicationContext).also { instance = it } }

        val PERMISSIONS = arrayOf(HealthPermissions.READ_STEPS, HealthPermissions.READ_SLEEP)

        private val AWAKE_STAGES = setOf(
            SleepSessionRecord.StageType.STAGE_TYPE_AWAKE,
            SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_OUT_OF_BED,
        )
    }
}
