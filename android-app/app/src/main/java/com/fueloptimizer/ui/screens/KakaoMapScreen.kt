package com.fueloptimizer.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
fun KakaoMapScreen(
    plan: FuelPlan,
    graph: FuelGraph,
    startId: String,
    goalId: String,
    kakaoAppKey: String,
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
    var isReady by remember { mutableStateOf(false) }

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

                    val htmlContent = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <meta charset="utf-8"/>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
                        <style>*{margin:0;padding:0;}#map{width:100vw;height:100vh;}</style>
                        </head>
                        <body>
                        <div id="map"></div>
                        <script>
                        window.MAP_READY = false;
                        window.addEventListener('message', function(e) {
                            if (e.data && e.data.type === 'init' && !window.MAP_READY) {
                                var initData = e.data;
                                var script = document.createElement('script');
                                script.type = 'text/javascript';
                                script.src = '//dapi.kakao.com/v2/maps/sdk.js?appkey=${kakaoAppKey}&autoload=false';
                                script.onload = function() {
                                    kakao.maps.load(function() {
                                        var container = document.getElementById('map');
                                        var options = {
                                            center: new kakao.maps.LatLng(initData.lat, initData.lng),
                                            level: initData.level || 8
                                        };
                                        window.__kakaoMap = new kakao.maps.Map(container, options);
                                        window.__markers = [];
                                        window.__polylines = [];
                                        window.MAP_READY = true;
                                        if (window.ReactNativeWebView) {
                                            window.ReactNativeWebView.postMessage(JSON.stringify({type:'ready'}));
                                        }
                                    });
                                };
                                document.head.appendChild(script);
                            }
                        });
                        </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)

                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun postMessage(msg: String) {
                            isReady = msg.contains("\"ready\"")
                        }
                    }, "AndroidBridge")
                }
            },
            update = { view ->
                webView = view
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    webView?.evaluateJavascript("window.parent.postMessage({type:'zoomIn'}, '*');", null)
                    webView?.evaluateJavascript(
                        "(function(){if(window.__kakaoMap)window.__kakaoMap.setLevel(window.__kakaoMap.getLevel()-1);})();",
                        null
                    )
                }
            ) { Icon(Icons.Filled.Add, contentDescription = "Zoom in") }
            FloatingActionButton(
                onClick = {
                    webView?.evaluateJavascript(
                        "(function(){if(window.__kakaoMap)window.__kakaoMap.setLevel(window.__kakaoMap.getLevel()+1);})();",
                        null
                    )
                }
            ) { Icon(Icons.Filled.Remove, contentDescription = "Zoom out") }
        }
    }

    LaunchedEffect(plan, isReady) {
        val v = webView ?: return@LaunchedEffect
        val first = refs[startId] ?: refs[plan.path.firstOrNull() ?: ""] ?: return@LaunchedEffect
        v.evaluateJavascript(
            "(function(){window.postMessage({type:'init',lat:${first.lat},lng:${first.lon},level:8},'*');})();",
            null
        )
    }

    LaunchedEffect(plan, webView) {
        val v = webView ?: return@LaunchedEffect
        kotlinx.coroutines.delay(800)
        v.evaluateJavascript(
            "(function(){if(window.__markers)window.__markers.forEach(function(m){m.setMap(null);});if(window.__polylines)window.__polylines.forEach(function(p){p.setMap(null);});})();",
            null
        )

        refs.forEach { (sid, st) ->
            val type = when (sid) {
                startId -> "START"
                goalId -> "GOAL"
                in refuelIds -> "REFUEL"
                else -> "NORMAL"
            }
            val colorMap = mapOf(
                "START" to "#4CAF50",
                "GOAL" to "#F44336",
                "REFUEL" to "#FF9800",
                "NORMAL" to "#2196F3"
            )
            val color = colorMap[type] ?: "#2196F3"
            val label = when (type) {
                "START" -> "START"
                "GOAL" -> "GOAL"
                "REFUEL" -> "REFUEL"
                else -> ""
            }
            v.evaluateJavascript(
                """
                (function(){
                if(!window.__kakaoMap) return;
                var content = '<div style="padding:4px 8px;background:${'$'}color;color:white;border-radius:12px;font-size:11px;font-weight:bold;white-space:nowrap;">${'$'}{label} ${'$'}{st.name}<br>${'$'}{st.pricePerLiter.toInt()} KRW/L</div>';
                var overlay = new kakao.maps.CustomOverlay({
                    map: window.__kakaoMap,
                    content: content,
                    position: new kakao.maps.LatLng(${st.lat}, ${st.lon}),
                    yAnchor: 1.4,
                    xAnchor: 0.5
                });
                window.__markers.push(overlay);
                })();
                """.trimIndent(),
                null
            )
        }

        if (pathCoords.isNotEmpty()) {
            val jsonArr = JSONArray()
            pathCoords.forEach { c ->
                jsonArr.put(JSONArray().put(c[0]).put(c[1]))
            }
            val pathJson = jsonArr.toString()
            v.evaluateJavascript(
                """
                (function(){
                if(!window.__kakaoMap) return;
                var coords = ${'$'}pathJson;
                var linePath = coords.map(function(c){return new kakao.maps.LatLng(c[0],c[1]);});
                var polyline = new kakao.maps.Polyline({
                    map: window.__kakaoMap,
                    path: linePath,
                    strokeWeight: 6,
                    strokeColor: '#E53935',
                    strokeOpacity: 0.9,
                    strokeStyle: 'solid'
                });
                window.__polylines.push(polyline);
                })();
                """.trimIndent().replace("\$pathJson", pathJson),
                null
            )

            if (pathCoords.size >= 2) {
                val lats = pathCoords.map { it[0] }
                val lngs = pathCoords.map { it[1] }
                val swLat = lats.min()
                val neLat = lats.max()
                val swLng = lngs.min()
                val neLng = lngs.max()
                kotlinx.coroutines.delay(200)
                v.evaluateJavascript(
                    """
                    (function(){
                    if(!window.__kakaoMap) return;
                    var sw = new kakao.maps.LatLng(${swLat}, ${swLng});
                    var ne = new kakao.maps.LatLng(${neLat}, ${neLng});
                    var bounds = new kakao.maps.LatLngBounds(sw, ne);
                    window.__kakaoMap.setBounds(bounds);
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }
    }
}
