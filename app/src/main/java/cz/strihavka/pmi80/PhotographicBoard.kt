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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
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
private fun displayCrop(photo: ImageBitmap) = Rect(photo.width*0.10f,photo.height*0.48f,photo.width*0.995f,photo.height*0.78f)
private fun boardDisplayRegion(photo: ImageBitmap): Rect {
    val crop=displayCrop(photo)
    return Rect(203f,449f,590f,449f+387f*crop.height/crop.width)
}

@Composable
internal fun PhotographicBoard(digits: List<Int>, key: (String,Boolean)->Unit) {
    val photograph=rememberBoardPhoto()
    val context=LocalContext.current
    val displayPhoto=remember(context) { BitmapFactory.decodeResource(context.resources,R.drawable.pmi80_display_off).asImageBitmap() }
    val displayRegion=boardDisplayRegion(displayPhoto)
    val scope=rememberCoroutineScope()
    var magnified by remember { mutableStateOf(false) }
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(BoardRegion.width/BoardRegion.height)) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription="Skutečná deska PMI-80 s původní klávesnicí a živým displejem" }) {
                drawBoard(photograph,displayPhoto,digits,BoardRegion)
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
            Box(Modifier.offset(x=maxWidth*((displayRegion.left-BoardRegion.left)/BoardRegion.width),y=maxHeight*((displayRegion.top-BoardRegion.top)/BoardRegion.height))
                .size(width=maxWidth*(displayRegion.width/BoardRegion.width),height=maxHeight*(displayRegion.height/BoardRegion.height))
                .semantics { contentDescription="Zvětšit displej" }.clickable { magnified=true })
        }
        TextButton(onClick={magnified=true},modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("Zvětšit displej") }
    }
    if(magnified) AlertDialog(onDismissRequest={magnified=false},confirmButton={TextButton(onClick={magnified=false}){Text("Zavřít")}},title={Text("Displej PMI-80")},text={
        val crop=displayCrop(displayPhoto)
        Canvas(Modifier.fillMaxWidth().aspectRatio(crop.width/crop.height).semantics { contentDescription="Živý devítimístný displej PMI-80 podle detailní fotografie" }) {
            drawPhotoDisplay(displayPhoto,digits,Rect(0f,0f,size.width,size.height))
        }
    })
}

private fun DrawScope.drawBoard(photograph: ImageBitmap, displayPhoto: ImageBitmap, digits: List<Int>, region: Rect) {
    drawImage(photograph,srcOffset=IntOffset(region.left.toInt(),region.top.toInt()),srcSize=IntSize(region.width.toInt(),region.height.toInt()),dstSize=IntSize(size.width.toInt().coerceAtLeast(1),size.height.toInt().coerceAtLeast(1)))
    withTransform({
        scale(size.width/region.width,size.height/region.height,pivot=Offset.Zero)
        translate(-region.left,-region.top)
    }) { drawPhotoDisplay(displayPhoto,digits,boardDisplayRegion(displayPhoto)) }
}

// Lens anchors measured on the unpowered retouch of the front-facing reference.
// Individual positions retain the slight perspective rather than imposing an even grid.
private val DisplayLensCenters=listOf(
    Offset(0.2260f,0.6287f),Offset(0.3034f,0.6300f),Offset(0.3814f,0.6325f),
    Offset(0.4593f,0.6355f),Offset(0.5356f,0.6410f),Offset(0.6132f,0.6392f),
    Offset(0.6911f,0.6405f),Offset(0.7674f,0.6425f),Offset(0.8389f,0.6472f))

/** The large photo and the display inset use the same source geometry and live masks. */
private fun DrawScope.drawPhotoDisplay(photograph: ImageBitmap, digits: List<Int>, target: Rect) {
    val crop=displayCrop(photograph)
    clipRect(target.left,target.top,target.right,target.bottom) {
        withTransform({
            translate(target.left,target.top)
            scale(target.width/crop.width,target.height/crop.height,pivot=Offset.Zero)
            translate(-crop.left,-crop.top)
        }) {
            drawImage(photograph)
            val w=photograph.width.toFloat()
            val h=photograph.height.toFloat()
            digits.take(9).forEachIndexed { index,value ->
                val mask=value and 0x7f
                val cx=w*DisplayLensCenters[index].x
                val cy=h*DisplayLensCenters[index].y
                val left=cx-w*0.012f; val right=cx+w*0.012f
                val top=cy-h*0.028f; val bottom=cy+h*0.028f
                if(mask!=0) {
                    val center=Offset(cx,cy)
                    val radius=w*0.044f
                    drawCircle(Brush.radialGradient(listOf(Color(0xffff0950).copy(alpha=0.36f),Color(0xffff1459).copy(alpha=0.15f),Color.Transparent),center,radius),radius,center)
                }
                val segments=listOf(
                    Offset(left,top) to Offset(right,top),Offset(right,top) to Offset(right,cy),
                    Offset(right,cy) to Offset(right,bottom),Offset(left,bottom) to Offset(right,bottom),
                    Offset(left,cy) to Offset(left,bottom),Offset(left,top) to Offset(left,cy),
                    Offset(left,cy) to Offset(right,cy))
                fun slant(p: Offset) = p+Offset((cy-p.y)*0.16f,0f)
                segments.forEachIndexed { bit,(start,end) ->
                    if(mask and (1 shl bit)!=0) {
                        drawLine(Color(0xffff1250).copy(alpha=0.18f),slant(start),slant(end),w*0.012f,StrokeCap.Round)
                        drawLine(Color(0xffff2876).copy(alpha=0.7f),slant(start),slant(end),w*0.006f,StrokeCap.Round)
                        drawLine(Color(0xffffe5ef),slant(start),slant(end),w*0.003f,StrokeCap.Round)
                    }
                }
            }
        }
    }
}
