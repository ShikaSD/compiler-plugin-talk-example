import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.INSTANCE_RECEIVER
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.referenceFunction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi2ir.findFirstFunction

/**
 * see IR dump in resources folder
 *
 * private object `$serializer` : KSerializer<Data> {
 *     private val delegate = String.serializer()
 *
 *     override val descriptor: SerialDescriptor = delegate.descriptor
 *
 *     override fun serialize(encoder: Encoder, value: Data) {
 *         delegate.serialize(encoder, value.toString())
 *     }
 *
 *     override fun deserialize(decoder: Decoder): Data =
 *         TODO()
 * }
 */
class SerializationLoweringClassGeneration(
    private val pluginContext: IrPluginContext
) {
    private val kSerializer = pluginContext.moduleDescriptor.findClassAcrossModuleDependencies(ClassIds.KSERIALIZER)

    fun createSerializerImpl(
        irClass: IrClass
    ): IrClass {
        /**
         * CLASS OBJECT name:$serializer modality:FINAL visibility:private superTypes:[kotlinx.serialization.KSerializer<<root>.Data>]
         */
        val serializerCls = buildClass {
            name = Names.SERIALIZER_IMPL
            modality = Modality.FINAL
            visibility = Visibilities.PRIVATE
            kind = ClassKind.OBJECT
        }

        serializerCls.superTypes.add(pluginContext.kSerializerOf(irClass.symbol))
        irClass.addChild(serializerCls)

        /**
         *  $this: VALUE_PARAMETER INSTANCE_RECEIVER name:<this> type:<root>.Data.$serializer
         */
        val serializerReceiver = buildValueParameter {
            name = Name.special("<this>")
            type = IrSimpleTypeImpl(
                classifier = serializerCls.symbol,
                hasQuestionMark = false,
                arguments = emptyList(),
                annotations = emptyList()
            )
            origin = INSTANCE_RECEIVER
        }
        serializerCls.thisReceiver = serializerReceiver
        serializerReceiver.parent = serializerCls

        /**
         * CONSTRUCTOR visibility:private <> () returnType:<root>.Data.$serializer [primary]
         * BLOCK_BODY
         */
        val constructor = serializerCls.addConstructor {
            visibility = Visibilities.PRIVATE
            returnType = serializerCls.defaultType
            isPrimary = true
        }
        constructor.body = pluginContext.blockBody(constructor.symbol) {
            /**
             * DELEGATING_CONSTRUCTOR_CALL 'public constructor <init> () [primary] declared in kotlin.Any'
             * INSTANCE_INITIALIZER_CALL classDescriptor='CLASS OBJECT name:$serializer modality:FINAL visibility:private superTypes:[kotlinx.serialization.KSerializer<<root>.Data>]'
             */
            val any = pluginContext.anyConstructor()
            val anyType = pluginContext.irBuiltIns.anyType
            + IrDelegatingConstructorCallImpl(
                startOffset,
                endOffset,
                anyType,
                any
            )

            + IrInstanceInitializerCallImpl(
                startOffset,
                endOffset,
                serializerCls.symbol,
                serializerCls.defaultType
            )
        }

        serializerCls.addDelegateField()
        serializerCls.addDescriptorField()
        serializerCls.addSerialize()
        serializerCls.addDeserialize()

        return serializerCls
    }

    private fun IrClass.addDelegateField() {
        /**
         * PROPERTY name:delegate visibility:private modality:FINAL [val]
         */
        val property = addProperty {
            name = Name.identifier("delegate")
            visibility = Visibilities.PRIVATE
            modality = Modality.FINAL
        }

        /**
         * FIELD PROPERTY_BACKING_FIELD name:delegate type:kotlinx.serialization.KSerializer<kotlin.String> visibility:private [final]
         */
        property.backingField = buildField {
            name = property.name
            visibility = property.visibility
            modality = property.modality
            type = pluginContext.kSerializerOf(pluginContext.symbols.string)
        }

        /**
         * GET_OBJECT 'CLASS OBJECT name:StringSerializer modality:FINAL visibility:public superTypes:[kotlinx.serialization.KSerializer<kotlin.String>]' type=kotlinx.serialization.internal.StringSerializer
         */
        property.backingField?.initializer =
            pluginContext.exprBody(
                property.symbol,
                IrGetObjectValueImpl(
                    startOffset = property.startOffset,
                    endOffset = property.endOffset,
                    type = pluginContext.stringSerializer().createType(hasQuestionMark = false, arguments = emptyList()),
                    symbol = pluginContext.stringSerializer()
                )
            )
        property.backingField?.parent = this

        // skipping getter
    }

    private fun IrClass.addDescriptorField() {
        /**
         * PROPERTY name:descriptor visibility:public modality:OPEN [val]
         */
        val property = addProperty {
            name = Name.identifier("descriptor")
            visibility = Visibilities.PUBLIC
            modality = Modality.OPEN
        }

        /**
         * FIELD PROPERTY_BACKING_FIELD name:descriptor type:kotlinx.serialization.SerialDescriptor visibility:private [final]
         */
        val serialDescriptor = pluginContext.referenceClass(
            ClassId(FqName("kotlinx.serialization"), Name.identifier("SerialDescriptor"))
        )
        val serialDescriptorType = serialDescriptor.createType(hasQuestionMark = false, arguments = emptyList())
        property.backingField = buildField {
            origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
            name = property.name
            type = serialDescriptorType
            visibility = Visibilities.PRIVATE
        }

        /**
         * EXPRESSION_BODY
         * CALL 'public abstract fun <get-descriptor> (): kotlinx.serialization.SerialDescriptor declared in kotlinx.serialization.KSerializer' type=kotlinx.serialization.SerialDescriptor origin=GET_PROPERTY
         */
        val delegateProperty = this.properties.first { it.name.asString() == "delegate" }
        val kserializerPropertyAccessor = pluginContext.kSerializerPropertyAccessor("descriptor")
        property.backingField?.initializer = pluginContext.exprBody(
            property.symbol,
            IrCallImpl(
                property.startOffset,
                property.endOffset,
                serialDescriptorType,
                kserializerPropertyAccessor
            ).also { call ->
                /**
                 * $this: CALL 'private final fun <get-delegate> (): kotlinx.serialization.KSerializer<kotlin.String> declared in <root>.Data.$serializer' type=kotlinx.serialization.KSerializer<kotlin.String> origin=GET_PROPERTY
                 */
                call.dispatchReceiver =
                    IrGetFieldImpl(
                        property.startOffset,
                        property.endOffset,
                        delegateProperty.backingField!!.symbol,
                        delegateProperty.backingField!!.type
                    )
            }
        )
        property.backingField?.parent = this

        /**
         * FUN DEFAULT_PROPERTY_ACCESSOR name:<get-descriptor> visibility:public modality:OPEN <> ($this:<root>.Data.$serializer) returnType:kotlinx.serialization.SerialDescriptor
         *     correspondingProperty: PROPERTY name:descriptor visibility:public modality:OPEN [val]
         */
        val getter = property.addGetter {
            visibility = Visibilities.PUBLIC
            modality = Modality.OPEN
            returnType = serialDescriptorType
        }

        /**
         * overridden:
         * public abstract fun <get-descriptor> (): kotlinx.serialization.SerialDescriptor declared in kotlinx.serialization.KSerializer
         * $this: VALUE_PARAMETER name:<this> type:<root>.Data.$serializer
         */
        getter.overriddenSymbols.add(kserializerPropertyAccessor as IrSimpleFunctionSymbol)
        getter.dispatchReceiverParameter = thisReceiver!!.copyTo(getter)

        /**
         * BLOCK_BODY
         * RETURN type=kotlin.Nothing from='public open fun <get-descriptor> (): kotlinx.serialization.SerialDescriptor declared in <root>.Data.$serializer'
         * GET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:descriptor type:kotlinx.serialization.SerialDescriptor visibility:private [final]' type=kotlinx.serialization.SerialDescriptor origin=null
         * receiver: GET_VAR '<this>: <root>.Data.$serializer declared in <root>.Data.$serializer.<get-descriptor>' type=<root>.Data.$serializer origin=null
         */
        getter.body = pluginContext.blockBody(getter.symbol) {
            + irReturn(
                irGetField(irGet(getter.dispatchReceiverParameter!!), property.backingField!!)
            )
        }
    }

    private fun IrClass.addSerialize() {
        /**
         * FUN name:serialize visibility:public modality:OPEN <> ($this:<root>.Data.$serializer, encoder:kotlinx.serialization.Encoder, value:<root>.Data) returnType:kotlin.Unit
         */
        val function = addFunction {
            name = Name.identifier("serialize")
            visibility = Visibilities.PUBLIC
            modality = Modality.OPEN
            returnType = pluginContext.irBuiltIns.unitType
        }

        /**
         * overridden:
         * public abstract fun serialize (encoder: kotlinx.serialization.Encoder, value: T of kotlinx.serialization.KSerializer): kotlin.Unit [fake_override] declared in kotlinx.serialization.KSerializer
         */
        val serializeFunction = pluginContext.kSerializerFunction("serialize")
        function.overriddenSymbols.add(serializeFunction as IrSimpleFunctionSymbol)

        /**
         * $this: VALUE_PARAMETER name:<this> type:<root>.Data.$serializer
         * VALUE_PARAMETER name:encoder index:0 type:kotlinx.serialization.Encoder
         * VALUE_PARAMETER name:value index:1 type:<root>.Data
         */
        function.dispatchReceiverParameter = thisReceiver!!.copyTo(function)
        val encoderParam = function.addValueParameter {
            index = 0
            name = Name.identifier("encoder")
            type = pluginContext.referenceClass(
                ClassId(FqName("kotlinx.serialization"), Name.identifier("Encoder"))
            ).createType(hasQuestionMark = false, arguments = emptyList())
        }
        val valueParam = function.addValueParameter {
            index = 1
            name = Name.identifier("value")
            type = parentAsClass.defaultType
        }

        val delegateProperty = this.properties.first { it.name.asString() == "delegate" }
        /**
         * BLOCK_BODY
         */
        function.body = pluginContext.blockBody(function.symbol) {
            /**
             * CALL 'public abstract fun serialize (encoder: kotlinx.serialization.Encoder, value: T of kotlinx.serialization.KSerializer): kotlin.Unit [fake_override] declared in kotlinx.serialization.KSerializer' type=kotlin.Unit origin=null
             */
            + irCall(serializeFunction, pluginContext.irBuiltIns.unitType).also { call ->
                /**
                 * $this: CALL 'private final fun <get-delegate> (): kotlinx.serialization.KSerializer<kotlin.String> declared in <root>.Data.$serializer' type=kotlinx.serialization.KSerializer<kotlin.String> origin=GET_PROPERTY
                 *     $this: GET_VAR '<this>: <root>.Data.$serializer declared in <root>.Data.$serializer.serialize' type=<root>.Data.$serializer origin=null
                 */
                call.dispatchReceiver =
                    irGetField(irGet(function.dispatchReceiverParameter!!), delegateProperty.backingField!!)

                /**
                 * encoder: GET_VAR 'encoder: kotlinx.serialization.Encoder declared in <root>.Data.$serializer.serialize' type=kotlinx.serialization.Encoder origin=null
                 */
                call.putValueArgument(0, irGet(encoderParam))
                /**
                 * value: CALL 'public open fun toString (): kotlin.String declared in <root>.Data' type=kotlin.String origin=null
                 *     $this: GET_VAR 'value: <root>.Data declared in <root>.Data.$serializer.serialize' type=<root>.Data origin=null
                 */
                call.putValueArgument(
                    1,
                    irCall(
                        pluginContext.toStringFunction(),
                        pluginContext.irBuiltIns.stringType
                    ).also { toStringCall ->
                        toStringCall.dispatchReceiver = irGet(valueParam)
                    })
            }
        }
    }

    private fun IrClass.addDeserialize() {
        /**
         * FUN name:deserialize visibility:public modality:OPEN <> ($this:<root>.Data.$serializer, decoder:kotlinx.serialization.Decoder) returnType:<root>.Data
         */
        val function = addFunction {
            name = Name.identifier("deserialize")
            visibility = Visibilities.PUBLIC
            modality = Modality.OPEN
            returnType = parentAsClass.defaultType
        }

        /**
         * overridden:
         * public abstract fun deserialize (decoder: kotlinx.serialization.Decoder): T of kotlinx.serialization.KSerializer [fake_override] declared in kotlinx.serialization.KSerializer
         */
        function.overriddenSymbols.add(pluginContext.kSerializerFunction("deserialize") as IrSimpleFunctionSymbol)

        /**
         * $this: VALUE_PARAMETER name:<this> type:<root>.Data.$serializer
         * VALUE_PARAMETER name:decoder index:0 type:kotlinx.serialization.Decoder
         */
        function.dispatchReceiverParameter = thisReceiver!!.copyTo(function)
        function.addValueParameter {
            name = Name.identifier("decoder")
            index = 0
            type = pluginContext.referenceClass(
                ClassId(FqName("kotlinx.serialization"), Name.identifier("Decoder"))
            ).createType(hasQuestionMark = false, arguments = emptyList())
        }

        /**
         * BLOCK_BODY
         * CALL 'public final fun TODO (): kotlin.Nothing [inline] declared in kotlin.StandardKt' type=kotlin.Nothing origin=null
         */
        function.body = pluginContext.blockBody(function.symbol) {
            + irCall(
                pluginContext.todoSymbol() as IrSimpleFunctionSymbol,
                pluginContext.irBuiltIns.nothingType
            )
        }
    }

    private fun IrPluginContext.anyConstructor(): IrConstructorSymbol =
        symbolTable.referenceConstructor(
            builtIns.any.constructors.single()
        )

    private fun IrPluginContext.toStringFunction(): IrFunctionSymbol =
        symbolTable.referenceFunction(
            builtIns.any.findFirstFunction("toString") { true }
        )

    private fun IrPluginContext.stringSerializer(): IrClassSymbol {
        val cls = moduleDescriptor.findClassAcrossModuleDependencies(
            ClassId(
                FqName("kotlinx.serialization.internal"),
                Name.identifier("StringSerializer")
            )
        )
        return symbolTable.referenceClass(cls!!)
    }

    private fun IrPluginContext.kSerializerPropertyAccessor(name: String): IrFunctionSymbol {
        val vars = kSerializer?.unsubstitutedMemberScope?.getContributedVariables(Name.identifier(name), NoLookupLocation.FROM_BACKEND)
        val property = vars?.first { it.name.asString() == name }
        return symbolTable.referenceFunction(property!!.getter!!)
    }

    private fun IrPluginContext.kSerializerFunction(name: String): IrFunctionSymbol {
        return symbolTable.referenceFunction(kSerializer?.findFirstFunction(name) { true }!!)
    }

    private fun IrPluginContext.todoSymbol(): IrFunctionSymbol {
        val kotlinPackage = moduleDescriptor.getPackage(FqName("kotlin"))
        val todoFunction = kotlinPackage.fragments.mapNotNull {
            it.getMemberScope().getContributedFunctions(Name.identifier("TODO"), NoLookupLocation.FROM_BACKEND)
                .firstOrNull { it.name.asString() == "TODO" }
        }.first()
        return symbolTable.referenceFunction(todoFunction)
    }
}
