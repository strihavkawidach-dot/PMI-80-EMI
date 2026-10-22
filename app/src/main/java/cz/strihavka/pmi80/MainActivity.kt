package cz.strihavka.pmi80

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
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
                    { importingRom=it; openFile.launch(arrayOf("*/*")) }, { saveFile.launch("pmi80-ram.bin") })
            }
        }
    }
    override fun onStart() { super.onStart(); vm.foreground(true) }
    override fun onStop() { vm.foreground(false); super.onStop() }
}

@Composable
private fun EmulatorScreen(state: ScreenState, key: (String,Boolean)->Unit, reset: ()->Unit, toggle: ()->Unit, step: ()->Unit, load: (Boolean)->Unit, save: ()->Unit) {
    var help by remember { mutableStateOf(false) }
    Surface(modifier=Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(18.dp).widthIn(max=720.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TESLA / 1982",color=MaterialTheme.colorScheme.primary,fontSize=12.sp,letterSpacing=3.sp)
                    Text("PMI–80",fontSize=34.sp,fontWeight=FontWeight.Bold)
                }
                TextButton(onClick={help=true}) { Text("Návod") }
            }
            Text("MIKROPOČÍTAČ • MHB8080A",fontSize=11.sp,letterSpacing=2.sp,color=Color.LightGray)
            Surface(color=Color(0xffc9a54b),shape=RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    LedDisplay(state.digits)
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Pmi80.keyRows.forEach { row ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                row.forEach { label -> Key(label,Modifier.weight(1f),key) }
                            }
                        }
                    }
                    Text("PMI 80     •     TESLA PIEŠŤANY",color=Color(0xff332b19),fontSize=10.sp,letterSpacing=2.sp)
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick=toggle,modifier=Modifier.weight(1f)) { Text(if(state.running) "Pozastavit" else "Pokračovat") }
                OutlinedButton(onClick=step) { Text("Krok") }
                OutlinedButton(onClick=reset) { Text("Reset") }
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
    if(help) AlertDialog(onDismissRequest={help=false},confirmButton={TextButton(onClick={help=false}){Text("Rozumím")}},title={Text("Ovládání PMI-80")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Po zapnutí svítí PMI -80. Stiskni libovolnou klávesu pro vstup do monitoru.")
            Text("M: zadej adresu (např. 1C00), =, dva hexadecimální znaky dat a = pro další adresu. Samotné = prochází paměť.")
            Text("EX: zadej adresu programu a = pro spuštění. R pro registry (8=SP, 9=HL, A=AF, B=BC, D=DE). BR nastavuje breakpoint. RE resetuje CPU a zachovává RAM. I vyvolává RST 7, pokud jsou povolena přerušení.")
            Text("Načíst BIN vloží surové bajty od 1C00 (max. 1024 B). Uložit RAM exportuje všech 1024 B. RAM se uchovává mezi spuštěními aplikace.")
            Text("L a S jsou původní příkazy kazetového monitoru. Kazetový magnetofon a handshake režimy 8255 nejsou emulované; pro přenos použij BIN soubory.")
            Text("Vzhled a rozložení inspirováno asm80.com/pmi80.html. Původní monitor: projekt maly/fpmi, licence MIT.")
        }
    })
}

@Composable
private fun Key(label: String, modifier: Modifier, key: (String,Boolean)->Unit) {
    val function=label in listOf("RE","I","EX","R","BR","M","L","S","=")
    var pressed by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    Surface(color=if(pressed) Color(0xff81929b) else if(function) Color(0xff245475) else Color(0xff242729),shape=RoundedCornerShape(6.dp),shadowElevation=if(pressed) 0.dp else 4.dp,
        modifier=modifier.heightIn(min=52.dp).semantics {
            contentDescription="Klávesa $label"
            onClick { scope.launch { key(label,true); delay(100); key(label,false) }; true }
        }.pointerInput(label) { detectTapGestures(onPress={
            pressed=true; key(label,true)
            try { tryAwaitRelease() } finally { pressed=false; key(label,false) }
        }) }) {
        Column(Modifier.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(label,color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)
            val sub=mapOf("8" to "SP","9" to "HL","A" to "AF","B" to "BC","D" to "DE")[label]
            if(sub!=null) Text(sub,fontSize=9.sp,color=Color(0xffa4b4bf))
        }
    }
}

@Composable
private fun LedDisplay(digits: List<Int>) {
    Row(Modifier.fillMaxWidth().background(Color(0xff140608),RoundedCornerShape(8.dp)).padding(horizontal=10.dp,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
        digits.forEachIndexed { index,mask ->
            Canvas(Modifier.weight(1f).aspectRatio(0.52f).semantics { contentDescription="Displej ${index+1}: segmenty %02X".format(mask) }) {
                val w=size.width; val h=size.height; val x=w*0.14f; val y=h*0.1f; val mid=h/2; val right=w*0.8f; val bottom=h*0.9f
                val segments=listOf(Offset(x,y) to Offset(right,y),Offset(right,y) to Offset(right,mid),Offset(right,mid) to Offset(right,bottom),Offset(x,bottom) to Offset(right,bottom),Offset(x,mid) to Offset(x,bottom),Offset(x,y) to Offset(x,mid),Offset(x,mid) to Offset(right,mid))
                segments.forEachIndexed { bit,(start,end) -> drawLine(if(mask and (1 shl bit)!=0) Color(0xffff3b3b) else Color(0xff351013),start,end,strokeWidth=w*0.09f,cap=StrokeCap.Round) }
                drawCircle(if(mask and 128!=0) Color(0xffff3b3b) else Color(0xff351013),w*0.06f,Offset(w*0.97f,bottom))
            }
        }
    }
}
