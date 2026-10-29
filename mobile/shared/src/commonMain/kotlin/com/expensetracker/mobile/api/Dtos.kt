package com.expensetracker.mobile.api

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * Money travels as a decimal string ("12.50") so no float is ever involved.
 * On decode it also accepts a bare JSON number, which is what the backend returns.
 */
object DecimalStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DecimalString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String =
        if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.content
        else decoder.decodeString()
}

@Serializable
data class ExpenseDto(
    val id: Long,
    val clientId: String? = null,
    val description: String,
    @Serializable(with = DecimalStringSerializer::class) val amount: String,
    val date: LocalDate,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

/** Body for POST and PUT /api/v1/expenses. clientId is required on POST and ignored by PUT. */
@Serializable
data class ExpenseWriteDto(
    val clientId: String,
    val description: String,
    @Serializable(with = DecimalStringSerializer::class) val amount: String,
    val date: LocalDate,
    val categoryId: Long? = null,
    val notes: String? = null,
)

@Serializable
data class CategoryDto(
    val id: Long,
    val clientId: String? = null,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val global: Boolean = false,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

@Serializable
data class CategoryWriteDto(
    val clientId: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
)

/** One page of GET /api/v1/{expenses|categories}/changes. Tombstones are included. */
@Serializable
data class ChangePage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val serverTime: Instant,
)

@Serializable
data class SendOtpRequestDto(val name: String, val email: String)

@Serializable
data class RegisterRequestDto(val name: String, val email: String, val password: String, val otp: String)

@Serializable
data class AuthRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class AuthResponseDto(
    val token: String,
    val refreshToken: String,
    val expiresIn: Long,
    val name: String,
    val email: String,
    val currency: String? = null,
)
