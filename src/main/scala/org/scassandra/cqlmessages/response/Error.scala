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

import akka.util.ByteString
import org.scassandra.cqlmessages._

object ErrorCodes {
  val ProtocolError = 0x000A
  val ReadTimeout = 0x1200
  val UnavailableException = 0x1000
  val WriteTimeout = 0x1100
}

class Error(protocolVersion: ProtocolVersion, val errorCode : Int, val errorMessage : String, stream: Byte) extends Response(new Header(protocolVersion.serverCode, opCode = OpCodes.Error, streamId = stream)) {

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  override def serialize() : ByteString = {
    val bs = ByteString.newBuilder
    bs.putBytes(header.serialize())

    bs.putInt(4 + 2 + errorMessage.length)

    bs.putInt(errorCode)
    val errorMessageBytes = CqlProtocolHelper.serializeString(errorMessage)
    bs.putBytes(errorMessageBytes.toArray)

    bs.result()
  }
}

case class QueryBeforeReadyMessage(stream : Byte = ResponseHeader.DefaultStreamId)(implicit protocolVersion: ProtocolVersion) extends Error(protocolVersion, ErrorCodes.ProtocolError, "Query sent before StartUp message", stream)

case class ReadRequestTimeout(stream : Byte)(implicit protocolVersion: ProtocolVersion) extends Error(protocolVersion, ErrorCodes.ReadTimeout, "Read Request Timeout", stream) {

  val consistency : Short = ONE.code
  val receivedResponses : Int = 0
  val blockFor : Int = 1
  val dataPresent : Byte = 0

  override def serialize() : ByteString = {
    val bs = ByteString.newBuilder

    val bodyBs = ByteString.newBuilder
    bodyBs.putInt(errorCode)
    val errorMessageBytes = CqlProtocolHelper.serializeString(errorMessage)
    bodyBs.putBytes(errorMessageBytes.toArray)
    bodyBs.putShort(consistency)
    bodyBs.putInt(receivedResponses)
    bodyBs.putInt(blockFor)
    bodyBs.putByte(dataPresent)
    val body = bodyBs.result()

    bs.putBytes(header.serialize())
    bs.putInt(body.length)
    bs.putBytes(body.toArray)
    bs.result()
  }
}

case class UnavailableException(stream: Byte)(implicit protocolVersion: ProtocolVersion) extends Error(protocolVersion, ErrorCodes.UnavailableException, "Unavailable Exception", stream) {
  val consistency : Short = ONE.code
  val required : Int = 1
  val alive : Int = 0

  override def serialize() : ByteString = {
    val bs = ByteString.newBuilder

    val bodyBs = ByteString.newBuilder
    bodyBs.putInt(errorCode)
    val errorMessageBytes = CqlProtocolHelper.serializeString(errorMessage)
    bodyBs.putBytes(errorMessageBytes.toArray)
    bodyBs.putShort(consistency)
    bodyBs.putInt(required)
    bodyBs.putInt(alive)
    val body = bodyBs.result()

    bs.putBytes(header.serialize())
    bs.putInt(body.length)
    bs.putBytes(body.toArray)
    bs.result()
  }
}

case class WriteRequestTimeout(stream: Byte)(implicit protocolVersion: ProtocolVersion) extends Error(protocolVersion, ErrorCodes.WriteTimeout, "Write Request Timeout", stream) {
  //<cl><received><blockfor><writeType>
  val consistency : Short = ONE.code
  val receivedResponses : Int = 0
  val blockFor : Int = 1
  val writeType : String = "SIMPLE"

  override def serialize() : ByteString = {
    val bs = ByteString.newBuilder

    val bodyBs = ByteString.newBuilder
    bodyBs.putInt(errorCode)
    val errorMessageBytes = CqlProtocolHelper.serializeString(errorMessage)
    bodyBs.putBytes(errorMessageBytes.toArray)
    bodyBs.putShort(consistency)
    bodyBs.putInt(receivedResponses)
    bodyBs.putInt(blockFor)
    bodyBs.putBytes(CqlProtocolHelper.serializeString(writeType).toArray)
    val body = bodyBs.result()

    bs.putBytes(header.serialize())
    bs.putInt(body.length)
    bs.putBytes(body.toArray)
    bs.result()
  }
}