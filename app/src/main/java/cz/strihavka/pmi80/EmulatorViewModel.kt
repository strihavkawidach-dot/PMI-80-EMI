package cz.strihavka.pmi80

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.strihavka.pmi80.core.Pmi80
import cz.strihavka.pmi80.core.PowerSupply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScreenState(
    val digits: List<Int> = List(9) { 0 }, val registers: String = "",
    val running: Boolean = false, val message: String = "Otevři krabičku a zapni zdroj",
    val power: PowerSupply.Phase = PowerSupply.Phase.CLOSED, val mains: Boolean = false, val rails: Int = 0
)

class EmulatorViewModel(app: Application) : AndroidViewModel(app) {
    private val machine=Pmi80()
    private val supply=PowerSupply()
    private val lock=Any()
    private val mutable=MutableStateFlow(ScreenState())
    val state=mutable.asStateFlow()
    private var running=false
    @Volatile private var active=false
    private var message="Otevři krabičku a zapni zdroj"
    init {
        val saved=app.getFileStreamPath("ram.bin")
        if(saved.exists()) runCatching { machine.loadRam(saved.readBytes()) }
        viewModelScope.launch(Dispatchers.Default) {
            var lastTick=SystemClock.elapsedRealtime()
            while(isActive) {
                val now=SystemClock.elapsedRealtime()
                val elapsed=(now-lastTick).coerceIn(0,100)
                lastTick=now
                if(active) synchronized(lock) {
                    val powered=supply.cpuPowered
                    supply.advance(elapsed)
                    if(!powered && supply.cpuPowered) {
                        machine.reset(); running=true; message="Zdroj zapnutý • původní monitor PMI-80"
                    }
                    if(supply.phase==PowerSupply.Phase.OFF && message.startsWith("Vypínám")) message="Zdroj vypnutý"
                    try { if(supply.cpuPowered && running) machine.runCycles(Pmi80.CLOCK_HZ/60) }
                    catch(e: IllegalArgumentException) { running=false; message=e.message ?: "Chyba emulace" }
                    publish()
                }
                delay(16)
            }
        }
    }
    private fun publish() {
        val c=machine.cpu
        mutable.value=ScreenState(
            if(supply.cpuPowered) machine.digits() else List(9) { 0 },
            "PC %04X  SP %04X\nAF %02X%02X  BC %02X%02X\nDE %02X%02X  HL %04X  %s".format(c.pc,c.sp,c.a,c.flags,c.b,c.c,c.d,c.e,c.hl,if(c.halted) "HLT" else ""),
            running,message,supply.phase,supply.mains,supply.rails)
    }
    fun foreground(value: Boolean) {
        active=value
        if(!value) synchronized(lock) {
            machine.releaseKeys()
            runCatching { getApplication<Application>().openFileOutput("ram.bin",0).use { it.write(machine.ramBytes()) } }
        }
    }
    fun openCase() = synchronized(lock) { supply.openCase(); message="Zapni síťový napáječ SN 080"; publish() }
    fun closeCase() = synchronized(lock) { supply.closeCase(); publish() }
    fun power() = synchronized(lock) {
        if(!supply.busy) {
            machine.releaseKeys(); running=false; supply.toggle()
            message=if(supply.mains) "Zapínám zdroj…" else "Vypínám zdroj • vybíjení napětí…"
            publish()
        }
    }
    fun key(label: String, down: Boolean) = synchronized(lock) {
        if(supply.cpuPowered) machine.key(label,down)
        publish()
    }
    fun toggle() = synchronized(lock) { if(supply.cpuPowered) running=!running; machine.releaseKeys(); publish() }
    fun step() = synchronized(lock) {
        if(supply.cpuPowered) { running=false; runCatching { machine.cpu.step() }.onFailure { message=it.message ?: "Chyba" } }
        publish()
    }
    fun reset() = synchronized(lock) {
        if(supply.cpuPowered) { machine.reset(); running=true; message="Monitor resetován, RAM zachována" }
        publish()
    }
    fun importFile(bytes: ByteArray, rom: Boolean) = synchronized(lock) {
        runCatching {
            if(rom) machine.loadRom(bytes) else { machine.reset(); machine.loadProgram(bytes) }
            running=supply.cpuPowered
            message=if(rom) "Vlastní ROM načtena" else "BIN načten od 1C00; spusť EX 1C00 ="
        }.onFailure { message=it.message ?: "Chybný soubor" }
        publish()
    }
    fun exportRam() = synchronized(lock) { machine.ramBytes() }
    fun notice(text: String) = synchronized(lock) { message=text; publish() }
}
