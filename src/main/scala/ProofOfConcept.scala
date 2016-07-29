// This is a proof of concept for typesafe heaps with a merge operation.
// Here, "typesafe" means that the interface will never allow different
// orderings to be mixed within the same heap. In particular,
//   * when adding an element to an existing heap, that insertion
//     cannot involve an ordering different from the one used to
//     create the existing heap, and
//   * when merging two existing heaps, the heaps are guaranteed to
//     have been created with the same ordering.
//
// Ensuring the first condition is easy: the insertion method does not
// take an ordering as a parameter. (This might seem obvious but implementations
// adapted from Haskell to Scala often violate this rule.)
//
// Ensuring the second conditian is harder.  It can be achieved by
// careful staging where an ordering is supplied to create a new "factory",
// and then all heaps created from that factory are guaranteed to use the
// same ordering.  Crucially, path-dependent types ensure that attempting
// to merge heaps from different factories will be a type error detected at
// compile time.
//
// ***See design/heaps.md for more a fuller discussion of these issues.***
//
// Note that, for easier reference as a proof of concept, I've kept almost
// everything in the same file, even though the various traits would typically
// be written in separate files.  Also, I've limited the methods to a fairly
// small set. In the future, expect methods (and possibly traits) to be added,
// removed, renamed, and generally reorganized.

package scads.immutable

import scala.collection.mutable.Builder

/** A simple heap WITHOUT a `merge` method. */
trait SHeap[Elem] extends scads.HasInvariant {
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def size: Int

  def add(elem: Elem): SHeap[Elem]

  def first: Elem
  def firstOption: Option[Elem]
  def rest: SHeap[Elem]
  def firstView: Option[(Elem, SHeap[Elem])]

  def iterator: Iterator[Elem] // generates elements in an undefined order, should be O(N)

  def sorted: Iterator[Elem] = {
    var heap = this
    new scala.collection.AbstractIterator[Elem] {
      def hasNext: Boolean = heap.nonEmpty
      def next(): Elem = {
        if (heap.isEmpty) throw new NoSuchElementException("next on empty iterator")
        else {
          val result = heap.first
          heap = heap.rest
          result
        }
      }
    }
  }

  def mkString(start: String, sep: String, end: String) = iterator.mkString(start,sep,end)
  def mkString(sep: String): String = iterator.mkString(sep)
  def mkString: String = iterator.mkString
  override def toString(): String = mkString("Heap(",", ",")")
}

/** A more specialized type for heaps WITH a `merge` method. */
trait MHeap[Elem,Heap <: SHeap[Elem]] extends SHeap[Elem] {
  def add(elem: Elem): Heap
  def merge(other: Heap): Heap
  def rest: Heap
  def firstView: Option[(Elem, Heap)]
}

/** A factory for creating heaps.
  *
  * Every heap from the same fatory uses the same element type and
  * the same Ordering.
  */
trait HeapFactory {
  type Elem
  type Heap <: MHeap[Elem,Heap]

  def newBuilder: Builder[Elem,Heap]
  def empty: Heap
  def single(elem: Elem): Heap
  def apply(elems: Elem*): Heap
}

/** A factory for creating heap factories. */
trait HeapFactoryFactory {
  // Sigh. I'm well aware that having an interface for a factory of factories
  // can sometimes be a sign that a design has gone off the rails, that the
  // designer has drunk too much of the abstraction kool-aid and didn't know
  // when to stop.  In this case, if I only wanted to support one implementation
  // of heaps (say, leftist heaps), there would be no need for a factory
  // of factories.  But, eventually, I hope to support a variety of
  // implementations--for benchmarking comparisons if nothing else--so I'll
  // want those implementations to use a common interface.

  def minFactory[E](implicit ord: Ordering[E]): HeapFactory{ type Elem = E }
  def maxFactory[E](implicit ord: Ordering[E]): HeapFactory{ type Elem = E } =
    minFactory[E](ord.reverse)
  def pairedMinFactory[Key,Value](implicit ord: Ordering[Key]):
              HeapFactory { type Elem = (Key,Value) } =
    minFactory[(Key,Value)](Ordering.by[(Key,Value), Key](_._1)(ord))
  def pairedMaxFactory[Key,Value](implicit ord: Ordering[Key]):
              HeapFactory { type Elem = (Key,Value) } =
    pairedMinFactory(ord.reverse)

