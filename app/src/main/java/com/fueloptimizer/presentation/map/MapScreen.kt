package com.fueloptimizer.presentation.map

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fueloptimizer.domain.Route
import com.fueloptimizer.domain.Station
import com.fueloptimizer.domain.stationHeading
import com.fueloptimizer.presentation.MainViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val route by viewModel.currentRoute.collectAsState()
    val plan by viewModel.refuelPlan.collectAsState()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapView by remember { mutableStateOf<MapView?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
            mapView = null
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "지도",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (route == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "홈에서 출발지와 도착지를 검색해주세요",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }
            val stations = plan?.options?.map { it.station }.orEmpty()
            val bestId = plan?.best?.station?.id

            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().load(
                        ctx,
                        ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                    )
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        mapView = this
                    }
                },
                update = { map ->
                    renderMap(map, route!!, stations, bestId)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dest = route!!.destination
                OutlinedButton(
                    onClick = {
                        val uri = "kakaonavi://navigate?name=${Uri.encode(dest.name)}" +
                            "&x=${dest.lng}&y=${dest.lat}&coord_type=wgs84"
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(text = "카카오내비 안내")
                }
                Button(
                    onClick = {
                        val uri = "https://map.kakao.com/link/to/" +
                            "${Uri.encode(dest.name)},${dest.lat},${dest.lng}"
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(text = "카카오맵 경로")
                }
            }
        }
    }
}

private fun renderMap(
    map: MapView,
    route: Route,
    stations: List<Station>,
    bestId: String?
) {
    runCatching {
        map.overlays.clear()
        val boundsPoints = mutableListOf<GeoPoint>()

        val routePoints = route.polyline.map { GeoPoint(it.lat, it.lng) }
        if (routePoints.size >= 2) {
            val line = Polyline().apply {
                setPoints(routePoints)
                outlinePaint.color = Color.parseColor("#1B4F8E")
                outlinePaint.strokeWidth = 10f
            }
            map.overlays.add(line)
            boundsPoints.addAll(routePoints)
        }

        fun addPin(lat: Double, lng: Double, title: String) {
            val point = GeoPoint(lat, lng)
            val marker = Marker(map).apply {
                position = point
                this.title = title
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
            boundsPoints.add(point)
        }

        addPin(route.origin.lat, route.origin.lng, "출발: ${route.origin.name}")
        addPin(route.destination.lat, route.destination.lng, "도착: ${route.destination.name}")
        stations.take(10).forEach { s ->
            addPin(s.lat, s.lng, (if (s.id == bestId) "★ " else "") + stationHeading(s))
        }

        if (boundsPoints.size >= 2) {
            runCatching {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(boundsPoints), false, 120)
            }
        } else if (boundsPoints.size == 1) {
            map.controller.setZoom(14.0)
            map.controller.setCenter(boundsPoints.first())
        }
        map.invalidate()
    }
}
