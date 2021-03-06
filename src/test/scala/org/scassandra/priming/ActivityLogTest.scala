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
package org.scassandra.priming

import org.scalatest.{BeforeAndAfter, Matchers, FunSuite}
import org.scassandra.cqlmessages.{TWO, ONE}

class ActivityLogTest extends FunSuite with Matchers with BeforeAndAfter {

  before {
    ActivityLog.clearConnections()
    ActivityLog.clearQueries()
  }

  test("Clear connection activity log") {
    ActivityLog.recordConnection()
    ActivityLog.clearConnections()
    ActivityLog.retrieveConnections().size should equal(0)
  }

  test("Clear query activity log") {
    ActivityLog.recordQuery("select * from people", ONE)
    ActivityLog.clearQueries()
    ActivityLog.retrieveQueries().size should equal(0)
  }

  test("No connections should exist by default") {
    ActivityLog.retrieveConnections().size should equal(0)
  }

  test("Store connection and retrieve connection") {
    ActivityLog.recordConnection()
    ActivityLog.retrieveConnections().size should equal(1)
  }

  test("Store query and retrieve connection") {
    val query: String = "select * from people"
    ActivityLog.recordQuery(query, ONE)
    ActivityLog.retrieveQueries().size should equal(1)
    ActivityLog.retrieveQueries()(0).query should equal(query)
    ActivityLog.retrieveQueries()(0).consistency should equal(ONE)
  }
  
  test("Store primed statement and retrieve primed statement") {
    ActivityLog.clearPreparedStatementExecutions()
    val preparedStatementText = "select * from people where name = ?"
    val variables = List("Chris")
    val consistency = ONE
    
    ActivityLog.recordPreparedStatementExecution(preparedStatementText, consistency, variables)
    val preparedStatementRecord = ActivityLog.retrievePreparedStatementExecutions()

    preparedStatementRecord.size should equal(1)
    preparedStatementRecord(0) should equal(PreparedStatementExecution(preparedStatementText, consistency, variables))
  }

  test("Clear prepared statement activity log") {
    ActivityLog.recordPreparedStatementExecution("anything", TWO, List())
    ActivityLog.clearPreparedStatementExecutions()
    ActivityLog.retrievePreparedStatementExecutions().size should equal(0)
  }


}
