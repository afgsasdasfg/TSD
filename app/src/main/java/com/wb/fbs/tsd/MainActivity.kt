package com.wb.fbs.tsd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wb.fbs.tsd.data.model.*
import com.wb.fbs.tsd.ui.screens.*
import com.wb.fbs.tsd.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = PrimaryGreen,
                    secondary = InfoBlue,
                    tertiary = WarningOrange,
                    background = DarkBackground,
                    surface = DarkSurface,
                    onPrimary = OnDarkPrimary,
                    onSecondary = OnDarkPrimary,
                    onBackground = OnDarkPrimary,
                    onSurface = OnDarkPrimary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    AppNavigator(createDemoOrders())
                }
            }
        }
    }
}

private fun createDemoOrders(): List<Order> = listOf(
    Order(
        id = "ORD-001", clientId = "CLT-001", clientName = "ИП Иванов",
        createdAt = System.currentTimeMillis(), status = OrderStatus.NEW,
        items = listOf(
            OrderItem("ITM-001", "ORD-001", "FB-12345", "Футболка белая", "Белый", "L",
                "1234567890123", 5, 0, requiresMarking = false),
            OrderItem("ITM-002", "ORD-001", "FB-12346", "Футболка чёрная", "Чёрный", "M",
                "1234567890124", 3, 0, requiresMarking = true)
        )
    ),
    Order(
        id = "ORD-002", clientId = "CLT-002", clientName = "ООО Ромашка",
        createdAt = System.currentTimeMillis() - 86400000, status = OrderStatus.NEW,
        items = listOf(
            OrderItem("ITM-003", "ORD-002", "DR-98765", "Платье летнее", "Красный", "S",
                "9876543210123", 2, 0, requiresMarking = true)
        )
    )
)

fun validateKizFormat(code: String): Boolean {
    return code.isNotBlank() && code.length >= 10
}

// --- Утилиты: принимают orderList, ВОЗВРАЩАЮТ новый список (или null если не применимо) ---

fun processPickOne(currentOrderList: List<Order>, orderId: String, itemId: String): List<Order>? {
    val idx = currentOrderList.indexOfFirst { it.id == orderId }
    if (idx < 0) return null
    val o = currentOrderList[idx]
    val newItems = o.items.map { i ->
        if (i.id == itemId && !i.isFullyScanned) i.copy(scannedQuantity = i.scannedQuantity + 1) else i
    }
    return currentOrderList.mapIndexed { i, order ->
        if (i == idx) order.copy(items = newItems) else order
    }
}

fun processScanBarcode(currentOrderList: List<Order>, orderId: String): List<Order>? {
    val idx = currentOrderList.indexOfFirst { it.id == orderId }
    if (idx < 0) return null
    val o = currentOrderList[idx]
    var found = false
    val newItems = o.items.map { i ->
        if (!found && !i.isFullyScanned && i.scannedQuantity < i.quantity) {
            found = true
            i.copy(scannedQuantity = i.scannedQuantity + 1)
        } else i
    }
    if (!found) return null
    return currentOrderList.mapIndexed { i, order ->
        if (i == idx) order.copy(items = newItems) else order
    }
}
fun processScanKiz(currentOrderList: List<Order>, orderId: String, itemId: String, kizCode: String): Pair<List<Order>?, String?> {
    if (!validateKizFormat(kizCode)) {
        return null to "Неверный формат: нужен 4B + 29 символов"
    }
    val idx = currentOrderList.indexOfFirst { it.id == orderId }
    if (idx < 0) return null to "Заказ не найден"
    val o = currentOrderList[idx]
    val item = o.items.find { it.id == itemId } ?: return null to "Товар не найден"
    if (item.markedQuantity >= item.quantity) {
        return null to "Все коды уже введены"
    }
    val newItems = o.items.map { i ->
        if (i.id == itemId) i.copy(markedQuantity = i.markedQuantity + 1) else i
    }
    val newList = currentOrderList.mapIndexed { i, order ->
        if (i == idx) order.copy(items = newItems) else order
    }
    return newList to "Код ${kizCode.take(8)}... принят"
}

fun processUndoLastKiz(currentOrderList: List<Order>, orderId: String, itemId: String): Pair<List<Order>?, String?> {
    val idx = currentOrderList.indexOfFirst { it.id == orderId }
    if (idx < 0) return null to "Заказ не найден"
    val o = currentOrderList[idx]
    val item = o.items.find { it.id == itemId } ?: return null to "Товар не найден"
    if (item.markedQuantity <= 0) {
        return null to "Нет кодов для отмены"
    }
    val newItems = o.items.map { i ->
        if (i.id == itemId) i.copy(markedQuantity = maxOf(0, i.markedQuantity - 1)) else i
    }
    val newList = currentOrderList.mapIndexed { i, order ->
        if (i == idx) order.copy(items = newItems) else order
    }
    return newList to "Последний код удалён"
}

fun processReadyOrder(currentOrderList: List<Order>, orderId: String): List<Order>? {
    val idx = currentOrderList.indexOfFirst { it.id == orderId }
    if (idx < 0) return null
    return currentOrderList.mapIndexed { i, order ->
        if (i == idx) order.copy(status = OrderStatus.READY) else order
    }
}

fun processShippedOrder(currentOrderList: List<Order>, orderId: String): List<Order>? {
    val idx = currentOrderList.indexOfFirst { it.id == orderId }
    if (idx < 0) return null
    return currentOrderList.mapIndexed { i, order ->
        if (i == idx) order.copy(status = OrderStatus.SHIPPED) else order
    }
}

@Composable
fun AppNavigator(initialOrders: List<Order>) {
    val navController = rememberNavController()
    var orderList by remember { mutableStateOf(initialOrders.toList()) }

    fun findById(id: String) = orderList.find { it.id == id }

    NavHost(navController, startDestination = "orders") {
        composable("orders") {
            OrdersListScreen(orderList) { o ->
                navController.navigate("picking/${o.id}")
            }
        }
        composable("picking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable
            PickingScreen(o,
                onBackClick = { navController.popBackStack() },
                onItemPicked = {
                    val updated = processPickOne(orderList, oid, it.id)
                    if (updated != null) orderList = updated
                },
                onNextClick = { navController.navigate("checking/$oid") })
        }
        composable("checking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable
            CheckingScreen(o,
                onBackClick = { navController.popBackStack() },
                onBarcodeScanned = {
                    val updated = processScanBarcode(orderList, oid)
                    if (updated != null) orderList = updated
                },
                onNextClick = { navController.navigate("marking/$oid") })
        }
        composable("marking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable
            MarkingScreen(o,
                onBackClick = { navController.popBackStack() },
                onKizScanned = { code, item ->
                    val (newList, msg) = processScanKiz(orderList, oid, item.id, code)
                    if (newList != null) orderList = newList
                    println(if (newList != null) "✓ $msg" else "✗ $msg")
                },
                onCompleteClick = {
                    val updated = processReadyOrder(orderList, oid)
                    if (updated != null) orderList = updated
                    navController.navigate("complete/$oid")
                })
        }
        composable("complete/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable
            ShipmentCompleteScreen(o,
                onNewOrderClick = {
                    val updated = processShippedOrder(orderList, oid)
                    if (updated != null) orderList = updated
                    navController.navigate("orders") { popUpTo(0) }
                },
                onPrintLabelClick = {})
        }
    }
}
