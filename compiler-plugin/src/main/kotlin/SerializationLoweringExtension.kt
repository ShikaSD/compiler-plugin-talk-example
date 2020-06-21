import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.companionObject

class SerializationLoweringExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        SerializationLoweringPass(pluginContext).lower(moduleFragment)
    }
}

private class SerializationLoweringPass(private val pluginContext: IrPluginContext) : ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (irClass.descriptor.isSerializable) {
            val serializerClass = generateSerializerImpl(irClass)
            generateSerializerFactory(irClass.companionObject() as IrClass, serializerClass)
        }
    }

    private fun generateSerializerImpl(irClass: IrClass): IrClass =
        SerializationLoweringClassGeneration(pluginContext).createSerializerImpl(irClass)

    /**
     * FUN name:serializer visibility:public modality:FINAL <> ($this:<root>.Data.Companion) returnType:kotlinx.serialization.KSerializer<<root>.Data>
     * $this: VALUE_PARAMETER name:<this> type:<root>.Data.Companion
     * BLOCK_BODY
     *     RETURN type=kotlin.Nothing from='public final fun serializer (): kotlinx.serialization.KSerializer<<root>.Data> declared in <root>.Data.Companion'
     *     GET_OBJECT 'CLASS OBJECT name:$serializer modality:FINAL visibility:private superTypes:[kotlinx.serialization.KSerializer<<root>.Data>]' type=<root>.Data.$serializer
     */
    private fun generateSerializerFactory(companionClass: IrClass, serializerClass: IrClass) {
        val function = companionClass.addFunction {
            name = Names.SERIALIZER_METHOD
            visibility = Visibilities.PUBLIC
            modality = Modality.FINAL
            returnType = serializerClass.superTypes.first()
        }

        function.dispatchReceiverParameter = companionClass.thisReceiver?.copyTo(function)

        function.body = pluginContext.blockBody(function.symbol) {
            + irReturn(
                irGetObject(serializerClass.symbol)
            )
        }
    }
}


