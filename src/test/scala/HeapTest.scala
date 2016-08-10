// Resuable tests for different heap implementations.
// See LeftistHeapTest.scala and SkewHeapTest.scala for examples of usage.
// TODO: Needs more tests, especially for pairedFactory
// TODO: Fix tests to account for the fact that "equal" elements according to
//   the ordering are not necessarily equal.  Therefore, it's not enough to test
//   (for example) that the first element in the heap is == the first element
//   in the sorted list.

import scads.immutable._
import org.scalacheck._
import Prop._
import Arbitrary._

trait HeapTest { self: Properties =>
  def companion: HeapCompanion

  type E = Int // the element type used for most/all of the tests

  def test(isMin: Boolean) {
    val name = if (isMin) "Min." else "Max."
    val hname = name + "factory.Heap."
    val hff: HeapFactoryFactory = if (isMin) companion.Min else companion.Max
    val factory = hff.factory[E]
    def sort(list: List[E]): List[E] = if (isMin) list.sorted else list.sorted.reverse
    def sameElements(heap: SHeap[E], list: List[E]): Boolean = heap.sorted.toList == sort(list)
    def firstOption(list: List[E]): Option[E] = sort(list).headOption
    val ord: Ordering[Int] = if (isMin) Ordering.Int else Ordering.Int.reverse

    property(name + "factory.from") = forAll { list: List[E] =>
      val h = factory.from(list)
      h.checkInvariant()
      sameElements(h, list)
    }
    property(name + "factory.apply") = forAll { list: List[E] =>
      val h = factory.apply(list : _*)
      h.checkInvariant()
      sameElements(h, list)
    }
    property(name + "factory.newBuilder") = forAll { list: List[E] =>
      val builder = factory.newBuilder
      for (x <- list) builder += x
      val h = builder.result
      h.checkInvariant()
      sameElements(h, list)
    }
    property(name + "factory._fromSorted") = forAll { list: List[E] =>
      val s = sort(list)
      val h = factory._fromSorted(s)
      h.checkInvariant()
      sameElements(h, s)
    }
    property(name + "factory._fromReverseSorted") = forAll { list: List[E] =>
      val s = sort(list).reverse
      val h = factory._fromReverseSorted(s)
      h.checkInvariant()
      sameElements(h, s)
    }
    property(name + "from") = forAll { list: List[E] =>
      val h = hff.from(list)
      h.checkInvariant()
      sameElements(h, list)
    }
    property(name + "apply") = forAll { list: List[E] =>
      val h = hff.apply(list : _*)
      h.checkInvariant()
      sameElements(h, list)
    }
    property(hname + "isEmpty/nonEmpty") = forAll {list: List[E] =>
      val h = factory.from(list)
      h.isEmpty == list.isEmpty && h.nonEmpty == list.nonEmpty
    }
    property(hname + "size") = forAll { list: List[E] =>
      val h = factory.from(list)
      h.size == list.size
    }
    property(hname + "add") = forAll { (list: List[E], x: E) =>
      val h = factory.from(list)
      val h2 = h.add(x)
      h2.checkInvariant()
      sameElements(h2, x +: list)
    }
    property(hname + "_addFirst") = forAll { list: List[E] => list.nonEmpty ==> {
      val x = firstOption(list).get
      val listWithoutX = list.takeWhile(_ != x) ++ list.dropWhile(_ != x).tail
      val h = factory.from(listWithoutX)
      val h2 = h._addFirst(x)
      h2.checkInvariant()
      sameElements(h2, list)
    }}
    property(hname + "first") = forAll { list: List[E] =>
      val h = factory.from(list)
      scala.util.Try(h.first).toOption == firstOption(list)
    }
    property(hname + "firstOption") = forAll { list: List[E] =>
      val h = factory.from(list)
      h.firstOption == firstOption(list)
    }
    property(hname + "rest") = forAll { list: List[E] =>
      val h = factory.from(list)
      val r = scala.util.Try(h.rest)
      if (list.isEmpty) r.isFailure
      else {
        val h2 = r.get
        h2.checkInvariant()
        sameElements(h2, sort(list).tail)
      }
    }
    property(hname + "firstView") = forAll { list: List[E] =>
      val h = factory.from(list)
      val s = sort(list)
      h.firstView match {
        case None => list.isEmpty
        case Some((x,h2)) =>
        h2.checkInvariant()
        x == s.head && sameElements(h2, s.tail)
      }
    }
    property(hname + "merge") = forAll { (list1: List[E], list2: List[E]) =>
      val h1 = factory.from(list1)
      val h2 = factory.from(list2)
      val merged = h1.merge(h2)
      merged.checkInvariant()
      sameElements(merged, list1 ++ list2)
    }
    property(hname + "takeUntil") = forAll { (list: List[E], x: E) =>
      val h = factory.from(list)
      val h2 = h.takeUntil(x)
      h2.checkInvariant()
      sameElements(h2, list.filter(ord.lt(_,x)))
    }
    property(hname + "takeTo") = forAll { (list: List[E], x: E) =>
      val h = factory.from(list)
      val h2 = h.takeTo(x)
      h2.checkInvariant()
      sameElements(h2, list.filter(ord.lteq(_,x)))
    }
    property(hname + "dropUntil") = forAll { (list: List[E], x: E) =>
      val h = factory.from(list)
      val h2 = h.dropUntil(x)
      h2.checkInvariant()
      sameElements(h2, list.filter(ord.gteq(_,x)))
    }
    property(hname + "dropTo") = forAll { (list: List[E], x: E) =>
      val h = factory.from(list)
      val h2 = h.dropTo(x)
      h2.checkInvariant()
      sameElements(h2, list.filter(ord.gt(_,x)))
    }
    property(hname + "iterator") = forAll { list: List[E] =>
      val h = factory.from(list)
      val list2 = h.iterator.toList
      list2.sorted == list.sorted
    }
  }

  test(true)
  test(false)

}
