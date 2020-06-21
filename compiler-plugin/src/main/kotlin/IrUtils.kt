import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.buildTypeProjection
import org.jetbrains.kotlin.name.ClassId

fun IrPluginContext.kSerializerOf(irClassSymbol: IrClassSymbol): IrType {
    val typeSymbol = referenceClass(ClassIds.KSERIALIZER)
    return typeSymbol.createType(
        hasQuestionMark = false,
        arguments = listOf(
            IrSimpleTypeImpl(
                classifier = irClassSymbol,
                hasQuestionMark = false,
                arguments = emptyList(),
                annotations = emptyList()
            ).buildTypeProjection {  }
        )
    )
}

fun IrPluginContext.referenceClass(
    id: ClassId
): IrClassSymbol =
    symbolTable.referenceClass(
        moduleDescriptor.findClassAcrossModuleDependencies(id)!!
    )

fun IrPluginContext.blockBody(
    symbol: IrSymbol,
    block: IrBlockBodyBuilder.() -> Unit
): IrBlockBody =
    DeclarationIrBuilder(this, symbol).irBlockBody { block() }

fun IrPluginContext.exprBody(
    symbol: IrSymbol,
    block: IrExpression
): IrExpressionBody =
    DeclarationIrBuilder(this, symbol).irExprBody(block)
