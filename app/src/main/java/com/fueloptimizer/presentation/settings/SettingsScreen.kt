package com.fueloptimizer.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueloptimizer.domain.Brand
import com.fueloptimizer.domain.FillPolicyMode
import com.fueloptimizer.ui.components.TossCard

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var timeValue by remember { mutableStateOf("0") }
    var cardDiscount by remember { mutableStateOf("0") }
    var extraDiscount by remember { mutableStateOf("0") }
    var maxDetourKm by remember { mutableStateOf("5") }
    var maxDetourMin by remember { mutableStateOf("30") }
    var minSaving by remember { mutableStateOf("1000") }
    var selfServiceOnly by remember { mutableStateOf(false) }
    var avoidHighwayExit by remember { mutableStateOf(false) }
    var fillPolicy by remember { mutableStateOf(FillPolicyMode.toDestination) }
    var fixedLiters by remember { mutableStateOf("0") }
    var fixedBudget by remember { mutableStateOf("0") }
    var selectedBrands by remember { mutableStateOf<Set<Brand>>(emptySet()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "주유 정책") {
            RadioGroup(
                selected = fillPolicy,
                onSelected = { fillPolicy = it },
                items = FillPolicyMode.entries.map { it.name }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (fillPolicy == FillPolicyMode.fixedLiters) {
                TossInputField(label = "정량 (L)", value = fixedLiters, onValueChange = { fixedLiters = it })
            } else if (fillPolicy == FillPolicyMode.fixedBudget) {
                TossInputField(label = "정액 (원)", value = fixedBudget, onValueChange = { fixedBudget = it })
            }
        }

        SettingsSection(title = "시간 가치 및 할인") {
            TossInputField(label = "시간 가치 (원/분)", value = timeValue, onValueChange = { timeValue = it })
            TossInputField(label = "카드 할인 (원/L)", value = cardDiscount, onValueChange = { cardDiscount = it })
            TossInputField(label = "추가 정률 할인 (0~1)", value = extraDiscount, onValueChange = { extraDiscount = it })
            TossInputField(label = "최소 의미 있는 절감액 (원)", value = minSaving, onValueChange = { minSaving = it })
        }

        SettingsSection(title = "우회 제한") {
            TossInputField(label = "최대 우회 거리 (km)", value = maxDetourKm, onValueChange = { maxDetourKm = it })
            TossInputField(label = "최대 우회 시간 (분)", value = maxDetourMin, onValueChange = { maxDetourMin = it })
        }

        SettingsSection(title = "필터") {
            TossSwitch(label = "셀프 주유소만", checked = selfServiceOnly, onCheckedChange = { selfServiceOnly = it })
            TossSwitch(label = "고속도로 진출 필요 주유소 제외", checked = avoidHighwayExit, onCheckedChange = { avoidHighwayExit = it })
        }

        SettingsSection(title = "브랜드 제한") {
            BrandCheckboxGroup(
                selectedBrands = selectedBrands,
                onChange = { selectedBrands = it }
            )
        }

        SettingsSection(title = "앱 정보") {
            TossCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Gas Smart v1.0", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "연료 최적화 경로 탐색기", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        TossCard {
            Column(modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()) {
                content()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun RadioGroup(
    selected: FillPolicyMode,
    onSelected: (FillPolicyMode) -> Unit,
    items: List<String>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val mode = FillPolicyMode.valueOf(item)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelected(mode) }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selected == mode),
                    onClick = { onSelected(mode) }
                )
                Text(
                    text = when (mode) {
                        FillPolicyMode.toDestination -> "목적지까지 (필요한 만큼)"
                        FillPolicyMode.full -> "가득 채우기"
                        FillPolicyMode.fixedLiters -> "정량 주유"
                        FillPolicyMode.fixedBudget -> "정액 주유"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
fun BrandCheckboxGroup(
    selectedBrands: Set<Brand>,
    onChange: (Set<Brand>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Brand.entries.forEach { brand ->
            val checked = brand in selectedBrands
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onChange(if (checked) selectedBrands - brand else selectedBrands + brand) }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onChange(if (checked) selectedBrands - brand else selectedBrands + brand) }
                )
                Text(
                    text = brand.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
fun TossInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun TossSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCheckedChange(!checked) }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
    }
}