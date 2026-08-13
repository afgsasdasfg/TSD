package com.wb.fbs.tsd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wb.fbs.tsd.ui.screens.*
import com.wb.fbs.tsd.ui.theme.*
import com.wb.fbs.tsd.ui.viewmodel.OrdersViewModel
import com.wb.fbs.tsd.ui.viewmodel.OrdersViewModelFactory
import com.wb.fbs.tsd.utils.KizParser

class MainActivity : ComponentActivity() {

    private val app: TsdApplication by lazy { TsdApplication.instance }

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
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    TsdApp()
                }
            }
        }
    }
}

@Composable
fun TsdApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember { TsdApplication.instance }
    val repository = remember { app.repository }

    val prefs = remember { context.getSharedPreferences("wb_prefs", android.content.Context.MODE_PRIVATE) }
    val token = remember { prefs.getString("api_token", null) }

    // API уже восстановлен в TsdApplication.onCreate()
    // Но если токен появился впервые — инициализируем
    LaunchedEffect(token) {
        token?.let {
            if (!repository.hasApi) {
                app.initWbApi(it)
            }
        }
    }

    val viewModel: OrdersViewModel = viewModel(
        factory = OrdersViewModelFactory(repository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val orders by viewModel.newOrders.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (token == null) "auth" else "home"
    ) {
        composable("auth") {
            AuthScreen(
                onTokenSaved = { newToken ->
                    prefs.edit().putString("api_token", newToken).apply()
                    app.initWbApi(newToken)
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(onNavigate = { route -> navController.navigate(route) })
        }

        composable("orders") {
            OrderListScreen(
                orders = orders,
                uiState = uiState,
                onSyncClick = { viewModel.loadOrders() },
                onOrderClick = { order ->
                    Toast.makeText(context, "Заказ ${order.article}", Toast.LENGTH_SHORT).show()
                },
                onScanClick = { navController.navigate("scan") },
                onScanKizClick = { navController.navigate("scan_kiz") },
                onCreateSupplyClick = { /* TODO */ },
                onSgtinEntered = { orderId, sgtin ->
                    viewModel.scanSgtin(orderId, sgtin)
                }
            )
        }

        composable("receiving") {
            ReceivingPlaceholderScreen(onBackClick = { navController.popBackStack() })
        }

        composable("scan") {
            ScanScreen(
                onBackClick = { navController.popBackStack() },
                onBarcodeScanned = { barcode ->
                    viewModel.scanBarcode(barcode)
                    navController.popBackStack()
                },
                onSgtinScanned = { sgtin ->
                    // Этот экран для штрихкодов, КИЗ отдельно
                    navController.popBackStack()
                },
                requiresSgtin = false,
                article = null,
                size = null
            )
        }

        composable("scan_kiz") {
            KizScannerScreen(
                onBackClick = { navController.popBackStack() },
                onKizScanned = { kizString ->
                    val gtin = KizParser.extractGtin(kizString)
                    val serial = KizParser.extractSerial(kizString)
                    val sgtin = KizParser.toSgtin(kizString)

                    android.util.Log.d("KIZ_PARSE", "Raw: $kizString")
                    android.util.Log.d("KIZ_PARSE", "GTIN: $gtin")
                    android.util.Log.d("KIZ_PARSE", "Serial: $serial")
                    android.util.Log.d("KIZ_PARSE", "SGTIN: $sgtin")

                    viewModel.scanKiz(kizString)
                    navController.popBackStack()
                }
            )
        }
        composable("picking") {
            PickingScreen(
                orders = orders,
                onBackClick = { navController.popBackStack() },
                onOrderClick = { order ->
                    viewModel.markOrderScanned(order.id)
                },
                onScanKizForOrder = { orderId ->
                    navController.navigate("scan_kiz/$orderId")
                },
                onScanClick = { navController.navigate("scan") }
            )
        }
        composable("picking") {
            PickingScreen(
                orders = orders,
                onBackClick = { navController.popBackStack() },
                onOrderClick = { order ->
                    viewModel.markOrderScanned(order.id)
                },
                onScanKizForOrder = { orderId ->  // ← ЕСТЬ?
                    navController.navigate("scan_kiz/$orderId")
                },
                onScanClick = { navController.navigate("scan") }
            )
        }

        // Новый роут — сканирование КИЗ для конкретного заказа
        composable(
            "scan_kiz/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong("orderId") ?: 0
            KizScannerScreen(
                onBackClick = { navController.popBackStack() },
                onKizScanned = { kizString ->
                    viewModel.scanKizForOrder(orderId, kizString)
                    viewModel.markOrderScanned(orderId)  // Автоматически ставим галку
                    navController.popBackStack()
                }
            )
        }
    }
}