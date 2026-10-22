package cz.strihavka.pmi80

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.strihavka.pmi80.core.Pmi80
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScreenState(val digits: List<Int> = List(9) { 0 }, val registers: String = "", val running: Boolean = true, val message: String = "Původní monitor • 1,111 MHz")

class EmulatorViewModel(app: Application) : AndroidViewModel(app) {
    private val machine=Pmi80()
    private val lock=Any()
    private val mutable=MutableStateFlow(ScreenState())
    val state=mutable.asStateFlow()
    private var running=true
    @Volatile private var active=false
    private var message="Původní monitor • 1,111 MHz"
    init {
        val saved=app.getFileStreamPath("ram.bin")
        if(saved.exists()) runCatching { machine.loadRam(saved.readBytes()) }
        viewModelScope.launch(Dispatchers.Default) {
            while(isActive) {
                if(active) synchronized(lock) {
                    try { if(running) machine.runCycles(Pmi80.CLOCK_HZ/60) }
                    catch(e: IllegalArgumentException) { running=false; message=e.message ?: "Chyba emulace" }
                    publish()
                }
                delay(16)
            }
        }
    }
    private fun publish() {
        val c=machine.cpu
        mutable.value=ScreenState(machine.digits(),"PC %04X  SP %04X\nAF %02X%02X  BC %02X%02X\nDE %02X%02X  HL %04X  %s".format(c.pc,c.sp,c.a,c.flags,c.b,c.c,c.d,c.e,c.hl,if(c.halted) "HLT" else ""),running,message)
    }
    fun foreground(value: Boolean) { active=value; if(!value) synchronized(lock) { machine.releaseKeys(); runCatching { getApplication<Application>().openFileOutput("ram.bin",0).use { it.write(machine.ramBytes()) } } } }
    fun key(label: String, down: Boolean) = synchronized(lock) { machine.key(label,down); publish() }
    fun toggle() = synchronized(lock) { running=!running; machine.releaseKeys(); publish() }
    fun step() = synchronized(lock) { running=false; runCatching { machine.cpu.step() }.onFailure { message=it.message ?: "Chyba" }; publish() }
    fun reset() = synchronized(lock) { machine.reset(); running=true; message="Monitor resetován, RAM zachována"; publish() }
    fun importFile(bytes: ByteArray, rom: Boolean) = synchronized(lock) {
        runCatching {
            if(rom) machine.loadRom(bytes) else { machine.reset(); machine.loadProgram(bytes) }
            running=true; message=if(rom) "Vlastní ROM načtena" else "BIN načten od 1C00; spusť EX 1C00 ="
        }.onFailure { message=it.message ?: "Chybný soubor" }
        publish()
    }
    fun exportRam() = synchronized(lock) { machine.ramBytes() }
    fun notice(text: String) = synchronized(lock) { message=text; publish() }
}
