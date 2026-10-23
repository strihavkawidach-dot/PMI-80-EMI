package cz.strihavka.pmi80

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cz.strihavka.pmi80.core.PowerSupply

// Normalized coordinates of the complete front panel, preserving its photographic perspective.
private val PowerPanelCrop=Rect(0.012f,0.329f,0.979f,0.876f)
private val SwitchPatch=Rect(0.087f,0.515f,0.181f,0.647f)
private val PowerLights=listOf(Offset(0.140f,0.415f),Offset(0.277f,0.452f),Offset(0.276f,0.573f),Offset(0.275f,0.692f))

@Composable
internal fun SourcePanel(state: ScreenState, togglePower: ()->Unit) {
    val context=LocalContext.current
    val onPhoto=remember(context) { BitmapFactory.decodeResource(context.resources,R.drawable.sn080_on).asImageBitmap() }
    val offPhoto=remember(context) { BitmapFactory.decodeResource(context.resources,R.drawable.sn080_off).asImageBitmap() }
    val busy=state.power==PowerSupply.Phase.STARTING || state.power==PowerSupply.Phase.STOPPING
    val switchLevel by animateFloatAsState(if(state.mains) 1f else 0f,tween(120),label="Poloha síťového přepínače")
    val lights=listOf(state.mains,state.rails and 1!=0,state.rails and 2!=0,state.rails and 4!=0)
    val levels=lights.mapIndexed { i,on -> animateFloatAsState(if(on) 1f else 0f,tween(220),label="Fotografická kontrolka $i").value }
    val cropWidth=onPhoto.width*PowerPanelCrop.width
    val cropHeight=onPhoto.height*PowerPanelCrop.height
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(cropWidth/cropHeight)) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription="Fotografický čelní panel síťového napáječe SN 080, původní nápisy a znak"
                stateDescription="Síť ${if(state.mains) "zapnuta" else "vypnuta"}, +5 V ${if(lights[1]) "svítí" else "zhasnuto"}, −5 V ${if(lights[2]) "svítí" else "zhasnuto"}, +12 V ${if(lights[3]) "svítí" else "zhasnuto"}"
            }) {
                if(switchLevel>=0.999f && levels.all { it>=0.999f }) {
                    drawPanel(onPhoto) // Fully powered: the actual original photo, without drawn controls.
                } else {
                    drawPanel(offPhoto)
                    drawPanelPatch(onPhoto,SwitchPatch,switchLevel)
                    PowerLights.forEachIndexed { index,center ->
                        val patch=Rect(center.x-0.020f,center.y-0.025f,center.x+0.020f,center.y+0.025f)
                        drawPanelPatch(onPhoto,patch,levels[index])
                    }
                }
            }
            val switchX=maxWidth*((0.135f-PowerPanelCrop.left)/PowerPanelCrop.width)
            val switchY=maxHeight*((0.579f-PowerPanelCrop.top)/PowerPanelCrop.height)
            Box(Modifier.offset(x=switchX-24.dp,y=switchY-24.dp).size(48.dp)
                .semantics { contentDescription="Síťový vypínač napáječe SN 080" }
                .toggleable(value=state.mains,enabled=!busy,role=Role.Switch,onValueChange={togglePower()}))
        }
        TextButton(onClick=togglePower,enabled=!busy,modifier=Modifier.align(Alignment.CenterHorizontally)) {
            Text(when(state.power) {
                PowerSupply.Phase.STARTING->"Zapínám zdroj…"
                PowerSupply.Phase.STOPPING->"Napětí dobíhají…"
                PowerSupply.Phase.ON->"Vypnout zdroj"
                else->"Zapnout zdroj"
            })
        }
    }
}

private fun DrawScope.drawPanel(photo: ImageBitmap) {
    drawImage(photo,
        srcOffset=IntOffset((photo.width*PowerPanelCrop.left).toInt(),(photo.height*PowerPanelCrop.top).toInt()),
        srcSize=IntSize((photo.width*PowerPanelCrop.width).toInt(),(photo.height*PowerPanelCrop.height).toInt()),
        dstSize=IntSize(size.width.toInt().coerceAtLeast(1),size.height.toInt().coerceAtLeast(1)))
}

/** Blend the original photographed lamp/lever, rather than a generic drawn LED or switch. */
private fun DrawScope.drawPanelPatch(photo: ImageBitmap, patch: Rect, alpha: Float) {
    if(alpha<=0f) return
    drawImage(photo,
        srcOffset=IntOffset((photo.width*patch.left).toInt(),(photo.height*patch.top).toInt()),
        srcSize=IntSize((photo.width*patch.width).toInt().coerceAtLeast(1),(photo.height*patch.height).toInt().coerceAtLeast(1)),
        dstOffset=IntOffset(((patch.left-PowerPanelCrop.left)*size.width/PowerPanelCrop.width).toInt(),((patch.top-PowerPanelCrop.top)*size.height/PowerPanelCrop.height).toInt()),
        dstSize=IntSize((patch.width*size.width/PowerPanelCrop.width).toInt().coerceAtLeast(1),(patch.height*size.height/PowerPanelCrop.height).toInt().coerceAtLeast(1)),
        alpha=alpha)
}
