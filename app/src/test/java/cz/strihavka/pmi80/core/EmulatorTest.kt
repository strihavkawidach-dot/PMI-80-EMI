package cz.strihavka.pmi80.core

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class EmulatorTest {
    private class TestBus : Intel8080.Bus {
        val memory=IntArray(65536)
        var lastPort=-1; var lastValue=-1
        override fun read(address: Int) = memory[address]
        override fun write(address: Int,value: Int) { memory[address]=value }
        override fun input(port: Int) = 0xa5
        override fun output(port: Int,value: Int) { lastPort=port; lastValue=value }
        fun program(vararg bytes: Int) { bytes.copyInto(memory) }
    }
    @Test fun arithmeticAndConditionalCall() {
        val bus=TestBus(); val cpu=Intel8080(bus)
        bus.program(0x31,0,0x20, 0x3e,0xff, 0xc6,1, 0xcc,0x10,0, 0x76)
        bus.memory[0x10]=0x3c; bus.memory[0x11]=0xc9
        repeat(7) { cpu.step() }
        assertEquals(1,cpu.a); assertTrue(cpu.halted); assertEquals(0x2000,cpu.sp)
    }
    @Test fun eiIsDelayedAndInterruptWakesHalt() {
        val bus=TestBus(); val cpu=Intel8080(bus)
        bus.program(0xfb,0x76); cpu.sp=0x1000; cpu.interrupt()
        assertEquals(4,cpu.step()); assertFalse(cpu.interruptsEnabled)
        assertEquals(7,cpu.step()); assertTrue(cpu.halted)
        assertEquals(11,cpu.step()); assertEquals(0x38,cpu.pc); assertFalse(cpu.halted)
        assertEquals(2,bus.memory[0xffe]); assertFalse(cpu.interruptsEnabled)
    }
    @Test fun daaConvertsPackedBcd() {
        val bus=TestBus()
        for(left in 0..99) for(right in 0..99) {
            val cpu=Intel8080(bus)
            val a=(left/10 shl 4) or (left%10); val b=(right/10 shl 4) or (right%10)
            bus.program(0x3e,a,0xc6,b,0x27)
            repeat(3) { cpu.step() }
            val sum=(left+right)%100
            assertEquals("$left+$right",(sum/10 shl 4) or (sum%10),cpu.a)
            assertEquals(if(left+right>99) 1 else 0,cpu.flags and 1)
        }
    }
    @Test fun subtractAuxCarryAndPswReservedBits() {
        val bus=TestBus(); val cpu=Intel8080(bus)
        bus.program(0x3e,0x10,0xd6,1,0x3d)
        repeat(2) { cpu.step() }; assertEquals(0x0f,cpu.a); assertEquals(0,cpu.flags and 16)
        cpu.step(); assertEquals(0x0e,cpu.a); assertEquals(16,cpu.flags and 16)
        cpu.pc=0x100; bus.memory[0x100]=0xf1; cpu.sp=0x200
        bus.memory[0x200]=255; bus.memory[0x201]=0x42; cpu.step()
        assertEquals(0xd7,cpu.flags); assertEquals(0x42,cpu.a)
    }
    @Test fun stackWrapAndIo() {
        val bus=TestBus(); val cpu=Intel8080(bus)
        bus.program(0xdb,0xf8,0xd3,0xf4,0xc7)
        cpu.step(); cpu.step(); assertEquals(0xa5,cpu.a); assertEquals(0xf4,bus.lastPort); assertEquals(0xa5,bus.lastValue)
        cpu.sp=0; cpu.step(); assertEquals(0xfffe,cpu.sp); assertEquals(5,bus.memory[0xfffe]); assertEquals(0,cpu.pc)
    }
    @Test fun allOpcodesDecode() {
        for(op in 0..255) {
            val bus=TestBus(); bus.memory[0]=op; val cpu=Intel8080(bus); cpu.sp=0x1000
            assertTrue("opcode $op",cpu.step() in 4..18)
        }
    }
    @Test fun memoryMappingAndRomProtection() {
        val m=Pmi80(); val boot=m.read(0)
        m.write(0,123); assertEquals(boot,m.read(0)); assertEquals(boot,m.read(0x2000))
        m.write(0x1c00,0xa5); assertEquals(0xa5,m.read(0x3c00)); assertEquals(255,m.read(0x8000))
        m.reset(); assertEquals(0xa5,m.read(0x1c00))
    }
    @Test fun ppiDirectionAndBsr() {
        val p=Ppi8255(input={0xa0})
        assertEquals(0xa0,p.read(0)); p.write(3,0x8a) // A output, B input, upper C input.
        p.write(0,0x55); assertEquals(0x55,p.read(0))
        p.write(2,0x0f); assertEquals(0xaf,p.read(2))
        p.write(3,0x04); assertEquals(0xab,p.read(2)); assertEquals(0x8a,p.control)
    }
    @Test fun multiplexAndKeyboardMatrix() {
        val m=Pmi80(); m.output(0xfb,0x8a); m.output(0xfa,0xf7); m.output(0xf8,0xc0)
        assertEquals(0x3f,m.digits()[8]); m.key("=",true)
        assertEquals(0,m.input(0xfa) and 0x10); m.key("=",false); assertEquals(0x10,m.input(0xfa) and 0x10)
        m.output(0xfa,0xff); m.key("0",true); assertEquals(0,m.input(0xfa) and 0x40)
    }
    @Test fun monitorChecksumAndBoot() {
        assertEquals(1024,Monitor.bytes.size)
        val hash=MessageDigest.getInstance("SHA-1").digest(Monitor.bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("43153441070ed0572f33d2815635eb7bae878e38",hash)
        val m=Pmi80(); m.runCycles(500_000)
        assertFalse(m.cpu.halted); assertTrue(m.digits().count { it!=0 }>=5)
    }
    private fun press(m: Pmi80,label: String) { m.key(label,true); m.runCycles(100_000); m.key(label,false); m.runCycles(100_000) }
    @Test fun realMonitorEditsRamThroughKeyboard() {
        val m=Pmi80(); m.runCycles(500_000)
        press(m,"0"); press(m,"M")
        listOf("1","C","0","0","=","A","5","=").forEach { press(m,it) }
        assertEquals(0xa5,m.read(0x1c00))
    }
}
