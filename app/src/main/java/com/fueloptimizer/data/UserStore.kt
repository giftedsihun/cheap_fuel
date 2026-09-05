package com.fueloptimizer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fueloptimizer.domain.Brand
import com.fueloptimizer.domain.DiscountRule
import com.fueloptimizer.domain.FillPolicy
import com.fueloptimizer.domain.FillPolicyMode
import com.fueloptimizer.domain.FillRecord
import com.fueloptimizer.domain.FuelKind
import com.fueloptimizer.domain.Preferences
import com.fueloptimizer.domain.ReportKind
import com.fueloptimizer.domain.StationReport
import com.fueloptimizer.domain.Vehicle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userDataStore by preferencesDataStore(name = "gas_smart_prefs")

private object Keys {
    val VEHICLE = stringPreferencesKey("vehicle")
    val PREFERENCES = stringPreferencesKey("preferences")
    val FILLS = stringPreferencesKey("fills")
    val REPORTS = stringPreferencesKey("reports")
    val DEPART_AT = stringPreferencesKey("depart_at")
}

private val gson = Gson()

data class StoredVehicle(
    val fuelKind: String = "gasoline",
    val kmPerLiter: Double = 12.0,
    val tankCapacityL: Double = 50.0,
    val currentFuelL: Double = 20.0,
    val reserveL: Double = 5.0
)

data class StoredPreferences(
    val timeValueKrwPerMin: Double = 0.0,
    val cardDiscountKrwPerL: Double = 0.0,
    val extraDiscountRate: Double = 0.0,
    val maxDetourKm: Double = 5.0,
    val maxDetourMin: Double = 30.0,
    val fillPolicyMode: String = "toDestination",
    val fillLiters: Double? = null,
    val fillKrw: Double? = null,
    val minMeaningfulSavingKrw: Double = 1000.0,
    val selfServiceOnly: Boolean = false,
    val brands: List<String> = emptyList(),
    val avoidHighwayExit: Boolean = false,
    val discountRules: List<StoredDiscountRule> = emptyList()
)

data class StoredDiscountRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val flatKrwPerL: Double = 0.0,
    val rate: Double = 0.0,
    val brands: List<String> = emptyList()
)

fun StoredVehicle.toDomain() = Vehicle(
    fuelKind = runCatching { FuelKind.valueOf(fuelKind) }.getOrDefault(FuelKind.gasoline),
    kmPerLiter = kmPerLiter,
    tankCapacityL = tankCapacityL,
    currentFuelL = currentFuelL,
    reserveL = reserveL
)

fun Vehicle.toStored() = StoredVehicle(
    fuelKind = fuelKind.name,
    kmPerLiter = kmPerLiter,
    tankCapacityL = tankCapacityL,
    currentFuelL = currentFuelL,
    reserveL = reserveL
)

fun StoredPreferences.toDomain() = Preferences(
    timeValueKrwPerMin = timeValueKrwPerMin,
    cardDiscountKrwPerL = cardDiscountKrwPerL,
    extraDiscountRate = extraDiscountRate,
    maxDetourKm = maxDetourKm,
    maxDetourMin = maxDetourMin,
    fillPolicy = FillPolicy(
        mode = runCatching { FillPolicyMode.valueOf(fillPolicyMode) }.getOrDefault(FillPolicyMode.toDestination),
        liters = fillLiters,
        krw = fillKrw
    ),
    minMeaningfulSavingKrw = minMeaningfulSavingKrw,
    selfServiceOnly = selfServiceOnly,
    brands = brands.mapNotNull { runCatching { Brand.valueOf(it) }.getOrNull() },
    avoidHighwayExit = avoidHighwayExit,
    discountRules = discountRules.map {
        DiscountRule(
            id = it.id,
            name = it.name,
            enabled = it.enabled,
            flatKrwPerL = it.flatKrwPerL,
            rate = it.rate,
            brands = it.brands.mapNotNull { b -> runCatching { Brand.valueOf(b) }.getOrNull() }
        )
    }
)

