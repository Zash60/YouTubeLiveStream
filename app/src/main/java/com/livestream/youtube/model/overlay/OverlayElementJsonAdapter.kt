package com.livestream.youtube.model.overlay

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * Custom Gson adapter for serializing and deserializing OverlayElement subclasses.
 * Handles polymorphic type resolution based on the "type" field.
 */
class OverlayElementJsonAdapter : JsonSerializer<OverlayElement>, JsonDeserializer<OverlayElement> {

    override fun serialize(
        src: OverlayElement,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val json = JsonObject()
        json.add("type", JsonPrimitive(src.type))
        when (src) {
            is TextOverlayElement -> serializeTextElement(src, json)
            is ImageOverlayElement -> serializeImageElement(src, json)
            is TimerOverlayElement -> serializeTimerElement(src, json)
            is ViewerCountOverlayElement -> serializeViewerCountElement(src, json)
            is ChatOverlayElement -> serializeChatElement(src, json)
        }
        return json
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): OverlayElement {
        val jsonObject = json.asJsonObject
        val type = jsonObject.get("type")?.asString ?: return TextOverlayElement()

        return when (type) {
            OverlayElement.TYPE_TEXT -> deserializeTextElement(jsonObject)
            OverlayElement.TYPE_IMAGE -> deserializeImageElement(jsonObject)
            OverlayElement.TYPE_TIMER -> deserializeTimerElement(jsonObject)
            OverlayElement.TYPE_VIEWER_COUNT -> deserializeViewerCountElement(jsonObject)
            OverlayElement.TYPE_CHAT -> deserializeChatElement(jsonObject)
            else -> TextOverlayElement()
        }
    }

    private fun serializeTextElement(element: TextOverlayElement, json: JsonObject) {
        json.addProperty("id", element.id)
        json.addProperty("x", element.x)
        json.addProperty("y", element.y)
        json.addProperty("width", element.width)
        json.addProperty("height", element.height)
        json.addProperty("rotation", element.rotation)
        json.addProperty("opacity", element.opacity)
        json.addProperty("isVisible", element.isVisible)
        json.addProperty("zIndex", element.zIndex)
        json.addProperty("text", element.text)
        json.addProperty("fontFamily", element.fontFamily)
        json.addProperty("fontSize", element.fontSize)
        json.addProperty("textColor", element.textColor)
        json.addProperty("backgroundColor", element.backgroundColor)
        json.addProperty("hasShadow", element.hasShadow)
        json.addProperty("shadowColor", element.shadowColor)
        json.addProperty("shadowRadius", element.shadowRadius.toDouble())
        json.addProperty("shadowDx", element.shadowDx.toDouble())
        json.addProperty("shadowDy", element.shadowDy.toDouble())
        json.addProperty("isBold", element.isBold)
        json.addProperty("isItalic", element.isItalic)
        json.addProperty("alignment", element.alignment.name)
    }

    private fun serializeImageElement(element: ImageOverlayElement, json: JsonObject) {
        json.addProperty("id", element.id)
        json.addProperty("x", element.x)
        json.addProperty("y", element.y)
        json.addProperty("width", element.width)
        json.addProperty("height", element.height)
        json.addProperty("rotation", element.rotation)
        json.addProperty("opacity", element.opacity)
        json.addProperty("isVisible", element.isVisible)
        json.addProperty("zIndex", element.zIndex)
        json.addProperty("imagePath", element.imagePath)
        json.addProperty("scaleType", element.scaleType.name)
        json.addProperty("cornerRadius", element.cornerRadius.toDouble())
        json.addProperty("borderWidth", element.borderWidth)
        json.addProperty("borderColor", element.borderColor)
    }

    private fun serializeTimerElement(element: TimerOverlayElement, json: JsonObject) {
        json.addProperty("id", element.id)
        json.addProperty("x", element.x)
        json.addProperty("y", element.y)
        json.addProperty("width", element.width)
        json.addProperty("height", element.height)
        json.addProperty("rotation", element.rotation)
        json.addProperty("opacity", element.opacity)
        json.addProperty("isVisible", element.isVisible)
        json.addProperty("zIndex", element.zIndex)
        json.addProperty("format", element.format.name)
        json.addProperty("direction", element.direction.name)
        json.addProperty("startTime", element.startTime)
        json.addProperty("fontFamily", element.fontFamily)
        json.addProperty("fontSize", element.fontSize)
        json.addProperty("textColor", element.textColor)
        json.addProperty("backgroundColor", element.backgroundColor)
        json.addProperty("hasShadow", element.hasShadow)
        json.addProperty("showLabels", element.showLabels)
        json.addProperty("labelText", element.labelText)
    }

