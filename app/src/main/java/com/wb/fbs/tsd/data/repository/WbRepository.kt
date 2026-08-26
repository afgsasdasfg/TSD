package com.wb.fbs.tsd.data.repository

import retrofit2.Response
import kotlin.Result
import kotlinx.coroutines.delay
import com.wb.fbs.tsd.data.db.*
import com.wb.fbs.tsd.data.network.*
import com.wb.fbs.tsd.utils.KizParser
import com.wb.fbs.tsd.utils.WbStickerParser
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.first
class WbRepository(
    private val orderDao: OrderDao,
    private val supplyDao: SupplyDao,
    private val scanLogDao: ScanLogDao
) {
    private var apiService: WbApiService? = null
    private var contentApiService: WbContentApiService? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    fun setApiService(service: WbApiService) {
        apiService = service
    }

    fun setContentApiService(service: WbContentApiService) {
        contentApiService = service
    }

    suspend fun getDbStats(): String {
        val total = orderDao.getTotalOrderCount()
        val withStickers = orderDao.getStickerCount()
        val statuses = orderDao.getOrderStatusCounts().joinToString(", ") { "${it.status}=${it.cnt}" }
        // Показываем первые 10 стикер-баркодов для отладки
        val stickers = orderDao.getOrdersForStickerSearch().filter { !it.stickerBarcode.isNullOrEmpty() }.take(10)
        val stickerSamples = stickers.joinToString(", ") { "${it.id}=${it.stickerBarcode}" }
        return "Всего: $total | Со стикерами: $withStickers | Статусы: $statuses | Стикеры: $stickerSamples"
    }

    private fun requireApi(): WbApiService {
        return apiService ?: throw IllegalStateException(
            "WB API не инициализирован. Войдите через настройки."
        )
    }

    private fun requireContentApi(): WbContentApiService {
        return contentApiService ?: throw IllegalStateException(
            "WB Content API не инициализирован."
        )
    }

    val hasApi: Boolean
        get() = apiService != null

    /**
     * Очистка локальных заказов при смене API-токена/кабинета WB.
     * Баркоды товаров повторяются между кабинетами — старые заказы
     * другого кабинета не должны путаться с новыми.
     */
    suspend fun clearOrdersForNewToken() {
        try {
            orderDao.clearAllOrders()
        } catch (e: Exception) {
            // Не критчно — fallbackToDestructiveMigration всё равно очистит при апгрейде
        }
    }

    // ==================== OFFLINE: Заказы ====================

    fun getNewOrders(): Flow<List<OrderEntity>> = orderDao.getNewOrders()

    fun getOrdersBySupply(supplyId: String): Flow<List<OrderEntity>> =
        orderDao.getOrdersBySupply(supplyId)

    suspend fun getOrderById(orderId: Long): OrderEntity? = orderDao.getOrderById(orderId)

    suspend fun syncNewOrders(): kotlin.Result<Int> {
        // Тянем новые заказы через /api/v3/orders/new
        // Это отдаёт только status=new — то что нужно для вкладки «Заказы FBS».
        // Заказы, переведённые в confirm на портале, уже в БД — syncOrderStatuses()
        // обновит их статус. Не тянем all=1000 заказов — это пустая трата лимитов WB.
        val newCount = safeApiCall { requireApi().getNewOrders() }.map { response ->
            val serverOrders = response.orders?.map { it.toEntity() } ?: emptyList()
            orderDao.insertOrders(serverOrders)
            serverOrders.size
        }.getOrElse { 0 }

        // Пауза перед обновлением статусов
        delay(300)

        // Обновляем статусы уже существующих в БД заказов (new → confirm → complete…)
        // syncOrderStatuses() внутри вызывает syncMissingStickers()
        try { syncOrderStatuses() } catch (_: Throwable) {}

        return kotlin.Result.success(newCount)
    }

    /**
     * Фоновая предзагрузка confirm заказов и их стикеров.
     * Берём 2 страницы по 1000 заказов через /api/v3/orders,
     * обновляем статусы, качаем стикеры только для confirm.
     * Вызывается с задержкой после основного syncNewOrders(),
     * чтобы приложение не висло при старте.
     */
    suspend fun prefetchConfirmStickers() {
        if (!hasApi) return
        try {
            android.util.Log.i("WbRepo", "prefetchConfirmStickers: starting...")
            val allFreshIds = mutableListOf<Long>()
            for (page in 0..1) {
                val nextOffset = (page * 1000).toLong()
                val resp = safeApiCall { requireApi().getOrders(limit = 1000, next = nextOffset) }.getOrNull()
                val freshOrders = resp?.orders?.map { it.toEntity() } ?: emptyList()
                if (freshOrders.isEmpty()) break
                orderDao.insertOrders(freshOrders)
                allFreshIds.addAll(freshOrders.map { it.id })
                val freshIds = freshOrders.map { it.id }
                syncStatuses(freshIds)
                delay(200)
            }
            // Качаем стикеры для confirm И complete из свежескачанных
            // (WB API отдаёт стикеры для обоих статусов)
            val allOrders = orderDao.getOrdersForStickerSearch()
            val needStickers = allOrders.filter {
                (it.status == "confirm" || it.status == "complete") && it.stickerBarcode == null && allFreshIds.contains(it.id)
            }
            if (needStickers.isNotEmpty()) {
                android.util.Log.i("WbRepo", "prefetchConfirmStickers: downloading stickers for ${needStickers.size} orders (confirm+complete)")
                // Качаем батчами по 100
                val ids = needStickers.map { it.id }
                for (i in ids.indices step 100) {
                    val batch = ids.subList(i, minOf(i + 100, ids.size))
                    downloadStickers(batch)
                    delay(150)
                }
            }
            android.util.Log.i("WbRepo", "prefetchConfirmStickers: done")
        } catch (e: Throwable) {
            android.util.Log.w("WbRepo", "prefetchConfirmStickers failed: ${e.message}")
        }
    }

    suspend fun createSupply(name: String): kotlin.Result<String> = safeApiCall {
        requireApi().createSupply(WbCreateSupplyRequest(name))
    }.map { response ->
        val supplyId = response.id
        supplyDao.insertSupply(
            SupplyEntity(
                id = supplyId,
                name = name,
                createdAt = System.currentTimeMillis(),
                closedAt = null,
                scanDt = null,
                cargoType = 0,
                crossBorderType = 0,
                destinationOfficeId = null,
                isDone = false,
                isB2b = false,
                orderCount = 0,
                isSynced = true,
                qrCodeSvg = null,
                status = "active"
            )
        )
        supplyId
    }

    suspend fun addOrdersToSupply(supplyId: String, orderIds: List<Long>): kotlin.Result<Unit> = safeApiCall {
        requireApi().addOrdersToSupply(supplyId, WbAddOrdersToSupplyRequest(orderIds))
    }.map {
        orderDao.addOrdersToSupply(orderIds, supplyId)
    }

    suspend fun deliverSupply(supplyId: String): kotlin.Result<Unit> = safeApiCall {
        requireApi().deliverSupply(supplyId)
    }.map {
        supplyDao.markSupplyDelivered(supplyId)
    }

    suspend fun scanSgtin(orderId: Long, sgtin: String): kotlin.Result<Unit> = safeApiCall {
        requireApi().setSgtin(orderId, WbSgtinRequest(sgtin))
    }.map {
        orderDao.setOrderSgtin(orderId, sgtin)
        orderDao.markOrderSynced(orderId)
    }

    suspend fun downloadStickers(orderIds: List<Long>): kotlin.Result<List<WbStickerDto>> = safeApiCall {
        // Защита: WB отдаёт base64 SVG (~10-50KB/стикер). Если передать
        // 500+ ID — ответ будет 25MB+, что роняет ТСД (OutOfMemoryError).
        val batch = if (orderIds.size > 200) orderIds.take(200) else orderIds
        requireApi().getStickers(
            type = "svg",
            width = 58,
            height = 40,
            request = WbStickerRequest(batch)
        )
    }.map { response ->
        val stickers = response.stickers ?: emptyList()
        android.util.Log.i("WbRepo", "downloadStickers: requested=${orderIds.size}, got=${stickers.size}")
        if (stickers.isEmpty()) {
            android.util.Log.w("WbRepo", "downloadStickers: empty response! requested ids=${orderIds}")
        }
        stickers.forEach { sticker ->
            android.util.Log.i("WbRepo", "downloadStickers: orderId=${sticker.orderId}, barcode=${sticker.barcode}, partA=${sticker.partA}")
            orderDao.updateStickerData(
                orderId = sticker.orderId,
                barcode = sticker.barcode,
                partA = sticker.partA,
                partB = sticker.partB
            )
        }
        stickers
    }

    /**
     * Догружает стикеры для заказов "на сборке"/"в доставке", у которых ещё
     * не сохранён stickerBarcode. Вызывать после syncNewOrders()/syncOrderStatuses(),
     * иначе scanWbSticker() не сможет найти заказ по отсканированному стикеру.
     */
    suspend fun syncMissingStickers(): kotlin.Result<Int> {
        val pending = orderDao.getOrdersNeedingStickers()
        android.util.Log.i("WbRepo", "syncMissingStickers: pending=${pending.size}, ids=${pending.map { "${it.id}=${it.status}" }}")
        if (pending.isEmpty()) return kotlin.Result.success(0)
        val ids = pending.map { it.id }
        var total = 0
        // Бьём на батчи по 100 — WB отдаёт base64 SVG для каждого стикера
        // (~10-50KB на стикер). 100 стикеров за раз — ~1-5MB, терпимо для TSD.
        val batchSize = 100
        for (i in ids.indices step batchSize) {
            val batch = ids.subList(i, minOf(i + batchSize, ids.size))
            android.util.Log.i("WbRepo", "syncMissingStickers: batch=${batch}")
            downloadStickers(batch).onSuccess { stickers ->
                total += stickers.size
                android.util.Log.i("WbRepo", "syncMissingStickers: got ${stickers.size} stickers")
            }.onFailure { e ->
                android.util.Log.e("WbRepo", "syncMissingStickers: batch failed: ${e.message}")
            }
            // Пауза между батчами стикеров
            if (i + batchSize < ids.size) delay(200)
        }
        android.util.Log.i("WbRepo", "syncMissingStickers: total=$total")
        return kotlin.Result.success(total)
    }

    suspend fun syncStatuses(orderIds: List<Long>): kotlin.Result<Unit> = safeApiCall {
        requireApi().getOrdersStatus(WbStatusRequest(orderIds))
    }.map { response ->
        response.orders?.forEach { statusDto ->
            val order = orderDao.getOrderById(statusDto.id) ?: return@forEach
            orderDao.updateOrder(order.copy(status = statusDto.supplierStatus))
        }
    }

    // ==================== OFFLINE: Сканирование ====================
    suspend fun scanBarcode(barcode: String): ScanResult {
        val allOrders: List<OrderEntity> = orderDao.getNewOrders().first()

        // 1. Точное совпадение по barcode
        val exactMatch = allOrders.find { it.barcode == barcode }
        // 2. Частичное совпадение (если штрихкод сканером считался не полностью)
            ?: allOrders.find {
                it.barcode != null && (
                        it.barcode.contains(barcode) ||
                                barcode.contains(it.barcode)
                        )
            }

        return if (exactMatch != null) {
            orderDao.markOrderScanned(exactMatch.id, System.currentTimeMillis())
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = exactMatch.id,
                    supplyId = null,
                    scanType = "barcode",
                    scannedValue = barcode,
                    success = true,
                    errorMessage = null
                )
            )
            ScanResult.Success(exactMatch)
        } else {
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = null,
                    supplyId = null,
                    scanType = "barcode",
                    scannedValue = barcode,
                    success = false,
                    errorMessage = "Заказ со штрихкодом $barcode не найден"
                )
            )
            ScanResult.NotFound
        }
    }
    /**
     * Сканирование КИЗ — ищем заказ по GTIN/skuss
     */
    suspend fun scanKiz(kizString: String): KizScanResult {
        val gtin = KizParser.extractGtin(kizString) ?: return KizScanResult.InvalidFormat

        // Получаем список заказов из Flow
        val allOrders: List<OrderEntity> = orderDao.getNewOrders().first()

        val match: OrderEntity? = allOrders.find { order: OrderEntity ->
            val barcode = order.barcode
            barcode != null && (
                    barcode.contains(gtin) ||
                            barcode.contains(gtin.trimStart('0')) ||
                            gtin.contains(barcode)
                    )
        }

        return if (match != null) {
            val sgtin = KizParser.toSgtin(kizString) ?: kizString
            orderDao.setOrderSgtin(match.id, sgtin)
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = match.id,
                    supplyId = null,
                    scanType = "sgtin",
                    scannedValue = kizString,
                    success = true,
                    errorMessage = null
                )
            )
            KizScanResult.Success(match, sgtin)
        } else {
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = null,
                    supplyId = null,
                    scanType = "sgtin",
                    scannedValue = kizString,
                    success = false,
                    errorMessage = "Заказ с GTIN $gtin не найден"
                )
            )
            KizScanResult.OrderNotFound(gtin)
        }
    }

    // ==================== УТИЛИТЫ ====================

    private fun WbOrderDto.toEntity(): OrderEntity {
        // Размер: brandSize (M/L/XL), fallback techSize (46/48), fallback chrtId
        // Размер из Orders API. brandSize (M/L/XL) предпочтительнее, 
        // fallback на techSize (46/48). chrtId НЕ использовать как размер —
        // это внутренний ID, не размер. Если Orders API не отдал размер,
        // он подтянется позже через syncProductSizes() из Content API.
        val sizeStr = size?.brandSize
            ?: size?.techSize

        return OrderEntity(
            id = id,
            orderUid = orderUid ?: "",
            article = article ?: "",
            nmId = nmId ?: 0,
            chrtId = chrtId ?: 0,
            name = "", // Название подтягивается из карточки товара (nmId)
            color = colorCode,
            size = sizeStr,
            barcode = skus?.firstOrNull(),
            price = price ?: 0,
            finalPrice = finalPrice ?: 0,
            warehouseId = warehouseId ?: 0,
            officeId = officeId,
            cargoType = cargoType ?: 0,
            deliveryType = deliveryType ?: "fbs",
            requiredMeta = requiredMeta?.joinToString(",") ?: "",
            optionalMeta = optionalMeta?.joinToString(",") ?: "",
            comment = comment,
            createdAt = parseDate(createdAt),
            supplyId = supplyId,
            status = supplierStatus ?: "new", // /api/v3/orders отдаёт supplierStatus
            isMarked = (requiredMeta?.joinToString(",") ?: "").contains("sgtin") || (optionalMeta?.joinToString(",") ?: "").contains("sgtin"),
            sgtin = null,
            isSynced = true,
            scannedAt = null,
            stickerPrinted = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun parseDate(dateStr: String?): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    /**
     * Подтягивает реальные размеры товаров через WB Content API.
     *
     * Orders API иногда не отдаёт brandSize/techSize (пустой размер),
     * а на ТСД размер — главный ориентир при сборке (не баркод).
     * Content API отдаёт карточку по nmId, в ней — список sizes
     * с chrtId и techSize. Маппим chrtId → techSize и обновляем
     * заказы в БД, у которых размер пустой.
     *
     * Пагинация: Content API отдаёт максимум 100 карточек за запрос,
     * используем cursor (updatedAt + nmID) для следующих страниц.
     */
    suspend fun syncProductSizes(): kotlin.Result<Int> {
        try {
            val nmIds = orderDao.getNmIdsNeedingSizes()
            if (nmIds.isEmpty()) return kotlin.Result.success(0)

            val contentApi = try { requireContentApi() } catch (e: Exception) {
                return kotlin.Result.failure(e)
            }

            // Карточки отдаются пагинацией по 100, фильтруем по nmId
            // из наших заказов. Content API не умеет фильтровать по списку
            // nmId, поэтому перебираем страницы пока не найдём все.
            var cursor: WbCardsResponseCursor? = null
            val nmIdSet = nmIds.toMutableSet()
            var updatedCount = 0
            var pages = 0
            val maxPages = 20 // 100 * 20 = 2000 карточек — лимит для защиты от бесконечного цикла

            while (nmIdSet.isNotEmpty() && pages < maxPages) {
                pages++

                val request = WbCardsListRequest(
                    settings = WbCardsSettings(
                        cursor = WbCardsCursor(
                            limit = 100,
                            updatedAt = cursor?.updatedAt,
                            nmID = cursor?.nmID
                        )
                    )
                )

                val response = contentApi.getCardsList(request)
                if (!response.isSuccessful) {
                    return kotlin.Result.failure(
                        ApiException.HttpError(response.code(), response.errorBody()?.string() ?: "Content API error")
                    )
                }

                val cards = response.body()?.cards ?: emptyList()
                if (cards.isEmpty()) break

                for (card in cards) {
                    if (card.nmID !in nmIdSet) continue
                    // Нашли нужную карточку — маппим все её размеры (chrtId → techSize)
                    for (sizeDto in card.sizes ?: emptyList()) {
                        val techSize = sizeDto.techSize
                        if (!techSize.isNullOrBlank()) {
                            orderDao.updateSizeByChrtId(sizeDto.chrtID, techSize)
                            updatedCount++
                        }
                    }
                    nmIdSet.remove(card.nmID)
                }

                cursor = response.body()?.cursor
                if (cursor?.updatedAt == null && cursor?.nmID == null) break

                // Пауза между страницами Content API
                if (nmIdSet.isNotEmpty()) delay(700)
            }

            return kotlin.Result.success(updatedCount)
        } catch (e: Throwable) {
            return kotlin.Result.failure(e)
        }
    }

    suspend fun getSupplyBarcode(supplyId: String): kotlin.Result<WbBarcodeResponse> = safeApiCall {
        requireApi().getSupplyBarcode(supplyId)
    }

    suspend fun getUnsyncedOrders(): List<OrderEntity> = orderDao.getUnsyncedOrders()

    /** Активные заказы с привязанным КИЗ (sgtin) — для авто-вывода/возврата. */
    suspend fun getActiveOrdersWithSgtin(): List<OrderEntity> =
        orderDao.getActiveOrdersWithSgtin()
    suspend fun markOrderScanned(orderId: Long) {
        orderDao.markOrderScanned(orderId, System.currentTimeMillis())
    }
    suspend fun unmarkOrderScanned(orderId: Long) {
        orderDao.markOrderScanned(orderId, null)
    }
    suspend fun markOrderPacked(orderId: Long) {
        orderDao.markOrderPacked(orderId, System.currentTimeMillis())
    }
    suspend fun unmarkOrderPacked(orderId: Long) {
        orderDao.markOrderPacked(orderId, null)
    }

    suspend fun syncOrderStatuses() {
        try {
            val allIds = orderDao.getActiveOrderIds().first()
            if (allIds.isEmpty()) return

            // WB API не принимает больше ~100 ID за запрос — бьём на батчи.
            // WB API принимает батчи по 1000 — это убирает 217 запросов
            // и превращает 144с ожидания в ~18с.
            val batchSize = 1000
            for (i in allIds.indices step batchSize) {
                val batch = allIds.subList(i, minOf(i + batchSize, allIds.size))
                val response = requireApi().getOrdersStatus(WbStatusRequest(batch))
                val ordersList = response.body()?.orders
                if (ordersList != null) {
                    for (dto in ordersList) {
                        orderDao.updateOrderStatus(dto.id, dto.supplierStatus)
                    }
                }
                if (i + batchSize < allIds.size) delay(200)
            }
            // Заказы, перешедшие в confirm/complete ("на сборке"), ещё не имеют
            // stickerBarcode — дотягиваем стикеры, иначе сканирование стикера на сборке
            // не сработает (WB присваивает стикер только после перевода в confirm/complete).
            syncMissingStickers()
        } catch (e: Throwable) {
            // Не критчно — ловим даже Error (OOM и т.д.)
        }
    }

    /**
     * Сканирование стикера WB (наклейка на сборочном задании).
     *
     * ВАЖНО: ищем заказ по stickerBarcode — уникальному коду конкретного
     * сборочного задания (поле `barcode` из WbStickerDto, POST /api/v3/orders/stickers).
     * Это НЕ то же самое, что barcode товара — один и тот же товар может продаваться
     * в нескольких кабинетах WB с одинаковым штрихкодом, поэтому поиск по barcode товара
     * не позволяет однозначно определить, к какому заказу/кабинету относится сборка.
     * stickerBarcode привязан к конкретному orderId и уникален.
     *
     * Fallback на "голый orderId в строке" оставлен только для случая, когда
     * стикер ещё не был синхронизирован (syncMissingStickers() не запускался) —
     * это деградация, а не основной путь.
     */
    suspend fun scanWbSticker(stickerData: String): ScanResult {
        val trimmed = stickerData.trim()
        android.util.Log.i("WbRepo", "scanWbSticker: searching for '$trimmed' (len=${trimmed.length}, bytes=${trimmed.map { it.code }.joinToString(",")})")

        // 1. Основной путь: точное совпадение по уникальному коду стикера
        val byStickerBarcode = orderDao.getOrderByStickerBarcode(trimmed)
        android.util.Log.i("WbRepo", "scanWbSticker: getOrderByStickerBarcode result = ${byStickerBarcode?.id ?: "null"}")
        if (byStickerBarcode != null) {
            android.util.Log.i("WbRepo", "scanWbSticker: found by stickerBarcode! orderId=${byStickerBarcode.id}")
            return finishStickerScan(byStickerBarcode)
        }

        // 2. Фолбэк: часть A стикера (partA) — то, что напечатано под штрихкодом
        val allOrders: List<OrderEntity> = orderDao.getOrdersForStickerSearch()
        // Дамп первых 5 стикер-баркодов для отладки
        val sample = allOrders.filter { !it.stickerBarcode.isNullOrEmpty() }.take(5).joinToString(", ") { "${it.id}='${it.stickerBarcode}'(len=${it.stickerBarcode?.length})" }
        android.util.Log.i("WbRepo", "scanWbSticker: DB has ${allOrders.size} orders, sample stickers: $sample")
        val byPartA = allOrders.find { it.stickerPartA != null && it.stickerPartA == trimmed }
        if (byPartA != null) {
            return finishStickerScan(byPartA)
        }
        val byPartB = allOrders.find { it.stickerPartB != null && it.stickerPartB == trimmed }
        if (byPartB != null) {
            return finishStickerScan(byPartB)
        }

        // 3. Деградация: если stickerBarcode ещё не подтянут (syncMissingStickers()
        // не выполнялся) — пробуем распознать голый orderId в отсканированной строке.
        val orderId = trimmed.filter { it.isDigit() }.takeIf { it.length >= 5 }?.toLongOrNull()
        if (orderId != null) {
            val order = orderDao.getOrderById(orderId)
            if (order != null) {
                return finishStickerScan(order)
            }
        }

        // 4. Last resort: стикеры не скачаны или заказы не синхронизированы.
        // Сначала обновляем статусы (заказы могли перейти new→confirm на портале),
        // затем тянем стикеры для confirm-заказов, и повторяем поиск.
        try {
            android.util.Log.i("WbRepo", "scanWbSticker: sticker not found, running full sync...")
            // Получаем все ID заказов из БД для синхронизации статусов
            val allIds = orderDao.getOrdersForStickerSearch().map { it.id }
            if (allIds.isNotEmpty()) {
                android.util.Log.i("WbRepo", "scanWbSticker: syncing ${allIds.size} order statuses...")
                syncStatuses(allIds)
                android.util.Log.i("WbRepo", "scanWbSticker: statuses synced, downloading stickers...")
            }
            syncMissingStickers()
            // Повторяем поиск по barcode
            val retryByBarcode = orderDao.getOrderByStickerBarcode(trimmed)
            if (retryByBarcode != null) {
                android.util.Log.i("WbRepo", "scanWbSticker: found after sync! orderId=${retryByBarcode.id}")
                return finishStickerScan(retryByBarcode)
            }
            // Повторяем поиск по partA/partB
            val retryOrders = orderDao.getOrdersForStickerSearch()
            val retryByPartA = retryOrders.find { it.stickerPartA != null && it.stickerPartA == trimmed }
            if (retryByPartA != null) {
                android.util.Log.i("WbRepo", "scanWbSticker: found by partA after sync! orderId=${retryByPartA.id}")
                return finishStickerScan(retryByPartA)
            }
            val retryByPartB = retryOrders.find { it.stickerPartB != null && it.stickerPartB == trimmed }
            if (retryByPartB != null) {
                android.util.Log.i("WbRepo", "scanWbSticker: found by partB after sync! orderId=${retryByPartB.id}")
                return finishStickerScan(retryByPartB)
            }
            android.util.Log.w("WbRepo", "scanWbSticker: sticker '$trimmed' not found even after sync, trying WB API direct search...")
            // 5. Заказ может быть в confirm на портале, но не в БД.
            // Перебираем страницы /api/v3/orders, вставляем заказы в БД,
            // синхронизируем статусы, качаем стикеры для confirm, повторяем поиск.
            try {
                var nextOffset: Long = 0
                var page = 0
                loop@ while (true) {
                    page++
                    android.util.Log.i("WbRepo", "scanWbSticker: step 5 — fetching page $page (next=$nextOffset)")
                    val resp = safeApiCall { requireApi().getOrders(limit = 1000, next = nextOffset) }.getOrNull()
                    val freshOrders = resp?.orders?.map { it.toEntity() } ?: emptyList()
                    if (freshOrders.isEmpty()) {
                        android.util.Log.i("WbRepo", "scanWbSticker: step 5 — no more orders, stopping")
                        break@loop
                    }
                    // Вставляем в БД
                    orderDao.insertOrders(freshOrders)
                    // Синхронизируем статусы
                    val freshIds = freshOrders.map { it.id }
                    syncStatuses(freshIds)
                    // Качаем стикеры только для confirm без стикеров
                    val confirmPending = orderDao.getOrdersNeedingStickers()
                    if (confirmPending.isNotEmpty()) {
                        android.util.Log.i("WbRepo", "scanWbSticker: step 5 — page $page: ${confirmPending.size} confirm orders without stickers")
                        val ids = confirmPending.map { it.id }
                        downloadStickers(ids).onSuccess { stickers ->
                            android.util.Log.i("WbRepo", "scanWbSticker: step 5 — got ${stickers.size} stickers")
                        }
                    }
                    // Повторяем поиск
                    val found = orderDao.getOrderByStickerBarcode(trimmed)
                    if (found != null) {
                        android.util.Log.i("WbRepo", "scanWbSticker: found on page $page! orderId=${found.id}")
                        return finishStickerScan(found)
                    }
                    // Проверяем partA
                    val allOrders = orderDao.getOrdersForStickerSearch()
                    val byPartA = allOrders.find { it.stickerPartA != null && it.stickerPartA == trimmed }
                    if (byPartA != null) {
                        android.util.Log.i("WbRepo", "scanWbSticker: found by partA on page $page! orderId=${byPartA.id}")
                        return finishStickerScan(byPartA)
                    }
                    // Следующая страница
                    nextOffset = resp?.next ?: break@loop
                    // Небольшая пауза между страницами
                    delay(300)
                }
            } catch (e: Throwable) {
                android.util.Log.e("WbRepo", "scanWbSticker: WB direct search failed: ${e.message}", e)
            }
        } catch (e: Throwable) {
            android.util.Log.e("WbRepo", "scanWbSticker: sync failed: ${e.message}", e)
        }

        scanLogDao.insert(
            ScanLogEntity(
                orderId = null,
                supplyId = null,
                scanType = "sticker",
                scannedValue = trimmed,
                success = false,
                errorMessage = "Стикер не найден в локальной базе"
            )
        )
        return ScanResult.Error("Стикер не найден. Возможно, стикеры не синхронизированы — обновите список заказов.")
    }

    private suspend fun finishStickerScan(order: OrderEntity): ScanResult {
        if (order.status == "cancel") {
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = order.id,
                    supplyId = null,
                    scanType = "sticker",
                    scannedValue = order.stickerBarcode ?: order.id.toString(),
                    success = false,
                    errorMessage = "Заказ отменён"
                )
            )
            return ScanResult.Error("Заказ отменён")
        }
        orderDao.markOrderScanned(order.id, System.currentTimeMillis())
        scanLogDao.insert(
            ScanLogEntity(
                orderId = order.id,
                supplyId = null,
                scanType = "sticker",
                scannedValue = order.stickerBarcode ?: order.id.toString(),
                success = true,
                errorMessage = null
            )
        )
        return ScanResult.Success(order)
    }
    private suspend fun <T> safeApiCall(
        maxRetries: Int = 3,
        block: suspend () -> retrofit2.Response<T>
    ): kotlin.Result<T> {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = block()

                when (response.code()) {
                    200, 201, 204 -> {
                        val body = response.body()
                        return if (body != null) {
                            kotlin.Result.success(body)
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            kotlin.Result.success(Unit as T)
                        }
                    }
                    401 -> {
                        return kotlin.Result.failure(
                            ApiException.Unauthorized("Токен невалиден. Авторизуйтесь заново.")
                        )
                    }
                    429 -> {
                        // WB rate limit: ~100 req/min. Экспоненциальная задержка + джиттер.
                        // 1s → 2s → 4s (+ random 0-500ms) = max ~6.5s за 3 ретрая
                        val delayMs = (1 shl attempt) * 1000L + (0..500).random()
                        delay(delayMs)
                        lastException = ApiException.RateLimit("429, retry ${attempt + 1}/$maxRetries")
                    }
                    500, 502, 503, 504 -> {
                        val delayMs = (1 shl attempt) * 1000L
                        delay(delayMs)
                        lastException = ApiException.ServerError(
                            "HTTP ${response.code()}, retry ${attempt + 1}/$maxRetries"
                        )
                    }
                    else -> {
                        return kotlin.Result.failure(
                            ApiException.HttpError(
                                response.code(),
                                response.errorBody()?.string() ?: "Unknown error"
                            )
                        )
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastException = ApiException.Timeout("Таймаут, retry ${attempt + 1}/$maxRetries")
                delay(2000)
            } catch (e: java.net.UnknownHostException) {
                return kotlin.Result.failure(ApiException.NoInternet("Нет подключения к интернету"))
            } catch (e: Exception) {
                lastException = e
                delay(1000)
            }
        }

        return kotlin.Result.failure(lastException ?: Exception("Unknown error"))
    }

}

// Sealed class для ошибок — top-level, а не вложенный: OrdersViewModel импортирует
// com.wb.fbs.tsd.data.repository.ApiException напрямую, вложенный класс с таким же именем
// не резолвится по этому импорту и молча ломает все `is ApiException.Xxx` проверки.
sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized(message: String) : ApiException(message)
    class RateLimit(message: String) : ApiException(message)
    class ServerError(message: String) : ApiException(message)
    class HttpError(val code: Int, message: String) : ApiException("HTTP $code: $message")
    class Timeout(message: String) : ApiException(message)
    class NoInternet(message: String) : ApiException(message)
}

sealed class ScanResult {
    data class Success(val order: OrderEntity) : ScanResult()
    object NotFound : ScanResult()
    data class Error(val message: String) : ScanResult()
}
sealed class KizScanResult {
    data class Success(val order: OrderEntity, val sgtin: String) : KizScanResult()
    data class OrderNotFound(val gtin: String) : KizScanResult()
    object InvalidFormat : KizScanResult()
}