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
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max=720.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EMULÁTOR / 1982",color=MaterialTheme.colorScheme.primary,fontSize=12.sp,letterSpacing=3.sp)
                    Text("PMI–80",fontSize=34.sp,fontWeight=FontWeight.Bold)
                }
                TextButton(onClick={help=true}) { Text("Návod") }
            }
            PmiPanel(state.digits,key)
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
            Text("Panel a klávesnice vycházejí z fotografií původního PMI-80: zelená deska, červený kryt displeje a krémový štítek s modrými funkčními klávesami. Původní monitor: projekt maly/fpmi, licence MIT.")
        }
    })
}

private val Plate = Color(0xffded8c7)
private val BoardInk = Color(0xffe0dfc7)

@Composable
private fun PmiPanel(digits: List<Int>, key: (String,Boolean)->Unit) {
    // Yellow case lip, ivory moulding and an olive solder mask, as on the real unit.
    Box(Modifier.fillMaxWidth().background(Color(0xffb79a19),RoundedCornerShape(12.dp)).padding(5.dp)) {
        Box(Modifier.background(Brush.verticalGradient(listOf(Color(0xffeee6d4),Color(0xffcbc2ae))),RoundedCornerShape(8.dp)).padding(10.dp)) {
            Column(Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xff858e6c),Color(0xff626e55))),RoundedCornerShape(3.dp))
                .border(1.dp,Color(0xff4c5741),RoundedCornerShape(3.dp))
                .drawBehind {
                    // Sparse tin-plated tracks remain visible beside the keyboard plate.
                    val unit=size.width/100f
                    for(i in 0..5) {
                        val x=(3f+i*1.65f)*unit
                        val y=size.height*(0.38f+i*0.025f)
                        val path=Path().apply {
                            moveTo(0f,y); lineTo(x,y); lineTo(x+7*unit,y+7*unit)
                            lineTo(x+7*unit,size.height-8*unit)
                        }
                        drawPath(path,Color(0xffbac2a0).copy(alpha=0.5f),style=androidx.compose.ui.graphics.drawscope.Stroke(unit*0.26f))
                    }
                    listOf(Offset(9.dp.toPx(),9.dp.toPx()),Offset(size.width-9.dp.toPx(),9.dp.toPx()),Offset(9.dp.toPx(),size.height-9.dp.toPx()),Offset(size.width-9.dp.toPx(),size.height-9.dp.toPx())).forEach { at ->
                        drawCircle(Color(0xff34382d),4.dp.toPx(),at)
                        drawCircle(Color(0xff151b15),1.3.dp.toPx(),at)
                    }
                }.padding(horizontal=11.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(35.dp).border(1.dp,BoardInk,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) {
                        Text("T",color=BoardInk,fontSize=21.sp,fontWeight=FontWeight.Bold)
                    }
                    Text("PMI-80",modifier=Modifier.weight(1f),color=BoardInk,fontSize=29.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    Text("TESLA",color=BoardInk,fontSize=9.sp,letterSpacing=1.sp)
                }
                BoardComponents()
                LedDisplay(digits)
                // The keyboard has a separate warm metal legend plate; all 25 caps are equal.
                Column(Modifier.fillMaxWidth().background(Plate,RoundedCornerShape(2.dp)).border(1.dp,Color(0xffaaa592)).padding(horizontal=6.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Pmi80.keyRows.forEach { row ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            row.forEach { label -> Key(label,Modifier.weight(1f),key) }
                        }
                    }
                }
                Text("MIKROPOČÍTAČ   •   MHB 8080A",modifier=Modifier.align(Alignment.CenterHorizontally),color=BoardInk,fontSize=8.sp,letterSpacing=1.sp)
            }
        }
    }
}

