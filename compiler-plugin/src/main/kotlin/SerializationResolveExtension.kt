import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

class SerializationResolveExtension : SyntheticResolveExtension {
    /**
     * Ensure companion is added to the class
     */
    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? =
        Names.DEFAULT_COMPANION

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> =
        if (thisDescriptor.isCompanionObject && thisDescriptor.isSerializableCompanion) {
            listOf(Names.SERIALIZER_METHOD)
        } else {
            emptyList()
        }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        if (name != Names.SERIALIZER_METHOD) return
        val classDescriptor = getSerializableForCompanion(thisDescriptor) ?: return

        result.add(createSerializerGetterDescriptor(thisDescriptor, classDescriptor))
    }

    private fun getSerializableForCompanion(descriptor: ClassDescriptor): ClassDescriptor? =
        if (descriptor.isSerializableCompanion) {
            descriptor.containingDeclaration as ClassDescriptor
        } else {
            null
        }
}
