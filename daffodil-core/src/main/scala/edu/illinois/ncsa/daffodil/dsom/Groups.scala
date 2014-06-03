package edu.illinois.ncsa.daffodil.dsom

/* Copyright (c) 2012-2013 Tresys Technology, LLC. All rights reserved.
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

import java.util.UUID

import scala.Option.option2Iterable
import scala.xml.Attribute
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq.seqToNodeSeq
import scala.xml.Null
import scala.xml.Text
import scala.xml._

import edu.illinois.ncsa.daffodil.Implicits.ns2String
import edu.illinois.ncsa.daffodil.exceptions.Assert
import edu.illinois.ncsa.daffodil.schema.annotation.props.AlignmentType
import edu.illinois.ncsa.daffodil.schema.annotation.props.SeparatorSuppressionPolicyMixin
import edu.illinois.ncsa.daffodil.schema.annotation.props.gen.AlignmentUnits
import edu.illinois.ncsa.daffodil.schema.annotation.props.gen.Choice_AnnotationMixin
import edu.illinois.ncsa.daffodil.schema.annotation.props.gen.Group_AnnotationMixin
import edu.illinois.ncsa.daffodil.schema.annotation.props.gen.OccursCountKind
import edu.illinois.ncsa.daffodil.schema.annotation.props.gen.SequenceKind
import edu.illinois.ncsa.daffodil.schema.annotation.props.gen.Sequence_AnnotationMixin
import edu.illinois.ncsa.daffodil.util.ListUtils
import edu.illinois.ncsa.daffodil.xml.XMLUtils

/////////////////////////////////////////////////////////////////
// Groups System
/////////////////////////////////////////////////////////////////

// A term is content of a group
abstract class Term(xmlArg: Node, parentArg: SchemaComponent, val position: Int)
  extends AnnotatedSchemaComponent(xmlArg, parentArg)
  with LocalComponentMixin
  with TermGrammarMixin
  with DelimitedRuntimeValuedPropertiesMixin
  with InitiatedTerminatedMixin {

  /**
   * An integer which is the alignment of this term. This takes into account the
   * representation, type, charset encoding and alignment-related properties.
   */
  def alignmentValueInBits: Int

  /**
   * True if it is sensible to scan this data e.g., with a regular expression.
   * Requires that all children have same encoding as enclosing groups and
   * elements, requires that there is no leading or trailing alignment regions,
   * skips. We have to be able to determine that we are for sure going to
   * always be properly aligned for text.
   * <p>
   * Caveat: we only care that the encoding is the same if the term
   * actually could have text (couldHaveText is an LV) as part of its
   * representation. For example, a sequence
   * with no initiator, terminator, nor separators can have any encoding at all,
   * without disqualifying an element containing it from being scannable. There
   * has to be text that would be part of the scan.
   * <p>
   * If the root element isScannable, and encodingErrorPolicy is 'replace',
   * then we can use a lower-overhead I/O layer - basically we can use a java.io.InputStreamReader
   * directly.
   * <p>
   * We are going to depend on the fact that if the encoding is going to be this
   * US-ASCII-7Bit-PACKED thingy (7-bits wide code units, so aligned at 1 bit) that
   * this encoding must be specified statically in the schema.
   * <p>
   * If an encoding is determined at runtime, then we will
   * insist on it being 8-bit aligned code units.
   */

  final lazy val isScannable: Boolean = {
    if (!this.isRepresented) true
    else {
      val res = summaryEncoding match {
        case Mixed => false
        case Binary => false
        case NoText => false
        case Runtime => false
        case _ => true
      }
      res
    }
  }

  /**
   * If s1 and s2 are the same encoding name
   * then s1, else "mixed". Also "notext" combines
   * with anything.
   */
  def combinedEncoding(
    s1: EncodingLattice,
    s2: EncodingLattice): EncodingLattice = {
    (s1, s2) match {
      case (x, y) if (x == y) => x
      case (Mixed, _) => Mixed
      case (_, Mixed) => Mixed
      case (Binary, Binary) => Binary
      case (Binary, _) => Mixed
      case (_, Binary) => Mixed
      case (NoText, x) => x
      case (x, NoText) => x
      case (x, y) => Mixed
    }
  }

  /**
   * Roll up from the bottom. This is abstract interpretation.
   * The top (aka conflicting encodings) is "mixed"
   * The bottom is "noText" (combines with anything)
   * The values are encoding names, or "runtime" for expressions.
   * <p>
   * By doing expression analysis we could do a better job
   * here and determine when things that use expressions
   * to get the encoding are all going to get the same
   * expression value. For now, if it is an expression
   * then we lose.
   */
  lazy val summaryEncoding: EncodingLattice = {
    val myEnc = if (!isRepresented) NoText
    else if (!isLocallyTextOnly) Binary
    else if (!couldHaveText) NoText
    else if (!this.isKnownEncoding) Runtime
    else Encoding(this.knownEncodingName)
    val childEncs: Seq[EncodingLattice] = termChildren.map { x => x.summaryEncoding }
    val res = childEncs.fold(myEnc) { (x, y) => combinedEncoding(x, y) }
    res
  }

  /**
   * True if this term is known to have some text aspect. This can be the value, or it can be
   * delimiters.
   * <p>
   * False only if this term cannot ever have text in it. Example: a sequence with no delimiters.
   * Example: a binary int with no delimiters.
   * <p>
   * Note: this is not recursive - it does not roll-up from children terms.
   * TODO: it does have to deal with the prefix length situation. The type of the prefix
   * may be textual.
   * <p>
   * Override in element base to take simple type or prefix length situations into account
   */
  lazy val couldHaveText = hasDelimiters

  /**
   * Returns true if this term either cannot conflict because it has no textual
   * aspects, or if it couldHaveText then the encoding must be same.
   */
  def hasCompatibleEncoding(t2: Term): Boolean = {
    if (!this.couldHaveText) true
    else if (!t2.couldHaveText) true
    else this.knownEncodingCharset == t2.knownEncodingCharset
  }

  /**
   * True if this element itself consists only of text. No binary stuff like alignment
   * or skips.
   * <p>
   * Not recursive into contained children.
   */
  def isLocallyTextOnly: Boolean

  //TODO: if we add recursive types capability to DFDL this will have to change
  // but so will many of these compiler passes up and down through the DSOM objects.

  /**
   * The termChildren are the children that are Terms, i.e., derived from the Term
   * base class. This is to make it clear
   * we're not talking about the XML structures inside the XML parent (which might
   * include annotations, etc.
   */
  def termChildren: Seq[Term]

  val tID = UUID.randomUUID()

  // Scala coding style note: This style of passing a constructor arg that is named fooArg,
  // and then having an explicit val/lazy val which has the 'real' name is 
  // highly recommended. Lots of time wasted because a val constructor parameter can be 
  // accidently hidden if a derived class uses the same name as one of its own parameters.
  // These errors just seem easier to deal with if you use the fooArg style. 

  lazy val someEnclosingComponent = enclosingComponent.getOrElse(Assert.invariantFailed("All terms except a root element have an enclosing component."))

  lazy val referredToComponent = this // override in ElementRef and GroupRef

  lazy val isRepresented = true // overridden by elements, which might have inputValueCalc turning this off

  def isScalar = true // override in local elements

  lazy val allTerminatingMarkup: List[(CompiledExpression, String, String)] = {
    val (tElemName, tElemPath) = this.terminatorLoc
    val tm = List((this.terminator, tElemName, tElemPath)) ++ this.allParentTerminatingMarkup
    tm.filter { case (delimValue, elemName, elemPath) => delimValue.isKnownNonEmpty }
  }

  lazy val allParentTerminatingMarkup: List[(CompiledExpression, String, String)] = {
    // Retrieves the terminating markup for all parent
    // objects

    // TODO: This is not entirely correct as it assumes that separator and terminator
    // will always be defined.  It's entirely possible that one or neither is defined.
    // The call to this non-existant property will result in an SDE.
    // See created issue DFDL-571
    val pTM: List[(CompiledExpression, String, String)] = parent match {
      case s: Sequence => {
        val (sElemName, sElemPath) = s.separatorLoc
        val (tElemName, tElemPath) = s.terminatorLoc
        List((s.separator, sElemName, sElemPath), (s.terminator, tElemName, tElemPath)) ++ s.allParentTerminatingMarkup
      }
      case c: Choice => {
        val (tElemName, tElemPath) = c.terminatorLoc
        List((c.terminator, tElemName, tElemPath)) ++ c.allParentTerminatingMarkup
      }
      case d: SchemaDocument =>
        // we're a global object. Our parent is a schema document
        // so follow backpointers to whatever is referencing us.
        this match {
          case ge: GlobalElementDecl => ge.elementRef match {
            case None => {
              // we are root. So there is no enclosing sequence at all
              List.empty
            }
            case Some(er) => er.allTerminatingMarkup
          }
        }
      case ct: LocalComplexTypeDef => ct.parent match {
        case local: LocalElementDecl => local.allTerminatingMarkup
        case global: GlobalElementDecl => {
          global.elementRef match {
            case None => {
              val (tElemName, tElemPath) = global.terminatorLoc
              List((global.terminator, tElemName, tElemPath))
            }
            case Some(eRef) => eRef.allTerminatingMarkup
          }
        }
        case _ => Assert.impossibleCase()
      }
      // global type, we have to follow back to the element referencing this type
      case ct: GlobalComplexTypeDef => {
        // Since we are a term directly inside a global complex type def,
        // our nearest enclosing sequence is the one enclosing the element that
        // has this type. 
        //
        // However, that element might be local, or might be global and be referenced
        // from an element ref.
        //
        ct.element match {
          case local: LocalElementDecl => local.allTerminatingMarkup
          case global: GlobalElementDecl => {
            global.elementRef match {
              case None => {
                val (tElemName, tElemPath) = global.terminatorLoc
                List((global.terminator, tElemName, tElemPath))
              }
              case Some(eRef) => eRef.allTerminatingMarkup
            }
          }
          case _ => Assert.impossibleCase()
        }
      }
      case gd: GlobalGroupDef => gd.groupRef.allTerminatingMarkup
      // We should only be asking for the enclosingSequence when there is one.
      case _ => Assert.invariantFailed("No parent terminating markup for : " + this)
    }
    val res = pTM.filter { case (delimValue, elemName, elemPath) => delimValue.isKnownNonEmpty }
    res
  }

  /**
   * nearestEnclosingSequence
   *
   * An attribute that looks upward to the surrounding
   * context of the schema, and not just lexically surrounding context. It needs to see
   * what declarations will physically surround the place. This is the dynamic scope,
   * not just the lexical scope. So, a named global type still has to be able to
   * ask what sequence is surrounding the element that references the global type.
   *
   * This is why we have to have the GlobalXYZDefFactory stuff. Because this kind of back
   * pointer (contextual sensitivity) prevents sharing.
   */
  lazy val nearestEnclosingSequence: Option[Sequence] = enclosingTerm match {
    case None => None
    case Some(s: Sequence) => Some(s)
    case Some(_) => enclosingTerm.get.nearestEnclosingSequence
  }

  lazy val nearestEnclosingChoiceBeforeSequence: Option[Choice] = enclosingTerm match {
    case None => None
    case Some(s: Sequence) => None
    case Some(c: Choice) => Some(c)
    case Some(_) => enclosingTerm.get.nearestEnclosingChoiceBeforeSequence
  }

  lazy val nearestEnclosingUnorderedSequence: Option[Sequence] = enclosingTerm match {
    case None => None
    case Some(s: Sequence) if !s.isOrdered => Some(s)
    case Some(_) => enclosingTerm.get.nearestEnclosingUnorderedSequence
  }

  lazy val nearestEnclosingUnorderedSequenceBeforeSequence: Option[Sequence] = enclosingTerm match {
    case None => None
    case Some(s: Sequence) if !s.isOrdered => Some(s)
    case Some(s: Sequence) => None
    case Some(_) => enclosingTerm.get.nearestEnclosingUnorderedSequence
  }

  lazy val inChoiceBeforeNearestEnclosingSequence: Boolean = enclosingTerm match {
    case None => false
    case Some(s: Sequence) => false
    case Some(c: Choice) => true
    case Some(_) => enclosingTerm.get.inChoiceBeforeNearestEnclosingSequence
  }

  lazy val nearestEnclosingElement: Option[ElementBase] = enclosingTerm match {
    case None => None
    case Some(eb: ElementBase) => Some(eb)
    case Some(_) => enclosingTerm.get.nearestEnclosingElement
  }

  lazy val thisTermNoRefs: Term = thisTermNoRefs_.value
  private val thisTermNoRefs_ = LV('thisTermNoRefs) {
    val es = nearestEnclosingSequence

    val thisTerm = this match {
      case eRef: ElementRef => eRef.referencedElement
      // case gd: GlobalGroupDef => gd.thisTermNoRefs // TODO: scala 2.10 compiler says this line is impossible. 
      case gb: GroupBase if gb.enclosingTerm.isDefined => {
        // We're a group.  We need to determine what we're enclosed by.
        gb.enclosingTerm.get match {
          case encGRef: GroupRef => {
            // We're enclosed by a GroupRef.  We need to retrieve
            // what encloses that GroupRef 

            val res = encGRef.enclosingTerm match {
              case None => encGRef.group
              case Some(encTerm) => encTerm.thisTermNoRefs
            }
            //encGRef.thisTerm
            res
          }
          case encGB: GroupBase if es.isDefined && encGB == es.get => {
            // We're an immediate child of the nearestEnclosingSequence
            // therefore we just return our self as the Term
            this
          }
          case e: LocalElementBase => e // Immediate enclosed by LocalElementBase, return it.
          case _ => gb.group
        }
      }
      case gb: GroupBase => gb.group
      case x => x
    }
    thisTerm
  }

  /**
   * We want to determine if we're in an unordered sequence
   * at any point along our parents.
   */
  lazy val inUnorderedSequence: Boolean =
    nearestEnclosingSequence match {
      case None => {
        false
      }
      case Some(s) => {
        if (s.isOrdered) {
          val result = s.inUnorderedSequence
          result
        } else true
      }
    }

  lazy val immediatelyEnclosingModelGroup: Option[ModelGroup] = {
    val res = parent match {
      case c: Choice => Some(c)
      case s: Sequence => Some(s)
      case d: SchemaDocument => {
        // we're a global object. Our parent is a schema document
        // so follow backpointers to whatever is referencing us.
        this match {
          case ge: GlobalElementDecl => ge.elementRef match {
            case None => {
              // we are root. So there is no enclosing model group at all
              None
            }
            case Some(er) => er.immediatelyEnclosingModelGroup
          }
        }
      }
      case gdd: GlobalGroupDef => gdd.groupRef.immediatelyEnclosingModelGroup
      case ct: ComplexTypeBase => {
        None
        // The above formerly was ct.element.immediatelyEnclosingModelGroup, 
        // but if we have a CT as our parent, the group around the element whose type 
        // that is, isn't "immediately enclosing".
      }
      case _ => Assert.invariantFailed("immediatelyEnclosingModelGroup called on " + this + "with parent " + parent)
    }
    res
  }

  lazy val positionInNearestEnclosingSequence: Int = {
    val res =
      if (enclosingComponent == nearestEnclosingSequence) position
      else {
        enclosingComponent match {
          case Some(term: Term) => term.positionInNearestEnclosingSequence
          case Some(ct: ComplexTypeBase) => {
            val ctElem = ct.element
            val ctPos = ctElem.positionInNearestEnclosingSequence
            ctPos
          }
          case Some(ggd: GlobalGroupDef) => ggd.groupRef.positionInNearestEnclosingSequence
          case _ => Assert.invariantFailed("unable to compute position in nearest enclosing sequence")
        }
      }
    res
  }

  lazy val terminatingMarkup: List[CompiledExpression] = {
    if (hasTerminator) List(terminator)
    else nearestEnclosingSequence match {
      case None => Nil
      case Some(sq) => {
        val sep = {
          if (sq.hasInfixSep || sq.hasPostfixSep) List(sq.separator)
          else Nil
        }
        if (!hasLaterRequiredSiblings) {
          val entm = sq.terminatingMarkup
          val res = sep ++ entm
          res
        } else {
          sep
        }
      }
    }
  }

  lazy val prettyTerminatingMarkup =
    terminatingMarkup.map { _.prettyExpr }.map { "'" + _ + "'" }.mkString(" ")

  lazy val isDirectChildOfSequence = parent.isInstanceOf[Sequence]

  import edu.illinois.ncsa.daffodil.util.ListUtils

  lazy val allSiblings: Seq[Term] = {
    val res = nearestEnclosingSequence.map { enc =>
      val allSiblings = enc.groupMembers.map { _.referredToComponent }
      allSiblings
    }
    res.getOrElse(Nil)
  }

  lazy val priorSiblings = ListUtils.preceding(allSiblings, this)
  lazy val laterSiblings = ListUtils.tailAfter(allSiblings, this)

  lazy val priorSibling = priorSiblings.lastOption
  lazy val nextSibling = laterSiblings.headOption

  lazy val hasLaterRequiredSiblings = laterSiblings.exists(_.hasStaticallyRequiredInstances)
  lazy val hasPriorRequiredSiblings = priorSiblings.exists(_.hasStaticallyRequiredInstances)

  def hasStaticallyRequiredInstances: Boolean
  def isKnownRequiredElement = false
  def isKnownToBePrecededByAllByteLengthItems: Boolean = false
  def hasKnownRequiredSyntax = false

}

