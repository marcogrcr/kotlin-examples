import kotlin.collections.ArrayDeque as KotlinArrayDeque
import kotlin.collections.ArrayList as KotlinArrayList
import kotlin.collections.HashMap as KotlinHashMap
import kotlin.collections.HashSet as KotlinHashSet
import kotlin.collections.LinkedHashMap as KotlinLinkedHashMap
import kotlin.collections.LinkedHashSet as KotlinLinkedHashSet
import java.util.ArrayDeque as JavaArrayDeque
import java.util.ArrayList as JavaArrayList
import java.util.HashMap as JavaHashMap
import java.util.HashSet as JavaHashSet
import java.util.LinkedHashMap as JavaLinkedHashMap
import java.util.LinkedHashSet as JavaLinkedHashSet

fun main() {
    // NOTE: `::MyClass` is a reference to `MyClass` constructor.

    // KotlinArrayList == JavaArrayList
    // Hash code and equality works as expected, order matters
    compareCollection(::KotlinArrayList)
    print("----------\n")
    compareCollection(::JavaArrayList)
    print("----------\n")

    // KotlinArrayDeque != JavaArrayDeque so we get different results
    // KotlinArrayDeque: Hash code and equality works as expected, order matters
    // JavaArrayDequeue: Hash code and equality does not work as expected, even with same order
    compareCollection(::KotlinArrayDeque)
    print("----------\n")
    compareCollection(::JavaArrayDeque)
    print("----------\n")

    // KotlinHashSet == JavaHashSet
    // Hash code and equality works as expected, order does not matter
    compareCollection(::KotlinHashSet)
    print("----------\n")
    compareCollection(::JavaHashSet)
    print("----------\n")

    // KotlinLinkedHashSet == JavaLinkedHashSet
    // Hash code and equality works as expected, order does not matter
    compareCollection(::KotlinLinkedHashSet)
    print("----------\n")
    compareCollection(::JavaLinkedHashSet)
    print("----------\n")

    // KotlinHashMap == JavaHashMap
    // Hash code and equality works as expected, order does not matter
    compareMap(::KotlinHashMap)
    print("----------\n")
    compareMap(::JavaHashMap)
    print("----------\n")

    // KotlinLinkedHashMap == JavaLinkedHashMap
    // Hash code and equality works as expected, order does not matter
    compareMap(::KotlinLinkedHashMap)
    print("----------\n")
    compareMap(::JavaLinkedHashMap)
    print("----------\n")

    // Hash code and equality does not work as expected
    comparePerson(::PlainPerson)
    print("----------\n")

    // Hash code and equality works as expected
    comparePerson(::DataPerson)
    print("----------\n")
}

// represents a person interface for implementing with two
// nearly-identical implementations with an important difference
interface Person {
    val id: Int
    val name: String
}

// normal classes do not override the `.hashCode()` and `.equals()` methods, as a result:
// `.hashCode()` will return a value that depends on the object instance
// `.equals()` will return true only when both objects are the same instance
// see: https://github.com/openjdk/jdk/blob/jdk-17+35/src/java.base/share/classes/java/lang/Object.java
class PlainPerson(
    override val id: Int,
    override val name: String
) : Person

// data classes override the `.hashCode()` and `.equals()` methods, as a result:
// `.hasCode()` will return a value dependent on the `.hashCode()` values of its fields
// `.equals()` will return a value dependent on the `.equals()` values of its fields
data class DataPerson(
    override val id: Int,
    override val name: String
) : Person

// prints the object's `.hashCode()` and the result of `.equals()`
// note that `==` invokes `.equals()`, whereas `===` compares the object instances
fun printComparison(type: String, left: Any, right: Any) {
    println("${left.javaClass.name} | $type | hash code: ${left.hashCode()}, ${right.hashCode()} | equals: ${left == right}")
}

// helper method to compare `Collection<T>` instances
fun <T : Collection<Int>> compareCollection(factory: (c: Collection<Int>) -> T) {
    val sameOrderLeft = factory(listOf(1, 2, 3))
    val sameOrderRight = factory(listOf(1, 2, 3))
    printComparison("same order", sameOrderLeft, sameOrderRight)

    val differentOrderLeft = factory(listOf(1, 2, 3))
    val differentOrderRight = factory(listOf(3, 2, 1))
    printComparison("different order", differentOrderLeft, differentOrderRight)
}

// helper method to compare `Map<K, V>` instances
fun <T : MutableMap<Int, String>> compareMap(factory: () -> T) {
    val normalOrder: (MutableMap<Int, String>) -> MutableMap<Int, String> =
        { it[1] = "one"; it[2] = "two"; it[3] = "three"; it }
    val reverseOrder: (MutableMap<Int, String>) -> MutableMap<Int, String> =
        { it[3] = "three"; it[2] = "two"; it[1] = "one"; it }

    val sameOrderLeft = normalOrder(factory())
    val sameOrderRight = normalOrder(factory())
    printComparison("same order", sameOrderLeft, sameOrderRight)

    val differentOrderLeft = normalOrder(factory())
    val differentOrderRight = reverseOrder(factory())
    printComparison("different order", differentOrderLeft, differentOrderRight)
}

// helper method to compare `Person` instances
fun <T : Person> comparePerson(factory: (Int, String) -> T) {
    val samePersonLeft = factory(1, "John Doe")
    val samePersonRight = factory(1, "John Doe")
    printComparison("same person", samePersonLeft, samePersonRight)

    val differentPersonLeft = factory(1, "John Doe")
    val differentPersonRight = factory(2, "Jane Doe")
    printComparison("different person", differentPersonLeft, differentPersonRight)
}

main()
