package com.fueloptimizer.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueloptimizer.domain.NamedPlace
import com.fueloptimizer.presentation.MainViewModel
import com.fueloptimizer.presentation.PlaceSearchTarget
import com.fueloptimizer.ui.components.TossCard

data class PlaceOption(
    val place: NamedPlace,
    val icon: ImageVector,
    val subtitle: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val target by viewModel.placeSearchTarget.collectAsState()
    val isOrigin = target == PlaceSearchTarget.ORIGIN
    var query by remember { mutableStateOf("") }
    val results by viewModel.placeResults.collectAsState()
    val searching by viewModel.placeSearching.collectAsState()

    val options = remember(results) {
        results.mapIndexed { index, place ->
            PlaceOption(
                place = place,
                icon = if (place.name.contains("주유소")) Icons.Filled.MyLocation else {
                    if (index % 3 == 0) Icons.Filled.Domain
                    else Icons.Filled.LocationOn
                },
                subtitle = place.address ?: "위치 선택"
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isOrigin) "출발지 검색" else "도착지 검색",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TossCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.searchPlaces(it)
                    },
                    placeholder = { Text("장소 이름으로 검색") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (options.isEmpty() && !searching) {
                    item {
                        Text(
                            text = "검색 결과가 없어요. 다른 키워드로 검색해 보세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
                items(options, key = { "${it.place.lat},${it.place.lng},${it.place.name}" }) { option ->
                    PlaceRow(
                        option = option,
                        onClick = { viewModel.selectPlace(option.place) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(option: PlaceOption, onClick: () -> Unit) {
    TossCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = option.place.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                )
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