  // shortcuts for simple heaps, when the user doesn't need merge
  def minEmpty[E](implicit ord: Ordering[E]): SHeap[E] =
    minFactory[E](ord).empty
  def maxEmpty[E](implicit ord: Ordering[E]): SHeap[E] =
    maxFactory[E](ord).empty
  def minFrom[E](elems: E*)(implicit ord: Ordering[E]): SHeap[E] =
    minFactory[E](ord).apply(elems: _*)
  def maxFrom[E](elems: E*)(implicit ord: Ordering[E]): SHeap[E] =
    maxFactory[E](ord).apply(elems: _*)
}

// a sample implementation bosed an leftist heaps
object LeftistHeap extends HeapFactoryFactory {
  private class LeftistFactory[E](ord: Ordering[E]) extends HeapFactory {
    // smaller elements (according to ord) come before bigger elements
    type Elem = E
    sealed trait Heap extends MHeap[Elem, Heap] {
      def iterator: Iterator[Elem] = {
        import scala.collection.mutable.ArrayStack
        val stack: ArrayStack[Node] = ArrayStack.empty[Node]
        def push(h: Heap): Unit = h match {
          case Empty => {}
          case node: Node => stack += node
        }
        push(this)

        new scala.collection.AbstractIterator[Elem] {
          def hasNext: Boolean = stack.nonEmpty
          def next(): Elem = {
            if (stack.isEmpty) throw new NoSuchElementException("next on empty iterator")
            val Node(elem,left,right) = stack.pop()
            push(right)
            push(left)
            elem
          }
        }
      }

      // TODO: really should override equals and hashCode!
    }

    case object Empty extends Heap {
      def isEmpty: Boolean = true
      def nonEmpty: Boolean = false
      def size: Int = 0

      def first: Elem = throw new NoSuchElementException("first on empty heap")
      def firstOption: Option[Elem] = None
      def rest: Heap = throw new UnsupportedOperationException("rest on empty heap")
      def firstView: Option[(Elem, Heap)] = None

      def add(elem: Elem): Heap = Node(elem, Empty, Empty)
      def merge(other: Heap): Heap = other

      def checkInvariant(): Unit = {}
    }

    case class Node(elem: Elem, left: Heap, right: Heap) extends Heap {
      def isEmpty: Boolean = false
      def nonEmpty: Boolean = true
      val size: Int = left.size + right.size + 1

      def first: Elem = elem
      def firstOption: Option[Elem] = Some(elem)
      def rest: Heap = left merge right
      def firstView: Option[(Elem, Heap)] = Some((elem,left merge right))

      def add(elem: Elem): Heap = merge(Node(elem, Empty, Empty))
      def merge(other: Heap): Heap = other match {
        case Empty => this
        case Node(oelem, oleft, oright) =>
          if (ord.lteq(elem, oelem)) makeNode(elem, left, right merge other)
          else makeNode(oelem, oleft, this merge oright)
      }
      private def makeNode(elem: Elem, left: Heap, right: Heap): Node = {
        if (left.size < right.size) Node(elem, right, left)
        else Node(elem, left, right)
      }

      def checkInvariant(): Unit = {
        if (left.size < right.size)
          invariantViolation(s"LeftistHeap: left.size should be >= right.size but left.size=${left.size} and right.size=${right.size}")
        if (left.nonEmpty && ord.lt(left.first,first))
          invariantViolation(s"LeftistHeap: left.first should come after first in ordering but left.first=${left.first} and first=$first")
        if (right.nonEmpty && ord.lt(right.first,first))
          invariantViolation(s"LeftistHeap: right.first should come after first in ordering but right.first=${right.first} and first=$first")
        left.checkInvariant()
        right.checkInvariant()
      }
    }

    def empty: Heap = Empty
    def single(elem: Elem): Heap = Node(elem,Empty,Empty)
    def apply(elems: Elem*): Heap = {
      var len = elems.length
      val array = new Array[Heap](len)
      for (i <- 0 until len) array(i) = Node(elems(i),Empty,Empty)
      while (len > 1) {
        val half = (len-1)/2
        for (i <- 0 until half) array(i) = array(2*i) merge array(2*i+1)
        if (len % 2 == 0) len = half
        else {
          array(half) = array(len-1)
          len = half+1
        }
      }
      if (len == 0) Empty
      else array(1)
    }
    def newBuilder: Builder[Elem,Heap] = new Builder[Elem,Heap] {
      val buffer = scala.collection.mutable.ArrayBuffer.empty[Elem]
      def +=(elem: Elem) = { buffer += elem; this }
      def clear() = buffer.clear()
      def result(): Heap = apply(buffer: _*)
    }
  }

  def minFactory[E](implicit ord: Ordering[E]): HeapFactory{ type Elem = E } =
    new LeftistFactory[E](ord)
}
