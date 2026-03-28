package app.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed interface UiText {
    data class Resource(
        val id: StringResource,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

fun uiText(id: StringResource, vararg args: Any): UiText = UiText.Resource(id, args.toList())

@Composable
internal fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Dynamic -> value
}
