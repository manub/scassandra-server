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

package org.scassandra

import akka.actor.ActorRef
import scala.concurrent.{ExecutionContext, Await}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._


object ServerReadyAwaiter {

  import ExecutionContext.Implicits.global

  // awaitStartup() : timeout used implicitly by the ask pattern
  implicit val timeout = Timeout(10 seconds)

  def run(primingReadyListener: ActorRef, tcpReadyListener: ActorRef) = {

    val allReady = for {
      _ <- primingReadyListener ? OnServerReady
      _ <- tcpReadyListener ? OnServerReady
    } yield ServerReady // just need to yield something

    // This timeout should be greater than the one used by the ask pattern above
    Await.result(allReady, Timeout(12 seconds).duration)
  }
}
