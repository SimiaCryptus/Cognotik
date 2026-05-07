package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.DynamicEnumDeserializer
import com.simiacryptus.cognotik.util.DynamicEnumSerializer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Represents a login method (e.g. username/password, OAuth provider, etc.)
 *
 * Each login method declares:
 *  - a unique [name] (used for serialization/lookup)
 *  - a human-readable [displayName] for UI rendering
 *  - an optional [iconUrl] used by the login page
 *
 * Concrete implementations are responsible for handling the GET (render UI / start
 * the flow) and POST (process credentials / callback) requests for their flow.
 */
@JsonSerialize(using = LoginMethodSerializer::class)
@JsonDeserialize(using = LoginMethodDeserializer::class)
abstract class LoginMethod(
    name: String,
    val displayName: String,
    val iconUrl: String? = null,
) : DynamicEnum<LoginMethod>(name) {

    /**
     * Render any UI / markup specific to this login method.
     *
     * For the default username/password method this returns the standard form.
     * For OAuth providers this would typically return a button / link that
     * initiates the OAuth flow.
     */
    abstract fun renderForm(request: HttpServletRequest, redirect: String?): String

    /**
     * Handle a POST submission for this login method.
     *
     * Implementations should write to the response (set cookies, redirects, etc.)
     * and return true if handling completed (no further processing needed),
     * or false to allow other handlers to continue.
     */
    abstract fun handleLogin(request: HttpServletRequest, response: HttpServletResponse): Boolean

    companion object {
        @JvmStatic
        fun register(method: LoginMethod) = DynamicEnum.register(LoginMethod::class.java, method)

        @JvmStatic
        fun unregister(name: String) = DynamicEnum.unregister(LoginMethod::class.java, name)

        @JvmStatic
        fun valueOf(name: String): LoginMethod =
            DynamicEnum.valueOf(LoginMethod::class.java, name)

        @JvmStatic
        fun values(): List<LoginMethod> = DynamicEnum.values(LoginMethod::class.java)
    }
}

class LoginMethodSerializer : DynamicEnumSerializer<LoginMethod>(LoginMethod::class.java)
class LoginMethodDeserializer : DynamicEnumDeserializer<LoginMethod>(LoginMethod::class.java)