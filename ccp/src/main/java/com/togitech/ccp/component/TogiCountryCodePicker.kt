package com.togitech.ccp.component

import android.content.Context
import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.togitech.ccp.R
import com.togitech.ccp.data.CountryData
import com.togitech.ccp.data.Iso31661alpha2
import com.togitech.ccp.data.PhoneCode
import com.togitech.ccp.data.utils.ValidatePhoneNumber
import com.togitech.ccp.data.utils.extractCountryCode
import com.togitech.ccp.data.utils.getCountryFromPhoneCode
import com.togitech.ccp.data.utils.getUserIsoCode
import com.togitech.ccp.data.utils.numberHint
import com.togitech.ccp.transformation.PhoneNumberTransformation
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.launch

private val DEFAULT_TEXT_FIELD_SHAPE = RoundedCornerShape(24.dp)
private const val TAG = "TogiCountryCodePicker"

/**
 * @param onValueChange Called when the text in the text field changes.
 * The first parameter is string pair of (country phone code, phone number) and the second parameter is
 * a boolean indicating whether the phone number is valid.
 * @param modifier Modifier to be applied to the inner OutlinedTextField.
 * @param autoDetectCode Boolean indicating if will auto detect the code from initial phone number
 * @param enabled Boolean indicating whether the field is enabled.
 * @param shape Shape of the text field.
 * @param showCountryCode Whether to show the country code in the text field.
 * @param showCountryFlag Whether to show the country flag in the text field.
 * @param colors TextFieldColors to be used for the text field.
 * @param fallbackCountry The country to be used as a fallback if the user's country cannot be determined.
 * Defaults to the United States.
 * @param showPlaceholder Whether to show the placeholder number hint in the text field.
 * @param includeOnly A set of 2 digit country codes to be included in the list of countries.
 * Set to null to include all supported countries.
 * @param clearIcon ImageVector to be used for the clear button. Set to null to disable the clear button.
 * Defaults to Icons.Filled.Clear
 * @param initialPhoneNumber an optional phone number to be initial value of the input field
 * @param initialCountryIsoCode Optional ISO-3166-1 alpha-2 country code to set the initially selected country.
 * Note that if a valid initialCountryPhoneCode is provided, this will be ignored.
 * @param initialCountryPhoneCode Optional country phone code to set the initially selected country.
 * This takes precedence over [initialCountryIsoCode].
 * @param label An optional composable to be used as a label for input field
 * @param textStyle An optional [TextStyle] for customizing text style of phone number input field.
 * Defaults to MaterialTheme.typography.body1
 * @param [keyboardOptions] An optional [KeyboardOptions] to customize keyboard options.
 * @param [keyboardActions] An optional [KeyboardActions] to customize keyboard actions.
 * @param [showError] Whether to show error on field when number is invalid, default true.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("LongMethod")
@Composable
fun TogiCountryCodePicker(
    onValueChange: (Pair<PhoneCode, String>, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    autoDetectCode: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = DEFAULT_TEXT_FIELD_SHAPE,
    showCountryCode: Boolean = true,
    showCountryFlag: Boolean = true,
    colors: TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(),
    fallbackCountry: CountryData = CountryData.UnitedStates,
    showPlaceholder: Boolean = true,
    includeOnly: ImmutableSet<String>? = null,
    clearIcon: ImageVector? = Icons.Filled.Clear,
    initialPhoneNumber: String? = null,
    initialCountryIsoCode: Iso31661alpha2? = null,
    initialCountryPhoneCode: PhoneCode? = null,
    label: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.body1.copy(
        color = colors.textColor(enabled = true).value,
    ),
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions? = null,
    showError: Boolean = true,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val countryCode = autoDetectedCountryCode(
        autoDetectCode = autoDetectCode,
        initialPhoneNumber = initialPhoneNumber,
    )

    val phoneNumberWithoutCode = if (countryCode != null) {
        initialPhoneNumber?.replace(countryCode, "")
    } else {
        initialPhoneNumber
    }

    var phoneNumber by remember {
        mutableStateOf(
            TextFieldValue(
                text = phoneNumberWithoutCode.orEmpty(),
                selection = TextRange(phoneNumberWithoutCode?.length ?: 0),
            ),
        )
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    var country: CountryData by rememberSaveable(
        context,
        countryCode,
        initialCountryPhoneCode,
        initialCountryIsoCode,
    ) {
        mutableStateOf(
            configureInitialCountry(
                initialCountryPhoneCode = countryCode ?: initialCountryPhoneCode,
                context = context,
                initialCountryIsoCode = initialCountryIsoCode,
                fallbackCountry = fallbackCountry,
            ),
        )
    }

    val phoneNumberTransformation = remember(country) {
        PhoneNumberTransformation(country.countryIso, context)
    }
    val validatePhoneNumber = remember(context) { ValidatePhoneNumber(context) }

    var isNumberValid: Boolean by rememberSaveable(country, phoneNumber) {
        mutableStateOf(
            validatePhoneNumber(
                fullPhoneNumber = country.countryPhoneCode + phoneNumber.text,
            ),
        )
    }

    val coroutineScope = rememberCoroutineScope()

    OutlinedTextField(
        value = phoneNumber,
        onValueChange = { enteredPhoneNumber ->
            val preFilteredPhoneNumber = phoneNumberTransformation.preFilter(enteredPhoneNumber)
            phoneNumber = TextFieldValue(
                text = preFilteredPhoneNumber,
                selection = TextRange(preFilteredPhoneNumber.length),
            )
            isNumberValid = validatePhoneNumber(
                fullPhoneNumber = country.countryPhoneCode + phoneNumber.text,
            )
            onValueChange(country.countryPhoneCode to phoneNumber.text, isNumberValid)
        },
        modifier = modifier
            .fillMaxWidth()
            .focusable()
            .autofill(
                autofillTypes = listOf(AutofillType.PhoneNumberNational),
                onFill = { filledPhoneNumber ->
                    val preFilteredPhoneNumber =
                        phoneNumberTransformation.preFilter(filledPhoneNumber)
                    phoneNumber = TextFieldValue(
                        text = preFilteredPhoneNumber,
                        selection = TextRange(preFilteredPhoneNumber.length),
                    )
                    isNumberValid = validatePhoneNumber(
                        fullPhoneNumber = country.countryPhoneCode + phoneNumber.text,
                    )
                    onValueChange(country.countryPhoneCode to phoneNumber.text, isNumberValid)
                    keyboardController?.hide()
                    coroutineScope.launch {
                        focusRequester.safeFreeFocus()
                    }
                },
                focusRequester = focusRequester,
            )
            .focusRequester(focusRequester = focusRequester),
        enabled = enabled,
        textStyle = textStyle,
        label = label,
        placeholder = {
            if (showPlaceholder) {
                PlaceholderNumberHint(country.countryIso)
            }
        },
        leadingIcon = {
            TogiCodeDialog(
                selectedCountry = country,
                includeOnly = includeOnly,
                onCountryChange = { countryData ->
                    country = countryData
                    isNumberValid = validatePhoneNumber(
                        fullPhoneNumber = country.countryPhoneCode + phoneNumber.text,
                    )
                    onValueChange(country.countryPhoneCode to phoneNumber.text, isNumberValid)
                },
                showCountryCode = showCountryCode,
                showFlag = showCountryFlag,
                textStyle = textStyle,
                backgroundColor = colors.backgroundColor(enabled = true).value.let { color ->
                    if (color == Color.Unspecified || color == Color.Transparent) {
                        MaterialTheme.colors.surface
                    } else {
                        color
                    }
                },
            )
        },
        trailingIcon = {
            if (clearIcon != null) {
                ClearIconButton(
                    imageVector = clearIcon,
                    colors = colors,
                    isNumberValid = !showError || isNumberValid,
                ) {
                    phoneNumber = TextFieldValue("")
                    isNumberValid = false
                    onValueChange(country.countryPhoneCode to phoneNumber.text, isNumberValid)
                }
            }
        },
        isError = showError && !isNumberValid,
        visualTransformation = phoneNumberTransformation,
        keyboardOptions = keyboardOptions ?: KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Phone,
            autoCorrectEnabled = true,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = keyboardActions ?: KeyboardActions(
            onDone = {
                keyboardController?.hide()
                coroutineScope.launch {
                    focusRequester.safeFreeFocus()
                }
            },
        ),
        singleLine = true,
        shape = shape,
        colors = colors,
    )
}

private fun configureInitialCountry(
    initialCountryPhoneCode: PhoneCode?,
    context: Context,
    initialCountryIsoCode: Iso31661alpha2?,
    fallbackCountry: CountryData,
): CountryData {
    if (initialCountryPhoneCode?.run { !startsWith("+") } == true) {
        Log.e(TAG, "initialCountryPhoneCode must start with +")
    }
    return initialCountryPhoneCode?.let { getCountryFromPhoneCode(it, context) }
        ?: CountryData.entries.firstOrNull { it.countryIso == initialCountryIsoCode }
        ?: CountryData.isoMap[getUserIsoCode(context)]
        ?: fallbackCountry
}

private fun FocusRequester.safeFreeFocus() {
    try {
        this.freeFocus()
    } catch (exception: IllegalStateException) {
        Log.e(TAG, "Unable to free focus", exception)
    }
}

@Composable
private fun PlaceholderNumberHint(countryIso: Iso31661alpha2) {
    Text(
        text = stringResource(
            id = numberHint.getOrDefault(countryIso, R.string.unknown),
        ),
    )
}

@Composable
private fun ClearIconButton(
    imageVector: ImageVector,
    colors: TextFieldColors,
    isNumberValid: Boolean,
    onClick: () -> Unit,
) = IconButton(onClick = onClick) {
    Icon(
        imageVector = imageVector,
        contentDescription = stringResource(id = R.string.clear),
        tint = colors.trailingIconColor(
            enabled = true,
            isError = !isNumberValid,
            interactionSource = remember { MutableInteractionSource() },
        ).value,
    )
}

@Composable
private fun autoDetectedCountryCode(autoDetectCode: Boolean, initialPhoneNumber: String?): String? =
    if (initialPhoneNumber?.startsWith("+") == true && autoDetectCode) {
        extractCountryCode(initialPhoneNumber)
    } else {
        null
    }

@Preview
@Composable
private fun TogiCountryCodePickerPreview() {
    TogiCountryCodePicker(
        onValueChange = { _, _ -> },
        showCountryCode = true,
        showCountryFlag = true,
        showPlaceholder = true,
        includeOnly = null,
    )
}
