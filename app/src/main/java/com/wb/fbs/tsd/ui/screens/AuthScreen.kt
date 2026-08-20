package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.ui.theme.*

@Composable
fun AuthScreen(onTokenSaved: (String) -> Unit) {
    var token by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "📦 TSD WB FBS",
            fontSize = TextSizeExtraLarge,
            fontWeight = FontWeight.Bold,
            color = OnDarkPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Введите API-токен из личного кабинета WB",
            fontSize = TextSizeMedium,
            color = OnDarkSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Token", color = OnDarkSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGreen,
                unfocusedBorderColor = OnDarkDisabled,
                focusedTextColor = OnDarkPrimary,
                unfocusedTextColor = OnDarkPrimary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { if (token.isNotBlank()) onTokenSaved(token.trim().replace("\n", "").replace("\r", "")) },
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeightLarge),
            enabled = token.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            Text(
                "🔐 Подключиться",
                fontSize = TextSizeLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Токен берётся в WB Partners → Настройки → Доступ к API",
            fontSize = TextSizeSmall,
            color = OnDarkDisabled
        )
    }
}