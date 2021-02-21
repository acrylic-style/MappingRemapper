package xyz.acrylicstyle.mappingRemapper.util

import org.intellij.lang.annotations.Language

data class ObfuscatedMember(
    val obf: String,
    var bukkitName: String? = null,
    var deobf: String? = null,
    val params: List<String> = ArrayList(),
    val returnType: String? = null,
)

data class MappingData(val className: ObfuscatedMember, val fields: MutableList<ObfuscatedMember> = ArrayList(), val methods: MutableList<ObfuscatedMember> = ArrayList())

data class Mappings(val oldMappingData: MutableList<MappingData> = ArrayList(), val newMappingData: MutableList<MappingData> = ArrayList()) {
    fun findNewMappingByOldObfCN(oldObfName: String): MappingData? {
        val deobf = oldMappingData.firstOrNull { data -> data.className.obf == oldObfName }?.className?.deobf ?: return null
        return newMappingData.firstOrNull { data -> data.className.deobf == deobf }
    }

    fun findOldMappingByOldObfCN(oldObfName: String): MappingData? {
        return oldMappingData.firstOrNull { data -> data.className.obf == oldObfName }
    }

    fun findOldMappingByOldBukkitCN(oldBukkitName: String): MappingData? =
        oldMappingData.firstOrNull { data -> data.className.bukkitName == oldBukkitName }

    fun findOldDeobfOrDefaultByOldBukkitCN(oldBukkitName: String): String {
        val reader = StringReader(oldBukkitName)
        var suffix = ""
        while (!reader.isEOF()) {
            if (reader.peek() == '[') {
                reader.skip()
                if (reader.peek() == ']') {
                    suffix += "[]"
                }
            }
            reader.skip()
        }
        val data = oldMappingData.firstOrNull { data -> data.className.bukkitName == oldBukkitName.replace(suffix, "") }
            ?: return oldBukkitName
        return if (data.className.deobf != null) data.className.deobf + suffix else oldBukkitName
    }

    fun findNewObfOrDefaultByDeobfCN(deobf: String): String {
        val reader = StringReader(deobf)
        var suffix = ""
        while (!reader.isEOF()) {
            if (reader.peek() == '[') {
                reader.skip()
                if (reader.peek() == ']') {
                    suffix += "[]"
                }
            }
            reader.skip()
        }
        val data = newMappingData.firstOrNull { data -> data.className.deobf == deobf.replace(suffix, "") }
            ?: return deobf
        return if (data.className.bukkitName != null) data.className.bukkitName + suffix else data.className.obf + suffix
    }

    fun findNewMappingByNewObfCN(newObfName: String): MappingData? =
        newMappingData.firstOrNull { data -> data.className.obf == newObfName }

    fun findOldMappingByDeobfCN(deobf: String): MappingData? =
        oldMappingData.firstOrNull { data -> data.className.deobf == deobf }
}

fun String.startsWithRegex(@Language("RegExp") regex: String) = this.matches("^$regex.*".toRegex())

fun String.signatureToType(): String {
    val reader = StringReader(this)
    var suffix = ""
    while (reader.peek() == '[') {
        reader.skip()
        suffix += "[]"
    }
    val text = reader.read()
    if (text == "I") return "int$suffix"
    if (text == "Z") return "boolean$suffix"
    if (text == "D") return "double$suffix"
    if (text == "J") return "long$suffix"
    if (text == "F") return "float$suffix"
    if (text == "B") return "byte$suffix"
    if (text == "V") return "void$suffix"
    if (text == "S") return "short$suffix"
    if (text == "C") return "char$suffix"
    if (text.startsWith("L") && text.endsWith(";"))
        return text.replace("L(.*);".toRegex(), "$1").replace("/", ".") + suffix
    throw IllegalArgumentException("signature did not match any types: text: '$text', suffix: '$suffix'")
}

fun String.signaturesToTypes(): MutableList<String> {
    if (this.length == 1) return mutableListOf(this.signatureToType())
    val list = ArrayList<String>()
    if (this.isEmpty()) return list
    val reader = StringReader(this)
    while (!reader.isEOF()) {
        var suffix = ""
        while (reader.peek() == '[') {
            reader.skip()
            suffix += "[]"
        }
        val peek = reader.peek()
        reader.skip()
        if (peek == 'I') {
            list.add("int$suffix")
            continue
        }
        if (peek == 'Z') {
            list.add("boolean$suffix")
            continue
        }
        if (peek == 'D') {
            list.add("double$suffix")
            continue
        }
        if (peek == 'J') {
            list.add("long$suffix")
            continue
        }
        if (peek == 'F') {
            list.add("float$suffix")
            continue
        }
        if (peek == 'B') {
            list.add("byte$suffix")
            continue
        }
        if (peek == 'V') {
            list.add("void$suffix")
            continue
        }
        if (peek == 'S') {
            list.add("short$suffix")
            continue
        }
        if (peek == 'C') {
            list.add("char$suffix")
            continue
        }
        if (peek == 'L') {
            var cn = ""
            while (!reader.isEOF()) {
                if (reader.peek() == ';') {
                    reader.skip()
                    break
                }
                cn += reader.peekString()
                reader.skip()
            }
            list.add(cn.replace("/", ".") + suffix)
            continue
        }
        throw IllegalArgumentException("Unreadable signature: $peek (remaining: ${reader.read()}, text: $this)")
    }
    return list
}

fun String.toSignature(): String {
    if (this.isEmpty()) return this
    val reader = StringReader(this)
    var prefix = ""
    while (!reader.isEOF()) {
        if (reader.peek() == '[') {
            reader.skip()
            if (reader.peek() == ']') {
                prefix += "["
            }
        }
        reader.skip()
    }
    return when (val text = this.replace("\\[]".toRegex(), "")) {
        "int" -> "${prefix}I"
        "double" -> "${prefix}D"
        "boolean" -> "${prefix}Z"
        "long" -> "${prefix}J"
        "float" -> "${prefix}F"
        "byte" -> "${prefix}B"
        "void" -> "${prefix}V"
        "short" -> "${prefix}S"
        "char" -> "${prefix}C"
        else -> "${prefix}L${text.replace(".", "/")};"
    }
}

fun List<String>.toSignature(returnType: String): String {
    var signature = "("
    this.forEach { s ->
        signature += s.toSignature()
    }
    signature += ")"
    signature += returnType.toSignature()
    return signature
}

// fun test stuff :)
fun main() {
    // java.lang.String
    println("Ljava/lang/String;".signatureToType())
    // int[][][], int, boolean, double, long, float, byte, void, short, char, java.lang.String
    println("[[[IIZDJFBVSCLjava/lang/String;".signaturesToTypes())
    // int, int
    println("II".signaturesToTypes())
    // should return true
    println("    11:11:void <init>() -> <init>".startsWithRegex("\\s{4}\\d"))
    // java/lang/String
    println("java.lang.String".toSignature())
    // [[I
    println("int[][]".toSignature())
    // ([I[Ljava/lang/String;[[J)V
    println(listOf("int[]", "java.lang.String[]", "long[][]").toSignature("void"))
}
