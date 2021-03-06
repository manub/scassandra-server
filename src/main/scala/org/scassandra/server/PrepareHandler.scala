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
package org.scassandra.server

import com.typesafe.scalalogging.slf4j.{Logging}
import akka.actor.{ActorRef, Actor}
import akka.util.{ByteString}
import org.scassandra.cqlmessages._
import org.scassandra.priming.prepared.PrimePreparedStore
import org.scassandra.priming._
import org.scassandra.priming.query.PrimeMatch
import scala.Some
import org.scassandra.cqlmessages.types.{CqlVarchar, ColumnType}

class PrepareHandler(primePreparedStore: PrimePreparedStore) extends Actor with Logging {

  import CqlProtocolHelper._

  var preparedStatementId : Int = 1
  var preparedStatementsToId : Map[Int, String] = Map()

  def receive: Actor.Receive = {
    case PrepareHandlerMessages.Prepare(body, stream, msgFactory, connection) => {
      logger.debug(s"Received prepare message ${body}")
      val query = readLongString(body.iterator).get


      val preparedPrime = primePreparedStore.findPrime(PrimeMatch(query))
      val preparedResult = if (preparedPrime.isDefined) {
        msgFactory.createPreparedResult(stream, preparedStatementId, preparedPrime.get.variableTypes)
      } else {
        val numberOfParameters = query.toCharArray.filter(_ == '?').size
        val variableTypes: List[ColumnType[_]] = (0 until numberOfParameters).map(num => CqlVarchar).toList
        msgFactory.createPreparedResult(stream, preparedStatementId, variableTypes)
      }

      preparedStatementsToId += (preparedStatementId -> query)

      preparedStatementId = preparedStatementId + 1

      logger.info(s"Prepared Statement has been prepared: |${query}|. Prepared result is: ${preparedResult}")
      connection ! preparedResult
    }
    case PrepareHandlerMessages.Execute(body, stream, msgFactory, connection) => {
      logger.debug(s"Received execute bytes $body")
      val executeRequest = msgFactory.parseExecuteRequestWithoutVariables(stream, body)
      logger.debug(s"Received execute message $executeRequest")
      val preparedStatement = preparedStatementsToId.get(executeRequest.id)

      if (preparedStatement.isDefined) {
        val preparedStatementText = preparedStatement.get
        val prime = primePreparedStore.findPrime(PrimeMatch(preparedStatementText, executeRequest.consistency))
        logger.info(s"Received execution of prepared statement |${preparedStatementText}| and consistency |${executeRequest.consistency}|")
        prime match {
          case Some(preparedPrime) => {

            if (executeRequest.numberOfVariables == preparedPrime.variableTypes.size) {
              val executeRequestParsedWithVariables = msgFactory.parseExecuteRequestWithVariables(stream, body, preparedPrime.variableTypes)
              ActivityLog.recordPreparedStatementExecution(preparedStatementText, executeRequestParsedWithVariables.consistency, executeRequestParsedWithVariables.variables)
           } else {
             ActivityLog.recordPreparedStatementExecution(preparedStatementText, executeRequest.consistency, List())
             logger.warn(s"Execution of prepared statement has a different number of variables to the prime. Number of variables in message ${executeRequest.numberOfVariables}. Variables won't be recorded. $preparedPrime")
           }

           preparedPrime.prime.result match {
             case Success => connection ! msgFactory.createRowsMessage(preparedPrime.prime, stream)
             case ReadTimeout => connection ! msgFactory.createReadTimeoutMessage(stream)
             case WriteTimeout => connection ! msgFactory.createWriteTimeoutMessage(stream)
             case Unavailable => connection ! msgFactory.createUnavailableMessage(stream)
           }

          }
          case None => {
            logger.info(s"Received execution of prepared statement that hasn't been primed so can't record variables. $preparedStatementText")
            ActivityLog.recordPreparedStatementExecution(preparedStatement.get, executeRequest.consistency, List())
            connection ! msgFactory.createVoidMessage(stream)
          }
        }
      } else {
        logger.warn(s"Received execution of an unknown prepared statement. Have you restarted Scassandra since your application prepared the statement?")
        connection ! msgFactory.createVoidMessage(stream)
      }
    }
    case msg @ _ => {
      logger.debug(s"Received unknown message $msg")
    }
  }
}

object PrepareHandlerMessages {
  case class Prepare(body: ByteString, stream: Byte, msgFactory: CqlMessageFactory, connection: ActorRef)
  case class Execute(body: ByteString, stream: Byte, msgFactory: CqlMessageFactory, connection: ActorRef)
}

/*
Example execute message body
ByteString(
0, 4, // length of the prepared statement id
0, 0, 0, 1, // prepared statement id
0, 1, // consistency
5, // flags
0, 7, // number of variables
0, 0, 0, 8,    0, 0, 0, 0, 0, 0, 4, -46, -    val bigInt : java.lang.Long = 1234
0, 0, 0, 8,    0, 0, 0, 0, 0, 0, 9, 41,  -    val counter : java.lang.Long = 2345
0, 0, 0, 5,    0, 0, 0, 0, 1,            -    val decimal : java.math.BigDecimal = new java.math.BigDecimal("1")
0, 0, 0, 8,    63, -8, 0, 0, 0, 0, 0, 0, -    val double : java.lang.Double = 1.5
0, 0, 0, 4,    64, 32, 0, 0,             -    val float : java.lang.Float = 2.5f
0, 0, 0, 4,    0, 0, 13, -128,           -    val int : java.lang.Integer = 3456
0, 0, 0, 1,   123,                       -    val varint : java.math.BigInteger = new java.math.BigInteger("123")

0, 0, 19, -120) // serial consistency?? not sure


ByteString(
0, 4,
 0, 0, 0, 1,
 0, 1,
 5,
 0, 9,

 0, 0, 0, 5,    97, 115, 99, 105, 105,
 0, 0, 0, 0,
 0, 0, 0, 1,    1,
 0, 0, 0, 8,    0, 0, 1, 69, -11, 123, 55, 49,
 0, 0, 0, 16,  -84, 87, -28, 43, 59, -11, 72, -102, -128, 95, 83, -11, -80, -117, 97, -41,
 0, 0, 0, 7,   118, 97, 114, 99, 104, 97, 114,
 0, 0, 0, 16,  48, -99, -19, 96, -38, -105, 17, -29, -71, 0, -23, -112, 98, 25, 105, 100,
 0, 0, 0, 4,   127, 0, 0, 1,

 -1, -1, -1, -1, 0, 0, 19, -120)

 execute from v1 driver

 ByteString(
 0, 4,
 0, 0, 0, 1,
 0, 1,  // number of
 0,  0, 0, 5,   67, 104, 114, 105, 115,
 0, 1)
 */
