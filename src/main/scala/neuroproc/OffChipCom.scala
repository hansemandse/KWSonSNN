package neuroproc

import chisel3._
import chisel3.util._
import spray.json._

// This file is very hard coded for this project
class OffChipCom(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle{
    val tx = Output(Bool())
    val rx = Input(Bool())

    // Valid/ready for in and out cores
    val inC0Data  = Output(UInt(24.W))
    val inC0Valid = Output(Bool())
    val inC0Ready = Input(Bool())
    
    val inC1Data  = Output(UInt(24.W))
    val inC1Valid = Output(Bool())
    val inC1Ready = Input(Bool())
    
    val outCData  = Input(UInt(8.W))
    val outCValid = Input(Bool())
    val outCReady = Output(Bool())

    // Synchronize channels for input cores
    val inC0HSin  = Input(Bool())
    val inC0HSout = Output(Bool())

    val inC1HSin  = Input(Bool())
    val inC1HSout = Output(Bool())
  })

  val uart = Module(new Uart(frequency, baudRate))

  val txBuf = RegInit(0.U(8.W))
  val txV   = RegInit(false.B)

  val phase = RegInit(false.B) // Init to in phase. download first

  val byteCnt = RegInit(0.U(2.W))
  val pixCnt = RegInit(0.U(log2Up(INPUTSIZE).W))

  val inC0V = RegInit(false.B)
  val inC1V = RegInit(false.B)
  
  val addr1 = RegInit(0.U(8.W))
  val addr0 = RegInit(0.U(8.W))
  val rate1 = RegInit(0.U(8.W))
  val rate0 = RegInit(0.U(8.W))

  val inCData = Wire(UInt(24.W))

  val idle :: start :: receiveW :: toCore :: Nil = Enum(4)
  val stateReg = RegInit(idle)

  // Concatenate buffers for a combined data word
  inCData := addr0 ## rate1 ## rate0

  // RX logic
  uart.io.rxd := io.rx
  uart.io.rxReady := false.B

  // Input core valid/ready logic
  io.inC0HSout := phase
  io.inC0Data  := inCData
  io.inC0Valid := inC0V
  when(inC0V && io.inC0Ready) {
    inC0V := false.B
  }

  io.inC1HSout := phase
  io.inC1Data  := inCData
  io.inC1Valid := inC1V
  when(inC1V && io.inC1Ready) {
    inC1V := false.B
  }

  // TX logic
  io.tx := uart.io.txd
  uart.io.txValid := txV
  uart.io.txByte := txBuf
  when(uart.io.txReady && txV) {
    txV := false.B
  }

  // Output cores valid/ready logic
  io.outCReady := false.B
  when(io.outCValid && ~txV) { //not busy in receive loop
    txV := true.B
    io.outCReady := true.B
    txBuf := io.outCData
  }

  // Control FSM
  switch(stateReg) {
    is(idle) {
      // When the input cores are in phase, transfers can begin
      when(phase === io.inC0HSin && io.inC0HSin === io.inC1HSin) {
        stateReg := start
      }
    }
    is(start) {
      // When the UART has completed a transfer, receive an image;
      // otherwise reset the counters and transfer
      when(uart.io.txReady) {
        stateReg := receiveW
      }.otherwise {
        pixCnt := 0.U
        byteCnt := 0.U
        txBuf := 255.U //start msg never the case for spike as they are 0-199
        txV := true.B
      }
    }
    is(receiveW) {
      // When a new byte has been received, shift it into the registers
      when(uart.io.rxValid) {
        addr1 := addr0
        addr0 := rate1
        rate1 := rate0
        rate0 := uart.io.rxByte
        uart.io.rxReady := true.B

        byteCnt := byteCnt + 1.U 

        when (byteCnt === 3.U) {
          stateReg := toCore
        }.otherwise {
          stateReg := receiveW
        }
      }
    }
    is(toCore) {
      // Transfer the received word to one of the input cores
      when(addr1 === 0.U) {
        inC0V := true.B
      }.otherwise {
        inC1V := true.B
      }
      pixCnt := pixCnt + 1.U
      
      // If a full image has been received, go back to idle;
      // otherwise, receive more words
      when(pixCnt === (INPUTSIZE-1).U) {
        stateReg := idle
        phase := ~phase
      }.otherwise {
        stateReg := receiveW
      }
    }
  }
}
