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

import org.scassandra.cqlmessages._
import java.math.BigDecimal
import java.util.UUID
import java.net.{UnknownHostException, InetAddress}
import scala.collection.immutable.Map
import org.scassandra.priming.query.{Prime, PrimeCriteria}
import org.scassandra.cqlmessages.types.ColumnType

class PrimeValidator {

  def validate(criteria: PrimeCriteria, prime: Prime, queryToResults: Map[PrimeCriteria, Prime]): PrimeAddResult = {
    // 1. Validate consistency
    validateConsistency(criteria, queryToResults) match {
      case c: ConflictingPrimes => c
      // 2. Then validate column types
      case _ => validateColumnTypes(prime)
    }
  }

  private def validateConsistency(criteria: PrimeCriteria, queryToResults: Map[PrimeCriteria, Prime]): PrimeAddResult = {

    def intersectsExistingCriteria: (PrimeCriteria) => Boolean = {
      existing => existing.query == criteria.query && existing.consistency.intersect(criteria.consistency).size > 0
    }

    val intersectingCriteria = queryToResults.filterKeys(intersectsExistingCriteria).keySet.toList
    intersectingCriteria match {
      // exactly one intersecting criteria: if the criteria is the newly passed one, this is just an override. Otherwise, conflict.
      case list@head :: Nil if head != criteria => ConflictingPrimes(list)
      // two or more intersecting criteria: this means one or more conflicts
      case list@head :: second :: rest => ConflictingPrimes(list)
      // all other cases: carry on
      case _ => PrimeAddSuccess
    }
  }

  private def validateColumnTypes(prime: Prime): PrimeAddResult = {
    val typeMismatches = {
      for {
        row <- prime.rows
        (column, value) <- row
        columnType = prime.columnTypes(column)
        if isTypeMismatch(value, columnType)
      } yield {
        TypeMismatch(value, column, columnType.stringRep)
      }
    }

    typeMismatches match {
      case Nil => PrimeAddSuccess
      case l: List[TypeMismatch] => TypeMismatches(typeMismatches)
    }
  }

  private def isTypeMismatch(value: Any, columnType: ColumnType[_]): Boolean = {
    try {
      convertValue(value, columnType)
      false
    } catch {
      case
        _: ClassCastException |
        _: NumberFormatException |
        _: IllegalArgumentException |
        _: StringIndexOutOfBoundsException |
        _: UnknownHostException =>
        true
    }
  }

  private def convertValue(value: Any, columnType: ColumnType[_]): Any = {
    columnType.writeValue(value)
  }
}

abstract class PrimeAddResult

case class TypeMismatches(typeMismatches: List[TypeMismatch]) extends PrimeAddResult

case object PrimeAddSuccess extends PrimeAddResult

case class ConflictingPrimes(existingPrimes: List[PrimeCriteria]) extends PrimeAddResult

case class TypeMismatch(value: Any, name: String, columnType: String)

object PrimeValidator {
  def apply() = {
    new PrimeValidator
  }
}
