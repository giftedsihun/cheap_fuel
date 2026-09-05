package com.fueloptimizer.presentation.result

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fueloptimizer.domain.ItineraryStop
import com.fueloptimizer.domain.RankedOption
import com.fueloptimizer.domain.RefuelPlan
import com.fueloptimizer.domain.ReportKind
import com.fueloptimizer.domain.Severity
import com.fueloptimizer.domain.formatKm
import com.fueloptimizer.domain.formatKrw
import com.fueloptimizer.domain.formatLiters
import com.fueloptimizer.domain.formatMinutes
import com.fueloptimizer.domain.formatPerLiter
import com.fueloptimizer.domain.formatSignedKrw
import com.fueloptimizer.domain.relativeTime
import com.fueloptimizer.domain.stationHeading
import com.fueloptimizer.presentation.MainViewModel
import com.fueloptimizer.ui.components.TossCard
import com.fueloptimizer.ui.components.TossDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToMap: () -> Unit = {}
) {
    val plan by viewModel.refuelPlan.collectAsState()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "결과",
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            plan?.let { p ->
                HeadlineCard(p)

                p.best?.let { best ->
                    BestStationCard(
                        best = best,
                        onReportPrice = {
                            viewModel.addReport(best.station.id, best.station.name, ReportKind.priceMismatch)
                            Toast.makeText(context, "가격 오류를 제보했습니다", Toast.LENGTH_SHORT).show()
                        },
                        onReportClosed = {
                            viewModel.addReport(best.station.id, best.station.name, ReportKind.closed)
                            Toast.makeText(context, "휴·폐업을 제보했습니다", Toast.LENGTH_SHORT).show()
                        }
                    )
                    FuelTimelineCard(p, best)
                }

                if (p.itinerary.size >= 2) {
                    ItineraryCard(p.itinerary)
                }

                WarningsCard(p)

                if (p.options.size > 1) {
                    Text(
                        text = "다른 후보",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                    p.options.forEachIndexed { index, option ->
                        if (index > 0) {
                            OptionRowCard(
                                option = option,
                                onReportPrice = {
                                    viewModel.addReport(option.station.id, option.station.name, ReportKind.priceMismatch)
                                    Toast.makeText(context, "가격 오류를 제보했습니다", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                if (p.excluded.isNotEmpty()) {
                    ExcludedCard(p)
                }

                MetaCard(p)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToMap,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Text(text = "인앱 지도")
                    }
                    p.best?.let { best ->
                        Button(
                            onClick = {
                                val dest = state.destination
                                val target = if (dest != null) {
                                    "https://map.kakao.com/link/to/${Uri.encode(dest.name)},${dest.lat},${dest.lng}"
                                } else {
                                    "https://map.kakao.com/link/map/${Uri.encode(best.station.name)},${best.station.lat},${best.station.lng}"
                                }
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Text(text = "카카오맵 안내")
                        }
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "경로를 먼저 검색해주세요",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadlineCard(p: RefuelPlan) {
    TossCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = p.headline,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            )
            Text(
                text = "${formatKm(p.route.distanceM / 1000)} · ${formatMinutes(p.route.durationS / 60)}" +
                    if (p.route.tollKrw > 0) " · 톨게이트 ${formatKrw(p.route.tollKrw)}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!p.canReachWithoutRefueling) {
                Text(
                    text = "주유 필요량(우회 제외): ${formatLiters(p.litersRequiredWithoutDetour)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BestStationCard(
    best: RankedOption,
    onReportPrice: () -> Unit,
    onReportClosed: () -> Unit
) {
    TossCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "최적 주유소",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = stationHeading(best.station),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            )
            Text(
                text = "${best.station.address ?: "주소 미상"}" +
                    (if (best.station.isSelfService) " · 셀프" else "") +
                    (if (best.detour.extraDistanceM > 0) " · 우회 ${formatKm(best.detour.extraDistanceM / 1000)}" else " · 경로상"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CostColumn("리터당 가격", formatPerLiter(best.effectivePriceKrwPerL))
                CostColumn("실제 단가", formatPerLiter(best.krwPerUsefulLiter))
                CostColumn("주유량", formatLiters(best.litersToBuy))
            }

            TossDivider()

            Text(
                text = "비용 분해",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
            )
            CostRow("현장 결제", formatKrw(best.outOfPocketKrw))
            CostRow("우회 연료비", formatKrw(best.detourFuelCostKrw))
            CostRow("시간 비용", formatKrw(best.timeCostKrw))
            CostRow("톨게이트 증감", formatSignedKrw(best.tollDeltaKrw))
            if (best.surplusCreditKrw > 0) CostRow("잉여 연료 가치", "−${formatKrw(best.surplusCreditKrw)}")
            if (best.shortfallCostKrw > 0) CostRow("부족분 대체 비용", "+${formatKrw(best.shortfallCostKrw)}")
            TossDivider()
            CostRow("정규화 총비용", formatKrw(best.normalizedCostKrw), emphasized = true)
            CostRow("현금 비용(시간 제외)", formatKrw(best.normalizedCostKrw - best.timeCostKrw))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CostColumn("절감액", formatSignedKrw(best.savingKrw))
                CostColumn("비관적 절감", formatSignedKrw(best.savingPessimisticKrw))
                CostColumn(
                    "손익분기 우회",
                    if (best.breakEvenDetourKm.isInfinite()) "무제한" else formatKm(best.breakEvenDetourKm)
                )
            }
            if (best.stockUpValueKrw > 0) {
                Text(
                    text = "가득 주유 시 추가 가치 ${formatKrw(best.stockUpValueKrw)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onReportPrice, modifier = Modifier.weight(1f)) {
                    Text(text = "가격 오류 제보", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onReportClosed, modifier = Modifier.weight(1f)) {
                    Text(text = "휴·폐업 제보", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FuelTimelineCard(p: RefuelPlan, best: RankedOption) {
    TossCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "연료 타임라인",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
            )
            TimelineRow("출발", p.vehicle.currentFuelL, "현재 연료")
            TimelineRow("주유소 도착", best.fuelOnArrivalL, "주행 후 잔량")
            TimelineRow(
                "주유소 출발",
                best.fuelOnArrivalL + best.litersToBuy,
                "${formatLiters(best.litersToBuy)} 주유"
            )
            TimelineRow("목적지 도착", best.fuelAtDestinationL, "예상 잔량")
            Text(
                text = "가격 기준일: ${relativeTime(best.station.priceUpdatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimelineRow(label: String, liters: Double, note: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatLiters(liters),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun ItineraryCard(stops: List<ItineraryStop>) {
    TossCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "경유 일정 (${stops.size}곳)",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
            )
            stops.forEachIndexed { i, stop ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${i + 1}. ${stationHeading(stop.option.station)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = stop.fillReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${formatLiters(stop.litersToBuy)} · ${formatKrw(stop.outOfPocketKrw)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                if (i < stops.lastIndex) TossDivider()
            }
        }
    }
}

@Composable
private fun WarningsCard(p: RefuelPlan) {
    val all = p.best?.warnings.orEmpty() + p.options.drop(1).flatMap { it.warnings }.distinctBy { it.message }.take(5)
    if (all.isEmpty()) return
    TossCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "주의사항",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
            )
            all.forEach { w ->
                val color = when (w.severity) {
                    Severity.error -> MaterialTheme.colorScheme.error
                    Severity.warn -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = "• ${w.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun ExcludedCard(p: RefuelPlan) {
    TossCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "제외된 후보 ${p.excluded.size}곳",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
            )
            p.excluded.take(5).forEach { (station, reason) ->
                Text(
                    text = "• ${stationHeading(station)}: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetaCard(p: RefuelPlan) {
    TossCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "계산 정보",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = "가격 ${p.meta.stationProvider} · 경로 ${p.meta.routeProvider}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "후보 ${p.meta.candidateCount}곳 중 ${p.meta.exactlyEvaluated}곳 정밀 계산" +
                    if (p.meta.optimalityGuaranteed) " (최적 보장)" else " (일부 생략)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "계산 시각 ${relativeTime(p.meta.computedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CostRow(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun CostColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        )
    }
}

@Composable
fun OptionRowCard(option: RankedOption, onReportPrice: () -> Unit = {}) {
    TossCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stationHeading(option.station),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${option.station.address ?: "주소 미상"}" +
                            (if (option.station.isSelfService) " · 셀프" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatPerLiter(option.effectivePriceKrwPerL),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "절감 ${formatSignedKrw(option.savingKrw)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (option.savingKrw >= 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CostColumn("주유량", formatLiters(option.litersToBuy))
                CostColumn("실제 비용", formatKrw(option.outOfPocketKrw))
                CostColumn("우회", formatKm(option.detour.extraDistanceM / 1000))
                CostColumn("정규화 비용", formatKrw(option.normalizedCostKrw))
            }

            TextButton(onClick = onReportPrice) {
                Text(text = "가격 오류 제보", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
