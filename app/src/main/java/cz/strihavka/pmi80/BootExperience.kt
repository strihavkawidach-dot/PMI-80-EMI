package cz.strihavka.pmi80

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.strihavka.pmi80.core.PowerSupply
import kotlinx.coroutines.delay

@Composable
internal fun CaseWelcome(open: ()->Unit) {
    val context=LocalContext.current
    val casePhoto=remember(context) { BitmapFactory.decodeResource(context.resources,R.drawable.pmi80_case).asImageBitmap() }
    // Fit the entire supplied lid photograph, preserving every edge and inscription.
    val lidOffset=IntOffset.Zero
    val lidSize=IntSize(casePhoto.width,casePhoto.height)
    var opening by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(if(opening) -108f else 0f,tween(900),label="Otevírání víka")
    LaunchedEffect(opening) { if(opening) { delay(950); open() } }
    Surface(Modifier.fillMaxSize(),color=Color(0xff171b18)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text("TESLA  •  PIEŠŤANY",fontSize=12.sp,letterSpacing=3.sp,color=Color(0xffc2c3ae))
            Spacer(Modifier.height(16.dp))
            Text("PMI-80 EMU",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Color(0xffe7dfc4))
            Text("Školský mikropočítač",fontSize=14.sp,color=Color(0xffa9af9e))
            Spacer(Modifier.height(44.dp))
            Box(Modifier.fillMaxWidth().widthIn(max=520.dp).aspectRatio(lidSize.width.toFloat()/lidSize.height)) {
                Image(rememberBoardPhoto(),contentDescription="Skutečná deska a klávesnice PMI-80 pod víkem",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().graphicsLayer {
                    rotationX=angle; cameraDistance=14*density; transformOrigin=TransformOrigin(0.5f,0f)
                }.clipToBounds().semantics {
                    contentDescription="Původní žluté víko se znakem Tesla, nápisem Školský mikropočítač PMI 80 a úplným označením Tesla Piešťany k. p."
                }.clickable(enabled=!opening){opening=true}) {
                    // Display the lid region of the user's original photograph. The JPEG stays
                    // unmodified, retaining the real badge, lettering, patina and plastic texture.
                    drawImage(casePhoto,
                        srcOffset=lidOffset,
                        srcSize=lidSize,
                        dstSize=IntSize(size.width.toInt().coerceAtLeast(1),size.height.toInt().coerceAtLeast(1)))
                }
            }
            Spacer(Modifier.height(30.dp))
            Button(onClick={opening=true},enabled=!opening) { Text(if(opening) "Otevírám…" else "Otevřít krabičku") }
            TextButton(onClick={original=true}) { Text("Originální tabulka z víka") }
        }
    }
    if(original) OriginalLidDialog { original=false }
}

@Composable
internal fun OriginalLidDialog(close: ()->Unit) {
    val context=LocalContext.current
    val photograph=remember(context) {
        val raw=BitmapFactory.decodeResource(context.resources,R.drawable.pmi80_lid)
        // The original upload is sideways. Rotate only for viewing; preserve the source JPEG.
        Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(-90f) },true).asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    fun constrain(offset: Offset, zoom: Float): Offset {
        val maxX=viewport.width*(zoom-1)/2
        val maxY=viewport.height*(zoom-1)/2
        return Offset(offset.x.coerceIn(-maxX,maxX),offset.y.coerceIn(-maxY,maxY))
    }
    val transform=rememberTransformableState { zoom,offset,_ ->
        scale=(scale*zoom).coerceIn(1f,6f)
        pan=constrain(pan+offset,scale)
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Color(0xff141713)) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Originál z víka",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold)
                    TextButton(onClick=close) { Text("Zavřít") }
                }
                Text("Instrukční tabulka 8080 a schéma PMI-80",modifier=Modifier.padding(horizontal=16.dp),fontSize=12.sp,color=Color.LightGray)
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged { viewport=Size(it.width.toFloat(),it.height.toFloat()) }.transformable(transform),contentAlignment=Alignment.Center) {
                    Image(photograph,contentDescription="Původní fotografovaný štítek z víka PMI-80: instrukční tabulka Intel 8080, přenosy, řízení, aritmetické a logické operace, příznaky a blokové schéma počítače.",contentScale=ContentScale.Fit,
                        modifier=Modifier.fillMaxSize().graphicsLayer { scaleX=scale; scaleY=scale; translationX=pan.x; translationY=pan.y })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.Center) {
                    TextButton(onClick={scale=(scale/1.5f).coerceAtLeast(1f); pan=constrain(pan,scale)},enabled=scale>1f) { Text("− Zmenšit") }
                    TextButton(onClick={scale=1f; pan=Offset.Zero}) { Text("Celý list") }
                    TextButton(onClick={scale=(scale*1.5f).coerceAtMost(6f)},enabled=scale<6f) { Text("+ Zvětšit") }
                }
                Text("Zvětši dvěma prsty a posouvej. Čitelnost je omezená rozlišením původní fotografie.",modifier=Modifier.padding(16.dp),fontSize=11.sp,color=Color.Gray)
            }
        }
    }
}
