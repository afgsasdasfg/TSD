package com.wb.fbs.tsd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wb.fbs.tsd.data.model.*
import com.wb.fbs.tsd.data.network.WBApiClient
import com.wb.fbs.tsd.ui.screens.*
import com.wb.fbs.tsd.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = PrimaryGreen, secondary = InfoBlue, tertiary = WarningOrange,
                    background = DarkBackground, surface = DarkSurface,
                    onPrimary = OnDarkPrimary, onSecondary = OnDarkPrimary,
                    onBackground = OnDarkPrimary, onSurface = OnDarkPrimary
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    AppNavigator(createDemoOrders())
                }
            }
        }
    }
}

private fun createDemoOrders(): List<Order> = listOf(
    Order(id = "ORD-001", clientId = "CLT-001", clientName = "ИП Иванов",
        createdAt = System.currentTimeMillis(), status = OrderStatus.NEW,
        items = listOf(
            OrderItem("ITM-001", "ORD-001", "FB-12345", "Футболка белая", "Белый", "L", "1234567890123", 5, 0, false),
            OrderItem("ITM-002", "ORD-001", "FB-12346", "Футболка чёрная", "Чёрный", "M", "1234567890124", 3, 0, true)
        ), wbOrderId = "WB-12345"),
    Order(id = "ORD-002", clientId = "CLT-002", clientName = "ООО Ромашка",
        createdAt = System.currentTimeMillis() - 86400000, status = OrderStatus.NEW,
        items = listOf(OrderItem("ITM-003", "ORD-002", "DR-98765", "Платье летнее", "Красный", "S", "9876543210123", 2, 0, true)),
        wbOrderId = "WB-67890")
)

fun validateKizFormat(code: String): Boolean = code.isNotBlank() && code.length >= 10

fun processPickOne(list: List<Order>, oid: String, itemId: String): List<Order>? {
    val idx = list.indexOfFirst { it.id == oid } ?: return null
    val newItems = list[idx].items.map { i ->
        if (i.id == itemId && !i.isFullyScanned) i.copy(scannedQuantity = i.scannedQuantity + 1) else i
    }
    return list.mapIndexed { i, ord -> if (i == idx) ord.copy(items = newItems) else ord }
}

fun processScanBarcode(list: List<Order>, oid: String): List<Order>? {
    val idx = list.indexOfFirst { it.id == oid } ?: return null
    var found = false
    val newItems = list[idx].items.map { i ->
        if (!found && !i.isFullyScanned && i.scannedQuantity < i.quantity) {
            found = true; i.copy(scannedQuantity = i.scannedQuantity + 1)
        } else i
    }
    return if (found) list.mapIndexed { i, ord -> if (i == idx) ord.copy(items = newItems) else ord } else null
}
fun processScanKiz(list: List<Order>, oid: String, itemId: String, kizCode: String): Pair<List<Order>?, String?> {
    if (!validateKizFormat(kizCode)) return null to "Неверный формат"
    val idx = list.indexOfFirst { it.id == oid }
    if (idx < 0) return null to "Заказ не найден"
    val item = list[idx].items.find { it.id == itemId }
    if (item == null) return null to "Товар не найден"
    if (item.markedQuantity >= item.quantity) return null to "Все коды введены"
    val newItems = list[idx].items.map { i -> if (i.id == itemId) i.copy(markedQuantity = i.markedQuantity + 1) else i }
    val newList = list.mapIndexed { i, ord -> if (i == idx) ord.copy(items = newItems) else ord }
    return newList to "КИЗ ${kizCode.take(8)}... принят"
}

fun processUndoLastKiz(list: List<Order>, oid: String, itemId: String): Pair<List<Order>?, String?> {
    val idx = list.indexOfFirst { it.id == oid }
    if (idx < 0) return null to "Заказ не найден"
    val item = list[idx].items.find { it.id == itemId }
    if (item == null) return null to "Товар не найден"
    if (item.markedQuantity <= 0) return null to "Нет кодов"
    val newItems = list[idx].items.map { i -> if (i.id == itemId) i.copy(markedQuantity = maxOf(0, i.markedQuantity - 1)) else i }
    val newList = list.mapIndexed { i, ord -> if (i == idx) ord.copy(items = newItems) else ord }
    return newList to "Код удалён"
}

