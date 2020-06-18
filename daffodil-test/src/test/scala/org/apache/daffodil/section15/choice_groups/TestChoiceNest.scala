/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.daffodil.section15.choice_groups

import org.junit.Test
import org.apache.daffodil.tdml.Runner
import org.junit.AfterClass

object TestChoiceNest {
  val testDir = "/org/apache/daffodil/section15/choice_groups/"

  val runner = Runner(testDir, "choiceNests.tdml")

  @AfterClass def shutDown: Unit = {
    runner.reset
  }

}

class TestChoiceNest {

  import TestChoiceNest._

  @Test def test_choiceNest1(): Unit = { runner.runOneTest("choiceNest1") }
  @Test def test_choiceNest2(): Unit = { runner.runOneTest("choiceNest2") }
  @Test def test_choiceNest2a(): Unit = { runner.runOneTest("choiceNest2a") }
  @Test def test_choiceNest3(): Unit = { runner.runOneTest("choiceNest3") }
  @Test def test_choiceNest4(): Unit = { runner.runOneTest("choiceNest4") }

}