abstract class GroupBase(xmlArg: Node, parentArg: SchemaComponent, position: Int)
  extends Term(xmlArg, parentArg, position) {

  lazy val prettyIndex = {
    myPeers.map { peers =>
      {
        if (peers.length == 1) "" // no index expression if we are the only one
        else "[" + (peers.indexOf(this) + 1) + "]" // 1-based indexing in XML/XSD
      }
    }.getOrElse("")
  }

  override def prettyName = prettyBaseName + prettyIndex
  def prettyBaseName: String

  lazy val enclosingComponentModelGroup = enclosingComponent.collect { case mg: ModelGroup => mg }
  lazy val sequencePeers = enclosingComponentModelGroup.map { _.sequenceChildren }
  lazy val choicePeers = enclosingComponentModelGroup.map { _.choiceChildren }
  lazy val groupRefPeers = enclosingComponentModelGroup.map { _.groupRefChildren }

  def myPeers: Option[Seq[GroupBase]]

  def group: ModelGroup

  lazy val immediateGroup: Option[ModelGroup] = {
    val res: Option[ModelGroup] = this.group match {
      case (s: Sequence) => Some(s)
      case (c: Choice) => Some(c)
      case _ => None
    }
    res
  }

  lazy val alignmentValueChildren: Int = {
    immediateGroup match {
      case Some(m: ModelGroup) => {
        m.groupMembers.sortBy(m => -m.alignmentValueInBits).headOption match {
          case Some(child) => child.alignmentValueInBits
          case None => 0
        }
      }
      case None => 0
    }
  }
  lazy val alignmentValueInBits: Int = {
    this.alignment match {
      case AlignmentType.Implicit => alignmentValueChildren
      case align: Int => this.alignmentUnits match {
        case AlignmentUnits.Bits => align
        case AlignmentUnits.Bytes => 8 * align
      }
    }
  }

}