fun Preferences.toStored() = StoredPreferences(
    timeValueKrwPerMin = timeValueKrwPerMin,
    cardDiscountKrwPerL = cardDiscountKrwPerL,
    extraDiscountRate = extraDiscountRate,
    maxDetourKm = maxDetourKm,
    maxDetourMin = maxDetourMin,
    fillPolicyMode = fillPolicy.mode.name,
    fillLiters = fillPolicy.liters,
    fillKrw = fillPolicy.krw,
    minMeaningfulSavingKrw = minMeaningfulSavingKrw,
    selfServiceOnly = selfServiceOnly,
    brands = brands.map { it.name },
    avoidHighwayExit = avoidHighwayExit,
    discountRules = discountRules.map {
        StoredDiscountRule(
            id = it.id,
            name = it.name,
            enabled = it.enabled,
            flatKrwPerL = it.flatKrwPerL,
            rate = it.rate,
            brands = it.brands.map { b -> b.name }
        )
    }
)

class UserStore(private val context: Context) {
    val vehicleFlow: Flow<Vehicle> = context.userDataStore.data.map { prefs ->
        prefs[Keys.VEHICLE]?.let {
            runCatching { gson.fromJson(it, StoredVehicle::class.java).toDomain() }.getOrNull()
        } ?: StoredVehicle().toDomain()
    }

    val preferencesFlow: Flow<Preferences> = context.userDataStore.data.map { prefs ->
        prefs[Keys.PREFERENCES]?.let {
            runCatching { gson.fromJson(it, StoredPreferences::class.java).toDomain() }.getOrNull()
        } ?: StoredPreferences().toDomain()
    }

    val fillsFlow: Flow<List<FillRecord>> = context.userDataStore.data.map { prefs ->
        prefs[Keys.FILLS]?.let {
            runCatching {
                gson.fromJson<List<FillRecord>>(it, object : TypeToken<List<FillRecord>>() {}.type)
            }.getOrNull()
        } ?: emptyList()
    }

    val reportsFlow: Flow<List<StationReport>> = context.userDataStore.data.map { prefs ->
        prefs[Keys.REPORTS]?.let {
            runCatching {
                gson.fromJson<List<StationReport>>(it, object : TypeToken<List<StationReport>>() {}.type)
            }.getOrNull()
        } ?: emptyList()
    }

    val departAtFlow: Flow<Long?> = context.userDataStore.data.map { prefs ->
        prefs[Keys.DEPART_AT]?.toLongOrNull()
    }

    suspend fun saveVehicle(vehicle: Vehicle) {
        context.userDataStore.edit { it[Keys.VEHICLE] = gson.toJson(vehicle.toStored()) }
    }

    suspend fun savePreferences(prefs: Preferences) {
        context.userDataStore.edit { it[Keys.PREFERENCES] = gson.toJson(prefs.toStored()) }
    }

    suspend fun addFill(record: FillRecord) {
        val current = fillsFlow.first().toMutableList()
        current.add(0, record)
        while (current.size > 40) current.removeAt(current.size - 1)
        context.userDataStore.edit { it[Keys.FILLS] = gson.toJson(current) }
    }

    suspend fun addReport(report: StationReport) {
        val current = reportsFlow.first().toMutableList()
        val withId = if (report.id.isBlank()) report.copy(id = UUID.randomUUID().toString()) else report
        current.add(0, withId)
        while (current.size > 80) current.removeAt(current.size - 1)
        context.userDataStore.edit { it[Keys.REPORTS] = gson.toJson(current) }
    }

    suspend fun saveDepartAt(epochMs: Long?) {
        context.userDataStore.edit {
            if (epochMs == null) it.remove(Keys.DEPART_AT)
            else it[Keys.DEPART_AT] = epochMs.toString()
        }
    }

    suspend fun newReportId(): String = UUID.randomUUID().toString()
}
