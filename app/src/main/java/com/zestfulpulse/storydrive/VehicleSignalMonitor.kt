package com.zestfulpulse.storydrive

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log

/** 이야기에 쓰는 차량 신호 종류 (작업지시서 4장) */
enum class Signal(val label: String) {
    BRAKE("브레이크 페달"),
    GEAR("기어(주차/주행)"),
    TURN_SIGNAL("방향지시등"),
    WIPER("와이퍼"),
    SEAT_HEAT("시트 열선"),
    DOOR("도어"),
    BELT_WARNING("안전벨트 경고"),
    BELT_BUCKLED("안전벨트 체결"),
}

/**
 * Pleos Vehicle SDK로 신호마다 capability를 확인하고, 지원되는 신호만 상태 변화를 구독한다.
 * 함수 이름은 공식 샘플(pleos-sample-vehicle-app)과 Vehicle SDK 2.3.2 AAR에서 확인한 것만 사용한다.
 * 콜백은 SDK 스레드에서 올 수 있으므로, 화면 갱신은 호출하는 쪽에서 UI 스레드로 넘긴다.
 */
class VehicleSignalMonitor(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        /** supported=false면 화면에 "이 차량에서는 사용 안 함"으로 표시 */
        fun onSupport(signal: Signal, supported: Boolean, detail: String)
        fun onSignal(signal: Signal, area: String?, value: String)
    }

    private val vehicle = Vehicle(context.applicationContext)
    private val unregisterActions = mutableListOf<() -> Unit>()
    private var started = false

    fun start() {
        if (started) return
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Signal.entries.forEach { unsupported(it, "Vehicle 초기화 실패: ${e.message}") }
            return
        }
        started = true
        Log.i(MainActivity.TAG, "Vehicle SDK 초기화 성공")
        watchBrake()
        watchGear()
        watchTurnSignal()
        watchWiper()
        watchSeatHeat()
        watchDoors()
        watchBeltWarning()
        watchBeltBuckled()
    }

    fun stop() {
        if (!started) return
        unregisterActions.forEach { runCatching(it) }
        unregisterActions.clear()
        runCatching { vehicle.release() }
        started = false
    }

    // --- 신호별 구독 -------------------------------------------------------

    private fun watchBrake() {
        val brake = vehicle.getBrake()
        brake.checkBrakePedalStatusCapabilities(
            { config ->
                if (!config.readable()) return@checkBrakePedalStatusCapabilities unsupported(Signal.BRAKE, config.describe())
                val l = object : BrakePedalStatusListener {
                    override fun onBrakePedalStatusUpdated(status: Boolean?) =
                        signal(Signal.BRAKE, null, if (status == true) "밟음" else "뗌")
                    override fun onFailed(e: Exception) = failed(Signal.BRAKE, null, e)
                }
                subscribe(Signal.BRAKE, config.describe()) {
                    brake.registerBrakePedalStatus(l)
                    unregisterActions += { brake.unregisterBrakePedalStatus(l) }
                }
            },
            { unsupported(Signal.BRAKE, it.message) },
        )
    }

    /** Gear에는 capability 확인 함수가 없어 바로 구독한다. */
    private fun watchGear() {
        val gear = vehicle.getGear()
        val l = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) = signal(Signal.GEAR, null, gear?.name ?: "null")
            override fun onFailed(e: Exception) = failed(Signal.GEAR, null, e)
        }
        subscribe(Signal.GEAR, "capability 확인 API 없음") {
            gear.registerGearSelection(l)
            unregisterActions += { gear.unregisterGearSelection(l) }
        }
    }

    private fun watchTurnSignal() {
        val turn = vehicle.getTurnSignal()
        turn.checkTurnSignalStateCapabilities(
            { config ->
                if (!config.readable()) return@checkTurnSignalStateCapabilities unsupported(Signal.TURN_SIGNAL, config.describe())
                val l = object : TurnSignalStateListener {
                    override fun onTurnSignalStateUpdated(state: TurnSignalValue?) =
                        signal(Signal.TURN_SIGNAL, null, state?.name ?: "null")
                    override fun onFailed(e: Exception) = failed(Signal.TURN_SIGNAL, null, e)
                }
                subscribe(Signal.TURN_SIGNAL, config.describe()) {
                    turn.registerTurnSignalState(l)
                    unregisterActions += { turn.unregisterTurnSignalState(l) }
                }
            },
            { unsupported(Signal.TURN_SIGNAL, it.message) },
        )
    }

    private fun watchWiper() {
        val wiper = vehicle.getWiper()
        wiper.checkWiperStateCapabilities(
            { config: CapabilityConfig, areas: Map<WindowArea, List<WiperState>> ->
                if (!config.readable() || areas.isEmpty()) {
                    return@checkWiperStateCapabilities unsupported(Signal.WIPER, "${config.describe()} areas=${areas.keys.map { it.label() }}")
                }
                subscribe(Signal.WIPER, "${config.describe()} areas=${areas.keys.map { it.label() }}") {
                    areas.keys.forEach { area ->
                        val l = object : WiperStateListener {
                            override fun onWiperStateUpdated(state: WiperState?) =
                                signal(Signal.WIPER, area.label(), state?.name ?: "null")
                            override fun onFailed(e: Exception) = failed(Signal.WIPER, area.label(), e)
                        }
                        wiper.registerWiperState(area, l)
                        unregisterActions += { wiper.unregisterWiperState(area, l) }
                    }
                }
            },
            { unsupported(Signal.WIPER, it.message) },
        )
    }

    /**
     * 시트 열선 단계. 지원되는 모든 좌석을 구독하고 로그에 좌석 위치(뒷좌석/앞좌석)를 남긴다.
     * 제품 기준은 뒷좌석(2열)이다. 에뮬레이터 콘솔은 앞좌석 열선만 바꿀 수 있어서, 시연용으로 앞좌석도 받는다.
     * WARM 사건 판단(뒷좌석 우선, 앞좌석도 인정)은 M3에서 [isRearSeat]로 구분해 처리한다.
     */
    private fun watchSeatHeat() {
        val seat = vehicle.getSeat()
        seat.checkSeatTemperatureCapabilities(
            { config: CapabilityConfig, bounds ->
                val areas = bounds.keys.sortedByDescending { isRearSeat(it.label()) }
                val detail = "${config.describe()} 지원 좌석=${areas.map { it.label() }}"
                if (!config.readable() || areas.isEmpty()) {
                    return@checkSeatTemperatureCapabilities unsupported(Signal.SEAT_HEAT, detail)
                }
                subscribe(Signal.SEAT_HEAT, detail) {
                    areas.forEach { area ->
                        val where = "${if (isRearSeat(area.label())) "뒷좌석" else "앞좌석"} ${area.label()}"
                        val l = object : HvacSeatTemperatureListener {
                            override fun onHvacSeatTemperatureUpdated(level: Int?) =
                                signal(Signal.SEAT_HEAT, where, "단계 $level")
                            override fun onFailed(e: Exception) = failed(Signal.SEAT_HEAT, where, e)
                        }
                        seat.registerHvacSeatTemperature(area, l)
                        unregisterActions += { seat.unregisterHvacSeatTemperature(area, l) }
                    }
                }
            },
            { unsupported(Signal.SEAT_HEAT, it.message) },
        )
    }

    private fun watchDoors() {
        val door = vehicle.getDoor()
        door.checkDoorPositionCapabilities(
            { config: CapabilityConfig, bounds ->
                val detail = "${config.describe()} areas=${bounds.keys.map { it.label() }}"
                if (!config.readable() || bounds.isEmpty()) {
                    return@checkDoorPositionCapabilities unsupported(Signal.DOOR, detail)
                }
                subscribe(Signal.DOOR, detail) {
                    bounds.keys.forEach { area ->
                        val l = object : DoorPositionListener {
                            override fun onPositionUpdated(pos: Int?) =
                                signal(Signal.DOOR, area.label(), "위치 $pos")
                            override fun onFailed(e: Exception) = failed(Signal.DOOR, area.label(), e)
                        }
                        door.registerDoorPosition(area, l)
                        unregisterActions += { door.unregisterDoorPosition(area, l) }
                    }
                }
            },
            { unsupported(Signal.DOOR, it.message) },
        )
    }

    private fun watchBeltWarning() {
        val safety = vehicle.getSafety()
        safety.checkSeatNotBuckledWarningCapabilities(
            { config: CapabilityConfig, areas: List<SeatArea> ->
                val detail = "${config.describe()} areas=${areas.map { it.label() }}"
                if (!config.readable() || areas.isEmpty()) {
                    return@checkSeatNotBuckledWarningCapabilities unsupported(Signal.BELT_WARNING, detail)
                }
                subscribe(Signal.BELT_WARNING, detail) {
                    areas.forEach { area ->
                        val l = object : SeatBeltNotBuckledWarningListener {
                            override fun onSeatBeltNotBuckledWarningUpdated(warning: Boolean?) =
                                signal(Signal.BELT_WARNING, area.label(), if (warning == true) "경고 켜짐" else "경고 꺼짐")
                            override fun onFailed(e: Exception) = failed(Signal.BELT_WARNING, area.label(), e)
                        }
                        safety.registerSeatBeltNotBuckledWarning(area, l)
                        unregisterActions += { safety.unregisterSeatBeltNotBuckledWarning(area, l) }
                    }
                }
            },
            { unsupported(Signal.BELT_WARNING, it.message) },
        )
    }

    private fun watchBeltBuckled() {
        val seat = vehicle.getSeat()
        seat.checkSeatBeltBuckledCapabilities(
            { config: CapabilityConfig, areas: List<SeatArea> ->
                val detail = "${config.describe()} areas=${areas.map { it.label() }}"
                if (!config.readable() || areas.isEmpty()) {
                    return@checkSeatBeltBuckledCapabilities unsupported(Signal.BELT_BUCKLED, detail)
                }
                subscribe(Signal.BELT_BUCKLED, detail) {
                    areas.forEach { area ->
                        val l = object : SeatBeltBuckledStateListener {
                            override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) =
                                signal(Signal.BELT_BUCKLED, area.label(), if (buckled == true) "체결" else "풀림")
                            override fun onFailed(e: Exception) = failed(Signal.BELT_BUCKLED, area.label(), e)
                        }
                        seat.registerSeatBeltBuckledState(area, l)
                        unregisterActions += { seat.unregisterSeatBeltBuckledState(area, l) }
                    }
                }
            },
            { unsupported(Signal.BELT_BUCKLED, it.message) },
        )
    }

    // --- 공통 -------------------------------------------------------------

    private inline fun subscribe(signal: Signal, detail: String, block: () -> Unit) {
        try {
            block()
            Log.i(MainActivity.TAG, "[지원] ${signal.label} 구독 시작 ($detail)")
            listener.onSupport(signal, true, detail)
        } catch (e: Exception) {
            unsupported(signal, "구독 실패: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun unsupported(signal: Signal, detail: String?) {
        Log.w(MainActivity.TAG, "[사용 안 함] ${signal.label} ($detail)")
        listener.onSupport(signal, false, detail ?: "")
    }

    private fun failed(signal: Signal, area: String?, e: Exception) {
        Log.e(MainActivity.TAG, "[오류] ${signal.label}${area?.let { " $it" } ?: ""}: ${e.javaClass.simpleName} ${e.message}")
    }

    private fun signal(signal: Signal, area: String?, value: String) {
        Log.i(MainActivity.TAG, "[신호] ${signal.label}${area?.let { " $it" } ?: ""} = $value")
        listener.onSignal(signal, area, value)
    }

    private fun CapabilityConfig.readable() =
        accessType == AccessType.READ || accessType == AccessType.READ_WRITE

    private fun CapabilityConfig.describe() = "access=${accessType.name}"

    private fun SeatArea.label() = values.joinToString("+") { it.name }
    private fun DoorArea.label() = values.joinToString("+") { it.name }
    private fun WindowArea.label() = values.joinToString("+") { it.name }

    companion object {
        /** 좌석 라벨(예: "ROW_2_LEFT")이 뒷좌석(2열 이후)이면 true. 로그의 위치 표시와 M3 WARM 판단에 쓴다. */
        fun isRearSeat(areaLabel: String): Boolean =
            areaLabel.split("+").any { it.startsWith("ROW_2") || it.startsWith("ROW_3") }
    }
}
