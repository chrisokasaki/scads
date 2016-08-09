import scads.immutable._
import org.scalacheck._

object SkewHeapTest extends Properties("SkewHeap") with HeapTest {
  def companion: HeapCompanion = SkewHeap
}
