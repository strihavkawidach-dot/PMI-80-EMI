package cz.strihavka.pmi80.core

/** Intel 8080A interpreter. One step returns the number of T states consumed. */
class Intel8080(private val bus: Bus) {
    interface Bus {
        fun read(address: Int): Int
        fun write(address: Int, value: Int)
        fun input(port: Int): Int
        fun output(port: Int, value: Int)
    }
    var a = 0; var b = 0; var c = 0; var d = 0; var e = 0; var h = 0; var l = 0
    var pc = 0; var sp = 0
    var flags = 2
    var halted = false
    var interruptsEnabled = false
        private set
    var cycles = 0L
        private set
    private var eiDelay = 0
    private var irq: Int? = null
    val hl get() = (h shl 8) or l
    fun reset() {
        a=0; b=0; c=0; d=0; e=0; h=0; l=0; pc=0; sp=0; flags=2
        halted=false; interruptsEnabled=false; eiDelay=0; irq=null; cycles=0
    }
    /** Level request, sampled at the next instruction boundary. */
    fun interrupt(vector: Int = 0x38) { require(vector in 0..0x38 && vector % 8 == 0); irq=vector }
    fun releaseInterrupt() { irq=null }
    private fun read(addr: Int) = bus.read(addr and 0xffff) and 255
    private fun write(addr: Int, value: Int) = bus.write(addr and 0xffff, value and 255)
    private fun fetch(): Int { val value=read(pc); pc=(pc+1) and 0xffff; return value }
    private fun word(): Int { val lo=fetch(); return lo or (fetch() shl 8) }
    private fun pair(p: Int) = when(p) { 0 -> (b shl 8) or c; 1 -> (d shl 8) or e; 2 -> hl; else -> sp }
    private fun setPair(p: Int, value: Int) {
        val v=value and 0xffff
        when(p) { 0 -> { b=v shr 8; c=v and 255 }; 1 -> { d=v shr 8; e=v and 255 }; 2 -> { h=v shr 8; l=v and 255 }; else -> sp=v }
    }
    private fun reg(r: Int) = when(r) { 0->b; 1->c; 2->d; 3->e; 4->h; 5->l; 6->read(hl); else->a }
    private fun setReg(r: Int, v: Int) { val n=v and 255; when(r) { 0->b=n; 1->c=n; 2->d=n; 3->e=n; 4->h=n; 5->l=n; 6->write(hl,n); else->a=n } }
    private fun szp(v: Int): Int = (v and 0x80) or (if(v and 255 == 0) 0x40 else 0) or (if(Integer.bitCount(v and 255)%2==0) 4 else 0) or 2
    private fun carry() = flags and 1
    private fun push(v: Int) { sp=(sp-1) and 65535; write(sp,v shr 8); sp=(sp-1) and 65535; write(sp,v) }
    private fun pop(): Int { val lo=read(sp); sp=(sp+1) and 65535; val hi=read(sp); sp=(sp+1) and 65535; return lo or (hi shl 8) }
    private fun condition(n: Int) = when(n) { 0->flags and 0x40==0; 1->flags and 0x40!=0; 2->carry()==0; 3->carry()!=0; 4->flags and 4==0; 5->flags and 4!=0; 6->flags and 0x80==0; else->flags and 0x80!=0 }
    private fun alu(op: Int, value: Int) {
        val old=a
        when(op) {
            0,1 -> { val ci=if(op==1) carry() else 0; val sum=old+value+ci
                a=sum and 255; flags=szp(a) or (if(sum>255) 1 else 0) or (if((old and 15)+(value and 15)+ci>15) 16 else 0) }
            2,3,7 -> { val ci=if(op==3) carry() else 0; val diff=old-value-ci; val result=diff and 255
                flags=szp(result) or (if(diff<0) 1 else 0) or (if((old and 15)-(value and 15)-ci>=0) 16 else 0)
                if(op!=7) a=result }
            4 -> { a=old and value; flags=szp(a) or (if((old or value) and 8 != 0) 16 else 0) }
            5 -> { a=old xor value; flags=szp(a) }
            6 -> { a=old or value; flags=szp(a) }
        }
    }
    fun step(): Int {
        val pending=irq
        if(pending!=null && interruptsEnabled && eiDelay==0) {
            interruptsEnabled=false; halted=false; push(pc); pc=pending; cycles+=11; return 11
        }
        if(halted) { cycles+=4; return 4 }
        val op=fetch()
        val t=execute(op)
        if(eiDelay>0) { eiDelay--; if(eiDelay==0) interruptsEnabled=true }
        cycles+=t
        return t
    }
    private fun execute(op: Int): Int {
        if(op in 0x40..0x7f) {
            if(op==0x76) { halted=true; return 7 }
            setReg((op shr 3) and 7,reg(op and 7)); return if(op and 7==6 || (op shr 3) and 7==6) 7 else 5
        }
        if(op in 0x80..0xbf) { alu((op shr 3) and 7,reg(op and 7)); return if(op and 7==6) 7 else 4 }
        if(op and 0xc7==0x04) { val r=(op shr 3) and 7; val old=reg(r); val v=(old+1) and 255
            setReg(r,v); flags=szp(v) or carry() or (if(old and 15==15) 16 else 0); return if(r==6) 10 else 5 }
        if(op and 0xc7==0x05) { val r=(op shr 3) and 7; val old=reg(r); val v=(old-1) and 255
            setReg(r,v); flags=szp(v) or carry() or (if(old and 15!=0) 16 else 0); return if(r==6) 10 else 5 }
        if(op and 0xc7==0x06) { val r=(op shr 3) and 7; setReg(r,fetch()); return if(r==6) 10 else 7 }
        if(op and 0xcf==0x01) { setPair((op shr 4) and 3,word()); return 10 }
        if(op and 0xcf==0x03) { val p=(op shr 4) and 3; setPair(p,pair(p)+1); return 5 }
        if(op and 0xcf==0x0b) { val p=(op shr 4) and 3; setPair(p,pair(p)-1); return 5 }
        if(op and 0xcf==0x09) { val sum=hl+pair((op shr 4) and 3); setPair(2,sum); flags=(flags and 0xfe) or (if(sum>65535) 1 else 0); return 10 }
        if(op and 0xc7==0xc0) { if(condition((op shr 3) and 7)) { pc=pop(); return 11 }; return 5 }
        if(op and 0xc7==0xc2) { val addr=word(); if(condition((op shr 3) and 7)) pc=addr; return 10 }
        if(op and 0xc7==0xc4) { val addr=word(); if(condition((op shr 3) and 7)) { push(pc); pc=addr; return 17 }; return 11 }
        if(op and 0xc7==0xc6) { alu((op shr 3) and 7,fetch()); return 7 }
        if(op and 0xc7==0xc7) { push(pc); pc=op and 0x38; return 11 }
        if(op and 0xcf==0xc5) { val p=(op shr 4) and 3; push(if(p==3) (a shl 8) or flags else pair(p)); return 11 }
        if(op and 0xcf==0xc1) { val p=(op shr 4) and 3; val v=pop(); if(p==3) { a=v shr 8; flags=(v and 0xd5) or 2 } else setPair(p,v); return 10 }
        when(op) {
            0x02,0x12 -> { write(pair((op shr 4) and 1),a); return 7 }
            0x0a,0x1a -> { a=read(pair((op shr 4) and 1)); return 7 }
            0x07 -> { val cy=a shr 7; a=((a shl 1) or cy) and 255; flags=(flags and 0xfe) or cy }
            0x0f -> { val cy=a and 1; a=(a shr 1) or (cy shl 7); flags=(flags and 0xfe) or cy }
            0x17 -> { val cy=a shr 7; a=((a shl 1) or carry()) and 255; flags=(flags and 0xfe) or cy }
            0x1f -> { val cy=a and 1; a=(a shr 1) or (carry() shl 7); flags=(flags and 0xfe) or cy }
            0x22 -> { val addr=word(); write(addr,l); write(addr+1,h); return 16 }
            0x2a -> { val addr=word(); l=read(addr); h=read(addr+1); return 16 }
            0x27 -> { val old=a; var correction=0; val cy=carry()!=0 || old>0x99
                if(old and 15>9 || flags and 16!=0) correction=6
                if(cy) correction=correction or 0x60
                a=(old+correction) and 255
                flags=szp(a) or (if(cy) 1 else 0) or (if((old xor a xor correction) and 16!=0) 16 else 0) }
            0x2f -> a=a xor 255
            0x32 -> { write(word(),a); return 13 }
            0x3a -> { a=read(word()); return 13 }
            0x37 -> flags=flags or 1
            0x3f -> flags=flags xor 1
            0xc3,0xcb -> { pc=word(); return 10 }
            0xc9,0xd9 -> { pc=pop(); return 10 }
            0xcd,0xdd,0xed,0xfd -> { val addr=word(); push(pc); pc=addr; return 17 }
            0xd3 -> { bus.output(fetch(),a); return 10 }
            0xdb -> { a=bus.input(fetch()) and 255; return 10 }
            0xe3 -> { val lo=read(sp); val hi=read(sp+1); write(sp,l); write(sp+1,h); l=lo; h=hi; return 18 }
            0xe9 -> { pc=hl; return 5 }
            0xeb -> { val old=hl; setPair(2,pair(1)); setPair(1,old); return 5 }
            0xf3 -> { interruptsEnabled=false; eiDelay=0 }
            0xf9 -> { sp=hl; return 5 }
            0xfb -> eiDelay=2
            // 00,08,10,18,20,28,30,38: original silicon NOP aliases.
        }
        return 4
    }
}
