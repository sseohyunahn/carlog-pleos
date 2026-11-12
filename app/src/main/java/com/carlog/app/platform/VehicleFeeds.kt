package com.carlog.app.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.carlog.app.domain.DataMode
import com.carlog.app.domain.FieldStatus
import com.carlog.app.domain.Fields
import com.carlog.app.domain.LinkState
import com.carlog.app.domain.LocationRules
import com.carlog.app.domain.SnapshotMerge
import com.carlog.app.domain.VehicleSnapshot
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Vehicle feeds for CarLog v2.
 *  - DemoVehicleFeed: deterministic, clearly labelled demo values (never talks to a vehicle).
 *  - PleosVehicleFeed: read-only Pleos Vehicle 2.3.2 + FusedLocation 1.1.3. Persistent connection, rounds every 5 s,
 *    10 s round timeout ("차량 응답 지연"), late callbacks discarded by round identity, init retry every 5 s,
 *    release after at most a 3 s grace period. No vehicle-control (write) APIs are called.
 * Everything delivered to the listener runs on the main thread.
 */

private const val POLL_MS = 5_000L

private fun mainHandler() = Handler(Looper.getMainLooper())

private inline fun Handler.runOnMain(crossinline block: () -> Unit) {
    if (Looper.myLooper() == looper) block() else post { block() }
}

// ---------------------------------------------------------------------------------------------------------------
// Demo
// ---------------------------------------------------------------------------------------------------------------

/** Demo feed: emits the same labelled demo values every 5 s on the main thread. */
class DemoVehicleFeed : VehicleFeed {
    private val main = mainHandler()
    private var listener: ((VehicleSnapshot) -> Unit)? = null
    private var gpsUnavailable = false
    private val tick = object : Runnable {
        override fun run() {
            emit()
            main.postDelayed(this, POLL_MS)
        }
    }

    override fun start(onSnapshot: (VehicleSnapshot) -> Unit) = main.runOnMain {
        main.removeCallbacks(tick)
        listener = onSnapshot
        tick.run()
    }

    /** Emits immediately and restarts the 5 s cadence. */
    override fun refresh() = main.runOnMain {
        if (listener == null) return@runOnMain
        main.removeCallbacks(tick)
        tick.run()
    }

    override fun stop() = main.runOnMain {
        main.removeCallbacks(tick)
        listener = null
    }

    /** "GPS 음영" demo: location becomes null and the driver is asked to type the parking spot. */
    fun setGpsUnavailable(on: Boolean) = main.runOnMain {
        gpsUnavailable = on
        refresh()
    }

    private fun emit() {
        val target = listener ?: return
        val value = demoSnapshot(System.currentTimeMillis(), gpsUnavailable)
        try { target(value) } catch (e: Exception) { Log.e("CarLog", "Demo snapshot listener failed", e) }
    }

