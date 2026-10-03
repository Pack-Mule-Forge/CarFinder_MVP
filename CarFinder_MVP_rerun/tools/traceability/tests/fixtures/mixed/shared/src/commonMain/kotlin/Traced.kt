package fixture

/**
 * Implements the traced requirement.
 * @requirement FR-001
 */
fun traced() = Unit

/** @requirement FR-002 */
fun untested() = Unit

/**
 * Tags an ID the spec does not define.
 * @requirement FR-099
 */
fun orphaned() = Unit

// @requirement FR-003 is only a line comment in Kotlin, so it is not read.
fun notATag() = Unit
