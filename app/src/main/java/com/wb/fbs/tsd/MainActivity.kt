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
import com.wb.fbs.tsd.data.model.Product
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
            OrderItem("ITM-001", "ORD-001", "FB-12345", "Футболка белая", "Белый", "L", "1234567890123", 5, 0, false),
            OrderItem("ITM-002", "ORD-001", "FB-12346", "Футболка чёрная", "Чёрный", "M", "1234567890124", 3, 0, true)
        )
    ),
    Order(
        id = "ORD-002", clientId = "CLT-002", clientName = "ООО Ромашка",
        createdAt = System.currentTimeMillis() - 86400000, status = OrderStatus.NEW,
        items = listOf(
            OrderItem("ITM-003", "ORD-002", "DR-98765", "Платье летнее", "Красный", "S", "9876543210123", 2, 0, true)
        )
    )
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

@Composable
fun AppNavigator(initialOrders: List<Order>) {
    val navController = rememberNavController()
    var orderList by remember { mutableStateOf(initialOrders.toList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    fun findById(id: String) = orderList.find { it.id == id }

    NavHost(navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onNavigate = { route -> navController.navigate(route) })
        }

        composable("receiving") {
            ReceivingScreen(products = products,
                onBackClick = { navController.popBackStack() },
                onProductAdded = { p -> products += p },
                onDeleteProduct = { pid -> products = products.filter { it.id != pid } },
                onAddKizToProduct = { pid, kc ->
                    products = products.map { p -> if (p.id == pid) p.copy(kizCodes = p.kizCodes + kc) else p }
                })
        }

        composable("orders") {
            OrdersListScreen(orderList,
                onOrderClick = { o -> navController.navigate("picking/${o.id}") },
                onReceivingClick = { navController.navigate("receiving") })
        }

        composable("picking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable
            PickingScreen(o, onBackClick = { navController.popBackStack() },
                onItemPicked = { val u = processPickOne(orderList, oid, it.id); if (u != null) orderList = u },
                onNextClick = { navController.navigate("checking/$oid") })
        }

        composable("checking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("orderId") ?: return@composable
            CheckingScreen(findById(oid) ?: return@composable,
                onBackClick = { navController.popBackStack() },
                onBarcodeScanned = { val u = processScanBarcode(orderList, oid); if (u != null) orderList = u },
                onNextClick = { navController.navigate("marking/$oid") })
        }
        composable("marking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("orderId") ?: return@composable
            MarkingScreen(findById(oid) ?: return@composable,
                onBackClick = { navController.popBackStack() },
                onKizScanned = { code, item ->
                    val (nL, msg) = processScanKiz(orderList, oid, item.id, code)
                    println(if (nL != null) "✓ $msg" else "✗ $msg"); if (nL != null) orderList = nL
                },
                onCompleteClick = {
                    val u = processReadyOrder(orderList, oid); if (u != null) orderList = u
                    navController.navigate("complete/$oid")
                })
        }

        composable("complete/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("orderId") ?: return@composable
            ShipmentCompleteScreen(findById(oid) ?: return@composable,
                onNewOrderClick = { processShippedOrder(orderList, oid)?.let { ol -> orderList = ol }; navController.navigate("orders") { popUpTo(0) } },
                onPrintLabelClick = {})
        }
    }
}