@Composable
private fun BoardComponents() {
    Canvas(Modifier.fillMaxWidth().height(43.dp)) {
        val pitch=size.width/16f
        for(i in 0..15) {
            val x=pitch*(i+0.5f)
            drawLine(Color(0xffc4c7b2),Offset(x,0f),Offset(x,size.height*0.55f),1.dp.toPx())
            drawRoundRect(Color(0xffc8cdb5),topLeft=Offset(x-pitch*0.2f,size.height*0.1f),size=androidx.compose.ui.geometry.Size(pitch*0.4f,size.height*0.32f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            listOf(Color(0xff645448),Color(0xff943e30),Color(0xff645448)).forEachIndexed { band,color ->
                val y=size.height*(0.17f+band*0.07f)
                drawLine(color,Offset(x-pitch*0.2f,y),Offset(x+pitch*0.2f,y),1.dp.toPx())
            }
        }
        for(i in 0..6) {
            val at=Offset(size.width*(i+0.5f)/7f,size.height*0.8f)
            drawCircle(Color(0xff4e503e),5.3.dp.toPx(),at+Offset(1.dp.toPx(),1.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(Color(0xffe0dfd2),Color(0xff8b8f84),Color(0xffc1bda6)),at,5.dp.toPx()),5.dp.toPx(),at)
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, key: (String,Boolean)->Unit) {
    val function=label in listOf("RE","I","EX","R","BR","M","L","S","=")
    val legend=when(label) { "8"->"SP"; "9"->"HL"; "A"->"AF"; "B"->"BC"; "D"->"DE"; else->"" }
    var pressed by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally) {
        // Reserve the legend strip on every key, including keys with no secondary label.
        Box(Modifier.height(17.dp),contentAlignment=Alignment.Center) {
            Text(legend,color=Color(0xff38372e),fontSize=11.sp,fontWeight=FontWeight.Bold)
        }
        val top=if(function) Color(0xff5787a5) else Color(0xff4b4a46)
        val bottom=if(function) Color(0xff315b78) else Color(0xff282925)
        Box(Modifier.fillMaxWidth().height(48.dp)
            .background(Color(0xff77756a),RoundedCornerShape(1.dp)).padding(2.dp)
            .semantics {
                contentDescription="Klávesa $label" + if(legend.isEmpty()) "" else ", $legend"
                onClick {
                    scope.launch { key(label,true); try { delay(100) } finally { key(label,false) } }
                    true
                }
            }.pointerInput(label) { detectTapGestures(onPress={
                pressed=true; key(label,true)
                try { tryAwaitRelease() } finally { pressed=false; key(label,false) }
            }) },contentAlignment=Alignment.Center) {
            Box(Modifier.fillMaxSize().padding(top=if(pressed) 2.dp else 0.dp,bottom=if(pressed) 0.dp else 3.dp)
                .background(Brush.verticalGradient(if(pressed) listOf(bottom,top) else listOf(top,bottom)),RoundedCornerShape(1.dp))
                .border(1.dp,Color.White.copy(alpha=if(pressed) 0.08f else 0.21f),RoundedCornerShape(1.dp)),contentAlignment=Alignment.Center) {
                Text(label,color=Color(0xfff1efdf),fontSize=18.sp,fontFamily=FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun LedDisplay(digits: List<Int>) {
    // Nine round magnifying lenses behind a dark red translucent window.
    Row(Modifier.fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color(0xff6c301d),Color(0xff270b0b),Color(0xff451916))),RoundedCornerShape(2.dp))
        .border(2.dp,Color(0xff965638),RoundedCornerShape(2.dp))
        .drawBehind {
            drawLine(Color(0xffe7a377).copy(alpha=0.3f),Offset(5.dp.toPx(),5.dp.toPx()),Offset(size.width-5.dp.toPx(),5.dp.toPx()),1.dp.toPx())
            drawLine(Color(0xffdda07a).copy(alpha=0.16f),Offset(4.dp.toPx(),size.height-5.dp.toPx()),Offset(size.width-4.dp.toPx(),size.height-5.dp.toPx()),1.dp.toPx())
        }.padding(horizontal=8.dp,vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        digits.forEachIndexed { index,mask ->
            Canvas(Modifier.weight(1f).aspectRatio(0.82f).semantics { contentDescription="Displej ${index+1}: segmenty %02X".format(mask) }) {
                val w=size.width; val h=size.height
                val center=Offset(w*0.5f,h*0.51f)
                drawOval(Brush.radialGradient(listOf(if(mask!=0) Color(0xffa0142a) else Color(0xff351419),Color(0xff190c0c)),center,w*0.62f))
                drawOval(Color(0xffdb7348).copy(alpha=0.19f),topLeft=Offset(w*0.18f,h*0.07f),size=androidx.compose.ui.geometry.Size(w*0.54f,h*0.13f))
                // A small seven-segment die, slightly slanted, inside each lens.
                val x=w*0.30f; val y=h*0.24f; val mid=h*0.5f; val right=w*0.68f; val bottom=h*0.76f
                fun tilted(p: Offset) = p+Offset((h*0.5f-p.y)*0.12f,0f)
                val segments=listOf(Offset(x,y) to Offset(right,y),Offset(right,y) to Offset(right,mid),Offset(right,mid) to Offset(right,bottom),Offset(x,bottom) to Offset(right,bottom),Offset(x,mid) to Offset(x,bottom),Offset(x,y) to Offset(x,mid),Offset(x,mid) to Offset(right,mid))
                segments.forEachIndexed { bit,(start,end) ->
                    val lit=mask and (1 shl bit)!=0
                    if(lit) {
                        drawLine(Color(0xffff174d).copy(alpha=0.15f),tilted(start),tilted(end),strokeWidth=w*0.24f,cap=StrokeCap.Round)
                        drawLine(Color(0xffff174d).copy(alpha=0.4f),tilted(start),tilted(end),strokeWidth=w*0.12f,cap=StrokeCap.Round)
                    }
                    drawLine(if(lit) Color(0xffffceda) else Color(0xff49141e),tilted(start),tilted(end),strokeWidth=w*0.055f,cap=StrokeCap.Round)
                }
                if(mask and 128!=0) {
                    drawCircle(Color(0xffff174d).copy(alpha=0.35f),w*0.09f,Offset(w*0.83f,bottom))
                    drawCircle(Color(0xffffceda),w*0.03f,Offset(w*0.83f,bottom))
                }
            }
        }
    }
}
