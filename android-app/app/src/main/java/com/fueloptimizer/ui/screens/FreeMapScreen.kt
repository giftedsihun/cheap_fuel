package com.fueloptimizer.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fueloptimizer.graph.FuelGraph
import com.fueloptimizer.graph.FuelPlan
import org.json.JSONArray

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FreeMapScreen(
    plan: FuelPlan,
    graph: FuelGraph,
    startId: String,
    goalId: String,
    modifier: Modifier = Modifier
) {
    val refs = graph.stations
    val refuelIds = remember(plan.refuels) { plan.refuels.map { it.stationId }.toSet() }

    val pathCoords = remember(plan.path) {
        plan.path.mapNotNull { sid ->
            refs[sid]?.let { doubleArrayOf(it.lat, it.lon) }
        }
    }

    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    webChromeClient = WebChromeClient()
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    setBackgroundColor(0xFFEEEEEE.toInt())
                    loadUrl("file:///android_asset/freemap.html")
                }
            },
            update = { view -> webView = view }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { webView?.evaluateJavascript("zoomIn();", null) }
            ) { Icon(Icons.Filled.Add, contentDescription = "Zoom in") }
            FloatingActionButton(
                onClick = { webView?.evaluateJavascript("zoomOut();", null) }
            ) { Icon(Icons.Filled.Remove, contentDescription = "Zoom out") }
        }
    }

    LaunchedEffect(plan, webView) {
        val v = webView ?: return@LaunchedEffect
        kotlinx.coroutines.delay(1200)

        val first = refs[startId] ?: refs[plan.path.firstOrNull() ?: ""]
        if (first == null) return@LaunchedEffect

        v.evaluateJavascript("initMap(${first.lat}, ${first.lon}, 12);", null)
        kotlinx.coroutines.delay(400)

        v.evaluateJavascript("clearMarkers(); clearPolylines();", null)

        refs.forEach { (sid, st) ->
            val type = when (sid) {
                startId -> "START"
                goalId -> "GOAL"
                in refuelIds -> "REFUEL"
                else -> "NORMAL"
            }
            val escapedName = st.name.replace("'", "\\'")
            v.evaluateJavascript(
                "addMarker('$sid', '$escapedName', ${st.lat}, ${st.lon}, '$type', ${st.pricePerLiter.toInt()});",
                null
            )
        }

        if (pathCoords.isNotEmpty()) {
            val jsonArr = JSONArray()
            pathCoords.forEach { c ->
                jsonArr.put(JSONArray().put(c[0]).put(c[1]))
            }
            val pathJson = jsonArr.toString().replace("'", "\\'")
            v.evaluateJavascript("drawPath('$pathJson');", null)

            if (pathCoords.size >= 2) {
                val lats = pathCoords.map { it[0] }
                val lngs = pathCoords.map { it[1] }
                val swLat = lats.min(); val neLat = lats.max()
                val swLng = lngs.min(); val neLng = lngs.max()
                kotlinx.coroutines.delay(200)
                v.evaluateJavascript("fitBounds($swLat, $swLng, $neLat, $neLng);", null)
            }
        }
    }
}
