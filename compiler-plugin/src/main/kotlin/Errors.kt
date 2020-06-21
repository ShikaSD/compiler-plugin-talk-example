import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap

val INLINE_CLASSES_NOT_SUPPORTED =
    DiagnosticFactory0.create<PsiElement>(Severity.ERROR)

object SerializationPluginErrorsRendering : DefaultErrorMessages.Extension {
    private val _map = DiagnosticFactoryToRendererMap("SerializationPlugin")
    override fun getMap(): DiagnosticFactoryToRendererMap = _map

    init {
        _map.put(
            INLINE_CLASSES_NOT_SUPPORTED,
            "Inline classes are not supported by kotlinx.serialization yet"
        )
    }
}

