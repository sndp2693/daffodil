/* Copyright (c) 2017 Tresys Technology, LLC. All rights reserved.
 *
 * Developed by: Tresys Technology, LLC
 *               http://www.tresys.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal with
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimers.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimers in the
 *     documentation and/or other materials provided with the distribution.
 *
 *  3. Neither the names of Tresys Technology, nor the names of its contributors
 *     may be used to endorse or promote products derived from this Software
 *     without specific prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS WITH THE
 * SOFTWARE.
 */

package edu.illinois.ncsa.daffodil.section13.packed

import edu.illinois.ncsa.daffodil.tdml.Runner
import org.junit.AfterClass
import org.junit.Test

object TestPacked {
  val testDir = "/edu/illinois/ncsa/daffodil/section13/packed/"
  lazy val runner = Runner(testDir, "packed.tdml")

  @AfterClass def shutdown(): Unit = {
    runner.reset
  }

}

class TestPacked {
  import TestPacked._

  @Test def testHexCharset01(): Unit = { runner.runOneTest("hexCharset01") }
  @Test def testHexCharset02(): Unit = { runner.runOneTest("hexCharset02") }
  // @Test def testHexCharset03(): Unit = { runner.runOneTest("hexCharset03") } // textNumberPattern V symbol
  @Test def testHexCharset04(): Unit = { runner.runOneTest("hexCharset04") }

  @Test def testPackedCharset01(): Unit = { runner.runOneTest("packedCharset01") }
  @Test def testPackedCharset02(): Unit = { runner.runOneTest("packedCharset02") }
  @Test def testPackedCharset03(): Unit = { runner.runOneTest("packedCharset03") }
  //@Test def testPackedCharset04(): Unit = { runner.runOneTest("packedCharset04") } // need packed calendar
  @Test def testPackedCharset05(): Unit = { runner.runOneTest("packedCharset05") }
  @Test def testPackedCharset06(): Unit = { runner.runOneTest("packedCharset06") }
  @Test def testPackedCharset07(): Unit = { runner.runOneTest("packedCharset07") }
  @Test def testPackedCharset08(): Unit = { runner.runOneTest("packedCharset08") }
  @Test def testPackedCharset09(): Unit = { runner.runOneTest("packedCharset09") }
  @Test def testPackedCharset10(): Unit = { runner.runOneTest("packedCharset10") }

  @Test def testBCDCharset01(): Unit = { runner.runOneTest("bcdCharset01") }
  @Test def testBCDCharset02(): Unit = { runner.runOneTest("bcdCharset02") }
  @Test def testBCDCharset03(): Unit = { runner.runOneTest("bcdCharset03") }
  //@Test def testBCDCharset04(): Unit = { runner.runOneTest("bcdCharset04") } // need bcd calendar
  @Test def testBCDCharset05(): Unit = { runner.runOneTest("bcdCharset05") }
  @Test def testBCDCharset06(): Unit = { runner.runOneTest("bcdCharset06") }
  @Test def testBCDCharset07(): Unit = { runner.runOneTest("bcdCharset07") }
  @Test def testBCDCharset08(): Unit = { runner.runOneTest("bcdCharset08") }
  @Test def testBCDCharset09(): Unit = { runner.runOneTest("bcdCharset09") }
  @Test def testBCDCharset10(): Unit = { runner.runOneTest("bcdCharset10") }
  @Test def testBCDCharset11(): Unit = { runner.runOneTest("bcdCharset11") }
  @Test def testBCDCharset12(): Unit = { runner.runOneTest("bcdCharset12") }
  @Test def testBCDCharset13(): Unit = { runner.runOneTest("bcdCharset13") }

  @Test def testIBM4690Charset01(): Unit = { runner.runOneTest("IBM4690Charset01") }
  @Test def testIBM4690Charset02(): Unit = { runner.runOneTest("IBM4690Charset02") }
  @Test def testIBM4690Charset03(): Unit = { runner.runOneTest("IBM4690Charset03") }
  //@Test def testIBM4690Charset04(): Unit = { runner.runOneTest("IBM4690Charset04") } // need bcd calendar
  @Test def testIBM4690Charset05(): Unit = { runner.runOneTest("IBM4690Charset05") }
  @Test def testIBM4690Charset06(): Unit = { runner.runOneTest("IBM4690Charset06") }
  @Test def testIBM4690Charset07(): Unit = { runner.runOneTest("IBM4690Charset07") }
  @Test def testIBM4690Charset08(): Unit = { runner.runOneTest("IBM4690Charset08") }
  @Test def testIBM4690Charset09(): Unit = { runner.runOneTest("IBM4690Charset09") }
  @Test def testIBM4690Charset10(): Unit = { runner.runOneTest("IBM4690Charset10") }
}
