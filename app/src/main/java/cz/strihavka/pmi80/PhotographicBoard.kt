package cz.strihavka.pmi80

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cz.strihavka.pmi80.core.Pmi80
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Unmodified first, unpowered frame from the owner's reference recording. */
@Composable
internal fun rememberBoardPhoto(): ImageBitmap {
    val context=LocalContext.current
    return remember(context) {
        val raw=BitmapFactory.decodeResource(context.resources,R.drawable.pmi80_board_frame)
        Bitmap.createBitmap(raw,0,0,raw.width,raw.height,Matrix().apply { postRotate(90f) },true).asImageBitmap()
    }
}

private val BoardRegion=Rect(170f,220f,700f,1220f)
private val DisplayRegion=Rect(203f,450f,590f,543f)

@Composable
internal fun PhotographicBoard(digits: List<Int>, key: (String,Boolean)->Unit) {
    val photograph=rememberBoardPhoto()
    val scope=rememberCoroutineScope()
    var magnified by remember { mutableStateOf(false) }
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(BoardRegion.width/BoardRegion.height)) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription="Skutečná deska PMI-80 s původní klávesnicí a živým displejem" }) {
                drawBoard(photograph,digits,BoardRegion)
            }
            // Touch targets are intentionally larger than the photographed caps, with no
            // replacement lettering. Their coordinates are registered to the source frame.
            Pmi80.keyRows.forEachIndexed { row,labels ->
                labels.forEachIndexed { column,label ->
                    var pressed by remember(label) { mutableStateOf(false) }
                    val centerX=254f+87f*column
                    val centerY=775f+87.5f*row
                    Box(Modifier.offset(x=maxWidth*((centerX-43f-BoardRegion.left)/BoardRegion.width),y=maxHeight*((centerY-43f-BoardRegion.top)/BoardRegion.height))
                        .size(width=maxWidth*(86f/BoardRegion.width),height=maxHeight*(86f/BoardRegion.height))
                        .background(if(pressed) Color.Black.copy(alpha=0.18f) else Color.Transparent)
                        .semantics {
                            contentDescription="Klávesa $label"
                            onClick {
                                scope.launch { key(label,true); try { delay(100) } finally { key(label,false) } }
                                true
                            }
                        }.pointerInput(label) {
                            detectTapGestures(onPress={
                                pressed=true; key(label,true)
                                try { tryAwaitRelease() } finally { pressed=false; key(label,false) }
                            })
                        })
                }
            }
            Box(Modifier.offset(x=maxWidth*((DisplayRegion.left-BoardRegion.left)/BoardRegion.width),y=maxHeight*((DisplayRegion.top-BoardRegion.top)/BoardRegion.height))
                .size(width=maxWidth*(DisplayRegion.width/BoardRegion.width),height=maxHeight*(DisplayRegion.height/BoardRegion.height))
                .semantics { contentDescription="Zvětšit displej" }.clickable { magnified=true })
        }
        TextButton(onClick={magnified=true},modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("Zvětšit displej") }
    }
    if(magnified) AlertDialog(onDismissRequest={magnified=false},confirmButton={TextButton(onClick={magnified=false}){Text("Zavřít")}},title={Text("Displej PMI-80")},text={
        Canvas(Modifier.fillMaxWidth().aspectRatio(DisplayRegion.width/DisplayRegion.height)) { drawBoard(photograph,digits,DisplayRegion) }
    })
}

private fun DrawScope.drawBoard(photograph: ImageBitmap, digits: List<Int>, region: Rect) {
    drawImage(photograph,srcOffset=IntOffset(region.left.toInt(),region.top.toInt()),srcSize=IntSize(region.width.toInt(),region.height.toInt()),dstSize=IntSize(size.width.toInt().coerceAtLeast(1),size.height.toInt().coerceAtLeast(1)))
    val sx=size.width/region.width
    val sy=size.height/region.height
    fun screen(x: Float,y: Float) = Offset((x-region.left)*sx,(y-region.top)*sy)
    digits.take(9).forEachIndexed { index,value ->
        // This physical display has seven bars. PA7 is not drawn as an invented decimal dot.
        val mask=value and 0x7f
        val cx=260f+32.7f*index
        val cy=500f
        val left=cx-5f; val right=cx+5f; val top=cy-9f; val bottom=cy+9f
        val segments=listOf(
            Offset(left,top) to Offset(right,top),Offset(right,top) to Offset(right,cy),
            Offset(right,cy) to Offset(right,bottom),Offset(left,bottom) to Offset(right,bottom),
            Offset(left,cy) to Offset(left,bottom),Offset(left,top) to Offset(left,cy),
            Offset(left,cy) to Offset(right,cy))
        fun slant(p: Offset) = screen(p.x+(cy-p.y)*0.12f,p.y)
        segments.forEachIndexed { bit,(start,end) ->
            if(mask and (1 shl bit)!=0) {
                drawLine(Color(0xffff1749).copy(alpha=0.14f),slant(start),slant(end),7f*sx,StrokeCap.Round)
                drawLine(Color(0xffff214f).copy(alpha=0.45f),slant(start),slant(end),3.5f*sx,StrokeCap.Round)
                drawLine(Color(0xffffb1c7),slant(start),slant(end),1.5f*sx,StrokeCap.Round)
            }
        }
    }
}
