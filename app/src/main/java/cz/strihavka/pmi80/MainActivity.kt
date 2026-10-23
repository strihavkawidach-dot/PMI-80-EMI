package cz.strihavka.pmi80

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.strihavka.pmi80.core.PowerSupply
import cz.strihavka.pmi80.core.Pmi80
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: EmulatorViewModel by viewModels()
    private var importingRom=false
    private val openFile=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) runCatching {
            val bytes=contentResolver.openInputStream(uri)?.use { stream ->
                val buffer=ByteArray(2049)
                var count=0
                while(count<buffer.size) {
                    val n=stream.read(buffer,count,buffer.size-count)
                    if(n<0) break
                    count+=n
                }
                buffer.copyOf(count) } ?: error("Soubor nelze otevřít")
            vm.importFile(bytes,importingRom)
        }.onFailure { vm.notice(it.message ?: "Chyba čtení souboru") }
    }
    private val saveFile=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null) runCatching { contentResolver.openOutputStream(uri)?.use { it.write(vm.exportRam()) } ?: error("Soubor nelze zapsat") }
            .onSuccess { vm.notice("RAM uložena (1C00–1FFF)") }.onFailure { vm.notice(it.message ?: "Chyba zápisu") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xffe9b449),secondary=Color(0xff8bbdd9),background=Color(0xff151719))) {
                val state by vm.state.collectAsStateWithLifecycle()
                EmulatorScreen(state,vm::key,vm::reset,vm::toggle,vm::step,
                    { importingRom=it; openFile.launch(arrayOf("*/*")) }, { saveFile.launch("pmi80-ram.bin") },vm::openCase,vm::power,vm::closeCase)
            }
        }
    }
    override fun onStart() { super.onStart(); vm.foreground(true) }
    override fun onStop() { vm.foreground(false); super.onStop() }
}

@Composable
private fun EmulatorScreen(state: ScreenState, key: (String,Boolean)->Unit, reset: ()->Unit, toggle: ()->Unit, step: ()->Unit, load: (Boolean)->Unit, save: ()->Unit, openCase: ()->Unit, power: ()->Unit, closeCase: ()->Unit) {
    var help by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf(false) }
    if(state.power==PowerSupply.Phase.CLOSED) { CaseWelcome(openCase); return }
    val powered=state.power==PowerSupply.Phase.ON
    Surface(modifier=Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max=720.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EMULÁTOR / 1982",color=MaterialTheme.colorScheme.primary,fontSize=12.sp,letterSpacing=3.sp)
                    Text("PMI–80",fontSize=34.sp,fontWeight=FontWeight.Bold)
                }
                TextButton(onClick={help=true}) { Text("Návod") }
            }
            SourcePanel(state,power)
            TextButton(onClick={original=true}) { Text("Vnitřní strana víka – originální tabulka") }
            PmiPanel(state.digits,key)
            if(state.power==PowerSupply.Phase.OFF) TextButton(onClick=closeCase) { Text("Zavřít krabičku") }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick=toggle,enabled=powered,modifier=Modifier.weight(1f)) { Text(if(state.running) "Pozastavit" else "Pokračovat") }
                OutlinedButton(onClick=step,enabled=powered) { Text("Krok") }
                OutlinedButton(onClick=reset,enabled=powered) { Text("Reset") }
            }
            Text(state.message,fontSize=13.sp,color=MaterialTheme.colorScheme.secondary)
            Surface(color=Color(0xff202427),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("PROCESOR",fontSize=11.sp,letterSpacing=2.sp,color=Color.Gray)
                    Text(state.registers,fontFamily=FontFamily.Monospace,fontSize=14.sp)
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick={load(false)},modifier=Modifier.weight(1f)) { Text("Načíst BIN",fontSize=12.sp) }
                OutlinedButton(onClick=save,modifier=Modifier.weight(1f)) { Text("Uložit RAM",fontSize=12.sp) }
            }
            TextButton(onClick={load(true)}) { Text("Načíst vlastní ROM (max. 2 KiB)") }
            Text("ROM 0000–07FF  /  RAM 1C00–1FFF\n8255: F8–FB a F4–F7",fontFamily=FontFamily.Monospace,fontSize=11.sp,color=Color.Gray)
        }
    }
    if(help) AlertDialog(onDismissRequest={help=false},confirmButton={TextButton(onClick={help=false}){Text("Rozumím")}},dismissButton={TextButton(onClick={help=false; original=true}) { Text("Originální tabulka") }},title={Text("Ovládání PMI-80")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Otevři krabičku a zapni přepínač SÍŤ na zdroji SN 080. Po rozsvícení napětí se spustí skutečný monitor. Zdroj můžeš opět vypnout a krabičku zavřít. Tabulku z víka zvětšíš dvěma prsty.")
            Text("Po zapnutí svítí PMI -80. Stiskni libovolnou klávesu pro vstup do monitoru.")
            Text("M: zadej adresu (např. 1C00), =, dva hexadecimální znaky dat a = pro další adresu. Samotné = prochází paměť.")
            Text("EX: zadej adresu programu a = pro spuštění. R pro registry (8=SP, 9=HL, A=AF, B=BC, D=DE). BR nastavuje breakpoint. RE resetuje CPU a zachovává RAM. I vyvolává RST 7, pokud jsou povolena přerušení.")
            Text("Načíst BIN vloží surové bajty od 1C00 (max. 1024 B). Uložit RAM exportuje všech 1024 B. RAM se uchovává mezi spuštěními aplikace.")
            Text("L a S jsou původní příkazy kazetového monitoru. Kazetový magnetofon a handshake režimy 8255 nejsou emulované; pro přenos použij BIN soubory.")
            Text("Panel a klávesnice vycházejí z fotografií původního PMI-80: zelená deska, červený kryt displeje a krémový štítek s modrými funkčními klávesami. Původní monitor: projekt maly/fpmi, licence MIT.")
        }
    })
    if(original) OriginalLidDialog { original=false }
}

@Composable
private fun PmiPanel(digits: List<Int>, key: (String,Boolean)->Unit) {
    PhotographicBoard(digits,key)
}
