// Resuable tests for different heap implementations.
// See LeftistHeapTest.scala and SkewHeapTest.scala for examples of usage.
// TODO: Needs more tests!

import scads.immutable._
import org.scalacheck._
import Prop._
import Arbitrary._

trait HeapTest { self: Properties =>
  def companion: HeapCompanion

  type E = Int // the element type used for most/all of the tests
  val minFactory: HeapFactory { type Elem = E } = companion.Min.factory[E]
  val maxFactory: HeapFactory { type Elem = E } = companion.Max.factory[E]

  property("minFactory.from") = forAll { list: List[E] =>
    val h = minFactory.from(list)
    h.checkInvariant()
    h.sorted.toList == list.sorted
  }
  property("maxFactory.from") = forAll { list: List[E] =>
    val h = maxFactory.from(list)
    h.checkInvariant()
    h.sorted.toList == list.sorted.reverse
  }
  property("isEmpty/size (minFactory)") = forAll {list: List[E] =>
    val h = minFactory.from(list)
    h.checkInvariant()
    h.isEmpty == list.isEmpty && h.size == list.size
  }
  property("isEmpty/size (maxFactory)") = forAll {list: List[E] =>
    val h = maxFactory.from(list)
    h.checkInvariant()
    h.isEmpty == list.isEmpty && h.size == list.size
  }
  property("add (minFactory)") = forAll { (list: List[E], x: E) =>
    val h = minFactory.from(list)
    val h2 = h.add(x)
    h2.checkInvariant()
    h2.sorted.toList == (x +: list).sorted
  }
  property("add (maxFactory)") = forAll { (list: List[E], x: E) =>
    val h = maxFactory.from(list)
    val h2 = h.add(x)
    h2.checkInvariant()
    h2.sorted.toList == (x +: list).sorted.reverse
  }
  property("first (minFactory)") = forAll { list: List[E] =>
    val h = minFactory.from(list)
    val fst = scala.util.Try(h.first)
    if (list.isEmpty) fst.isFailure
    else fst.isSuccess && fst.get == list.min
  }
  property("first (maxFactory)") = forAll { list: List[E] =>
    val h = maxFactory.from(list)
    val fst = scala.util.Try(h.first)
    if (list.isEmpty) fst.isFailure
    else fst.isSuccess && fst.get == list.max
  }
  property("rest (minFactory)") = forAll { list: List[E] =>
    val h = minFactory.from(list)
    val r = scala.util.Try(h.rest)
    if (list.isEmpty) r.isFailure
    else {
      r.get.checkInvariant()
      r.isSuccess && r.get.sorted.toList == list.sorted.tail
    }
  }
  property("rest (maxFactory)") = forAll { list: List[E] =>
    val h = maxFactory.from(list)
    val r = scala.util.Try(h.rest)
    if (list.isEmpty) r.isFailure
    else {
      r.get.checkInvariant()
      r.isSuccess && r.get.sorted.toList == list.sorted.reverse.tail
    }
  }
  property("merge (minFactory)") = forAll { (list1: List[E], list2: List[E]) =>
    val h1 = minFactory.from(list1)
    val h2 = minFactory.from(list2)
    val merged = h1.merge(h2)
    merged.checkInvariant()
    merged.sorted.toList == (list1 ++ list2).sorted
  }
  property("merge (maxFactory)") = forAll { (list1: List[E], list2: List[E]) =>
    val h1 = maxFactory.from(list1)
    val h2 = maxFactory.from(list2)
    val merged = h1.merge(h2)
    merged.checkInvariant()
    merged.sorted.toList == (list1 ++ list2).sorted.reverse
  }
  property("takeUntil (minFactory)") = forAll { (list: List[E], x: E) =>
    val h = minFactory.from(list)
    val hout = h.takeUntil(x)
    hout.checkInvariant()
    hout.sorted.toList == list.filter(_ < x).sorted
  }
  property("takeTo (minFactory)") = forAll { (list: List[E], x: E) =>
    val h = minFactory.from(list)
    val hout = h.takeTo(x)
    hout.checkInvariant()
    hout.sorted.toList == list.filter(_ <= x).sorted
  }
  property("dropUntil (minFactory)") = forAll { (list: List[E], x: E) =>
    val h = minFactory.from(list)
    val hout = h.dropUntil(x)
    hout.checkInvariant()
    hout.sorted.toList == list.filter(_ >= x).sorted
  }
  property("dropTo (minFactory)") = forAll { (list: List[E], x: E) =>
    val h = minFactory.from(list)
    val hout = h.dropTo(x)
    hout.checkInvariant()
    hout.sorted.toList == list.filter(_ > x).sorted
  }
  property("takeUntil (maxFactory)") = forAll { (list: List[E], x: E) =>
    val h = maxFactory.from(list)
    val hout = h.takeUntil(x)
    hout.checkInvariant()
    hout.sorted.toList == list.filter(_ > x).sorted.reverse
  }
  property("takeTo (maxFactory)") = forAll { (list: List[E], x: E) =>
    val h = maxFactory.from(list)
    val hout = h.takeTo(x)
    hout.checkInvariant()
    hout.sorted.toList == list.filter(_ >= x).sorted.reverse
  }
  property("dropUntil (maxFactory)") = forAll { (list: List[E], x: E) =>
    val h = maxFactory.from(list)
    val hout = h.dropUntil(x)
    hout.checkInvariant()
    hout.sorted.toList == list.filter(_ <= x).sorted.reverse
  }
  property("dropTo (maxFactory)") = forAll { (list: List[E], x: E) =>
    val h = maxFactory.from(list)
    val hout = h.dropTo(x)
    hout.checkInvariant()
    hout.sorted.toList == list.filter(_ < x).sorted.reverse
  }
}
