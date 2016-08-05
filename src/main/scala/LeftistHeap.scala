// TODO: override equals and hashCode?
// TODO: improve algorithm of dropUntil and dropTo from O(n log n) to O(n)

package scads.immutable

import scala.collection.mutable.Builder

object LeftistHeap extends HeapCompanion {
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

      def takeUntil(e: Elem): Heap =
        if (ord.lt(elem, e)) makeNode(elem,left.takeUntil(e),right.takeUntil(e))
        else Empty
      def takeTo(e: Elem): Heap =
        if (ord.lteq(elem, e)) makeNode(elem,left.takeTo(e),right.takeTo(e))
        else Empty
      def dropUntil(e: Elem): Heap = // can be improved!
        if (ord.lt(elem, e)) left.dropUntil(e) merge right.dropUntil(e)
        else this
      def dropTo(e: Elem): Heap = // can be improved!
        if (ord.lteq(elem, e)) left.dropTo(e) merge right.dropTo(e)
        else this

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
      new LeftistFactory[E](ord)
  }
}
