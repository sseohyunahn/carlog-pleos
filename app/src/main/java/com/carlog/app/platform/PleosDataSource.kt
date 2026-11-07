package com.carlog.app.platform

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import com.carlog.app.domain.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Read-only, typed public SDK calls. No vendor/private APIs or vehicle control writes. */
class PleosDataSource(private val context: Context) {
    suspend fun capture(): VehicleSnapshot = coroutineScope {
        val notes = ConcurrentHashMap<String, String>()
        val vehicle = Vehicle(context.applicationContext)
        try {
            vehicle.initialize()
            val battery = async { read<Int>("배터리", notes,
                { ok, fail -> vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(ok, fail) },
                { ok, fail -> vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, fail) })?.takeIf { it in 0..100 } }
            val fuel = async { read<Int>("연료", notes,
                { ok, fail -> vehicle.getFuel().checkFuelPercentCapabilities(ok, fail) },
                { ok, fail -> vehicle.getFuel().getFuelPercent(ok, fail) })?.takeIf { it in 0..100 } }
            val distance = async { read<Float>("주행거리", notes,
                { ok, fail -> vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(ok, fail) },
                { ok, fail -> vehicle.getOdometer().getCurrentDistanceDriven(ok, fail) })?.takeIf { it.isFinite() && it >= 0f } }
            val washer = async { read<Boolean>("워셔액", notes,
                { ok, fail -> vehicle.getSafety().checkLowWasherFluidWarningStateCapabilities(ok, fail) },
                { ok, fail -> vehicle.getSafety().hasLowWasherFluidWarning(ok, fail) }) }
            val location = async { location(notes) }
            val loc = location.await()
            VehicleSnapshot(DataMode.PLEOS, System.currentTimeMillis(), battery.await(), fuel.await(), distance.await(), washer.await(),
                loc?.latitude, loc?.longitude, loc?.timestampMillis, loc?.confidence, notes.toMap())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            VehicleSnapshot(DataMode.PLEOS, System.currentTimeMillis(), diagnostics=mapOf("차량 연결" to (e.message ?: "연결 불가")))
        } finally { runCatching { vehicle.release() } }
    }
    private suspend fun <T> read(
        name: String, notes: MutableMap<String, String>,
        capability: ((CapabilityConfig) -> Unit, (Exception) -> Unit) -> Unit,
        getter: ((T?) -> Unit, (Exception) -> Unit) -> Unit
    ): T? {
        return try {
            val config = withTimeoutOrNull(3500) { callback<CapabilityConfig> { ok, fail -> capability({ ok(it) }, fail) } }
            if (config?.accessType !in listOf(AccessType.READ, AccessType.READ_WRITE)) {
                notes[name] = "미지원 또는 연결·권한 확인 필요"; null
            } else {
                val value = withTimeoutOrNull(3500) { callback(getter) }
                notes[name] = if (value == null) "현재 조회값 없음" else "조회됨"
                value
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { notes[name] = e.message ?: "조회 실패"; null }
    }
    private suspend fun location(notes: MutableMap<String, String>): LocationData? {
        val fused = FusedLocation(context.applicationContext)
        var started = false
        return try {
            if (!fused.isSupported()) { notes["위치"] = "미지원 · 층/구역 직접 입력"; return null }
            fused.initialize(); started = true
            val value = withTimeoutOrNull(3500) {
                suspendCancellableCoroutine { cont ->
                    fused.getLastKnownLocation(object : FusedLocationListener {
                        override fun onChanged(location: LocationData?) { if(cont.isActive) cont.resume(location) }
                        override fun onError(exception: Exception) {
                            notes["위치"] = exception.message ?: "조회 실패"
                            if(cont.isActive) cont.resume(null)
                        }
                    })
                }
            }
            notes["위치"] = if(value == null) "위치 없음 · 직접 입력 가능" else "마지막 위치 · 시각 확인 필요"
            value
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { notes["위치"] = e.message ?: "조회 실패"; null }
        finally { if(started) runCatching { fused.release() } }
    }
    private suspend fun <T> callback(request: ((T?) -> Unit, (Exception) -> Unit) -> Unit): T? =
        suspendCancellableCoroutine { cont -> request(
            { if(cont.isActive) cont.resume(it) },
            { if(cont.isActive) cont.resume(null) }
        ) }
}
