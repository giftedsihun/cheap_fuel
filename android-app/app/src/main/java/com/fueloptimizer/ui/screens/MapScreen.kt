package com.fueloptimizer.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fueloptimizer.graph.FuelGraph
import com.fueloptimizer.graph.FuelPlan
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.graphics.Color

@Composable
fun MapScreen(
    plan: FuelPlan,
    graph: FuelGraph,
    startId: String,
    goalId: String,
    modifier: Modifier = Modifier
) {
    val refs = graph.stations

    val pathCoords = remember(plan.path) {
        plan.path.mapNotNull { sid ->
            refs[sid]?.let { LatLng(it.lat, it.lon) }
        }
    }

    val refuelIds = remember(plan.refuels) {
        plan.refuels.map { it.stationId }.toSet()
    }

    val firstCoord = pathCoords.firstOrNull() ?: LatLng(37.5, 127.0)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(firstCoord, 11f)
    }

    LaunchedEffect(pathCoords) {
        if (pathCoords.size >= 2) {
            val boundsBuilder = LatLngBounds.builder()
            pathCoords.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngBounds(bounds, 80)
            )
        }
    }

    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = false
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = true,
            compassEnabled = true,
            mapToolbarEnabled = false
        )
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings
    ) {
        refs.forEach { (sid, st) ->
            val isStart = sid == startId
            val isGoal = sid == goalId
            val isRefuel = sid in refuelIds
            val position = LatLng(st.lat, st.lon)

            val hue = when {
                isStart -> BitmapDescriptorFactory.HUE_GREEN
                isGoal -> BitmapDescriptorFactory.HUE_RED
                isRefuel -> BitmapDescriptorFactory.HUE_ORANGE
                else -> BitmapDescriptorFactory.HUE_BLUE
            }
            val alpha = if (isStart || isGoal || isRefuel) 1f else 0.6f

            Marker(
                state = MarkerState(position = position),
                title = st.name,
                snippet = "${st.pricePerLiter.toInt()} KRW/L",
                icon = BitmapDescriptorFactory.defaultMarker(hue),
                alpha = alpha
            )
        }

        if (pathCoords.size >= 2) {
            Polyline(
                points = pathCoords,
                color = Color(0xFFE53935),
                width = 8f
            )
        }
    }
}
