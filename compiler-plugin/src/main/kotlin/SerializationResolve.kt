import FqNames.SERIALIZABLE_ANNOTATION
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeProjectionImpl

fun createSerializerGetterDescriptor(
    companionClass: ClassDescriptor,
    serializableClass: ClassDescriptor
): SimpleFunctionDescriptor {
    val function = SimpleFunctionDescriptorImpl.create(
        companionClass,
        Annotations.EMPTY,
        Names.SERIALIZER_METHOD,
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        companionClass.source
    )

    val kserializerClass = serializableClass.module.findClassAcrossModuleDependencies(
        ClassId(FqName("kotlinx.serialization"), Name.identifier("KSerializer"))
    )!!

    // KSerializer<Data>
    val returnType = KotlinTypeFactory.simpleNotNullType(
        Annotations.EMPTY,
        kserializerClass,
        listOf(
            TypeProjectionImpl(serializableClass.defaultType)
        )
    )

    function.initialize(
        null,
        companionClass.thisAsReceiverParameter,
        emptyList(),
        emptyList(),
        returnType,
        Modality.FINAL,
        Visibilities.PUBLIC
    )

    return function
}

val ClassDescriptor.isSerializableCompanion
    get() = isCompanionObject && (containingDeclaration as ClassDescriptor).isSerializable

val ClassDescriptor.isSerializable
    get() = annotations.hasAnnotation(SERIALIZABLE_ANNOTATION)
