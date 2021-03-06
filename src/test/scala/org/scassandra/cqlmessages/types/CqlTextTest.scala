package org.scassandra.cqlmessages.types

import org.scalatest.{Matchers, FunSuite}
import akka.util.ByteString

class CqlTextTest extends FunSuite with Matchers {

  test("Serialisation of CqlText") {
    CqlText.writeValue("hello") should equal(Array[Byte](0, 0, 0, 5, 104, 101, 108, 108, 111))
    CqlText.writeValueInCollection("hello") should equal(Array[Byte](0, 5, 104, 101, 108, 108, 111))
    CqlText.writeValue("") should equal(Array[Byte](0, 0, 0, 0))
    CqlText.writeValueInCollection("") should equal(Array[Byte](0, 0))
    CqlText.writeValue(BigDecimal("123.67")) should equal(Array[Byte](0, 0, 0, 6, 49, 50, 51, 46, 54, 55))
    CqlText.writeValueInCollection(BigDecimal("123.67")) should equal(Array[Byte](0, 6, 49, 50, 51, 46, 54, 55))
    CqlText.writeValue(true) should equal(Array[Byte](0, 0, 0, 4, 116, 114, 117, 101))
    CqlText.writeValueInCollection(true) should equal(Array[Byte](0, 4, 116, 114, 117, 101))

    intercept[IllegalArgumentException] {
      CqlText.writeValue(List())
      CqlText.writeValueInCollection(List())
    }
    intercept[IllegalArgumentException] {
      CqlText.writeValue(Map())
      CqlText.writeValueInCollection(Map())
    }
  }

  test("Reading null") {
    val bytes = ByteString(Array[Byte](-1,-1,-1,-1))
    val deserialisedValue = CqlText.readValue(bytes.iterator)

    deserialisedValue should equal(None)
  }
}
