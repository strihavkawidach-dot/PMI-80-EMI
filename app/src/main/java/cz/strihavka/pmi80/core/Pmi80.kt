package cz.strihavka.pmi80.core

/** Stock PMI-80: 1 KiB monitor, empty expansion ROM socket, 1 KiB RAM, two PPIs. */
class Pmi80 : Intel8080.Bus {
    private val rom=ByteArray(2048) { 0xff.toByte() }
    private val ram=ByteArray(1024)
    private val keys=mutableSetOf<String>()
    private val display=IntArray(9)
    private val refreshed=LongArray(9) { -1 }
    private var selector=255
    private var displayReady=false
    val cpu=Intel8080(this)
    val userPpi=Ppi8255()
    val systemPpi=Ppi8255(input={ p -> if(p==2) keyboard() else 255 }, output={ p,v ->
        when(p) {
            2 -> { selector=v; displayReady=true }
            0 -> if(displayReady) {
                displayReady=false
                val digit=selector xor 255
                if(digit in 0..8) { display[digit]=v xor 255; refreshed[digit]=cpu.cycles }
            }
        }
    })
    init { loadRom(Monitor.bytes) }
    fun loadRom(bytes: ByteArray) {
        require(bytes.size in 1..2048) { "ROM musí mít 1 až 2048 bajtů" }
        rom.fill(0xff.toByte()); bytes.copyInto(rom); reset()
    }
    fun reset() {
        cpu.reset(); systemPpi.reset(); userPpi.reset(); keys.clear()
        display.fill(0); refreshed.fill(-1); selector=255; displayReady=false
    }
    override fun read(address: Int): Int {
        val addr=address and 0xdfff // A13 is not decoded on the original board.
        return when(addr) { in 0..0x7ff -> rom[addr].toInt() and 255; in 0x1c00..0x1fff -> ram[addr-0x1c00].toInt() and 255; else -> 255 }
    }
    override fun write(address: Int, value: Int) {
        val addr=address and 0xdfff
        if(addr in 0x1c00..0x1fff) ram[addr-0x1c00]=value.toByte()
    }
    override fun input(port: Int): Int = when(port and 15) {
        in 8..11 -> systemPpi.read(port)
        in 4..7 -> userPpi.read(port)
        else -> 255
    }
    override fun output(port: Int, value: Int) {
        when(port and 15) { in 8..11 -> systemPpi.write(port,value); in 4..7 -> userPpi.write(port,value) }
    }
    private fun keyboard(): Int {
        val row=selector-0xf7
        var value=0x7f // PC7 tape input idle low.
        if(row in matrix.indices) matrix[row].forEachIndexed { column,key ->
            if(key in keys) value=value and (0x10 shl column).inv()
        }
        return value
    }
    fun key(label: String, down: Boolean) {
        if(label=="RE") { if(down) reset(); return }
        if(label=="I") { if(down) cpu.interrupt() else cpu.releaseInterrupt(); return }
        if(down) keys.add(label) else keys.remove(label)
    }
    fun releaseKeys() { keys.clear(); cpu.releaseInterrupt() }
    fun runCycles(budget: Int) { val end=cpu.cycles+budget; while(cpu.cycles<end) cpu.step() }
    fun digits(): List<Int> = display.indices.map { if(refreshed[it]>=0 && cpu.cycles-refreshed[it]<CLOCK_HZ/5) display[it] else 0 }
    fun ramBytes() = ram.copyOf()
    fun loadRam(bytes: ByteArray) { require(bytes.size==1024) { "Obraz RAM musí mít 1024 bajtů" }; bytes.copyInto(ram) }
    fun loadProgram(bytes: ByteArray, address: Int = 0x1c00) {
        require(address>=0x1c00 && address.toLong()+bytes.size<=0x2000) { "Program musí být v RAM 1C00–1FFF" }
        bytes.forEachIndexed { i,v -> write(address+i,v.toInt()) }
    }
    companion object {
        const val CLOCK_HZ=1_111_111 // 10 MHz oscillator / 9.
        val keyRows=listOf(listOf("RE","I","EX","R","BR"),listOf("C","D","E","F","M"),listOf("8","9","A","B","L"),listOf("4","5","6","7","S"),listOf("0","1","2","3","="))
        // Scan selector F7..FF, columns PC4, PC5, PC6.
        private val matrix=arrayOf(arrayOf("=","3","1"),arrayOf("","7","5"),arrayOf("","B","9"),arrayOf("M","E","C"),arrayOf("BR","F","D"),arrayOf("","R","EX"),arrayOf("L","A","8"),arrayOf("S","6","4"),arrayOf("","2","0"))
    }
}
