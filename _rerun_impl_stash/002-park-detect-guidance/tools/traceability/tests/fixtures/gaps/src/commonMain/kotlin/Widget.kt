package fixture

/**
 * Implements two requirements and one that does not exist.
 * @requirement FR-001, FR-002
 */
class Widget {
    /** @requirement FR-004 */
    fun spin() = Unit

    /** @requirement FR-005, FR-006 */
    val size: Int = 1

    /** @requirement FR-099 */
    fun obsolete() = Unit
}
