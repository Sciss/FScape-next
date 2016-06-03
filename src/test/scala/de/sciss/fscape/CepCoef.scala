package de.sciss.fscape

object CepCoef {
  val One = CepCoef(
    crr =  0, cri =  0,
    clr = +1, cli = +1,
    ccr = +1, cci = -1,
    car = +1, cai = -1,
    gain = 1.0/2097152    // XXX TODO --- what's this factor?
  )

  val Two = CepCoef(
    crr = +1, cri = +1,
    clr =  0, cli =  0,
    ccr = +1, cci = -1,
    car = +1, cai = -1,
    gain = 1.0/32         // XXX TODO --- what's this factor?
  )

  val Bypass = CepCoef(
    crr = +1, cri = +1,
    clr = +1, cli = +1,
    ccr =  0, cci =  0,
    car =  0, cai =  0,
    gain = 1.0
  )
}
final case class CepCoef(crr: Int, cri: Int, clr: Int, cli: Int, ccr: Int, cci: Int, car: Int, cai: Int,
                   gain: Double)