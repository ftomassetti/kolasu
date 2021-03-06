package com.strumenta.kolasu.generation

import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.toJsonArray
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonWriter
import com.strumenta.kolasu.emf.KOLASU_METAMODEL
import com.strumenta.kolasu.emf.getEClass
import com.strumenta.kolasu.emf.saveAsJson
import com.strumenta.kolasu.emf.toEObject
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.model.processProperties
import com.strumenta.kolasu.validation.Issue
import com.strumenta.kolasu.validation.Result
import java.io.File
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EPackage

class JsonGenerator {

    /**
     * Converts an AST to JSON format.
     */
    fun generateJSON(root: Node): JsonElement {
        return root.toJson()
    }

    /**
     * Converts "results" to JSON format.
     */
    fun generateJSON(result: Result<out Node>): JsonElement {
        return jsonObject(
            "errors" to result.errors.map { it.toJson() }.toJsonArray(),
            "root" to result.root?.toJson()
        )
    }

    /**
     * Converts "results" to JSON format.
     */
    fun generateJSONWithStreaming(result: Result<out Node>, writer: JsonWriter) {
        writer.beginObject()
        writer.name("errors")
        writer.beginArray()
        result.errors.forEach { it.toJsonStreaming(writer) }
        writer.endArray()
        writer.name("root")
        if (result.root == null) {
            writer.nullValue()
        } else {
            result.root.toJsonStreaming(writer)
        }
        writer.endObject()
    }

    fun generateJSONWithStreaming(root: Node, writer: JsonWriter) {
        root.toJsonStreaming(writer)
    }

    fun generateString(root: Node): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(generateJSON(root))
    }

    fun generateString(result: Result<out Node>): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(generateJSON(result))
    }

    private fun pointAsEObject(point: Point): EObject {
        val ec = KOLASU_METAMODEL.getEClass(Point::class.java)
        val eo = KOLASU_METAMODEL.eFactoryInstance.create(ec)
        val lineSF = ec.eAllStructuralFeatures.find { it.name == "line" }!!
        eo.eSet(lineSF, point.line)
        val columnSF = ec.eAllStructuralFeatures.find { it.name == "column" }!!
        eo.eSet(columnSF, point.column)
        return eo
    }

    private fun positionAsEObject(position: Position): EObject {
        val ec = KOLASU_METAMODEL.getEClass(Position::class.java)
        val eo = KOLASU_METAMODEL.eFactoryInstance.create(ec)
        val startSF = ec.eAllStructuralFeatures.find { it.name == "start" }!!
        eo.eSet(startSF, pointAsEObject(position.start))
        val stopSF = ec.eAllStructuralFeatures.find { it.name == "end" }!!
        eo.eSet(stopSF, pointAsEObject(position.end))
        return eo
    }

    private fun issueAsEObject(issue: Issue): EObject {
        val ec = KOLASU_METAMODEL.getEClass(Issue::class.java)
        val eo = KOLASU_METAMODEL.eFactoryInstance.create(ec)
        val typeSF = ec.eAllStructuralFeatures.find { it.name == "type" }!!
        // TODO eo.eSet(typeSF, issue.type.ordinal)
        val messageSF = ec.eAllStructuralFeatures.find { it.name == "message" }!!
        eo.eSet(messageSF, issue.message)
        val positionSF = ec.eAllStructuralFeatures.find { it.name == "position" }!!
        if (issue.position != null) {
            eo.eSet(positionSF, positionAsEObject(issue.position!!))
        }
        return eo
    }

    fun generateEMFString(result: Result<out Node>, astPackage: EPackage): String {
        val resultEC = KOLASU_METAMODEL.getEClass(Result::class.java)
        val issueEC = KOLASU_METAMODEL.getEClass(Issue::class.java)
        val resultEO = KOLASU_METAMODEL.eFactoryInstance.create(resultEC)
        val issuesSF = resultEC.eAllStructuralFeatures.find { it.name == "issues" }!!
        val issues = resultEO.eGet(issuesSF) as MutableList<EObject>
        result.errors.forEach {
            issues.add(issueAsEObject(it))
        }
        val rootSF = resultEC.eAllStructuralFeatures.find { it.name == "root" }!!
        if (result.root != null) {
            resultEO.eSet(rootSF, result.root!!.toEObject(astPackage))
        }

        return resultEO.saveAsJson()
    }

    fun generateFile(root: Node, file: File) {
        File(file.toURI()).writeText(generateString(root))
    }

    fun generateFile(result: Result<out Node>, file: File) {
        File(file.toURI()).writeText(generateString(result))
    }
}

