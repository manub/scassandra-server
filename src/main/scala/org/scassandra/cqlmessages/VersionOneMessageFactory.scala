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
package org.scassandra.cqlmessages

import org.scassandra.cqlmessages.response.PreparedResultV1
import akka.util.ByteString
import org.scassandra.cqlmessages.request.{ExecuteRequest}
import org.scassandra.cqlmessages.types.ColumnType

object VersionOneMessageFactory extends AbstractMessageFactory {

  val protocolVersion = ProtocolVersion.ServerProtocolVersionOne
  implicit val protocolVersionImp = VersionOne

  def createPreparedResult(stream: Byte, id : Int, variableTypes: List[ColumnType[_]]): PreparedResultV1 = {
    PreparedResultV1(stream, id, "keyspace", "table", variableTypes)
  }

  def parseExecuteRequestWithoutVariables(stream: Byte, byteString: ByteString): ExecuteRequest = {
    ExecuteRequest.versionOneWithoutTypes(stream, byteString)
  }

  def parseExecuteRequestWithVariables(stream: Byte, byteString: ByteString, variableTypes: List[ColumnType[_]]): ExecuteRequest = {
    ExecuteRequest.versionOneWithTypes(stream, byteString, variableTypes)
  }
}
