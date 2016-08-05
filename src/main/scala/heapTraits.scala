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
// Ensuring the second condition is harder.  It can be achieved by
// carefully staging where an ordering is supplied to create a new "factory",
// and then all heaps created from that factory are guaranteed to use the
// same ordering.  Crucially, path-dependent types ensure that attempting
// to merge heaps from different factories will be a type error detected at
// compile time.
//
// ***See design/heaps.md for more a fuller discussion of these issues.***
//
// Note that, for easier reference as a proof of concept, I've kept all
// the traits in the same file, even though the various traits would typically
// be written in separate files.  Also, I've limited the methods to a fairly
// small set. In the future, expect methods (and possibly traits) to be added,
// removed, renamed, and generally reorganized.
//
// See LeftistHeap.scala for a sample implementation using these traits.

// TODO: add scaladocs
// TODO: better name for firstView (because view tends to mean something different for Scala collections)
// TODO: what should the name/symbol for unapply be to support pattern matching?
// TODO: improve the algorithm of the default newBuilder/from in HeapFactory
// TODO: incorporate CanBuildFrom?

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

  def _addFirst(elem: Elem): SHeap[Elem]

  def takeUntil(e: Elem): SHeap[Elem]
  def takeTo(e: Elem): SHeap[Elem]
  def dropUntil(e: Elem): SHeap[Elem]
  def dropTo(e: Elem): SHeap[Elem]
}

/** A more specialized type for heaps WITH a `merge` method. */
trait MHeap[Elem,Heap <: SHeap[Elem]] extends SHeap[Elem] {
  def add(elem: Elem): Heap
  def merge(other: Heap): Heap
  def rest: Heap
  def firstView: Option[(Elem, Heap)] // need different name from View
  def _addFirst(elem: Elem): Heap

  def takeUntil(e: Elem): Heap
  def takeTo(e: Elem): Heap
  def dropUntil(e: Elem): Heap
  def dropTo(e: Elem): Heap
}

/** A factory for creating heaps.
  *
  * Every heap from the same fatory uses the same element type and
  * the same Ordering.
  */
trait HeapFactory {
  type Elem
  type Heap <: MHeap[Elem,Heap]

  def newBuilder: Builder[Elem,Heap] = new Builder[Elem,Heap] { // can be improved!
    val buffer = scala.collection.mutable.ArrayBuffer.empty[Elem]
    def +=(elem: Elem) = { buffer += elem; this }
    def clear() = buffer.clear()
    def result(): Heap = from(buffer)
  }
  def empty: Heap
  def single(elem: Elem): Heap = empty.add(elem)
  def apply(elems: Elem*): Heap = from(elems)
  def from(elems: TraversableOnce[Elem]): Heap = elems.foldLeft(empty)(_.add(_)) // inefficent! replace with something better!
  def _fromSorted(elems: Seq[Elem]): Heap = _fromReverseSorted(elems.reverse)
  def _fromReverseSorted(elems: Seq[Elem]): Heap = elems.foldLeft(empty)(_._addFirst(_))
}

trait HeapFactoryFactory {
  def factory[E](implicit ord: Ordering[E]): HeapFactory{ type Elem = E }
  def pairedFactory[Key,Value](implicit ord: Ordering[Key]):  HeapFactory { type Elem = (Key,Value) } =
    factory[(Key,Value)](Ordering.by[(Key,Value), Key](_._1)(ord))

  def empty[E](implicit ord: Ordering[E]): SHeap[E] = factory[E](ord).empty
  def single[E](elem: E)(implicit ord: Ordering[E]): SHeap[E] = factory[E](ord).single(elem)
  def apply[E](elems: E*)(implicit ord: Ordering[E]): SHeap[E] = factory[E](ord).from(elems)
  def from[E](elems: TraversableOnce[E])(implicit ord: Ordering[E]): SHeap[E] = factory[E](ord).from(elems)
}

trait HeapCompanion {
  val Min: HeapFactoryFactory
  val Max: HeapFactoryFactory = new HeapFactoryFactory {
    def factory[E](implicit ord: Ordering[E]): HeapFactory{ type Elem = E } =
      Min.factory[E](ord.reverse)
  }
}
