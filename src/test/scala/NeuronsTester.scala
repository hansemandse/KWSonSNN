import neuroproc._

import org.scalatest._
import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation

class NeuronsTester extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Neurons"

  it should "handle a spike" in {
    test(new Neurons(2)).withAnnotations(Seq(WriteVcdAnnotation)) {
      dut =>
        dut.clock.setTimeout(2*CYCLESPRSTEP)

        // Reset inputs
        dut.io.spikeCnt.poke(1.U)
        dut.io.aData.poke(0.U)

        dut.io.inOut.expect(false.B)
        dut.io.aAddr.expect(0.U)
        dut.io.aEna.expect(false.B)
        dut.io.n.expect(0.U)
        for (i <- 0 until EVALUNITS) 
          dut.io.spikes(i).expect(false.B)
        
        // Wait for the control unit to activate the axon memory
        while (!dut.io.aEna.peek.litToBoolean)
          dut.clock.step()
        // State 3
        dut.io.aAddr.expect(0.U)
        dut.clock.step()
        // State 4
        dut.io.aAddr.expect(1.U)
        dut.clock.step()
        // State 6
        dut.io.aAddr.expect(2.U)
        dut.clock.step()
        // State 7
        dut.io.aAddr.expect(2.U)
        dut.clock.step()
        // State 8
        dut.io.aAddr.expect(2.U)
        dut.clock.step()
        // State 9
        dut.io.aAddr.expect(2.U)
        dut.clock.step()
        // State A
        dut.io.aAddr.expect(2.U)
        dut.clock.step()
        // State B
        dut.io.aAddr.expect(2.U)
        dut.clock.step()
        // State 1
        dut.io.aAddr.expect(0.U)
        dut.io.n.expect(1.U)
    }
  }
}