    private fun serializeViewerCountElement(element: ViewerCountOverlayElement, json: JsonObject) {
        json.addProperty("id", element.id)
        json.addProperty("x", element.x)
        json.addProperty("y", element.y)
        json.addProperty("width", element.width)
        json.addProperty("height", element.height)
        json.addProperty("rotation", element.rotation)
        json.addProperty("opacity", element.opacity)
        json.addProperty("isVisible", element.isVisible)
        json.addProperty("zIndex", element.zIndex)
        json.addProperty("fontFamily", element.fontFamily)
        json.addProperty("fontSize", element.fontSize)
        json.addProperty("textColor", element.textColor)
        json.addProperty("backgroundColor", element.backgroundColor)
        json.addProperty("hasShadow", element.hasShadow)
        json.addProperty("showIcon", element.showIcon)
        json.addProperty("iconType", element.iconType.name)
        json.addProperty("viewerValue", element.viewerValue)
    }

    private fun deserializeTextElement(json: JsonObject): TextOverlayElement {
        return TextOverlayElement(
            id = json.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
            x = json.get("x")?.asFloat ?: 0.1f,
            y = json.get("y")?.asFloat ?: 0.1f,
            width = json.get("width")?.asFloat ?: 0.3f,
            height = json.get("height")?.asFloat ?: 0.05f,
            rotation = json.get("rotation")?.asFloat ?: 0f,
            opacity = json.get("opacity")?.asFloat ?: 1f,
            isVisible = json.get("isVisible")?.asBoolean ?: true,
            zIndex = json.get("zIndex")?.asInt ?: 0,
            text = json.get("text")?.asString ?: "Sample Text",
            fontFamily = json.get("fontFamily")?.asString ?: "sans-serif-medium",
            fontSize = json.get("fontSize")?.asInt ?: 24,
            textColor = json.get("textColor")?.asInt ?: android.graphics.Color.WHITE,
            backgroundColor = json.get("backgroundColor")?.asInt ?: android.graphics.Color.TRANSPARENT,
            hasShadow = json.get("hasShadow")?.asBoolean ?: true,
            shadowColor = json.get("shadowColor")?.asInt ?: android.graphics.Color.BLACK,
            shadowRadius = json.get("shadowRadius")?.asFloat ?: 4f,
            shadowDx = json.get("shadowDx")?.asFloat ?: 2f,
            shadowDy = json.get("shadowDy")?.asFloat ?: 2f,
            isBold = json.get("isBold")?.asBoolean ?: false,
            isItalic = json.get("isItalic")?.asBoolean ?: false,
            alignment = try {
                TextOverlayElement.TextAlignment.valueOf(
                    json.get("alignment")?.asString ?: "LEFT"
                )
            } catch (e: Exception) {
                TextOverlayElement.TextAlignment.LEFT
            }
        )
    }

    private fun deserializeImageElement(json: JsonObject): ImageOverlayElement {
        return ImageOverlayElement(
            id = json.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
            x = json.get("x")?.asFloat ?: 0.1f,
            y = json.get("y")?.asFloat ?: 0.1f,
            width = json.get("width")?.asFloat ?: 0.2f,
            height = json.get("height")?.asFloat ?: 0.1f,
            rotation = json.get("rotation")?.asFloat ?: 0f,
            opacity = json.get("opacity")?.asFloat ?: 1f,
            isVisible = json.get("isVisible")?.asBoolean ?: true,
            zIndex = json.get("zIndex")?.asInt ?: 0,
            imagePath = json.get("imagePath")?.asString ?: "",
            scaleType = try {
                ImageOverlayElement.ScaleType.valueOf(
                    json.get("scaleType")?.asString ?: "FIT_CENTER"
                )
            } catch (e: Exception) {
                ImageOverlayElement.ScaleType.FIT_CENTER
            },
            cornerRadius = json.get("cornerRadius")?.asFloat ?: 0f,
            borderWidth = json.get("borderWidth")?.asInt ?: 0,
            borderColor = json.get("borderColor")?.asInt ?: 0
        )
    }

    private fun deserializeTimerElement(json: JsonObject): TimerOverlayElement {
        return TimerOverlayElement(
            id = json.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
            x = json.get("x")?.asFloat ?: 0.1f,
            y = json.get("y")?.asFloat ?: 0.1f,
            width = json.get("width")?.asFloat ?: 0.2f,
            height = json.get("height")?.asFloat ?: 0.05f,
            rotation = json.get("rotation")?.asFloat ?: 0f,
            opacity = json.get("opacity")?.asFloat ?: 1f,
            isVisible = json.get("isVisible")?.asBoolean ?: true,
            zIndex = json.get("zIndex")?.asInt ?: 0,
            format = try {
                TimerOverlayElement.TimerFormat.valueOf(
                    json.get("format")?.asString ?: "HH_MM_SS"
                )
            } catch (e: Exception) {
                TimerOverlayElement.TimerFormat.HH_MM_SS
            },
            direction = try {
                TimerOverlayElement.TimerDirection.valueOf(
                    json.get("direction")?.asString ?: "UP"
                )
            } catch (e: Exception) {
                TimerOverlayElement.TimerDirection.UP
            },
            startTime = json.get("startTime")?.asLong ?: System.currentTimeMillis(),
            fontFamily = json.get("fontFamily")?.asString ?: "monospace",
            fontSize = json.get("fontSize")?.asInt ?: 32,
            textColor = json.get("textColor")?.asInt ?: android.graphics.Color.WHITE,
            backgroundColor = json.get("backgroundColor")?.asInt ?: android.graphics.Color.TRANSPARENT,
            hasShadow = json.get("hasShadow")?.asBoolean ?: true,
            showLabels = json.get("showLabels")?.asBoolean ?: false,
            labelText = json.get("labelText")?.asString ?: "LIVE"
        )
    }