private fun Node.toJson(): JsonElement {
    val jsonObject = jsonObject(
        "type" to this.javaClass.canonicalName,
        "position" to this.position?.toJson()
    )
    this.processProperties {
        if (it.value == null) {
            jsonObject.add(it.name, JsonNull.INSTANCE)
        } else if (it.multiple) {
            if (it.provideNodes) {
                jsonObject.add(it.name, (it.value as Collection<*>).map { (it as Node).toJson() }.toJsonArray())
            } else {
                jsonObject.add(it.name, (it.value as Collection<*>).toJsonArray())
            }
        } else {
            if (it.provideNodes) {
                jsonObject.add(it.name, (it.value as Node).toJson())
            } else {
                jsonObject.add(it.name, it.value.toJson())
            }
        }
    }
    return jsonObject
}

private fun Node.toJsonStreaming(writer: JsonWriter) {
    writer.beginObject()
    writer.name("type")
    writer.value(this.javaClass.simpleName)
    if (this.position != null) {
        writer.name("position")
        this.position!!.toJsonStreaming(writer)
    }
    this.processProperties {
        writer.name(it.name)
        if (it.value == null) {
            writer.nullValue()
        } else if (it.multiple) {
            writer.beginArray()
            if (it.provideNodes) {
                (it.value as Collection<*>).forEach {
                    (it as Node).toJsonStreaming(writer)
                }
            } else {
                (it.value as Collection<*>).forEach {
                    it.toJsonStreaming(writer)
                }
            }
            writer.endArray()
        } else {
            if (it.provideNodes) {
                (it.value as Node).toJsonStreaming(writer)
            } else {
                it.value.toJsonStreaming(writer)
            }
        }
    }
    writer.endObject()
}

private fun Any?.toJson(): JsonElement? {
    return when (this) {
        null -> JsonNull.INSTANCE
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(this.toString())
    }
}

private fun Any?.toJsonStreaming(writer: JsonWriter) {
    when (this) {
        null -> writer.nullValue()
        is String -> writer.value(this)
        is Number -> writer.value(this)
        is Boolean -> writer.value(this)
        else -> writer.value(this.toString())
    }
}

private fun Issue.toJson(): JsonElement {
    return jsonObject(
        "type" to this.type.name,
        "message" to this.message,
        "position" to this.position?.toJson()
    )
}

private fun Issue.toJsonStreaming(writer: JsonWriter) {
    writer.beginObject()
    writer.name("type")
    writer.value(this.type.name)
    writer.name("message")
    writer.value(this.message)
    writer.name("position")
    if (this.position == null) {
        writer.nullValue()
    } else {
        this.position!!.toJsonStreaming(writer)
    }
    writer.endObject()
}

private fun Position.toJson(): JsonElement {
    return jsonObject(
        "description" to this.toString(),
        "start" to this.start.toJson(),
        "end" to this.end.toJson()
    )
}

private fun Position.toJsonStreaming(writer: JsonWriter) {
    writer.beginObject()
    writer.name("description")
    writer.value(this.toString())
    writer.name("start")
    start.toJsonStreaming(writer)
    writer.name("end")
    end.toJsonStreaming(writer)
    writer.endObject()
}

private fun Point.toJson(): JsonElement {
    return jsonObject(
        "line" to this.line,
        "column" to this.column
    )
}

private fun Point.toJsonStreaming(writer: JsonWriter) {
    writer.beginObject()
    writer.name("line")
    writer.value(this.line)
    writer.name("column")
    writer.value(this.column)
    writer.endObject()
}
