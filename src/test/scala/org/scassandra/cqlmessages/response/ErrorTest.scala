/*
 * Copyright (C) 2014 Christopher Batey and Dogan Narinc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.scassandra.cqlmessages.response

import org.scalatest.{Matchers, FunSuite}
import org.scassandra.cqlmessages._
import org.scassandra.cqlmessages.VersionTwo

class ErrorTest extends FunSuite with Matchers {

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN
  implicit val protocolVersion = VersionTwo
  val defaultStream : Byte = 0x1

  test("Serialisation of a error response client protocol error") {
    val errorCode: Byte = 0xA
    val errorText = "Any old error message"
    val stream: Byte = 0x04
    val errorMessage = new response.Error(protocolVersion, errorCode, errorText, stream)
    val bytes: List[Byte] = errorMessage.serialize().toList

    bytes should equal(List[Byte](
      protocolVersion.serverCode, // protocol version
      0x00, // flags
      stream, // stream
      OpCodes.Error,
      0x0, 0x0, 0x0, (errorText.length + 6).toByte, // 4 byte integer - length (number of bytes)
      0x0, 0x0, 0x0, errorCode,
      0x00, errorText.length.toByte) ::: // length of the errorText
      errorText.getBytes.toList
    )
  }

  test("QueryBeforeReadyMessage is Error message with code 0xA") {
    val stream: Byte = 0x03
    val errorQueryBeforeReadyMessage = QueryBeforeReadyMessage(stream)

    errorQueryBeforeReadyMessage.header.opCode should equal(OpCodes.Error)
    errorQueryBeforeReadyMessage.errorCode should equal(0xA)
    errorQueryBeforeReadyMessage.errorMessage should equal("Query sent before StartUp message")
    errorQueryBeforeReadyMessage.header.streamId should equal(stream)
  }

  test("Read request timeout has error code 0x1200 and message Read Request Timeout") {
    val readTimeout = ReadRequestTimeout(defaultStream)

    readTimeout.errorCode should equal(ErrorCodes.ReadTimeout)
    readTimeout.errorMessage should equal("Read Request Timeout")
  }
/*
Read_timeout: Timeout exception during a read request. The rest
              of the ERROR message body will be
                <cl><received><blockfor><data_present>
              where:
                <cl> is the [consistency] level of the query having triggered
                     the exception.
                <received> is an [int] representing the number of nodes having
                           answered the request.
                <blockfor> is the number of replica whose response is
                           required to achieve <cl>. Please note that it is
                           possible to have <received> >= <blockfor> if
                           <data_present> is false. And also in the (unlikely)
                           case were <cl> is achieved but the coordinator node
                           timeout while waiting for read-repair
                           acknowledgement.
                <data_present> is a single byte. If its value is 0, it means
                               the replica that was asked for data has not
                               responded. Otherwise, the value is != 0.
 */
  test("Serialization of Read Request Timeout - hard coded data for now") {
    val readTimeoutBytes = ReadRequestTimeout(defaultStream).serialize().iterator
    val header = readTimeoutBytes.drop(4)
    val length = readTimeoutBytes.getInt
    val errorCode = readTimeoutBytes.getInt
    errorCode should equal(ErrorCodes.ReadTimeout)
    // error message - string
    val errorString = CqlProtocolHelper.readString(readTimeoutBytes)
    println(s"errorString ${errorString}")
    val consistency = readTimeoutBytes.getShort
    consistency should equal(ONE.code)
    val receivedResponses = readTimeoutBytes.getInt
    receivedResponses should equal(0)
    val blockedFor = readTimeoutBytes.getInt
    blockedFor should equal(1)
    val dataPresent = readTimeoutBytes.getByte
    dataPresent should equal(0)

    length should equal(4 + 2 + errorString.length + 2 + 4 + 4 + 1)
  }

  test("Serialization of Write Request Timeout - hard coded data for now") {
    val stream : Byte = 0x4
    val writeTimeoutBytes = WriteRequestTimeout(stream).serialize().iterator
    println(writeTimeoutBytes)
    // drop the header
    writeTimeoutBytes.drop(4)
    // drop the length
    writeTimeoutBytes.drop(4)

    val errorCode = writeTimeoutBytes.getInt
    errorCode should equal(ErrorCodes.WriteTimeout)

    val errorString = CqlProtocolHelper.readString(writeTimeoutBytes)
    errorString should equal("Write Request Timeout")

    val consistency = writeTimeoutBytes.getShort
    consistency should equal(ONE.code)

    val receivedResponses = writeTimeoutBytes.getInt
    receivedResponses should equal(0)

    val blockedFor = writeTimeoutBytes.getInt
    blockedFor should equal(1)

    val writeType = CqlProtocolHelper.readString(writeTimeoutBytes)
    writeType should equal(WriteTypes.Simple)
  }

  test("Serialization of Unavailable Exception - hard coded data for now") {
    val unavailableException = UnavailableException(0x1).serialize().iterator
    // header
    val header = unavailableException.drop(4)
    // length
    val length = unavailableException.getInt
    // error code - int
    val errorCode = unavailableException.getInt
    errorCode should equal(ErrorCodes.UnavailableException)
    // error message - string
    val errorString = CqlProtocolHelper.readString(unavailableException)
    // consistency - short 0x0001    ONE
    val consistency = unavailableException.getShort
    consistency should equal(0x1)
    // required - hard coded to 1
    val requiredResponses = unavailableException.getInt
    requiredResponses should equal(1)
    // alive - hard coded to 0
    val alive = unavailableException.getInt
    alive should equal(0)

    length should equal(4 + 2 + errorString.length + 2 + 4 + 4)
  }
}