    private fun deserializeViewerCountElement(json: JsonObject): ViewerCountOverlayElement {
        return ViewerCountOverlayElement(
            id = json.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
            x = json.get("x")?.asFloat ?: 0.85f,
            y = json.get("y")?.asFloat ?: 0.02f,
            width = json.get("width")?.asFloat ?: 0.14f,
            height = json.get("height")?.asFloat ?: 0.05f,
            rotation = json.get("rotation")?.asFloat ?: 0f,
            opacity = json.get("opacity")?.asFloat ?: 1f,
            isVisible = json.get("isVisible")?.asBoolean ?: true,
            zIndex = json.get("zIndex")?.asInt ?: 0,
            fontFamily = json.get("fontFamily")?.asString ?: "monospace",
            fontSize = json.get("fontSize")?.asInt ?: 24,
            textColor = json.get("textColor")?.asInt ?: android.graphics.Color.WHITE,
            backgroundColor = json.get("backgroundColor")?.asInt ?: android.graphics.Color.TRANSPARENT,
            hasShadow = json.get("hasShadow")?.asBoolean ?: true,
            showIcon = json.get("showIcon")?.asBoolean ?: true,
            iconType = try {
                ViewerCountOverlayElement.IconType.valueOf(
                    json.get("iconType")?.asString ?: "EYE"
                )
            } catch (e: Exception) {
                ViewerCountOverlayElement.IconType.EYE
            },
            viewerValue = json.get("viewerValue")?.asString ?: "0"
        )
    }

    private fun serializeChatElement(element: ChatOverlayElement, json: JsonObject) {
        json.addProperty("id", element.id)
        json.addProperty("x", element.x)
        json.addProperty("y", element.y)
        json.addProperty("width", element.width)
        json.addProperty("height", element.height)
        json.addProperty("rotation", element.rotation)
        json.addProperty("opacity", element.opacity)
        json.addProperty("isVisible", element.isVisible)
        json.addProperty("zIndex", element.zIndex)
        json.addProperty("maxMessages", element.maxMessages)
        json.addProperty("showTimestamps", element.showTimestamps)
        json.addProperty("fontFamily", element.fontFamily)
        json.addProperty("fontSize", element.fontSize)
        json.addProperty("textColor", element.textColor)
        json.addProperty("backgroundColor", element.backgroundColor)
        json.addProperty("hasShadow", element.hasShadow)
        json.addProperty("showBadges", element.showBadges)
        json.addProperty("messageAnimation", element.messageAnimation.name)
    }

    private fun deserializeChatElement(json: JsonObject): ChatOverlayElement {
        return ChatOverlayElement(
            id = json.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
            x = json.get("x")?.asFloat ?: 0.02f,
            y = json.get("y")?.asFloat ?: 0.5f,
            width = json.get("width")?.asFloat ?: 0.3f,
            height = json.get("height")?.asFloat ?: 0.45f,
            rotation = json.get("rotation")?.asFloat ?: 0f,
            opacity = json.get("opacity")?.asFloat ?: 1f,
            isVisible = json.get("isVisible")?.asBoolean ?: true,
            zIndex = json.get("zIndex")?.asInt ?: 0,
            maxMessages = json.get("maxMessages")?.asInt ?: 50,
            showTimestamps = json.get("showTimestamps")?.asBoolean ?: true,
            fontFamily = json.get("fontFamily")?.asString ?: "sans-serif",
            fontSize = json.get("fontSize")?.asInt ?: 14,
            textColor = json.get("textColor")?.asInt ?: android.graphics.Color.WHITE,
            backgroundColor = json.get("backgroundColor")?.asInt ?: android.graphics.Color.argb(128, 0, 0, 0),
            hasShadow = json.get("hasShadow")?.asBoolean ?: true,
            showBadges = json.get("showBadges")?.asBoolean ?: true,
            messageAnimation = try {
                ChatOverlayElement.MessageAnimation.valueOf(
                    json.get("messageAnimation")?.asString ?: "SCROLL"
                )
            } catch (e: Exception) {
                ChatOverlayElement.MessageAnimation.SCROLL
            }
        )
    }
}