/**
 * A GroupRef (group reference) is a term, but most everything is delgated to the
 * referred-to Global Group Definition object.
 */
class GroupRef(xmlArg: Node, parent: SchemaComponent, position: Int)
  extends GroupBase(xmlArg, parent, position)
  with DFDLStatementMixin
  with GroupRefGrammarMixin
  with Group_AnnotationMixin
  with SeparatorSuppressionPolicyMixin
  with SequenceRuntimeValuedPropertiesMixin
  with HasRefMixin {

  requiredEvaluations(groupDef)

  // delegate to the model group object. It assembles properties from
  // the group ref and the group def
  override def findPropertyOption(pname: String): PropertyLookupResult = {
    val res = group.findPropertyOption(pname)
    res
  }
  lazy val nonDefaultPropertySources = group.nonDefaultPropertySources
  lazy val defaultPropertySources = group.defaultPropertySources

  lazy val prettyBaseName = "group.ref." + localName

  lazy val myPeers = groupRefPeers

  override lazy val termChildren: Seq[Term] = {
    group.termChildren
  }

  lazy val qname = resolveQName(ref)
  lazy val (namespace, localName) = qname

  def annotationFactory(node: Node): DFDLAnnotation = {
    node match {
      case <dfdl:group>{ contents @ _* }</dfdl:group> => new DFDLGroup(node, this)
      case _ => annotationFactoryForDFDLStatement(node, this)
    }
  }

  def emptyFormatFactory = new DFDLGroup(newDFDLAnnotationXML("group"), this)
  def isMyFormatAnnotation(a: DFDLAnnotation) = a.isInstanceOf[DFDLGroup]

  def hasStaticallyRequiredInstances = group.hasStaticallyRequiredInstances

  lazy val group = groupDef.modelGroup

  override lazy val couldHaveText = group.couldHaveText

  override lazy val isLocallyTextOnly = group.isLocallyTextOnly

  override lazy val referredToComponent = group

  lazy val groupDef = groupDef_.value
  private val groupDef_ = LV('groupDef) {
    this.schemaSet.getGlobalGroupDef(namespace, localName) match {
      case None => SDE("Referenced group definition not found: %s", this.ref)
      case Some(x) => x.forGroupRef(this, position)
    }
  }

  lazy val statements = localStatements
  lazy val newVariableInstanceStatements = localNewVariableInstanceStatements
  lazy val assertStatements = localAssertStatements
  lazy val discriminatorStatements = localDiscriminatorStatements
  lazy val setVariableStatements = localSetVariableStatements

}

