import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

class SerializationComponentRegistrar : ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        SyntheticResolveExtension.registerExtension(project, SerializationResolveExtension())

        StorageComponentContainerContributor.registerExtension(
            project,
            object : StorageComponentContainerContributor {
                override fun registerModuleComponents(
                    container: StorageComponentContainer,
                    platform: TargetPlatform,
                    moduleDescriptor: ModuleDescriptor
                ) {
                    container.useInstance(SerializationDeclarationChecker())
                }
            }
        )

        IrGenerationExtension.registerExtension(project, SerializationLoweringExtension())
    }

}
