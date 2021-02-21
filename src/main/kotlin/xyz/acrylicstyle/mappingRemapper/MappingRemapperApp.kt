@file:JvmName("MappingRemapperApp")
@file:Suppress("DuplicatedCode")

package xyz.acrylicstyle.mappingRemapper

import util.option.OptionParser
import xyz.acrylicstyle.mappingRemapper.util.MappingData
import xyz.acrylicstyle.mappingRemapper.util.Mappings
import xyz.acrylicstyle.mappingRemapper.util.ObfuscatedMember
import xyz.acrylicstyle.mappingRemapper.util.signatureToType
import xyz.acrylicstyle.mappingRemapper.util.signaturesToTypes
import xyz.acrylicstyle.mappingRemapper.util.startsWithRegex
import xyz.acrylicstyle.mappingRemapper.util.toSignature
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = OptionParser()
    parser.accepts("help", "this")
    parser.accepts("debug", "enable debug logging")
    parser.accepts("verbose", "enable verbose logging")
    val argMappingFile = parser.accepts("mapping-file", "the mojang mapping file to load").withRequiredArg().ofType(File::class.java).defaultsTo(File("./mappings.txt"))
    val argClFile = parser.accepts("cl-file", "the input bukkit-cl.csrg file to process").withRequiredArg().ofType(File::class.java).defaultsTo(File("./bukkit-cl.csrg"))
    val argMembersFile = parser.accepts("members-file", "the input bukkit-members.csrg file to process").withRequiredArg().ofType(File::class.java).defaultsTo(File("./bukkit-members.csrg"))
    val argExcludeFile = parser.accepts("exclude-file", "the input bukkit-<version>.exclude file to process").withRequiredArg().ofType(File::class.java).defaultsTo(File("./bukkit.exclude"))
    val argNewMappingFile = parser.accepts("mapping2-file", "the mojang mapping file of new version to load").withRequiredArg().ofType(File::class.java).defaultsTo(File("./mappings-new.txt"))
    val argOutputClFile = parser.accepts("output-cl-file", "the output cl file path").withRequiredArg().ofType(File::class.java).defaultsTo(File("./output-cl.csrg"))
    val argOutputMembersFile = parser.accepts("output-members-file", "the output members file path").withRequiredArg().ofType(File::class.java).defaultsTo(File("./output-members.csrg"))
    val argOutputExcludeFile = parser.accepts("output-exclude-file", "the output exclude file path").withRequiredArg().ofType(File::class.java).defaultsTo(File("./output.exclude"))
    val result = parser.parse(*args)
    if (result.has("help")) {
        parser.printHelpOn(System.out)
        exitProcess(0)
    }
    val debug = result.has("debug")
    val verbose = result.has("verbose")
    val mappingFile = result.value(argMappingFile)!!
    val clFile = result.value(argClFile)!!
    val membersFile = result.value(argMembersFile)!!
    val excludeFile = result.value(argExcludeFile)!!
    val newMappingFile = result.value(argNewMappingFile)!!
    val outputClFile = result.value(argOutputClFile)!!
    val outputMembersFile = result.value(argOutputMembersFile)!!
    val outputExcludeFile = result.value(argOutputExcludeFile)!!
    if (!mappingFile.exists() || mappingFile.isDirectory) {
        println("mapping file " + mappingFile.absolutePath + " does not exist")
        exitProcess(1)
    }
    if (!clFile.exists() || clFile.isDirectory) {
        println("bukkit-cl file " + clFile.absolutePath + " does not exist")
        exitProcess(1)
    }
    if (!membersFile.exists() || membersFile.isDirectory) {
        println("bukkit-members file " + membersFile.absolutePath + " does not exist")
        exitProcess(1)
    }
    if (!excludeFile.exists() || excludeFile.isDirectory) {
        println("bukkit.exclude file " + excludeFile.absolutePath + " does not exist")
        exitProcess(1)
    }
    if (!newMappingFile.exists() || newMappingFile.isDirectory) {
        println("mapping file " + newMappingFile.absolutePath + " does not exist")
        exitProcess(1)
    }
    println("Loading files")
    val mappings = Mappings()
    val ln = AtomicInteger()
    val count = AtomicInteger()
    mappingFile.forEachLine { line ->
        ln.incrementAndGet()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        try {
            if (!line.startsWith("    ")) { // classes
                val matcher = "^(.*) -> (.*):".toRegex().matchEntire(line) ?: throw IllegalArgumentException("Does not match regex")
                val deobf = matcher.groups[1]!!.value
                val obf = matcher.groups[2]!!.value
                if (deobf.endsWith("package-info")) return@forEachLine
                if (verbose) println("OM '$obf' -> '$deobf'") // Old Mappings
                mappings.oldMappingData.add(MappingData(ObfuscatedMember(obf = obf, deobf = deobf)))
            } else { // members
                if (line.startsWithRegex("\\s{4}\\d") || line.contains("(")) { // methods
                    val body = line.replace("\\s{4}(\\d+:\\d+:|)(.*)".toRegex(), "$2")
                    val obfName = body.replace(".* -> (.*)".toRegex(), "$1")
                    val deobfBody = body.replace("(.*) -> .*".toRegex(), "$1")
                    val arr = deobfBody.split("\\s+".toRegex())
                    val returnType = arr[0]
                    val deobfName = deobfBody.replace(".* (.*)\\(.*".toRegex(), "$1")
                    val paramsBody = deobfBody.replace(".* .*\\((.*)\\).*".toRegex(), "$1")
                    val params = if (paramsBody.isEmpty()) listOf() else paramsBody.split(',')
                    if (returnType.isEmpty()) throw IllegalArgumentException("Return type is empty ($arr)")
                    mappings.oldMappingData.last().methods.add(ObfuscatedMember(obf = obfName, deobf = deobfName, params = params, returnType = returnType))
                    count.incrementAndGet()
                } else { // fields
                    val fieldType = line.replace("\\s{4}(.*) .* -> .*".toRegex(), "$1")
                    val deobfName = line.replace("\\s{4}.* (.*) -> .*".toRegex(), "$1")
                    val obfName = line.replace("\\s{4}.* .* -> (.*)".toRegex(), "$1")
                    mappings.oldMappingData.last().fields.add(ObfuscatedMember(obf = obfName, deobf = deobfName, returnType = fieldType))
                    count.incrementAndGet()
                }
            }
        } catch (e: Throwable) {
            System.err.println("Invalid mojang mapping at line ${ln.get()}: $line")
            System.err.println("Error message: ${e.javaClass.simpleName}: ${e.message}")
            if (debug) e.printStackTrace()
        }
    }
    println("Old: ${mappings.oldMappingData.size} classes loaded")
    println("Old: ${count.get()} members loaded")
    ln.set(0)
    count.set(0)
    clFile.forEachLine { line ->
        ln.incrementAndGet()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        try {
            val arr = line.split("\\s+".toRegex())
            if (arr.size <= 1) throw IllegalArgumentException("Invalid array size: ${arr.size} (Parsed as: [${arr.joinToString(", ")}])")
            val obf = arr[0].replace("/", ".")
            val deobf = arr[1]
            val data = mappings.findOldMappingByOldObfCN(obf) ?: throw NullPointerException("Could not find mapping data by obfuscated class name '$obf'")
            data.className.bukkitName = deobf
            count.incrementAndGet()
        } catch (e: Throwable) {
            System.err.println("Invalid bukkit cl mapping at line ${ln.get()}: $line")
            System.err.println("Error message: ${e.javaClass.simpleName}: ${e.message}")
            if (debug) e.printStackTrace()
        }
    }
    run {
        val data = mappings.findOldMappingByOldObfCN("net.minecraft.server.MinecraftServer") ?: throw NullPointerException("Could not find mapping data by obfuscated class name 'net.minecraft.server.MinecraftServer'")
        data.className.bukkitName = "net.minecraft.server.MinecraftServer"
        count.incrementAndGet()
    }
    run {
        val data = mappings.findOldMappingByOldObfCN("net.minecraft.server.Main") ?: throw NullPointerException("Could not find mapping data by obfuscated class name 'net.minecraft.server.Main'")
        data.className.bukkitName = "net.minecraft.server.Main"
        count.incrementAndGet()
    }
    println("Linked ${count.get()} classes (Bukkit <-> Mojang)")
    ln.set(0)
    count.set(0)
    membersFile.forEachLine { line ->
        ln.incrementAndGet()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        try {
            val arr = line.split("\\s+".toRegex())
            if (arr.size < 3) throw IllegalArgumentException("Invalid array size: ${arr.size}")
            val clazz = arr[0].replace("/", ".")
            val obfName = arr[1]
            val data = mappings.findOldMappingByOldBukkitCN(clazz)
                ?: throw NullPointerException("Could not find mapping data by bukkit class name '$clazz'")
            if (arr.size == 3) { // field
                val deobfName = arr[2] // bukkit name
                val member = data.fields.firstOrNull { member -> member.obf == obfName }
                    ?: throw NullPointerException("$clazz: Could not find field by obf field name '$obfName' ($deobfName)")
                //if (debug) println("$clazz: Setting bukkit name of ${member.obf} (${member.deobf}) to $deobfName")
                member.bukkitName = deobfName
                count.incrementAndGet()
            } else if (arr.size == 4) { // method
                val signature = arr[2]
                val deobfName = arr[3]
                val params = signature.replace("\\((.*)\\).*".toRegex(), "$1").signaturesToTypes().map { s -> mappings.findOldDeobfOrDefaultByOldBukkitCN(s) }
                val returnType = mappings.findOldDeobfOrDefaultByOldBukkitCN(signature.replace("\\(.*\\)(.*)".toRegex(), "$1").signatureToType())
                val members = data.methods.filter { member -> member.obf == obfName }
                if (members.isEmpty()) {
                    throw NullPointerException("$clazz: Could not find method by obf method name '$obfName' ($deobfName) params: $params, returnType: $returnType")
                }
                val member = members.firstOrNull { member -> member.params.joinToString(",") == params.joinToString(",") }
                    ?: throw IllegalArgumentException("$clazz: Parameter mismatch in obf method '$obfName' ($deobfName) params: $params, returnType: $returnType")
                if (member.returnType != returnType) throw IllegalArgumentException("$clazz: Return type mismatch: ${member.returnType} != $returnType in obf method '$obfName' ($deobfName, ${member.deobf}) params: $params")
                member.bukkitName = deobfName
                count.incrementAndGet()
            } else {
                throw IllegalArgumentException("$clazz: Invalid array size: ${arr.size}")
            }
        } catch (e: Throwable) {
            System.err.println("Invalid bukkit members mapping at line ${ln.get()}: $line")
            System.err.println("Error message: ${e.javaClass.simpleName}: ${e.message}")
            if (debug) e.printStackTrace()
        }
    }
    println("Linked ${count.get()} members (Bukkit <-> Mojang)")
    ln.set(0)
    count.set(0)
    newMappingFile.forEachLine { line ->
        ln.incrementAndGet()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        try {
            if (!line.startsWith("    ")) { // classes
                val matcher = "^(.*) -> (.*):".toRegex().matchEntire(line) ?: throw IllegalArgumentException("Does not match regex")
                val deobf = matcher.groups[1]!!.value
                val obf = matcher.groups[2]!!.value
                if (deobf.endsWith("package-info")) return@forEachLine
                if (verbose) println("NM '$obf' -> '$deobf'") // New Mappings
                mappings.newMappingData.add(MappingData(ObfuscatedMember(obf = obf, bukkitName = mappings.findOldMappingByDeobfCN(deobf)?.className?.bukkitName, deobf = deobf)))
            } else { // members
                if (line.startsWithRegex("\\s{4}\\d") || line.contains("(")) { // methods
                    val body = line.replace("\\s{4}(\\d+:\\d+:|)(.*)".toRegex(), "$2")
                    val obfName = body.replace(".* -> (.*)".toRegex(), "$1")
                    val deobfBody = body.replace("(.*) -> .*".toRegex(), "$1")
                    val arr = deobfBody.split("\\s+".toRegex())
                    val returnType = arr[0]
                    val deobfName = deobfBody.replace(".* (.*)\\(.*".toRegex(), "$1")
                    val paramsBody = deobfBody.replace(".* .*\\((.*)\\).*".toRegex(), "$1")
                    val params = if (paramsBody.isEmpty()) listOf() else paramsBody.split(',')
                    if (returnType.isEmpty()) throw IllegalArgumentException("Return type is empty ($arr)")
                    val data = mappings.newMappingData.last()
                    val clazz = data.className.deobf!!
                    data.methods.add(ObfuscatedMember(obf = obfName, bukkitName = mappings.findOldMappingByDeobfCN(clazz)?.methods?.firstOrNull { member -> member.deobf == deobfName && member.returnType == returnType && member.params.joinToString(",") == params.joinToString(",") }?.bukkitName, deobf = deobfName, params = params, returnType = returnType))
                    count.incrementAndGet()
                } else { // fields
                    val fieldType = line.replace("\\s{4}(.*) .* -> .*".toRegex(), "$1")
                    val deobfName = line.replace("\\s{4}.* (.*) -> .*".toRegex(), "$1")
                    val obfName = line.replace("\\s{4}.* .* -> (.*)".toRegex(), "$1")
                    val data = mappings.newMappingData.last()
                    val clazz = data.className.deobf!!
                    var bukkitName: String? = null
                    mappings.findOldMappingByDeobfCN(clazz)?.fields?.forEach { member ->
                        if (member.deobf == deobfName && bukkitName == null) {
                            if (member.returnType != fieldType) {
                                println("W: $clazz: Type differs from original field '${deobfName}': expected: ${member.returnType}, new: $fieldType")
                                return@forEach
                            }
                            if (member.bukkitName != null) {
                                if (debug) println("$clazz: $obfName - $deobfName - ${member.bukkitName}: $member")
                                bukkitName = member.bukkitName
                            }
                        }
                    }
                    data.fields.add(ObfuscatedMember(obf = obfName, bukkitName = bukkitName, deobf = deobfName, returnType = fieldType))
                    count.incrementAndGet()
                }
            }
        } catch (e: Throwable) {
            System.err.println("Invalid mojang mapping (new) at line ${ln.get()}: $line")
            System.err.println("Error message: ${e.javaClass.simpleName}: ${e.message}")
            if (debug) e.printStackTrace()
        }
    }
    println("New: ${mappings.newMappingData.size} classes loaded")
    println("New: ${count.get()} members loaded")
    ln.set(0)
    count.set(0)
    val excludes = ArrayList<String>()
    excludeFile.forEachLine { line ->
        ln.incrementAndGet()
        if (line.isEmpty() || line.startsWith("#")) {
            excludes.add(line)
            return@forEachLine
        }
        try {
            val obf = mappings.findNewMappingByOldObfCN(line)?.className?.obf
            if (obf == null) {
                excludes.add(line)
                return@forEachLine
            }
            excludes.add(obf)
        } catch (e: Throwable) {
            System.err.println("Invalid exclude file at line ${ln.get()}: $line")
            System.err.println("Error message: ${e.javaClass.simpleName}: ${e.message}")
            if (debug) e.printStackTrace()
        }
    }
    ln.set(0)
    count.set(0)
    println("Writing file")
    run {
        val classes = ArrayList<String>()
        val classesMap = HashMap<String, String>()
        mappings.newMappingData.filter { data -> data.className.bukkitName != null }.forEach { data ->
            classes.add(data.className.obf)
            classesMap[data.className.obf] = data.className.bukkitName!!
        }
        if (outputClFile.exists()) outputClFile.delete()
        val clWriter = outputClFile.printWriter()
        classes.sorted().forEach { obf ->
            clWriter.println("$obf ${classesMap[obf]}")
        }
        clWriter.flush()
        clWriter.close()
    }
    run {
        val toWrite = ArrayList<String>()
        mappings.newMappingData.filter { data -> data.className.bukkitName != null }.forEach { data ->
            data.methods.filter { member -> member.bukkitName != null }.forEach { member ->
                toWrite.add("${data.className.bukkitName} ${member.obf} ${member.params.map { s -> mappings.findNewObfOrDefaultByDeobfCN(s) }.toSignature(mappings.findNewObfOrDefaultByDeobfCN(member.returnType!!))} ${member.bukkitName}")
            }
            data.fields.filter { member -> member.bukkitName != null }.forEach { member ->
                toWrite.add("${data.className.bukkitName} ${member.obf} ${member.bukkitName}")
            }
        }
        if (outputMembersFile.exists()) outputMembersFile.delete()
        val writer = outputMembersFile.printWriter()
        toWrite.sorted().forEach(writer::println)
        writer.flush()
        writer.close()
    }
    run {
        if (outputExcludeFile.exists()) outputExcludeFile.delete()
        val writer = outputExcludeFile.printWriter()
        excludes.sorted().forEach(writer::println)
        writer.flush()
        writer.close()
    }
    println("Complete!")
}
