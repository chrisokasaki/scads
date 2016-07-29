package scads

/** Mixin for a class with an internal invariant that can be checked at run-time.
  *
  * Example: {{{
  *   class MyClass extends HasInvariant {
  *     ...
  *     var list: List[Int] // must be kept sorted!
  *     ...
  *     def checkInvariant(): Unit =
  *       if (list != list.sorted) {
  *         invariantViolation(s"MyClass: list must be sorted, but list=$list")
   *      }
  *   }
  * }}}
  */
trait HasInvariant {

  /** Verifies that the invariant holds.
    *
    * Usually called during testing.
    *
    * @throws InvariantViolationException if the invariant is violated.
    */
  def checkInvariant(): Unit

  /** Shorthand for throwing an InvariantViolationException.
    *
    * Usually called from within `checkInvariant`.
    *
    * @throws InvariantViolationException
    */
  def invariantViolation(msg: String): Nothing =
    throw new InvariantViolationException(msg: String)
}


/** An exception that indicates a class invariant was violated.
  *
  * Usually thrown within the `checkInvariant` method of [[scads.HasInvariant]].
  */
class InvariantViolationException(msg: String) extends Exception(msg)
