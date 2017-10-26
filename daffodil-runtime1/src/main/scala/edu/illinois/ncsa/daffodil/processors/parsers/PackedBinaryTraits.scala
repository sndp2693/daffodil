/* Copyright (c) 2012-2014 Tresys Technology, LLC. All rights reserved.
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

package edu.illinois.ncsa.daffodil.processors.parsers

import edu.illinois.ncsa.daffodil.processors.ElementRuntimeData
import edu.illinois.ncsa.daffodil.processors.ParseOrUnparseState
import edu.illinois.ncsa.daffodil.processors.FieldDFAParseEv
import edu.illinois.ncsa.daffodil.processors.TextJustificationType
import edu.illinois.ncsa.daffodil.processors.dfa.TextDelimitedParserBase
import edu.illinois.ncsa.daffodil.util.Maybe
import edu.illinois.ncsa.daffodil.exceptions.Assert
import edu.illinois.ncsa.daffodil.equality._
import edu.illinois.ncsa.daffodil.processors.dfa
import edu.illinois.ncsa.daffodil.util.MaybeChar
import java.math.{ BigInteger => JBigInteger, BigDecimal => JBigDecimal }
import java.nio.charset.StandardCharsets
import passera.unsigned.ULong

trait PackedBinaryConversion {
  def toBigInteger(num: Array[Byte]): JBigInteger
  def toBigDecimal(num: Array[Byte], scale: Int): JBigDecimal
}

abstract class PackedBinaryDecimalBaseParser(
  e: ElementRuntimeData,
  binaryDecimalVirtualPoint: Int)
  extends PrimParserObject(e)
  with PackedBinaryConversion {

  protected def getBitLength(s: ParseOrUnparseState): Int

  def parse(start: PState): Unit = {
    val nBits = getBitLength(start)
    if (nBits == 0) return // zero length is used for outputValueCalc often.
    val dis = start.dataInputStream

    if (!dis.isDefinedForLength(nBits)) {
      PE(start, "Insufficient bits in data. Needed %d bit(s) but found only %d available.", nBits, dis.remainingBits.get)
      return
    }

    try {
      val bigDec = toBigDecimal(dis.getByteArray(nBits, start), binaryDecimalVirtualPoint)
      start.simpleElement.overwriteDataValue(bigDec)
    } catch {
      case n: NumberFormatException => PE(start, "Error in packed data: \n%s", n.getMessage())
    }
  }
}

abstract class PackedBinaryIntegerBaseParser(
  e: ElementRuntimeData,
  signed: Boolean = false)
  extends PrimParserObject(e)
  with PackedBinaryConversion {

  protected def getBitLength(s: ParseOrUnparseState): Int

  def parse(start: PState): Unit = {
    val nBits = getBitLength(start)
    if (nBits == 0) return // zero length is used for outputValueCalc often.
    val dis = start.dataInputStream

    if (!dis.isDefinedForLength(nBits)) {
      PE(start, "Insufficient bits in data. Needed %d bit(s) but found only %d available.", nBits, dis.remainingBits.get)
      return
    }

    try {
      val int = toBigInteger(dis.getByteArray(nBits, start))
      if (!signed && (int.signum != 1))
        PE(start, "Expected unsigned data but parsed a negative number")
      else
        start.simpleElement.overwriteDataValue(int)
    } catch {
      case n: NumberFormatException => PE(start, "Error in packed data: \n%s", n.getMessage())
    }
  }
}

abstract class PackedBinaryIntegerDelimitedBaseParser(
  e: ElementRuntimeData,
  textParser: TextDelimitedParserBase,
  fieldDFAEv: FieldDFAParseEv,
  isDelimRequired: Boolean)
  extends StringDelimitedParser(e, TextJustificationType.None, MaybeChar.Nope, textParser, fieldDFAEv, isDelimRequired)
  with PackedBinaryConversion {

  override def processResult(parseResult: Maybe[dfa.ParseResult], state: PState): Unit = {
    Assert.invariant(e.encodingInfo.isKnownEncoding && e.encodingInfo.knownEncodingCharset.charset =:= StandardCharsets.ISO_8859_1)

    if (!parseResult.isDefined) this.PE(state, "%s - %s - Parse failed.", this.toString(), e.diagnosticDebugName)
    else {
      val result = parseResult.get
      val field = if (result.field.isDefined) result.field.get else ""
      val fieldBytes = field.getBytes(StandardCharsets.ISO_8859_1)
      captureValueLength(state, ULong(0), ULong(fieldBytes.length * 8))
      if (field == "") {
        this.PE(state, "%s - %s - Parse failed.", this.toString(), e.diagnosticDebugName)
        return
      } else {
        try {
          val num = toBigInteger(fieldBytes)
          state.simpleElement.setDataValue(num)
        } catch {
          case n: NumberFormatException => PE(state, "Error in packed data: \n%s", n.getMessage())
        }

        if (result.matchedDelimiterValue.isDefined) state.saveDelimitedParseResult(parseResult)
      }
      return
    }
  }
}


abstract class PackedBinaryDecimalDelimitedBaseParser(
  e: ElementRuntimeData,
  textParser: TextDelimitedParserBase,
  fieldDFAEv: FieldDFAParseEv,
  isDelimRequired: Boolean,
  binaryDecimalVirtualPoint: Int)
  extends StringDelimitedParser(e, TextJustificationType.None, MaybeChar.Nope, textParser, fieldDFAEv, isDelimRequired)
  with PackedBinaryConversion {

  /**
   * We are treating packed binary formats as just a string in iso-8859-1 encoding.
   *
   * This works because java/scala's decoder for iso-8859-1 does not implement any
   * unmapping error detection. The official definition of iso-8859-1 has a few unmapped
   * characters, but most interpretations of iso-8859-1 implement these code points anyway, with
   * their unicode code points being exactly the byte values (interpreted unsigned).
   *
   * So, in scala/java anyway, it appears one can use iso-8859-1 as characters corresponding to
   * raw byte values.
   */

  override def processResult(parseResult: Maybe[dfa.ParseResult], state: PState): Unit = {
    Assert.invariant(e.encodingInfo.isKnownEncoding && e.encodingInfo.knownEncodingCharset.charset =:= StandardCharsets.ISO_8859_1)

    if (!parseResult.isDefined) this.PE(state, "%s - %s - Parse failed.", this.toString(), e.diagnosticDebugName)
    else {
      val result = parseResult.get
      val field = if (result.field.isDefined) result.field.get else ""
      val fieldBytes = field.getBytes(StandardCharsets.ISO_8859_1)
      captureValueLength(state, ULong(0), ULong(fieldBytes.length * 8))
      if (field == "") {
        this.PE(state, "%s - %s - Parse failed.", this.toString(), e.diagnosticDebugName)
        return
      } else {
        try {
          val num = toBigDecimal(fieldBytes, binaryDecimalVirtualPoint)
          state.simpleElement.setDataValue(num)
        } catch {
          case n: NumberFormatException => PE(state, "Error in packed data: \n%s", n.getMessage())
        }

        if (result.matchedDelimiterValue.isDefined) state.saveDelimitedParseResult(parseResult)
      }
      return
    }
  }
}
