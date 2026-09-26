package io.github.samolego.canta.ops

import org.json.JSONArray
import org.json.JSONObject

/** Version one is the legacy flat snapshot shape with an explicit schema marker. */
object SnapshotState {
    private const val SCHEMA = "snapshotSchema"
    fun version(value: JSONObject): JSONObject = value.put(SCHEMA, 1)
    fun read(value: String): JSONObject = JSONObject(value).also {
        require(!it.has(SCHEMA) || it.getInt(SCHEMA) == 1) { "Unsupported operation snapshot schema" }
    }
    fun equal(left: String, right: String): Boolean = runCatching { equal(read(left), read(right)) }.getOrDefault(false)
    fun equal(left: JSONObject, right: JSONObject): Boolean {
        require((!left.has(SCHEMA) || left.getInt(SCHEMA) == 1) && (!right.has(SCHEMA) || right.getInt(SCHEMA) == 1)) { "Unsupported operation snapshot schema" }
        fun keys(value: JSONObject) = value.keys().asSequence().filter { it != SCHEMA }.toSet()
        return keys(left) == keys(right) && keys(left).all { equivalent(left.get(it), right.get(it)) }
    }
    private fun equivalent(left: Any?, right: Any?): Boolean = when {
        left is JSONObject && right is JSONObject -> equal(left, right)
        left is JSONArray && right is JSONArray -> left.length() == right.length() &&
            (0 until left.length()).all { equivalent(left.get(it), right.get(it)) }
        left is Number && right is Number -> left.toString().toBigDecimalOrNull() == right.toString().toBigDecimalOrNull()
        else -> left == right
    }
}
