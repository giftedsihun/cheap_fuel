package com.fueloptimizer.presentation.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fueloptimizer.domain.BRAND_LABEL
import com.fueloptimizer.domain.Brand
import com.fueloptimizer.domain.DiscountRule
import com.fueloptimizer.domain.FillPolicy
import com.fueloptimizer.domain.FillPolicyMode
import com.fueloptimizer.domain.fillEconomy
import com.fueloptimizer.domain.formatKm
import com.fueloptimizer.domain.formatLiters
import com.fueloptimizer.domain.learnedKmPerLiter
import com.fueloptimizer.presentation.MainViewModel
import com.fueloptimizer.ui.components.TossCard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private fun parseD(s: String, fallback: Double): Double = s.toDoubleOrNull() ?: fallback

private fun formatDepartAt(epochMs: Long?): String {
    if (epochMs == null) return "지금 출발"
    val sdf = SimpleDateFormat("M월 d일 (E) HH:mm 출발", Locale.KOREA)
    sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
    return sdf.format(java.util.Date(epochMs))
}

@Composable
fun SettingsScreen(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val prefs = state.preferences
    val fills by viewModel.fills.collectAsState()
    val departAtMs by viewModel.departAtMs.collectAsState()
    val context = LocalContext.current

    if (prefs == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var timeValue by remember(prefs) { mutableStateOf(prefs.timeValueKrwPerMin.toString()) }
    var cardDiscount by remember(prefs) { mutableStateOf(prefs.cardDiscountKrwPerL.toString()) }
    var extraDiscount by remember(prefs) { mutableStateOf(prefs.extraDiscountRate.toString()) }
    var maxDetourKm by remember(prefs) { mutableStateOf(prefs.maxDetourKm.toString()) }
    var maxDetourMin by remember(prefs) { mutableStateOf(prefs.maxDetourMin.toString()) }
    var minSaving by remember(prefs) { mutableStateOf(prefs.minMeaningfulSavingKrw.toString()) }
    var selfServiceOnly by remember(prefs) { mutableStateOf(prefs.selfServiceOnly) }
    var avoidHighwayExit by remember(prefs) { mutableStateOf(prefs.avoidHighwayExit) }
    var fillPolicy by remember(prefs) { mutableStateOf(prefs.fillPolicy.mode) }
    var fixedLiters by remember(prefs) { mutableStateOf(prefs.fillPolicy.liters?.toString() ?: "") }
    var fixedBudget by remember(prefs) { mutableStateOf(prefs.fillPolicy.krw?.toString() ?: "") }
    var selectedBrands by remember(prefs) { mutableStateOf(prefs.brands.toSet()) }
    var rules by remember(prefs) { mutableStateOf(prefs.discountRules) }
    var savedTick by remember { mutableStateOf(0) }

    var fillKm by remember { mutableStateOf("") }
    var fillLiters by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "출발 시각") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDepartAt(departAtMs),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.setDepartAt(null) }) {
                        Text(text = "지금")
                    }
                    TextButton(onClick = {
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                        departAtMs?.let { cal.timeInMillis = it }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        val c2 = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                                        c2.set(y, m, d, h, min, 0)
                                        c2.set(Calendar.MILLISECOND, 0)
                                        viewModel.setDepartAt(c2.timeInMillis)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text(text = "변경")
                    }
                }
            }
            Text(
                text = "미래 출발 시각이면 도착지 교통 상황을 반영합니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
            TossInputField(label = "추가 정률 할인 (0~1, 예: 0.05)", value = extraDiscount, onValueChange = { extraDiscount = it })
            TossInputField(label = "최소 의미 있는 절감액 (원)", value = minSaving, onValueChange = { minSaving = it })
        }

        SettingsSection(title = "할인 규칙") {
            if (rules.isEmpty()) {
                Text(
                    text = "등록된 할인 규칙이 없습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            rules.forEach { rule ->
                DiscountRuleRow(
                    rule = rule,
                    onToggle = { enabled ->
                        rules = rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }
                    },
                    onDelete = { rules = rules.filter { it.id != rule.id } }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            var newName by remember { mutableStateOf("") }
            var newFlat by remember { mutableStateOf("") }
            var newRate by remember { mutableStateOf("") }
            TossInputField(label = "규칙 이름 (예: ○○카드 5%)", value = newName, onValueChange = { newName = it })
            TossInputField(label = "L당 할인 (원)", value = newFlat, onValueChange = { newFlat = it })
            TossInputField(label = "정률 할인 (0~1)", value = newRate, onValueChange = { newRate = it })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (newName.isBlank()) return@OutlinedButton
                    rules = rules + DiscountRule(
                        id = UUID.randomUUID().toString(),
                        name = newName.trim(),
                        enabled = true,
                        flatKrwPerL = newFlat.toDoubleOrNull() ?: 0.0,
                        rate = newRate.toDoubleOrNull() ?: 0.0,
                        brands = emptyList()
                    )
                    newName = ""
                    newFlat = ""
                    newRate = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "규칙 추가")
            }
        }

        SettingsSection(title = "우회 제한") {
            TossInputField(label = "최대 우회 거리 (km)", value = maxDetourKm, onValueChange = { maxDetourKm = it })
            TossInputField(label = "최대 우회 시간 (분)", value = maxDetourMin, onValueChange = { maxDetourMin = it })
        }

        SettingsSection(title = "필터") {
            TossSwitch(label = "셀프 주유소만", checked = selfServiceOnly, onCheckedChange = { selfServiceOnly = it })
            TossSwitch(label = "고속도로 진출 필요 주유소 제외", checked = avoidHighwayExit, onCheckedChange = { avoidHighwayExit = it })
        }

        SettingsSection(title = "브랜드 제한 (선택 시 해당 브랜드만)") {
            BrandCheckboxGroup(
                selectedBrands = selectedBrands,
                onChange = { selectedBrands = it }
            )
        }

        SettingsSection(title = "주유 기록 (학습 연비)") {
            val learned = learnedKmPerLiter(fills)
            Text(
                text = if (learned != null) "학습 연비: ${String.format(Locale.KOREA, "%.1f", learned)}km/L (최근 기록 기준)"
                else "주유 기록을 입력하면 실제 연비를 학습합니다",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TossInputField(label = "주행거리 (km)", value = fillKm, onValueChange = { fillKm = it }, modifier = Modifier.weight(1f))
                TossInputField(label = "주유량 (L)", value = fillLiters, onValueChange = { fillLiters = it }, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val km = fillKm.toDoubleOrNull() ?: return@OutlinedButton
                    val l = fillLiters.toDoubleOrNull() ?: return@OutlinedButton
                    if (km <= 0 || l <= 0) return@OutlinedButton
                    viewModel.addFill(km, l)
                    fillKm = ""
                    fillLiters = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "기록 추가")
            }
            fills.take(5).forEach { f ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${f.at.take(10)} · ${formatKm(f.kmDriven)} / ${formatLiters(f.liters)} = ${String.format(Locale.KOREA, "%.1f", fillEconomy(f))}km/L",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = {
                viewModel.updatePreferences(
                    prefs.copy(
                        timeValueKrwPerMin = parseD(timeValue, prefs.timeValueKrwPerMin),
                        cardDiscountKrwPerL = parseD(cardDiscount, prefs.cardDiscountKrwPerL),
                        extraDiscountRate = parseD(extraDiscount, prefs.extraDiscountRate).coerceIn(0.0, 1.0),
                        maxDetourKm = parseD(maxDetourKm, prefs.maxDetourKm).coerceAtLeast(0.5),
                        maxDetourMin = parseD(maxDetourMin, prefs.maxDetourMin).coerceAtLeast(1.0),
                        minMeaningfulSavingKrw = parseD(minSaving, prefs.minMeaningfulSavingKrw).coerceAtLeast(0.0),
                        selfServiceOnly = selfServiceOnly,
                        avoidHighwayExit = avoidHighwayExit,
                        fillPolicy = FillPolicy(
                            mode = fillPolicy,
                            liters = if (fillPolicy == FillPolicyMode.fixedLiters) fixedLiters.toDoubleOrNull() else null,
                            krw = if (fillPolicy == FillPolicyMode.fixedBudget) fixedBudget.toDoubleOrNull() else null
                        ),
                        brands = selectedBrands.toList(),
                        discountRules = rules
                    )
                )
                savedTick++
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(
                text = if (savedTick > 0) "저장됨 ✓  다시 저장" else "설정 저장",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
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
fun DiscountRuleRow(
    rule: DiscountRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = rule.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(
                text = buildString {
                    if (rule.flatKrwPerL > 0) append("L당 ${rule.flatKrwPerL.toInt()}원 ")
                    if (rule.rate > 0) append("${(rule.rate * 100).toInt()}% ")
                    if (rule.brands.isNotEmpty()) append(rule.brands.joinToString(",") { BRAND_LABEL[it] ?: it.name })
                }.ifBlank { "할인 없음" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
        TextButton(onClick = onDelete) {
            Text(text = "삭제")
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
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
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
                    text = BRAND_LABEL[brand] ?: brand.name,
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
