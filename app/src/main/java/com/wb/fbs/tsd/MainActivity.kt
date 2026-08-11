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
        createdAt = System.currentTimeMillis() - 86400000, status = OrderStatus.PICKING,
        items = listOf(
            OrderItem("ITM-003", "ORD-002", "DR-98765", "Платье летнее", "Красный", "S",
                "9876543210123", 2, 1, requiresMarking = true)
        )
    )
)

@Composable
fun AppNavigator(initialOrders: List<Order>) {
    val navController = rememberNavController()

    // === ОБЩИЙ MUTABLE STATE ===
    var orderList by remember { mutableStateOf(initialOrders) }

    fun findById(id: String) = orderList.find { it.id == id }

    fun pickOne(orderId: String, itemId: String) {
        orderList = orderList.map { o ->
            if (o.id != orderId) return@map o
            val items = o.items.map { i ->
                if (i.id != itemId) return@map i
                if (i.scannedQuantity >= i.quantity) return@map i
                i.copy(scannedQuantity = i.scannedQuantity + 1)
            }
            o.copy(items = items)
        }
    }

    fun scanBarcode(orderId: String) {
        orderList = orderList.map { o ->
            if (o.id != orderId) return@map o
            var changed = false
            val items = o.items.map { i ->
                if (!changed && !i.isFullyScanned && i.scannedQuantity < i.quantity) {
                    changed = true
                    i.copy(scannedQuantity = i.scannedQuantity + 1)
                } else i
            }
            o.copy(items = items)
        }
    }

    fun scanKiz(orderId: String, itemId: String) {
        orderList = orderList.map { o ->
            if (o.id != orderId) return@map o
            val items = o.items.map { i ->
                if (i.id != itemId) return@map i
                if (i.markedQuantity >= i.quantity) return@map i
                i.copy(markedQuantity = i.markedQuantity + 1)
            }
            o.copy(items = items)
        }
    }

    NavHost(navController, startDestination = "orders") {
        composable("orders") {
            OrdersListScreen(orderList) { o ->
                navController.navigate("picking/${o.id}")
            }
        }

        composable("picking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { bse ->
            val oid = bse.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable

            PickingScreen(o,
                onBackClick = { navController.popBackStack() },
                onItemPicked = { pickOne(oid, it.id) },
                onNextClick = { navController.navigate("checking/$oid") })
        }

        composable("checking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { bse ->
            val oid = bse.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable

            CheckingScreen(o,
                onBackClick = { navController.popBackStack() },
                onBarcodeScanned = { _ -> scanBarcode(oid) },
                onNextClick = { navController.navigate("marking/$oid") })
        }

        composable("marking/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { bse ->
            val oid = bse.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable

            MarkingScreen(o,
                onBackClick = { navController.popBackStack() },
                onKizScanned = { _, item -> scanKiz(oid, item.id) },
                onCompleteClick = {
                    orderList = orderList.map { if (it.id == oid) it.copy(status = OrderStatus.READY) else it }
                    navController.navigate("complete/$oid")
                })
        }

        composable("complete/{orderId}", arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { bse ->
            val oid = bse.arguments?.getString("orderId") ?: return@composable
            val o = findById(oid) ?: return@composable

            ShipmentCompleteScreen(o,
                onNewOrderClick = {
                    orderList = orderList.map { if (it.id == oid) it.copy(status = OrderStatus.SHIPPED) else it }
                    navController.navigate("orders") { popUpTo(0) }
                },
                onPrintLabelClick = {})
        }
    }
}
