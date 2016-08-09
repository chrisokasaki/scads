// An implementation of skew heaps, with most of the code stolen from
// leftist heap.
//
// Note that the O(log n) amortized bounds for add and merge on skew heaps
// is NOT guaranteed to hold for immutable heaps, because you can create
// a "bad" skew heap that takes linear time for add/merge and then keep
// repeating that bad operation.
//
// Once I have a benchmarking harness up, I'll be able to compare this
// to leftist heaps.
//
// TODO: override equals and hashCode?
// TODO: improve algorithm of dropUntil and dropTo from O(n log n) to O(n)

package scads.immutable

import scala.collection.mutable.Builder

object SkewHeap extends HeapCompanion {
  private class SkewFactory[E](ord: Ordering[E]) extends HeapFactory {
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

      // assumes elem comes before (or equal) the current first
      def _addFirst(elem: Elem): Heap = Node(elem, this, Empty)
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

      def takeUntil(e: Elem): Heap = Empty
      def takeTo(e: Elem): Heap = Empty
      def dropUntil(e: Elem): Heap = Empty
      def dropTo(e: Elem): Heap = Empty

      def checkInvariant(): Unit = {}
    }

    case class Node(elem: Elem, left: Heap, right: Heap) extends Heap {
      def isEmpty: Boolean = false
      def nonEmpty: Boolean = true
      def size: Int = left.size + right.size + 1 // O(N) time!

      def first: Elem = elem
      def firstOption: Option[Elem] = Some(elem)
      def rest: Heap = left merge right
      def firstView: Option[(Elem, Heap)] = Some((elem,left merge right))

      def add(elem: Elem): Heap = merge(Node(elem, Empty, Empty))
      def merge(other: Heap): Heap = other match {
        case Empty => this
        case Node(oelem, oleft, oright) =>
          if (ord.lteq(elem, oelem)) Node(elem, right merge other, left)
          else Node(oelem, this merge oright, oleft)
      }

      def takeUntil(e: Elem): Heap =
        if (ord.lt(elem, e)) Node(elem,left.takeUntil(e),right.takeUntil(e))
        else Empty
      def takeTo(e: Elem): Heap =
        if (ord.lteq(elem, e)) Node(elem,left.takeTo(e),right.takeTo(e))
        else Empty
      def dropUntil(e: Elem): Heap = // can be improved!
        if (ord.lt(elem, e)) left.dropUntil(e) merge right.dropUntil(e)
        else this
      def dropTo(e: Elem): Heap = // can be improved!
        if (ord.lteq(elem, e)) left.dropTo(e) merge right.dropTo(e)
        else this

      def checkInvariant(): Unit = {
        if (left.nonEmpty && ord.lt(left.first,first))
          invariantViolation(s"SkewHeap: left.first should come after first in ordering but left.first=${left.first} and first=$first")
        if (right.nonEmpty && ord.lt(right.first,first))
          invariantViolation(s"SkewHeap: right.first should come after first in ordering but right.first=${right.first} and first=$first")
        left.checkInvariant()
        right.checkInvariant()
      }
    }

    def empty: Heap = Empty
    override def single(elem: Elem): Heap = Node(elem,Empty,Empty)
    override def apply(elems: Elem*): Heap = {
      var len = elems.length
      val array = new Array[Heap](len)
      for (i <- 0 until len) array(i) = Node(elems(i),Empty,Empty)
      while (len > 1) {
        val half = len/2
        for (i <- 0 until half) array(i) = array(2*i) merge array(2*i+1)
        if (len % 2 == 0) len = half
        else {
          array(half) = array(len-1)
          len = half+1
        }
      }
      if (len == 0) Empty
      else array(0)
    }
    override def newBuilder: Builder[Elem,Heap] = new Builder[Elem,Heap] {
      val buffer = scala.collection.mutable.ArrayBuffer.empty[Elem]
      def +=(elem: Elem) = { buffer += elem; this }
      def clear() = buffer.clear()
      def result(): Heap = apply(buffer: _*)
    }
  }

  object Min extends HeapFactoryFactory {
    def factory[E](implicit ord: Ordering[E]): HeapFactory{ type Elem = E } =
      new SkewFactory[E](ord)
  }
}
