import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object Names {
    val DEFAULT_COMPANION = Name.identifier("Companion")
    val SERIALIZER_METHOD = Name.identifier("serializer")
    val SERIALIZER_IMPL = Name.identifier("\$serializer")
}

internal object FqNames {
    val SERIALIZABLE_ANNOTATION = FqName("kotlinx.serialization.Serializable")
}

internal object ClassIds {
    val KSERIALIZER = ClassId(FqName("kotlinx.serialization"), Name.identifier("KSerializer"))
}
