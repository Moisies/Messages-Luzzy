package app.luzzy.extensions.gson

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.internal.LinkedTreeMap
import java.lang.reflect.Type
import kotlin.math.ceil

class MapDeserializerDoubleAsIntFix : JsonDeserializer<Map<String, Any>?> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Map<String, Any>? {
        return read(json) as Map<String, Any>?
    }

    fun read(element: JsonElement): Any? {
        when {
            element.isJsonArray -> {
                val list: MutableList<Any?> = ArrayList()
                val arr = element.asJsonArray
                for (anArr in arr) {
                    list.add(read(anArr))
                }
                return list
            }

            element.isJsonObject -> {
                val map: MutableMap<String, Any?> = LinkedTreeMap()
                val obj = element.asJsonObject
                val entitySet = obj.entrySet()
                for ((key, value) in entitySet) {
                    map[key] = read(value)
                }
                return map
            }

            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isBoolean -> {
                        return prim.asBoolean
                    }
                    prim.isString -> {
                        return prim.asString
                    }
                    prim.isNumber -> {
                        val num = prim.asNumber

                        return if (ceil(num.toDouble()) == num.toLong().toDouble()) num.toLong() else num.toDouble()
                    }
                }
            }
        }
        return null
    }
}
