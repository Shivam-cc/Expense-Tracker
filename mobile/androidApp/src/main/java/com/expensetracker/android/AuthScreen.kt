package com.expensetracker.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FieldShape = RoundedCornerShape(8.dp)
private val CardShape = RoundedCornerShape(16.dp)

@Composable
internal fun AuthScreen(vm: ExpenseViewModel, busy: Boolean, modifier: Modifier = Modifier) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var otp by remember { mutableStateOf("") }
    AuthForm(
        register = register,
        name = name,
        email = email,
        password = password,
        passwordVisible = passwordVisible,
        otp = otp,
        busy = busy,
        onRegisterChange = { register = it },
        onNameChange = { name = it },
        onEmailChange = { email = it },
        onPasswordChange = { password = it },
        onPasswordVisibleChange = { passwordVisible = it },
        onOtpChange = { otp = it },
        onSendOtp = { vm.sendOtp(name, email) },
        onSubmit = { vm.authenticate(Credentials(name, email, password, otp, register)) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthForm(
    register: Boolean,
    name: String,
    email: String,
    password: String,
    passwordVisible: Boolean,
    otp: String,
    busy: Boolean,
    onRegisterChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onOtpChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val otpFocus = remember { FocusRequester() }
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        val wide = maxWidth >= 720.dp
        val short = maxHeight < 640.dp
        val tall = maxHeight >= 780.dp
        Column(
            Modifier
                .fillMaxSize()
                .background(if (wide) ExpenseFlowCanvas else Color.White)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (wide) 28.dp else 24.dp, vertical = if (short) 16.dp else 28.dp)
                    .then(
                        if (wide) {
                            Modifier
                                .padding(top = 24.dp)
                                .background(Color.White, CardShape)
                                .border(1.dp, ExpenseFlowLine, CardShape)
                                .padding(horizontal = 32.dp, vertical = 36.dp)
                        } else {
                            Modifier
                        }
                    ),
            ) {
                BrandLockup()
                Spacer(Modifier.height(if (short) 24.dp else if (tall) 64.dp else 40.dp))
                Text(
                    if (register) "Create your account" else "Welcome back",
                    modifier = Modifier.fillMaxWidth(),
                    color = ExpenseFlowInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (short) 28.sp else 32.sp,
                    lineHeight = if (short) 34.sp else 40.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (register) {
                        "Enter your details and the email code to open your account."
                    } else {
                        "Expired sessions keep your offline data. Sign in with the same account to continue syncing."
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    color = ExpenseFlowMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(if (short) 20.dp else 32.dp))
                if (register) {
                    AuthField(
                        label = "Name",
                        value = name,
                        onValueChange = onNameChange,
                        enabled = !busy,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                        onImeAction = { emailFocus.requestFocus() },
                    )
                    Spacer(Modifier.height(16.dp))
                }
                AuthField(
                    label = "Email",
                    value = email,
                    onValueChange = onEmailChange,
                    enabled = !busy,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    onImeAction = { passwordFocus.requestFocus() },
                    modifier = Modifier.focusRequester(emailFocus),
                )
                Spacer(Modifier.height(16.dp))
                AuthField(
                    label = "Password",
                    value = password,
                    onValueChange = onPasswordChange,
                    enabled = !busy,
                    keyboardType = KeyboardType.Password,
                    imeAction = if (register) ImeAction.Next else ImeAction.Done,
                    onImeAction = { if (register) otpFocus.requestFocus() else if (!busy) onSubmit() },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.focusRequester(passwordFocus),
                    trailing = {
                        Text(
                            if (passwordVisible) "Hide" else "Show",
                            color = ExpenseFlowBlue,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .clickable(enabled = !busy) { onPasswordVisibleChange(!passwordVisible) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    },
                )
                if (register) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onSendOtp,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = FieldShape,
                        border = BorderStroke(1.dp, ExpenseFlowLine),
                    ) {
                        Text("Send email OTP", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(16.dp))
                    AuthField(
                        label = "OTP",
                        value = otp,
                        onValueChange = onOtpChange,
                        enabled = !busy,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (!busy) onSubmit() },
                        modifier = Modifier.focusRequester(otpFocus),
                    )
                }
                Spacer(Modifier.height(if (short) 16.dp else 24.dp))
                Button(
                    onClick = onSubmit,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = FieldShape,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = ExpenseFlowBlue,
                        disabledContentColor = Color.White,
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        if (register) "Create account" else "Sign in",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (register) "Already have an account?" else "Don't have an account?",
                        color = ExpenseFlowMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, end = 4.dp),
                    )
                    Text(
                        if (register) "Sign in" else "Sign up",
                        color = if (busy) ExpenseFlowMuted else ExpenseFlowBlue,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !busy) { onRegisterChange(!register) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandLockup() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .background(ExpenseFlowBlue, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("E", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text("ExpenseFlow", color = ExpenseFlowInk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        !enabled -> ExpenseFlowLine.copy(alpha = 0.7f)
        focused -> ExpenseFlowBlue
        else -> ExpenseFlowLine
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            // The visible label is cleared below so it is not a second "Email" node.
            // This puts that same word on the field node tests type into.
            .semantics(mergeDescendants = true) { text = AnnotatedString(label) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (enabled) ExpenseFlowInk else ExpenseFlowMuted,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        cursorBrush = SolidColor(ExpenseFlowBlue),
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    label,
                    color = Color(0xFF374151),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(Color.White, FieldShape)
                        .border(if (focused && enabled) 1.5.dp else 1.dp, borderColor, FieldShape)
                        .padding(start = 14.dp, end = if (trailing == null) 14.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                        inner()
                    }
                    trailing?.invoke()
                }
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignInPreview() {
    ExpenseFlowTheme {
        AuthForm(
            register = false,
            name = "",
            email = "",
            password = "",
            passwordVisible = false,
            otp = "",
            busy = false,
            onRegisterChange = {},
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordVisibleChange = {},
            onOtpChange = {},
            onSendOtp = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignUpPreview() {
    ExpenseFlowTheme {
        AuthForm(
            register = true,
            name = "",
            email = "",
            password = "",
            passwordVisible = false,
            otp = "",
            busy = false,
            onRegisterChange = {},
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordVisibleChange = {},
            onOtpChange = {},
            onSendOtp = {},
            onSubmit = {},
        )
    }
}
