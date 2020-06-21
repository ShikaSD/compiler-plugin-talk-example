Example of how compiler plugin works by implementating a fraction of [kotlinx.serialization](https://github.com/JetBrains/kotlin/tree/master/plugins/kotlin-serialization) plugin.

## Where to look?

`compiler-plugin` folder - the plugin itself

`integration` folder - playground

### Making new classes available for user

See [`SerializationResolveExtension`](compiler-plugin/src/main/kotlin/SerializationResolveExtension.kt) in `compiler-plugin` folder

### Handling errors

See [`Errors`](compiler-plugin/src/main/kotlin/Errors.kt) and [`SerializationDeclarationChecker`](compiler-plugin/src/main/kotlin/SerializationDeclarationChecker.kt) in `compiler-plugin` folder

### Generating IR

See [`SerializationLoweringExtension`](compiler-plugin/src/main/kotlin/SerializationLoweringExtension.kt) and [`SerializationLoweringClassGeneration`](compiler-plugin/src/main/kotlin/SerializationLoweringClassGeneration.kt) in `compiler-plugin` folder
