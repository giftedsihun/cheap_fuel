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
import com.fueloptimizer.network.MockStations
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

    val places = remember { samplePlaces() }
    val filtered = remember(query) {
        if (query.isBlank()) places
        else places.filter {
            it.place.name.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true)
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
                    onValueChange = { query = it },
                    placeholder = { Text("장소 이름으로 검색") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
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
                items(filtered, key = { it.place.name }) { option ->
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

private fun samplePlaces(): List<PlaceOption> {
    val places = listOf(
        NamedPlace(37.4979, 127.0276, "강남역", "서울 강남구 강남대로"),
        NamedPlace(37.4846, 126.9876, "서울역", "서울 용산구 한강대로"),
        NamedPlace(37.4966, 126.8738, "홍대입구", "서울 마포구 양화로"),
        NamedPlace(37.5547, 126.9707, "숙대입구", "서울 용산구 한강대로"),
        NamedPlace(37.5660, 126.9952, "광화문", "서울 종로구 세종대로"),
        NamedPlace(37.5184, 127.0280, "삼성역", "서울 강남구 테헤란로"),
        NamedPlace(37.5033, 127.0448, "선릉역", "서울 강남구 테헤란로"),
        NamedPlace(37.4750, 127.0300, "자영알뜰주유소", "서울 강남구 역삼로"),
        NamedPlace(37.4956, 127.0669, "판교테크노밸리", "경기 성남시 분당구"),
        NamedPlace(35.1796, 129.0756, "부산 서면", "부산 부산진구 중앙대로"),
        NamedPlace(36.3504, 127.3845, "대전역", "대전 동구 중앙로"),
        NamedPlace(35.1596, 126.8526, "광주 송정역", "광주 광산구 송정로")
    )
    return places.mapIndexed { index, place ->
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