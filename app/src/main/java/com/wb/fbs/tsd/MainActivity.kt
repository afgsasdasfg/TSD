package com.wb.fbs.tsd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * Главное приложение — навигация между экранами пайплайна
 */
class MainActivity : ComponentActivity() {
    
    // Демо-данные для тестирования (будут заменены на API)
    private val demoOrders = listOf(
        Order(
            id = "ORD-001",
            clientId = "CLT-001",
            clientName = "ИП Иванов",
            createdAt = System.currentTimeMillis(),
            status = OrderStatus.NEW,
            items = listOf(
                OrderItem(
                    id = "ITM-001",
                    orderId = "ORD-001",
                    article = "FB-12345",
                    name = "Футболка белая",
                    color = "Белый",
                    size = "L",
                    barcode = "1234567890123",
                    quantity = 5,
                    scannedQuantity = 0,
                    requiresMarking = false
                ),
                OrderItem(
                    id = "ITM-002",
                    orderId = "ORD-001",
                    article = "FB-12346",
                    name = "Футболка чёрная",
                    color = "Чёрный",
                    size = "M",
                    barcode = "1234567890124",
                    quantity = 3,
                    scannedQuantity = 0,
                    requiresMarking = true
                )
            )
        ),
        Order(
            id = "ORD-002",
            clientId = "CLT-002",
            clientName = "ООО Ромашка",
            createdAt = System.currentTimeMillis() - 86400000,
            status = OrderStatus.PICKING,
            items = listOf(
                OrderItem(
                    id = "ITM-003",
                    orderId = "ORD-002",
                    article = "DR-98765",
                    name = "Платье летнее",
                    color = "Красный",
                    size = "S",
                    barcode = "9876543210123",
                    quantity = 2,
                    scannedQuantity = 1,
                    requiresMarking = true
                )
            )
        )
    )
    
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
                    AppNavigation(orders = demoOrders)
                }
            }
        }
    }
}

/**
 * Навигация между экранами пайплайна
 */
@Composable
fun AppNavigation(orders: List<Order>) {
    val navController = rememberNavController()
    var currentOrder by remember { mutableStateOf<Order?>(null) }
    
    NavHost(
        navController = navController,
        startDestination = "orders"
    ) {
        // Экран 1: Список заказов
        composable("orders") {
            OrdersListScreen(
                orders = orders,
                onOrderClick = { order ->
                    currentOrder = order
                    navController.navigate("picking/${order.id}")
                }
            )
        }
        
        // Экран 2: Сборка
        composable(
            route = "picking/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId")
            val order = orders.find { it.id == orderId } ?: return@composable
            
            PickingScreen(
                order = order,
                onBackClick = { navController.popBackStack() },
                onItemPicked = { item ->
                    // TODO: Обновить статус в ViewModel
                },
                onNextClick = {
                    navController.navigate("checking/${order.id}")
                }
            )
        }
        
        // Экран 3: Проверка (сканирование)
        composable(
            route = "checking/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId")
            val order = orders.find { it.id == orderId } ?: return@composable
            
            CheckingScreen(
                order = order,
                onBackClick = { navController.popBackStack() },
                onBarcodeScanned = { barcode ->
                    // TODO: Обработать штрихкод через ViewModel
                },
                onNextClick = {
                    navController.navigate("marking/${order.id}")
                }
            )
        }
        
        // Экран 4: Маркировка (КИЗ)
        composable(
            route = "marking/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId")
            val order = orders.find { it.id == orderId } ?: return@composable
            
            MarkingScreen(
                order = order,
                onBackClick = { navController.popBackStack() },
                onKizScanned = { code, item ->
                    // TODO: Обработать КИЗ через ViewModel
                },
                onCompleteClick = {
                    navController.navigate("complete/${order.id}")
                }
            )
        }
        
        // Экран 5: Готово
        composable(
            route = "complete/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId")
            val order = orders.find { it.id == orderId } ?: return@composable
            
            ShipmentCompleteScreen(
                order = order,
                onNewOrderClick = {
                    currentOrder = null
                    navController.popBackStack(destinationId = R.id.orders, inclusive = false)
                },
                onPrintLabelClick = {
                    // TODO: Печать этикетки
                }
            )
        }
    }
}
