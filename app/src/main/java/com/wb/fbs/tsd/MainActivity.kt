package com.wb.fbs.tsd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wb.fbs.tsd.ui.screens.*
import com.wb.fbs.tsd.ui.theme.*
import com.wb.fbs.tsd.ui.viewmodel.OrdersViewModel
import com.wb.fbs.tsd.ui.viewmodel.OrdersViewModelFactory

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
                    navController.navigate("order_detail/${order.id}")
                },
                onScanClick = { navController.navigate("scan") },
                onCreateSupplyClick = {
                    // TODO: Диалог создания поставки
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
                    navController.popBackStack()
                },
                requiresSgtin = false,
                article = null,
                size = null
            )
        }
    }
}