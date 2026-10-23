package cz.strihavka.pmi80.core

import org.junit.Assert.*
import org.junit.Test

class PowerSupplyTest {
    @Test fun closedCaseCannotBePowered() {
        val supply=PowerSupply()
        supply.toggle(); supply.advance(10_000)
        assertEquals(PowerSupply.Phase.CLOSED,supply.phase)
        assertFalse(supply.cpuPowered); assertFalse(supply.mains); assertEquals(0,supply.rails)
    }
    @Test fun startupWaitsForAllThreeRailsBeforeEnablingCpu() {
        val supply=PowerSupply(); supply.openCase(); supply.toggle()
        assertTrue(supply.mains); assertFalse(supply.cpuPowered); assertEquals(0,supply.rails)
        supply.advance(120); assertEquals(1,supply.rails)
        supply.advance(180); assertEquals(3,supply.rails)
        supply.advance(220); assertEquals(7,supply.rails); assertFalse(supply.cpuPowered)
        supply.advance(380); assertTrue(supply.cpuPowered)
    }
    @Test fun shutdownDisablesCpuImmediatelyAndDischargesRailsInOrder() {
        val supply=PowerSupply(); supply.openCase(); supply.toggle(); supply.advance(900)
        supply.toggle(); assertFalse(supply.cpuPowered); assertFalse(supply.mains); assertEquals(7,supply.rails)
        supply.advance(800); assertEquals(3,supply.rails)
        supply.advance(1400); assertEquals(1,supply.rails)
        supply.advance(2300); assertEquals(0,supply.rails); assertEquals(PowerSupply.Phase.OFF,supply.phase)
        supply.closeCase(); assertEquals(PowerSupply.Phase.CLOSED,supply.phase)
    }
    @Test fun repeatedSwitchesAndClosingDuringStartupDoNotSkipSequence() {
        val supply=PowerSupply(); supply.openCase(); supply.toggle(); supply.advance(300)
        supply.toggle(); supply.closeCase()
        assertEquals(PowerSupply.Phase.STARTING,supply.phase)
        supply.advance(600); assertTrue(supply.cpuPowered)
        supply.toggle(); supply.advance(4500); supply.toggle(); assertFalse(supply.cpuPowered)
        supply.advance(900); assertTrue(supply.cpuPowered)
    }
}
