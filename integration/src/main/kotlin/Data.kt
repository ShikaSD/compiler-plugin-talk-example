import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Data(val a: Int, val b: String = "42")

fun main() {
    val value = Data(0)
    println(Json.toJson(Data.serializer(), value))
}