    companion object {
        const val DEMO_LATITUDE = 37.4967
        const val DEMO_LONGITUDE = 127.0282

        /** Deterministic demo snapshot (pure; usable from tests). */
        fun demoSnapshot(now: Long, gpsUnavailable: Boolean): VehicleSnapshot {
            val received = Fields.ALL.filter { it != Fields.FUEL && (it != Fields.LOCATION || !gpsUnavailable) }
            val status = Fields.ALL.associateWith { if (it in received) FieldStatus.OK else FieldStatus.NO_VALUE }
            return VehicleSnapshot(
                mode = DataMode.DEMO,
                capturedAt = now,
                battery = 64,
                fuel = null,
                odometer = 12503.4f,
                trip = 23.4f,
                rangeKm = 251f,
                unlockedDoors = 0,
                doorAreas = 4,
                openWindows = 1,
                windowAreas = 4,
                washerWarning = false,
                latitude = if (gpsUnavailable) null else DEMO_LATITUDE,
                longitude = if (gpsUnavailable) null else DEMO_LONGITUDE,
                locationTime = if (gpsUnavailable) null else now,
                confidence = if (gpsUnavailable) null else 0.9f,
                link = LinkState.DEMO,
                fieldTimes = received.associateWith { now },
                fieldStatus = status,
                diagnostics = mapOf(
                    "데이터" to "시연용 가상 데이터",
                    "위치" to if (gpsUnavailable) "GPS 음영 시연 · 직접 입력" else "시연 좌표 · 실차 위치 아님"
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Pleos
// ---------------------------------------------------------------------------------------------------------------

/**
 * Read-only Pleos feed. Each round reads battery SOC, fuel %, odometer, trip, remaining range, door locks per area,
 * window positions per area, the low-washer warning and the FusedLocation last known location, then merges the
 * round into the last valid snapshot with [SnapshotMerge]. All SDK work is asynchronous; callbacks are posted to
 * the main thread and ignored when they belong to an abandoned round or a stopped session.
 */
class PleosVehicleFeed(context: Context) : VehicleFeed {
    private val appContext = context.applicationContext
    private val main = mainHandler()
    private var session: Session? = null

    private enum class Fault { PERMISSION, UNSUPPORTED, NOT_READY, EMPTY, FAILED }

    private class Session(val listener: (VehicleSnapshot) -> Unit) {
        var active = true
        var released = false
        var vehicle: Vehicle? = null
        var initFault: Fault? = null
        var fused: FusedLocation? = null
        var fusedUnsupported = false
        var round: Round? = null
        var roundCounter = 0
        var notReadyRounds = 0
        var last: VehicleSnapshot? = null
        var lastLink: LinkState? = null
        lateinit var poll: Runnable
    }

    private class Round(val id: Int) {
        var pending = 0
        var closed = false
        val status = HashMap<String, FieldStatus>()
        val faults = HashMap<String, Fault>()
        val times = HashMap<String, Long>()
        var battery: Int? = null
        var fuel: Int? = null
        var odometer: Float? = null
        var trip: Float? = null
        var range: Float? = null
        var unlockedDoors: Int? = null
        var doorAreas: Int? = null
        var openWindows: Int? = null
        var windowAreas: Int? = null
        var washer: Boolean? = null
        var location: LocationData? = null
        var locationNote: String? = null
        var timeout: Runnable? = null
    }

    /** Counts answers for a per-area read (doors, windows). Only a full answer is published. */
    private class AreaGroup(val total: Int) {
        var left = total
        var answered = 0
        var hits = 0
        var fault: Fault? = null
    }

    // ---- VehicleFeed --------------------------------------------------------------------------------------------

    override fun start(onSnapshot: (VehicleSnapshot) -> Unit) = main.runOnMain {
        stopNow()
        val s = Session(onSnapshot)
        session = s
        s.poll = object : Runnable {
            override fun run() {
                if (!live(s)) return
                startRound(s)
                main.postDelayed(this, POLL_MS)
            }
        }
        connect(s)
        connectLocation(s)
        publish(s, VehicleSnapshot(
            mode = DataMode.PLEOS,
            capturedAt = System.currentTimeMillis(),
            link = if (s.vehicle == null) initLink(s) else LinkState.CONNECTING,
            fieldStatus = Fields.ALL.associateWith { FieldStatus.NOT_READ },
            diagnostics = if (s.vehicle == null) mapOf(KEY_VEHICLE to NOTE_RECONNECTING) else emptyMap()
        ))
        // Service binding is asynchronous: give it a moment before the first round.
        main.postDelayed(s.poll, FIRST_ROUND_DELAY_MS)
    }

    override fun refresh() = main.runOnMain {
        val s = session ?: return@runOnMain
        if (!s.active) return@runOnMain
        val r = s.round
        if (r != null && !r.closed && r.pending > 0) return@runOnMain // a round is in flight; its timeout handles stalls
        // Restart the cadence so the next automatic round is a full interval after this one.
        main.removeCallbacks(s.poll)
        s.poll.run()
    }

    override fun stop() = main.runOnMain { stopNow() }

    private fun stopNow() {
        val s = session ?: return
        session = null
        s.active = false
        main.removeCallbacks(s.poll)
        val r = s.round
        r?.timeout?.let { main.removeCallbacks(it) }
        if (r == null || r.closed || r.pending <= 0) release(s)
        else main.postDelayed({ release(s) }, RELEASE_GRACE_MS)
    }

    // ---- connection ---------------------------------------------------------------------------------------------

    private fun live(s: Session) = s.active && session === s
    private fun live(s: Session, r: Round) = live(s) && s.round === r && !r.closed

    /** Creates the Vehicle client; on failure the next round (5 s) retries, so a late-starting service is picked up. */
    private fun connect(s: Session) {
        if (s.vehicle != null) return
        val created = try {
            Vehicle(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle create failed", e)
            s.initFault = if (classify(e) == Fault.PERMISSION) Fault.PERMISSION else Fault.NOT_READY
            return
        }
        try {
            created.initialize()
            s.vehicle = created
            s.initFault = null
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle initialize failed", e)
            s.initFault = if (classify(e) == Fault.PERMISSION) Fault.PERMISSION else Fault.NOT_READY
            try { created.release() } catch (ignored: Exception) { /* half-initialised client */ }
        }
    }

    /** FusedLocation is optional: when unsupported the driver types the parking spot; otherwise retried each round. */
    private fun connectLocation(s: Session) {
        if (s.fused != null || s.fusedUnsupported) return
        val created = try {
            FusedLocation(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocation create failed", e)
            return
        }
        try {
            if (!created.isSupported()) {
                s.fusedUnsupported = true
                return
            }
            created.initialize()
            s.fused = created
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocation initialize failed", e)
            try { created.release() } catch (ignored: Exception) { /* half-initialised client */ }
        }
    }

    private fun release(s: Session) {
        if (s.released) return
        s.released = true
        s.vehicle?.let { v -> try { v.release() } catch (e: Exception) { Log.w(TAG, "Vehicle release failed", e) } }
        s.fused?.let { f -> try { f.release() } catch (e: Exception) { Log.w(TAG, "FusedLocation release failed", e) } }
        s.vehicle = null
        s.fused = null
    }

    // ---- rounds -------------------------------------------------------------------------------------------------

    // getRangeRemainingDistance is deprecated in SDK 2.3.2 in favour of a platform property, but it is still the only
    // read path the Pleos emulator answers (v1 used it the same way); it stays read-only here.
    @Suppress("DEPRECATION")
    private fun startRound(s: Session) {
        val inFlight = s.round
        if (inFlight != null && !inFlight.closed && inFlight.pending > 0) return
        if (s.vehicle == null) connect(s)
        if (s.fused == null) connectLocation(s)

        val r = Round(++s.roundCounter)
        s.round = r
        r.pending = 1 // guard: the round cannot finish while requests are still being issued
        val timeout = Runnable { abandon(s, r) }
        r.timeout = timeout
        main.postDelayed(timeout, ROUND_TIMEOUT_MS)

        val vehicle = s.vehicle
        if (vehicle != null) {
            readValue<Int>(s, r, Fields.BATTERY,
                { ok, err -> vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(ok, err) },
                { ok, err -> vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, err) }
            ) { v -> if (v in 0..100) { r.battery = v; true } else false }
            readValue<Int>(s, r, Fields.FUEL,
                { ok, err -> vehicle.getFuel().checkFuelPercentCapabilities(ok, err) },
                { ok, err -> vehicle.getFuel().getFuelPercent(ok, err) }
            ) { v -> if (v in 0..100) { r.fuel = v; true } else false }
            readValue<Float>(s, r, Fields.ODOMETER,
                { ok, err -> vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(ok, err) },
                { ok, err -> vehicle.getOdometer().getCurrentDistanceDriven(ok, err) }
            ) { v -> if (validDistance(v)) { r.odometer = v; true } else false }
            readValue<Float>(s, r, Fields.TRIP,
                { ok, err -> vehicle.getOdometer().checkTripCurrentDrivingDistanceCapabilities(ok, err) },
                { ok, err -> vehicle.getOdometer().getTripCurrentDrivingDistance(ok, err) }
            ) { v -> if (validDistance(v)) { r.trip = v; true } else false }
            // Remaining range has no capability query in SDK 2.3.2; failures are reported per field.
            readValue<Float>(s, r, Fields.RANGE, null,
                { ok, err -> vehicle.getOdometer().getRangeRemainingDistance(ok, err) }
            ) { v -> if (validDistance(v)) { r.range = v; true } else false }
            readValue<Boolean>(s, r, Fields.WASHER,
                { ok, err -> vehicle.getSafety().checkLowWasherFluidWarningStateCapabilities(ok, err) },
                { ok, err -> vehicle.getSafety().hasLowWasherFluidWarning(ok, err) }
            ) { v -> r.washer = v; true }
            readDoors(s, r, vehicle)
            readWindows(s, r, vehicle)
        } else if (s.initFault == Fault.PERMISSION) {
            VEHICLE_FIELDS.forEach { r.status[it] = FieldStatus.PERMISSION_DENIED }
        } // otherwise vehicle fields keep their previous status (or "아직 수신 없음") while we reconnect
        readLocation(s, r)
        done(s, r)
    }

    /** Called once per finished request (success, error or synchronous throw). */
    private fun done(s: Session, r: Round) {
        if (s.round !== r || r.closed) return
        r.pending--
        if (r.pending > 0) return
        r.timeout?.let { main.removeCallbacks(it) }
        if (!s.active) { release(s); return }
        if (session !== s) return
        r.closed = true
        val snapshot = buildSnapshot(s, r, delayed = false)
        publish(s, snapshot)
        recycleIfNotReady(s, snapshot.link)
    }

    /**
     * initialize() can succeed while the vehicle service is still unbound, after which every call fails with a
     * connection error. After two such rounds the client is dropped so the next round (5 s) reconnects from scratch.
     */
    private fun recycleIfNotReady(s: Session, link: LinkState) {
        if (link != LinkState.SERVICE_NOT_READY) { s.notReadyRounds = 0; return }
        val vehicle = s.vehicle ?: return
        if (++s.notReadyRounds < 2) return
        s.notReadyRounds = 0
        s.vehicle = null
        s.initFault = Fault.NOT_READY
        Log.i(TAG, "Vehicle service not ready for two rounds; reconnecting")
        try { vehicle.release() } catch (e: Exception) { Log.w(TAG, "Vehicle release before reconnect failed", e) }
    }

    private fun abandon(s: Session, r: Round) {
        if (!live(s, r) || r.pending <= 0) return
        Log.w(TAG, "Vehicle round ${r.id} timed out with ${r.pending} pending callbacks")
        r.closed = true
        publish(s, buildSnapshot(s, r, delayed = true))
    }

    private fun publish(s: Session, incoming: VehicleSnapshot) {
        if (!live(s)) return
        val merged = SnapshotMerge.merge(s.last, incoming)
        s.last = merged
        if (merged.link != s.lastLink) {
            Log.d(TAG, "Vehicle link ${s.lastLink} -> ${merged.link}")
            s.lastLink = merged.link
        }
        try { s.listener(merged) } catch (e: Exception) { Log.e(TAG, "Vehicle snapshot listener failed", e) }
    }

    private fun buildSnapshot(s: Session, r: Round, delayed: Boolean): VehicleSnapshot {
        val previous = s.last?.fieldStatus ?: emptyMap()
        val status = Fields.ALL.associateWith { field -> r.status[field] ?: previous[field] ?: FieldStatus.NOT_READ }
        val link = when {
            delayed -> LinkState.DELAYED
            s.vehicle == null -> initLink(s)
            else -> linkFor(r)
        }
        val diagnostics = LinkedHashMap<String, String>()
        when (link) {
            LinkState.DELAYED -> diagnostics[KEY_VEHICLE] = "응답이 늦어요 · 마지막 기록을 보여드려요"
            LinkState.SERVICE_NOT_READY -> diagnostics[KEY_VEHICLE] = NOTE_RECONNECTING
            LinkState.PERMISSION_DENIED -> diagnostics[KEY_VEHICLE] = "차량 정보 권한을 확인해 주세요"
            LinkState.UNSUPPORTED -> diagnostics[KEY_VEHICLE] = "이 차량에서는 상태 조회를 지원하지 않아요"
            LinkState.FAILED -> diagnostics[KEY_VEHICLE] = "잠시 후 다시 읽어 볼게요 · 마지막 기록을 보여드려요"
            else -> Unit
        }
        r.locationNote?.let { diagnostics[KEY_LOCATION] = it }
        val loc = r.location
        return VehicleSnapshot(
            mode = DataMode.PLEOS,
            capturedAt = System.currentTimeMillis(),
            battery = r.battery,
            fuel = r.fuel,
            odometer = r.odometer,
            trip = r.trip,
            rangeKm = r.range,
            unlockedDoors = r.unlockedDoors,
            doorAreas = r.doorAreas,
            openWindows = r.openWindows,
            windowAreas = r.windowAreas,
            washerWarning = r.washer,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            locationTime = loc?.timestampMillis,
            confidence = loc?.confidence?.takeIf { it.isFinite() },
            link = link,
            fieldTimes = r.times.toMap(),
            fieldStatus = status,
            diagnostics = diagnostics
        )
    }

    private fun initLink(s: Session) =
        if (s.initFault == Fault.PERMISSION) LinkState.PERMISSION_DENIED else LinkState.SERVICE_NOT_READY

    /** Link state from the vehicle fields of a finished round (location has its own service and does not count). */
    private fun linkFor(r: Round): LinkState {
        val statuses = VEHICLE_FIELDS.map { r.status[it] ?: FieldStatus.NOT_READ }
        if (statuses.any { it == FieldStatus.OK || it == FieldStatus.PARTIAL }) return LinkState.CONNECTED
        val faults = VEHICLE_FIELDS.mapNotNull { r.faults[it] }
        return when {
            Fault.PERMISSION in faults -> LinkState.PERMISSION_DENIED
            Fault.NOT_READY in faults -> LinkState.SERVICE_NOT_READY
            statuses.all { it == FieldStatus.UNSUPPORTED } -> LinkState.UNSUPPORTED
            // Service answered, just without values (e.g. EMPTY_RESULT) or with unsupported/no-value mixes.
            statuses.all { it == FieldStatus.NO_VALUE || it == FieldStatus.UNSUPPORTED } -> LinkState.CONNECTED
            else -> LinkState.FAILED
        }
    }

    // ---- request plumbing ---------------------------------------------------------------------------------------

    /**
     * Wraps one SDK request: counts it as pending, makes sure only its first callback counts, posts that callback
     * to the main thread, drops it when the round is no longer current, and never lets an exception escape.
     */
    private fun request(
        s: Session, r: Round, field: String,
        onError: (Exception?) -> Unit = { e -> markFault(r, field, classify(e)) },
        issue: (complete: (() -> Unit) -> Unit, fail: (Exception?) -> Unit) -> Unit
    ) {
        r.pending++
        val fired = AtomicBoolean(false)
        // Runs [block] for the live round on the main thread; always balances the pending count exactly once.
        val runNow: (() -> Unit) -> Unit = { block ->
            try {
                if (live(s, r)) block()
            } catch (e: Exception) {
                Log.w(TAG, "$field result handling failed", e)
                if (live(s, r)) markFault(r, field, Fault.FAILED)
            } finally {
                done(s, r)
            }
        }
        val complete: (() -> Unit) -> Unit = { block ->
            if (fired.compareAndSet(false, true)) main.post { runNow(block) }
        }
        val fail: (Exception?) -> Unit = { e ->
            Log.w(TAG, "$field read failed", e)
            complete { onError(e) }
        }
        try {
            issue(complete, fail)
        } catch (e: Exception) {
            // Synchronous throw: we are already on the main thread.
            Log.w(TAG, "$field request threw", e)
            if (fired.compareAndSet(false, true)) runNow { onError(e) }
        }
    }

    private fun markFault(r: Round, field: String, fault: Fault) {
        r.faults[field] = fault
        r.status[field] = statusFor(fault)
    }

    private fun accept(r: Round, field: String) {
        r.status[field] = FieldStatus.OK
        r.faults.remove(field)
        // Get APIs do not expose sensor acquisition time: this is the app's reception time.
        r.times[field] = System.currentTimeMillis()
    }

    /** capability check (if any) -> getter -> validate/store. */
    private fun <T : Any> readValue(
        s: Session, r: Round, field: String,
        capability: ((ok: (CapabilityConfig?) -> Unit, err: (Exception?) -> Unit) -> Unit)?,
        getter: (ok: (T?) -> Unit, err: (Exception?) -> Unit) -> Unit,
        store: (T) -> Boolean
    ) {
        val get: () -> Unit = {
            request(s, r, field) { complete, fail ->
                getter({ value: T? ->
                    complete {
                        if (value != null && store(value)) {
                            accept(r, field)
                        } else {
                            r.status[field] = FieldStatus.NO_VALUE
                        }
                    }
                }, fail)
            }
        }
        if (capability == null) {
            get()
            return
        }
        request(s, r, field) { complete, fail ->
            capability({ config: CapabilityConfig? ->
                complete {
                    if (readable(config)) get() else markFault(r, field, Fault.UNSUPPORTED)
                }
            }, fail)
        }
    }

    private fun readDoors(s: Session, r: Round, vehicle: Vehicle) {
        val field = Fields.DOORS
        request(s, r, field) { complete, fail ->
            vehicle.getDoor().checkDoorLockCapabilities({ config: CapabilityConfig?, areas: List<DoorArea>? ->
                complete {
                    val list = areas.orEmpty()
                    if (!readable(config) || list.isEmpty()) {
                        markFault(r, field, Fault.UNSUPPORTED)
                    } else {
                        val group = AreaGroup(list.size)
                        for (area in list) {
                            readArea<Boolean>(s, r, field, group, { ok, err -> vehicle.getDoor().getDoorLockState(area, ok, err) }) { locked: Boolean ->
                                !locked // a hit = unlocked door
                            }
                        }
                    }
                }
            }, fail)
        }
    }

    private fun readWindows(s: Session, r: Round, vehicle: Vehicle) {
        val field = Fields.WINDOWS
        request(s, r, field) { complete, fail ->
            vehicle.getWindow().checkWindowPositionCapabilities({ config: CapabilityConfig?, areas: Map<WindowArea, AreaBounds<*>>? ->
                complete {
                    val map = areas.orEmpty()
                    if (!readable(config) || map.isEmpty()) {
                        markFault(r, field, Fault.UNSUPPORTED)
                    } else {
                        val group = AreaGroup(map.size)
                        for ((area, bounds) in map) {
                            val closed = closedPosition(bounds)
                            readArea<Int>(s, r, field, group, { ok, err -> vehicle.getWindow().getCurrentWindowPosition(area, ok, err) }) { position: Int ->
                                position != closed // a hit = open window
                            }
                        }
                    }
                }
            }, fail)
        }
    }

    /**
     * One per-area read. When the last area of [group] has answered, the count is published only if *every* area
     * answered (a partial answer could hide an unlocked door or an open window -> PARTIAL, previous value kept).
     */
    private fun <T : Any> readArea(
        s: Session, r: Round, field: String, group: AreaGroup,
        getter: (ok: (T?) -> Unit, err: (Exception?) -> Unit) -> Unit,
        isHit: (T) -> Boolean
    ) {
        val finishOne: () -> Unit = {
            group.left--
            if (group.left == 0) concludeAreas(r, field, group)
        }
        val onError: (Exception?) -> Unit = { e ->
            if (group.fault == null) group.fault = classify(e)
            finishOne()
        }
        request(s, r, field, onError) { complete, fail ->
            getter({ value: T? ->
                complete {
                    if (value != null) {
                        group.answered++
                        if (isHit(value)) group.hits++
                    }
                    finishOne()
                }
            }, fail)
        }
    }

    private fun concludeAreas(r: Round, field: String, group: AreaGroup) {
        when {
            group.answered == group.total -> {
                if (field == Fields.DOORS) { r.unlockedDoors = group.hits; r.doorAreas = group.total }
                else { r.openWindows = group.hits; r.windowAreas = group.total }
                accept(r, field)
            }
            group.answered > 0 -> {
                r.status[field] = FieldStatus.PARTIAL
                r.faults.remove(field)
            }
            group.fault != null -> markFault(r, field, group.fault ?: Fault.FAILED)
            else -> { r.status[field] = FieldStatus.NO_VALUE }
        }
    }

    private fun readLocation(s: Session, r: Round) {
        val field = Fields.LOCATION
        val fused = s.fused
        if (fused == null) {
            if (s.fusedUnsupported) {
                markFault(r, field, Fault.UNSUPPORTED)
                r.locationNote = "위치 미지원 · 층·구역을 직접 입력해 주세요"
            } else {
                r.status[field] = FieldStatus.FAILED
                r.locationNote = "위치를 준비하고 있어요 · 직접 입력할 수 있어요"
            }
            return
        }
        val onError: (Exception?) -> Unit = { e ->
            val fault = classify(e)
            markFault(r, field, fault)
            r.locationNote = if (fault == Fault.PERMISSION) "위치 권한을 확인해 주세요 · 직접 입력할 수 있어요"
            else "최근 위치가 없어요 · 직접 입력해 주세요"
        }
        request(s, r, field, onError) { complete, fail ->
            fused.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    complete {
                        val now = System.currentTimeMillis()
                        if (location != null &&
                            LocationRules.usable(location.latitude, location.longitude, location.timestampMillis, now)
                        ) {
                            r.location = location
                            accept(r, field)
                            r.locationNote = "최근 위치를 받았어요"
                        } else {
                            r.status[field] = FieldStatus.NO_VALUE
                            r.locationNote = "최근 위치가 없어요 · 직접 입력해 주세요"
                        }
                    }
                }

                override fun onError(exception: Exception) {
                    fail(exception)
                }
            })
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private fun readable(config: CapabilityConfig?): Boolean {
        val access = config?.accessType ?: return false
        return access == AccessType.READ || access == AccessType.READ_WRITE
    }

    private fun validDistance(value: Float) = value.isFinite() && value >= 0f

    /**
     * Closed position of a window. When the range spans 0 (e.g. vent/tilt below 0, open above 0), 0 is closed;
     * otherwise the range minimum is the closed end.
     */
    private fun closedPosition(bounds: AreaBounds<*>?): Int {
        val min = (bounds?.minValue as? Number)?.toInt() ?: return 0
        val max = (bounds.maxValue as? Number)?.toInt() ?: return 0
        return if (min <= 0 && max >= 0) 0 else min
    }

    private fun statusFor(fault: Fault) = when (fault) {
        Fault.PERMISSION -> FieldStatus.PERMISSION_DENIED
        Fault.UNSUPPORTED -> FieldStatus.UNSUPPORTED
        Fault.EMPTY -> FieldStatus.NO_VALUE
        Fault.NOT_READY, Fault.FAILED -> FieldStatus.FAILED
    }

    /** Maps a raw SDK error to a short category. The raw text only goes to Logcat. */
    private fun classify(e: Throwable?): Fault {
        if (e == null) return Fault.FAILED
        if (e is SecurityException) return Fault.PERMISSION
        if (e is UnsupportedOperationException) return Fault.UNSUPPORTED
        // SDK error codes: FusedLocation's LocationException exposes getErrorCode() (its class is runtime-only, so it is
        // read reflectively); the Vehicle SDK exception is obfuscated and carries the code name in its message.
        val code = try {
            e.javaClass.getMethod("getErrorCode").invoke(e)?.toString().orEmpty()
        } catch (ignored: Exception) {
            ""
        }
        val text = "${e.javaClass.simpleName} $code ${e.message.orEmpty()}".uppercase()
        return when {
            "PERMISSION" in text -> Fault.PERMISSION
            "NOT_SUPPORTED" in text -> Fault.UNSUPPORTED
            "SERVICE_CONNECTION" in text || "NOT CONNECTED" in text -> Fault.NOT_READY
            "EMPTY_RESULT" in text -> Fault.EMPTY
            e is IllegalStateException -> Fault.NOT_READY
            else -> Fault.FAILED
        }
    }

    companion object {
        private const val TAG = "CarLogVehicle"
        private const val ROUND_TIMEOUT_MS = 10_000L
        private const val RELEASE_GRACE_MS = 3_000L
        private const val FIRST_ROUND_DELAY_MS = 750L
        private const val KEY_VEHICLE = "차량"
        private const val KEY_LOCATION = "위치"
        private const val NOTE_RECONNECTING = "차량 서비스를 다시 연결하고 있어요 · 마지막 기록을 보여드려요"
        private val VEHICLE_FIELDS = Fields.ALL - Fields.LOCATION
    }
}