fun processReadyOrder(list: List<Order>, oid: String): List<Order>? {
    val idx = list.indexOfFirst { it.id == oid }
    if (idx < 0) return null
    return list.mapIndexed { i, ord -> if (i == idx) ord.copy(status = OrderStatus.READY) else ord }
}

fun processShippedOrder(list: List<Order>, oid: String): List<Order>? {
    val idx = list.indexOfFirst { it.id == oid }
    if (idx < 0) return null
    return list.mapIndexed { i, ord -> if (i == idx) ord.copy(status = OrderStatus.SHIPPED) else ord }
}

private fun convertWBOrder(wbDto: WBOrderDto, existingOrders: List<Order>): Order {
    val existingOrder = existingOrders.find { it.wbOrderId == wbDto.orderNumber }
    val items = wbDto.items?.map { item ->
        val localItem = existingOrder?.items?.find { it.article == item.nmSku.toString() && it.size == (item.size ?: "") }
        OrderItem(
            id = localItem?.id ?: "wb-${item.nmSku}-${wbDto.orderNumber}",
            orderId = existingOrder?.id ?: "", article = item.nmSku.toString(), name = item.name,
            color = item.color ?: "Не указан", size = item.size ?: "Универсальный",
            barcode = item.barcode ?: "", quantity = item.quantity,
            scannedQuantity = localItem?.scannedQuantity ?: 0,
            requiresMarking = item.isKiz == true || !wbDto.dtmCode.isNullOrBlank(),
            markedQuantity = localItem?.markedQuantity ?: 0, isGrouped = localItem?.isGrouped ?: false
        )
    } ?: emptyList()
    val createdAt = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(wbDto.createdAt ?: "")?.time ?: System.currentTimeMillis()
    } catch (_: Exception) { System.currentTimeMillis() }
    return Order(
        id = existingOrder?.id ?: "wb-${wbDto.orderNumber}", clientId = wbDto.chc?.toString() ?: "UNKNOWN",
        clientName = wbDto.customerName ?: wbDto.purchaserName ?: "Клиент #${wbDto.chc ?: "?"}",
        createdAt = createdAt, status = mapWbStatusToModel(wbDto.status), items = items, wbOrderId = wbDto.orderNumber
    )
}

private fun mapWbStatusToModel(s: String): OrderStatus = when (s.lowercase()) {
    "new", "undefined" -> OrderStatus.NEW
    "accepted", "collecting" -> OrderStatus.ACCEPTED
    "in_work", "picking" -> OrderStatus.PICKING
    "checking", "delivering" -> OrderStatus.CHECKING
    "marking" -> OrderStatus.MARKING
    "ready", "success" -> OrderStatus.READY
    "shipped", "delivered" -> OrderStatus.SHIPPED
    else -> OrderStatus.NEW
}

