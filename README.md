Example of how compiler plugin works by implementating a fraction of [kotlinx.serialization](https://github.com/JetBrains/kotlin/tree/master/plugins/kotlin-serialization) plugin.

Given:
```kotlin
@Serializable
class Data(val i: String)
```

Generates:
```kotlin
@Serializable
class Data(val i: String) {
    companion object {
        fun serializer(): KSerializer<Data> = `$serializer`
    }
       
    private object `$serializer` : KSerializer<Data> {
        private val delegate = StringSerializer()
        override val descriptor = delegate.descriptor
        override fun serialize(encoder: Encoder, data: Data) =
            delegate.serialize(encoder, data.toString())
        override fun deserialize(decoder: Decoder) = 
            TODO()
    }
}
```

## Where to look?

`compiler-plugin` folder - the plugin itself

`integration` folder - playground

### Making new classes available for user

See [`SerializationResolveExtension`](compiler-plugin/src/main/kotlin/SerializationResolveExtension.kt) in `compiler-plugin` folder

### Handling errors

See [`SerializationErrors`](compiler-plugin/src/main/kotlin/SerializationErrors.kt) and [`SerializationDeclarationChecker`](compiler-plugin/src/main/kotlin/SerializationDeclarationChecker.kt) in `compiler-plugin` folder

### Generating IR

See [`SerializationLoweringExtension`](compiler-plugin/src/main/kotlin/SerializationLoweringExtension.kt) and [`SerializationLoweringClassGeneration`](compiler-plugin/src/main/kotlin/SerializationLoweringClassGeneration.kt) in `compiler-plugin` folder
