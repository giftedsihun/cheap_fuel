package com.fueloptimizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fueloptimizer.graph.FuelGraph
import com.fueloptimizer.graph.FuelPlan
import com.fueloptimizer.graph.Station

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    plan: FuelPlan,
    graph: FuelGraph,
    startId: String,
    goalId: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Optimal Route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                         Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(plan)
            PathCard(plan, graph, startId, goalId)
            RefuelsCard(plan)
        }
    }
}

@Composable
private fun SummaryCard(plan: FuelPlan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Total distance", style = MaterialTheme.typography.labelMedium, color = Color.White)
            Text(
                String.format("%.1f km", plan.totalDistance),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text("Total fuel cost", style = MaterialTheme.typography.labelMedium, color = Color.White)
            Text(
                String.format("%,.0f KRW", plan.totalCost),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PathCard(plan: FuelPlan, graph: FuelGraph, startId: String, goalId: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Route", style = MaterialTheme.typography.titleMedium)
            plan.path.forEachIndexed { idx, stationId ->
                val st = graph.stations[stationId] ?: return@forEachIndexed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                when (stationId) {
                                    startId -> Color(0xFF4CAF50)
                                    goalId -> Color(0xFFF44336)
                                    else -> Color(0xFF2196F3)
                                }
                            )
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "${idx + 1}. ${st.name}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "${st.pricePerLiter.toInt()} KRW/L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RefuelsCard(plan: FuelPlan) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Refuel stops", style = MaterialTheme.typography.titleMedium)
            if (plan.refuels.isEmpty()) {
                Text("No refueling needed - direct route")
            } else {
                plan.refuels.forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${r.stationName}")
                        Text("${"%.2f".format(r.liters)} L")
                        Text("%,d KRW".format(r.cost.toInt()))
                    }
                }
            }
        }
    }
}
