package cz.strihavka.pmi80.core

/** Presentation timing inspired by the supplied SN 080 recordings, not an electrical model.
 * While the app is in the background the presentation clock is paused. */
class PowerSupply {
    enum class Phase { CLOSED, OFF, STARTING, ON, STOPPING }
    var phase = Phase.CLOSED
        private set
    private var elapsed = 0L
    val cpuPowered get() = phase == Phase.ON
    val mains get() = phase == Phase.STARTING || phase == Phase.ON
    val busy get() = phase == Phase.STARTING || phase == Phase.STOPPING
    /** Bit 0: +5 V; bit 1: -5 V; bit 2: +12 V. */
    val rails: Int get() = when(phase) {
        Phase.STARTING -> (if(elapsed>=120) 1 else 0) or (if(elapsed>=300) 2 else 0) or (if(elapsed>=520) 4 else 0)
        Phase.ON -> 7
        Phase.STOPPING -> (if(elapsed<4500) 1 else 0) or (if(elapsed<2200) 2 else 0) or (if(elapsed<800) 4 else 0)
        else -> 0
    }
    fun openCase() { if(phase==Phase.CLOSED) phase=Phase.OFF }
    fun closeCase() { if(phase==Phase.OFF) phase=Phase.CLOSED }
    fun toggle() {
        when(phase) {
            Phase.OFF -> phase=Phase.STARTING
            Phase.ON -> phase=Phase.STOPPING
            else -> return
        }
        elapsed=0
    }
    fun advance(milliseconds: Long) {
        require(milliseconds>=0)
        if(!busy) return
        elapsed=(elapsed+milliseconds.coerceAtMost(10_000)).coerceAtMost(10_000)
        if(phase==Phase.STARTING && elapsed>=900) phase=Phase.ON
        if(phase==Phase.STOPPING && elapsed>=4500) phase=Phase.OFF
    }
}
