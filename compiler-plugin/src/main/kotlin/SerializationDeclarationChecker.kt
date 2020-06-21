import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.reportFromPlugin
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.source.getPsi

class SerializationDeclarationChecker : DeclarationChecker {
    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (descriptor !is ClassDescriptor) return
        if (!descriptor.isSerializable) return

        if (descriptor.isInline) {
            context.trace.reportOnSerializableAnnotation(
                descriptor,
                INLINE_CLASSES_NOT_SUPPORTED
            )
        }
    }

    private fun BindingTrace.reportOnSerializableAnnotation(
        descriptor: ClassDescriptor,
        error: DiagnosticFactory0<PsiElement>
    ) {
        descriptor.findSerializableAnnotation()?.let {
            reportFromPlugin(
                error.on(it),
                SerializationPluginErrorsRendering
            )
        }
    }

    private fun ClassDescriptor.findSerializableAnnotation(): KtAnnotationEntry? {
        val desc = annotations.findAnnotation(FqNames.SERIALIZABLE_ANNOTATION)
        return desc?.source?.getPsi() as KtAnnotationEntry
    }
}