@Composable
fun AppNavigator(initialOrders: List<Order>) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var orderList by remember { mutableStateOf(initialOrders.toList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var authToken by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("wb_prefs", android.content.Context.MODE_PRIVATE) }
    val hasStoredCredentials = remember(prefs) { prefs.getString("api_token", null) != null }

    fun findById(id: String) = orderList.find { it.id == id }

    fun loadOrdersFromAPI() {
        scope.launch {
            isLoading = true
            if (authToken.isNullOrEmpty()) { showAuthDialog = true; isLoading = false; return@launch }
            try {
                val result = WBApiClient.loadOrders(warehouseId = 1)
                result.onSuccess { wbOrders ->
                    val newOrders = wbOrders.map { dto -> convertWBOrder(dto, orderList) }
                    val mergedOrders = mutableListOf<Order>()
                    val updatedIds = mutableSetOf<String>()
                    for (lo in orderList) {
                        val wbMatch = newOrders.find { it.wbOrderId == lo.wbOrderId }
                        if (wbMatch != null) { mergedOrders.add(wbMatch.copy(id = lo.id)); updatedIds.add(lo.id) }
                        else mergedOrders.add(lo)
                    }
                    for (no in newOrders) if (!updatedIds.contains(no.id)) mergedOrders.add(no)
                    orderList = mergedOrders
                    isLoading = false
                }.onFailure { _ -> isLoading = false }
            } catch (_: Exception) { isLoading = false }
        }
    }

    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(onNavigate = { navController.navigate(it) }) }
        composable("receiving") {
            ReceivingScreen(products = products, onBackClick = { navController.popBackStack() },
                onProductAdded = { p -> products += p }, onDeleteProduct = { pid -> products = products.filter { it.id != pid } },
                onAddKizToProduct = { pid, kc -> products = products.map { p -> if (p.id == pid) p.copy(kizCodes = p.kizCodes + kc) else p } })
        }
        composable("orders") {
            OrdersListScreen(orderList,
                onLoadOrdersClick = { loadOrdersFromAPI() },
                onOrderClick = { navController.navigate("picking/${it.id}") },
                onReceivingClick = { navController.navigate("receiving") })
        }
        composable("picking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { bse ->
            val oid = bse.arguments?.getString("orderId") ?: return@composable
            PickingScreen(findById(oid)!!, onBackClick = { navController.popBackStack() },
                onItemPicked = { val u = processPickOne(orderList, oid, it.id); if (u != null) orderList = u },
                onNextClick = { navController.navigate("checking/$oid") })
        }
        composable("checking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { bse ->
            CheckingScreen(findById(bse.arguments?.getString("orderId")!!)!!,
                onBackClick = { navController.popBackStack() },
                onBarcodeScanned = { val u = processScanBarcode(orderList, bse.arguments!!.getString("orderId")!!); if (u != null) orderList = u },
                onNextClick = { navController.navigate("marking/${bse.arguments!!.getString("orderId")!!}") })
        }
        composable("marking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { bse ->
            val oid = bse.arguments?.getString("orderId") ?: return@composable
            MarkingScreen(findById(oid)!!, onBackClick = { navController.popBackStack() },
                onKizScanned = { code, item ->
                    val (nL, msg) = processScanKiz(orderList, oid, item.id, code)
                    println(if (nL != null) "✓ $msg" else "✗ $msg"); if (nL != null) orderList = nL
                }, onCompleteClick = { val u = processReadyOrder(orderList, oid); if (u != null) orderList = u; navController.navigate("complete/$oid") })
        }
        composable("complete/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { bse ->
            ShipmentCompleteScreen(findById(bse.arguments?.getString("orderId")!!)!!,
                onNewOrderClick = { processShippedOrder(orderList, bse.arguments!!.getString("orderId")!!)?.let { ol -> orderList = ol }; navController.navigate("orders") { popUpTo(0) } },
                onPrintLabelClick = {})
        }
    }

    // Показываем AuthDialog при первом запуске
    if (!hasStoredCredentials) {
        AuthDialog(
            tokenReceived = { token: String, clientId: Int ->
                try {
                    val trimmedToken = token.trim()

                    authToken = trimmedToken
                    prefs.edit().putString("api_token", trimmedToken).putInt("client_id", clientId).apply()
                    WBApiClient.init(context, trimmedToken, clientId)

                    showAuthDialog = false
                    loadOrdersFromAPI()
                } catch (e: Exception) {
                    // Лямбда не выполнится полностью → диалог не исчезнет, пользователь увидит проблему
                    println("Auth failed: ${e.message}")
                }
            },
            onCancel = {
                showAuthDialog = false
                // Можно добавить логику выхода из приложения или показа demo-режима
            }
        )
    }


    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OnDarkPrimary)
        }
    }
}

@Composable
fun AuthDialog(tokenReceived: (String, Int) -> Unit, onCancel: () -> Unit) {
    var tokenInput by remember { mutableStateOf("") }
    var clientIdText by remember { mutableStateOf("0") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("🔐 Доступ к WB API") },
        text = {
            Column {
                Text("Вставьте Bearer токен:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR...") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Client ID (магазин в WB FBS):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientIdText,
                    onValueChange = { newText -> clientIdText = newText.filter { it.isDigit() }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Например: 123456") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Сообщение об ошибке
                errorText?.let { err ->
                    Text(err, color = ErrorRed, fontSize = TextSizeSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    "Где взять Client ID:",
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "WB FBS → Настройки → API доступ.\nОдин магазин = один Client ID.\nДанные сохраняются на устройстве.",
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = tokenInput.trim()
                    val cid = clientIdText.toIntOrNull()

                    errorText = when {
                        trimmed.isBlank() -> "Токен пустой"
                        cid == null -> "Client ID — только цифры"
                        cid <= 0 -> "Client ID должен быть > 0"
                        else -> {
                            // Всё ок — передаём данные
                            tokenReceived(trimmed, cid!!)
                            null  // чистим ошибку если лямбда сработала
                        }
                    }
                },
                enabled = tokenInput.isNotBlank() && clientIdText.toIntOrNull() != null
            ) { Text("Подключить") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    )
}

