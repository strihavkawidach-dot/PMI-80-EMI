package cz.strihavka.pmi80.core

/** 8255 mode-0 parallel ports and bit set/reset control, as used by the PMI monitor.
 * Handshaking modes 1/2 are deliberately rejected instead of silently mis-emulated. */
class Ppi8255(
    private val input: (Int) -> Int = { 255 },
    private val output: (Int, Int) -> Unit = { _, _ -> }
) {
    var control = 0x9b
        private set
    private val latch = IntArray(3)
    fun reset() { control=0x9b; latch.fill(0) }
    private fun inputMask(port: Int): Int = when(port) {
        0 -> if(control and 0x10!=0) 255 else 0
        1 -> if(control and 2!=0) 255 else 0
        else -> (if(control and 8!=0) 0xf0 else 0) or (if(control and 1!=0) 15 else 0)
    }
    fun read(port: Int): Int {
        val p=port and 3
        if(p==3) return 255 // Control register is write-only.
        val mask=inputMask(p)
        return (input(p) and mask) or (latch[p] and (mask xor 255))
    }
    /** Includes pull-ups on pins configured as inputs. */
    fun pins(port: Int) = (latch[port] and (inputMask(port) xor 255)) or inputMask(port)
    fun write(port: Int, value: Int) {
        val p=port and 3; val v=value and 255
        if(p<3) { latch[p]=v; output(p,pins(p)); return }
        if(v and 0x80!=0) {
            require(v and 0x64==0) { "8255 handshake modes 1/2 are not supported" }
            control=v; latch.fill(0)
            for(i in 0..2) output(i,pins(i))
        } else {
            val bit=1 shl ((v shr 1) and 7)
            latch[2]=if(v and 1!=0) latch[2] or bit else latch[2] and bit.inv()
            output(2,pins(2))
        }
    }
}
