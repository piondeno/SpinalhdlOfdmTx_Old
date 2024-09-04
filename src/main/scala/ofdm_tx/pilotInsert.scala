package ofdm_tx

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import spinal.lib.fsm._

case class PilotInsert() extends Component{
  val io = new Bundle{
    val dataIn = slave(Stream(PunctInterleaverDataOutIF()))
    //val dataOut = master(Stream(CsgFftDataIf(GlobalDefine().modulationDataOutWidth) ))
    val dataOut = out(FftDataIf(GlobalDefine().modulationDataOutWidth))
    val newPackage = out(Bool())
  }

  def cntWidth : Int =  6

  //default value
  io.newPackage := False
  when(io.dataIn.symbolType === GlobalDefine().legacySignalSymbol && (io.dataIn.fire)){
    io.newPackage := True
  }

  //instantiate modulation
  val modulator = Modulation()
  val modulatorInputMux = cloneOf(modulator.io.dataIn.data)
  val modulatorNbpscMux = cloneOf(modulator.io.dataIn.nBPSC)
  modulator.io.dataIn.data := modulatorInputMux
  modulator.io.dataIn.nBPSC := modulatorNbpscMux

  //input data buffer for data 0 ~ 23 and two Pilot Tone, 24+2=26
  val pilotInsertCnt = Reg(UInt(cntWidth bits)) init(0)
  val dataInReg = Vec.fill(26)(Reg( ComplexDataType(GlobalDefine().modulationDataOutWidth) ))
  val pushEnable = Bool() // when true, data push into dataInReg
  val pilotTiming = Bool()   // the time for insert pilot
  val inputPreHalfValidTiming = io.dataIn.fire || pilotTiming

  when(io.dataIn.fire){
    pilotInsertCnt := pilotInsertCnt + 1
  } elsewhen (pilotInsertCnt === 57){
    pilotInsertCnt := 0
  } elsewhen(pilotInsertCnt =/= 0){
    pilotInsertCnt := pilotInsertCnt + 1
  }

  when(pushEnable ){
    for (index <- 1 until dataInReg.length) {
      dataInReg(index) := dataInReg(index - 1)
    }
    dataInReg(0) := modulator.io.dataOut
  }

  //record the symbolType
  val scrambleReg = RegInit(B"1111111")
  val scrambleIn = ~(scrambleReg(3)^scrambleReg(6))
  when(io.dataIn.fire && (pilotInsertCnt === 0)){
    when(io.dataIn.symbolType =/= GlobalDefine().dataSymbol){
      scrambleReg := B"1111111"
    } otherwise{  //only data symbol needs to scramble the pilot
      scrambleReg := (scrambleReg(5 downto 0),scrambleIn).asBits
    }
  }

  // pilotInsertCnt : 0 ~ 25, input preHalf data into dataInReg
  // pilotInsertCnt : 26 ~ 31, lower path only output null tone.
  // pilotInsertCnt : 32 ~ 63, output lower path data from dataInReg
  pushEnable := ((pilotInsertCnt < 26) && inputPreHalfValidTiming) || (pilotInsertCnt >31 ) // index : 26 ~ 32, lower path only output null tone.
  pilotTiming := (pilotInsertCnt === 5) || (pilotInsertCnt === 19) || (pilotInsertCnt === 33) || (pilotInsertCnt === 47)

  when(pilotTiming) {
    when(pilotInsertCnt === 47) {
      modulatorInputMux := Mux(scrambleIn, B(0, GlobalDefine().modulatonDataInWidth bits) , B(1, GlobalDefine().modulatonDataInWidth bits))
      modulatorNbpscMux := 1
    } otherwise {
      modulatorInputMux := Mux(scrambleIn, B(1, GlobalDefine().modulatonDataInWidth bits) , B(0, GlobalDefine().modulatonDataInWidth bits))
      modulatorNbpscMux := 1
    }
  } otherwise {
    modulatorInputMux := io.dataIn.data
    modulatorNbpscMux := io.dataIn.nBPSC
  }

  //io.dataIn.ready control
  val dataInReadyCtrl = False
  when(pilotInsertCnt < 26){
    dataInReadyCtrl := True
  } elsewhen((pilotInsertCnt > 26) && (pilotInsertCnt < 53)){
    dataInReadyCtrl := True
  }
  io.dataIn.ready := dataInReadyCtrl && (~pilotTiming)

  //io.dataOut.valid control
  io.dataOut.valid := Mux((pilotInsertCnt>25),True,False)

  //io.dataOut control
  io.dataOut.data.aData.I := 0
  io.dataOut.data.aData.Q := 0
  io.dataOut.data.bData.I := 0
  io.dataOut.data.bData.Q := 0

  when(pilotInsertCnt > 31){
    io.dataOut.data.bData := dataInReg(dataInReg.length-1)
  }
  when((pilotInsertCnt > 26) && (pilotInsertCnt < 53) ){
    io.dataOut.data.aData := modulator.io.dataOut
  }
}

object PilotInsert {
  def main(args: Array[String]) {
    SpinalVerilog(PilotInsert()) //Or SpinalVerilog
  }
}
