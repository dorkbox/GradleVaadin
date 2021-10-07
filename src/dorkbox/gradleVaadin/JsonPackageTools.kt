package dorkbox.gradleVaadin

import elemental.json.*
import elemental.json.impl.JsonUtil
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class JsonPackageTools {
    companion object {
        val regex = "\\\\".toRegex()

        fun mergeJson(sourceJson: JsonObject, destJson: JsonObject) {
            sourceJson.keys().forEach { origKey ->
                val origValue = sourceJson.get(origKey) as JsonValue
                updateJsonValue(sourceJson, destJson, origKey, origValue)
            }
        }

        private fun updateJsonArray(source: JsonArray, dest: JsonArray, index: Int, value: JsonValue) {
            // overwrite the generated values.
            when (value.type) {
                JsonType.OBJECT -> {
                    val jsonObject = value as JsonObject

                    var destObject = dest.get<JsonObject>(index)
                    if (destObject !is JsonObject) {
                        destObject = Json.createObject()
                    }

                    jsonObject.keys().forEach { origKey ->
                        val origValue = jsonObject.get(origKey) as JsonValue
                        updateJsonValue(jsonObject, destObject, origKey, origValue)
                    }

//                    println("o-$index : $destObject")
                    dest.set(index, destObject)
                }
                JsonType.ARRAY  -> {
                    val origValue = source.getArray(index)
                    val destArray = Json.createArray()

                    for (i in 0..origValue.length()) {
                        val newVal = origValue.get(i) as JsonValue
                        updateJsonArray(origValue, destArray, i, newVal)
                    }

//                    println("a-$index : $destArray")
                    dest.set(index, destArray)
                }
                JsonType.STRING -> {
                    val string = source.getString(index)
//                    println("s-$index : $string")
                    dest.set(index, string)
                }
                JsonType.NUMBER -> {
                    val number = source.getNumber(index)
//                    println("n-$index : $number")
                    dest.set(index, number)
                }
                JsonType.BOOLEAN -> {
                    val boolean = source.getBoolean(index)
//                    println("b-$index : $boolean")
                    dest.set(index, boolean)
                }
                JsonType.NULL -> {
//                    println("$index : null")
                    dest.set(index, Json.createNull())
                }
                else -> println("Unable to insert key $index value ($value) into generated json array!")
            }
        }

        private fun updateJsonValue(source: JsonObject, dest: JsonObject, key: String, value: JsonValue) {
            // overwrite the generated values.
            when (value.type) {
                JsonType.OBJECT -> {
                    val jsonObject = value as JsonObject

                    var destObject = dest.get<JsonObject>(key)
                    if (destObject !is JsonObject) {
                        destObject = Json.createObject()
                    }

                    jsonObject.keys().forEach { origKey ->
                        val origValue = jsonObject.get(origKey) as JsonValue
                        updateJsonValue(jsonObject, destObject, origKey, origValue)
                    }

//                    println("o-$key : $destObject")
                    dest.put(key, destObject)
                }
                JsonType.ARRAY  -> {
                    val origValue = source.getArray(key)
                    val destArray = Json.createArray()

                    for (i in 0..origValue.length()) {
                        val newVal = origValue.get(i) as JsonValue
                        updateJsonArray(origValue, destArray, i, newVal)
                    }

//                    println("a-$key : $destArray")
                    dest.put(key, destArray)
                }
                JsonType.STRING -> {
                    val string = source.getString(key)
//                    println("s-$key : $string")
                    dest.put(key, string)
                }
                JsonType.NUMBER -> {
                    val number = source.getNumber(key)
//                    println("n-$key : $number")
                    dest.put(key, number)
                }
                JsonType.BOOLEAN -> {
                    val boolean = source.getBoolean(key)
//                    println("b-$key : $boolean")
                    dest.put(key, boolean)
                }
                JsonType.NULL -> {
//                    println("$key : null")
                    dest.put(key, Json.createNull())
                }
                else -> println("Unable to insert key $key value ($value) into generated json file!")
            }
        }

        fun getJson(jsonFile: File): JsonObject? {
            if (jsonFile.canRead()) {
                return JsonUtil.parse(jsonFile.readText(Charsets.UTF_8)) as JsonObject?
            }
            return null
        }

        fun writeJson(jsonFile: File, jsonObject: JsonObject, debug: Boolean = true) {
            if (debug) {
                println("\tSaving json: $jsonFile")
            }
            jsonFile.ensureParentDirsCreated()
            jsonFile.writeText(JsonUtil.stringify(jsonObject, 2) + "\n", Charsets.UTF_8)
        }

        fun getOrCreateKey(json: JsonObject, key: String): JsonObject {
            if (!json.hasKey(key)) {
                json.put(key, Json.createObject())
            }
            return json.get(key)
        }

        fun addDependency(json: JsonObject, key: String, value: Boolean, overwrite: Boolean = false): Int {
            if (!json.hasKey(key) || (overwrite && json.getBoolean(key) != value)) {
                json.put(key, value)
                println("\t\tAdded '$key':'$value'")

                return 1
            }

            return 0
        }
    }
}
