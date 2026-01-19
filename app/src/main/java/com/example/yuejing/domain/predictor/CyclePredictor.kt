package com.example.yuejing.domain.predictor

import com.example.yuejing.data.model.PeriodRecord
import com.example.yuejing.data.model.RecordType
import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.yuejing.utils.DeepSeekManager

class CyclePredictor(private val records: List<PeriodRecord>) {
    companion object {
        private const val TAG = "YueJingPredictor"
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 提取所有经期开始日期
     */
    fun extractPeriodStarts(): List<LocalDate> {
        Log.d(TAG, "extractPeriodStarts: total records=${records.size}")
        
        val periodRecords = records.filter { it.type == RecordType.PERIOD }
        Log.d(TAG, "found ${periodRecords.size} PERIOD type records")
        
        val validPeriodRecords = periodRecords.filter { 
            val hasStartDate = it.startDate != null && it.startDate.isNotBlank()
            if (!hasStartDate) {
                Log.d(TAG, "PERIOD record filtered out: startDate is null or blank")
            }
            hasStartDate
        }
        Log.d(TAG, "among them ${validPeriodRecords.size} have non-empty startDate")
        
        if (validPeriodRecords.isNotEmpty()) {
            validPeriodRecords.forEachIndexed { index, record ->
                Log.d(TAG, "PERIOD record[$index]: startDate='${record.startDate}', endDate='${record.endDate}', date='${record.date}'")
            }
        } else if (periodRecords.isNotEmpty()) {
            // 有PERIOD记录但没有有效的startDate
            periodRecords.forEachIndexed { index, record ->
                Log.w(TAG, "Invalid PERIOD record[$index]: startDate='${record.startDate}' (null or blank), endDate='${record.endDate}', date='${record.date}'")
            }
        }
        
        val result = validPeriodRecords.mapNotNull { record ->
            try {
                Log.d(TAG, "attempting to parse startDate: '${record.startDate}' with format yyyy-MM-dd")
                val date = LocalDate.parse(record.startDate, dateFormatter)
                Log.d(TAG, "successfully parsed period start date: $date")
                date
            } catch (e: Exception) {
                Log.w(TAG, "failed to parse period start date: '${record.startDate}', error: ${e.message}")
                null
            }
        }.sorted()
        
        Log.d(TAG, "final result: extracted ${result.size} valid period start dates")
        if (result.isNotEmpty()) {
            Log.d(TAG, "period start dates: ${result.joinToString(", ") { it.toString() }}")
        }
        return result
    }

    /**
     * 计算加权平均周期长度（最近的数据权重更高）
     */
    fun calculateWeightedAverageCycle(nRecent: Int = 6): Double {
        val periodStarts = extractPeriodStarts()
        if (periodStarts.size < 2) {
            return 28.0 // 默认周期
        }

        val cycleLengths = mutableListOf<Pair<Int, Int>>() // (周期长度, 索引)
        for (i in 1 until periodStarts.size) {
            val daysDiff = ChronoUnit.DAYS.between(periodStarts[i-1], periodStarts[i]).toInt()
            if (daysDiff in 20..45) { // 合理的周期范围
                cycleLengths.add(Pair(daysDiff, i))
            }
        }

        if (cycleLengths.isEmpty()) {
            return 28.0
        }

        // 计算权重：最近的数据权重更高
        val weights = mutableListOf<Double>()
        val values = mutableListOf<Int>()

        cycleLengths.takeLast(nRecent).forEach { (length, idx) ->
            val weight = (idx.toDouble() / periodStarts.size) * 2 + 0.5
            weights.add(weight)
            values.add(length)
        }

        // 加权平均
        val weightedSum = weights.zip(values).sumOf { (w, v) -> w * v }
        val totalWeight = weights.sum()

        return if (totalWeight > 0) weightedSum / totalWeight else 28.0
    }

    /**
     * 获取最近的记录日期（从任何类型的记录中）
     */
    private fun getLatestRecordDate(): LocalDate? {
        val dates = records.mapNotNull { record ->
            val dateStr = record.date
            if (dateStr != null) {
                try {
                    LocalDate.parse(dateStr, dateFormatter)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
        
        return dates.maxOrNull()
    }

    /**
     * 预测下一个经期
     * @return List<LocalDate> (下次经期开始, 下次经期结束, 排卵期, 易孕期开始, 易孕期结束)
     *         基于用户的实际数据进行预测，如果没有经期记录但其他记录，使用最近记录日期作为参考
     */
    fun predictNextPeriod(): List<LocalDate> {
        val periodStarts = extractPeriodStarts()
        Log.d(TAG, "预测计算：找到${periodStarts.size}个经期开始日期，总记录数：${records.size}")
        
        // 智能选择参考日期：优先使用经期开始日期，其次使用最近记录日期
        val referenceDate = if (periodStarts.isNotEmpty()) {
            Log.d(TAG, "使用最近经期日期作为参考点：${periodStarts.last()}")
            periodStarts.last()
        } else {
            // 尝试使用最近的其他记录日期
            val latestRecordDate = getLatestRecordDate()
            if (latestRecordDate != null) {
                Log.d(TAG, "无经期记录，使用最近其他记录日期作为参考点：$latestRecordDate")
                latestRecordDate
            } else {
                // 没有任何记录，无法进行有意义的预测
                Log.w(TAG, "没有任何有效记录，无法进行基于实际数据的预测")
                // 返回默认预测，但添加警告标记（UI层需要处理）
                LocalDate.now()
            }
        }
        
        val avgCycle = if (periodStarts.size >= 2) {
            calculateWeightedAverageCycle()
        } else {
            28.0 // 少于2条记录时使用默认周期
        }
        Log.d(TAG, "计算平均周期长度：$avgCycle 天")
        
        // 预测下一个经期开始日期
        val nextPeriodStart = referenceDate.plusDays(avgCycle.toLong())

        // 预测排卵期（基于黄体期通常为14天）
        val ovulationDate = nextPeriodStart.minusDays(14)

        // 预测易孕期（排卵期前后几天）
        val fertileStart = ovulationDate.minusDays(5)
        val fertileEnd = ovulationDate.plusDays(1)

        // 预测经期结束日期（基于历史平均经期长度）
        val avgPeriodLength = calculateAvgPeriodLength()
        val nextPeriodEnd = nextPeriodStart.plusDays((avgPeriodLength - 1).toLong())
        
        Log.d(TAG, "预测结果：下次经期 $nextPeriodStart - $nextPeriodEnd, 排卵期 $ovulationDate, 易孕期 $fertileStart - $fertileEnd")

        return listOf(nextPeriodStart, nextPeriodEnd, ovulationDate, fertileStart, fertileEnd)
    }

    /**
     * 计算平均经期长度
     */
    fun calculateAvgPeriodLength(): Int {
        val periodLengths = records
            .filter { it.type == RecordType.PERIOD && it.startDate != null && it.endDate != null }
            .mapNotNull { record ->
                try {
                    val start = LocalDate.parse(record.startDate, dateFormatter)
                    val end = LocalDate.parse(record.endDate, dateFormatter)
                    ChronoUnit.DAYS.between(start, end).toInt() + 1
                } catch (e: Exception) {
                    null
                }
            }
            .filter { it in 2..10 } // 合理的经期长度范围

        return if (periodLengths.isNotEmpty()) {
            periodLengths.average().toInt()
        } else {
            5 // 默认经期长度
        }
    }

    /**
     * 获取周期统计数据
     */
    fun getCycleStatistics(): Map<String, Any> {
        val periodStarts = extractPeriodStarts()
        if (periodStarts.size < 2) {
            return emptyMap()
        }

        // 计算周期长度
        val cycleLengths = mutableListOf<Int>()
        for (i in 1 until periodStarts.size) {
            val daysDiff = ChronoUnit.DAYS.between(periodStarts[i-1], periodStarts[i]).toInt()
            if (daysDiff in 20..45) {
                cycleLengths.add(daysDiff)
            }
        }

        if (cycleLengths.isEmpty()) {
            return emptyMap()
        }

        // 计算统计数据
        val stats = mutableMapOf<String, Any>()
        stats["avg_cycle"] = cycleLengths.average()
        stats["min_cycle"] = cycleLengths.minOrNull() ?: 0
        stats["max_cycle"] = cycleLengths.maxOrNull() ?: 0
        stats["std_cycle"] = calculateStandardDeviation(cycleLengths)
        stats["cycle_count"] = cycleLengths.size
        stats["cycle_lengths"] = cycleLengths
        stats["irregularity"] = calculateIrregularityScore(cycleLengths)

        return stats
    }

    /**
     * 计算周期不规律性评分（0-100，越高越不规律）
     */
    private fun calculateIrregularityScore(cycleLengths: List<Int>): Double {
        if (cycleLengths.size < 3) {
            return 0.0
        }

        // 计算相邻周期差异
        val diffs = mutableListOf<Int>()
        for (i in 1 until cycleLengths.size) {
            diffs.add(Math.abs(cycleLengths[i] - cycleLengths[i-1]))
        }

        val avgDiff = diffs.average()
        val maxPossibleDiff = 25.0 // 最大可能的周期差异

        // 将平均差异转换为0-100的评分
        return minOf(100.0, (avgDiff / maxPossibleDiff) * 100)
    }

    /**
     * 计算标准差
     */
    private fun calculateStandardDeviation(values: List<Int>): Double {
        if (values.size <= 1) return 0.0

        val mean = values.average()
        val squaredDifferences = values.map { Math.pow(it - mean, 2.0) }
        return Math.sqrt(squaredDifferences.average())
    }
    
    /**
     * 生成用于AI预测的周期数据字符串
     */
    private fun generateAIPredictionData(): String {
        val periodStarts = extractPeriodStarts()
        val stats = getCycleStatistics()
        val avgPeriodLength = calculateAvgPeriodLength()
        
        val sb = StringBuilder()
        
        sb.append("你是一位专业的女性健康AI助手，擅长月经周期预测。")
        sb.append("请基于以下详细的月经周期数据，进行科学准确的预测。\n\n")
        
        // 添加基本信息
        sb.append("【基本信息】\n")
        sb.append("当前日期：${LocalDate.now().format(dateFormatter)}\n")
        sb.append("历史记录数量：${records.size}条\n")
        sb.append("经期记录数量：${records.filter { it.type == RecordType.PERIOD }.size}条\n\n")
        
        // 确保有足够的数据，如果没有经期记录，添加默认信息
        if (periodStarts.isEmpty()) {
            sb.append("【经期记录】\n")
            sb.append("用户目前没有完整的经期记录，")
            sb.append("请使用默认的28天周期进行预测。\n\n")
        } else {
            // 添加完整的经期记录
            sb.append("【经期记录详情】\n")
            periodStarts.forEachIndexed { index, startDate ->
                // 查找对应的经期记录
                val periodRecord = records.find { 
                    it.type == RecordType.PERIOD && 
                    it.startDate != null && 
                    it.startDate == startDate.format(dateFormatter) 
                }
                
                val endDateStr = periodRecord?.endDate ?: "未知"
                sb.append("周期${index + 1}：开始=${startDate.format(dateFormatter)}，结束=${endDateStr}\n")
            }
            sb.append("\n")
            
            // 添加周期统计数据
            if (stats.isNotEmpty()) {
                sb.append("【周期统计数据】\n")
                sb.append("平均周期长度：${stats["avg_cycle"]}天\n")
                sb.append("最短周期：${stats["min_cycle"]}天\n")
                sb.append("最长周期：${stats["max_cycle"]}天\n")
                val irregularity = stats["irregularity"] as Double
                sb.append("周期规律性：${(100 - irregularity.toInt())}%\n")
            }
            
            // 添加平均经期长度
            sb.append("平均经期持续时间：${avgPeriodLength}天\n\n")
        }
        
        // 添加预测要求
        sb.append("【预测要求】\n")
        sb.append("1. 请根据历史数据预测未来3个月的月经周期\n")
        sb.append("2. 预测应考虑周期的规律性和变化趋势\n")
        sb.append("3. 请提供科学依据，说明预测的准确性和可能的误差范围\n")
        sb.append("4. 请严格按照以下格式返回结果，不要添加其他内容\n\n")
        
        // 明确要求AI返回具体的日期格式
        sb.append("【输出格式】\n")
        sb.append("下次月经开始日期：YYYY-MM-DD\n")
        sb.append("下次月经结束日期：YYYY-MM-DD\n")
        sb.append("排卵期：YYYY-MM-DD\n")
        sb.append("易孕期开始：YYYY-MM-DD\n")
        sb.append("易孕期结束：YYYY-MM-DD\n")
        
        Log.d(TAG, "AI预测输入数据：$sb")
        
        return sb.toString()
    }
    
    /**
     * AI辅助周期预测
     */
    suspend fun predictCycleWithAI(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val deepSeekManager = DeepSeekManager(context)
                val aiPredictionData = generateAIPredictionData()
                deepSeekManager.predictCycle(aiPredictionData)
            } catch (e: Exception) {
                Log.e(TAG, "AI预测失败: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * AI预测结果解析为日期列表
     * @param aiPredictionResult AI预测结果文本
     * @return List<LocalDate> (下次经期开始, 下次经期结束, 排卵期, 易孕期开始, 易孕期结束)
     */
    fun parseAIPredictionResult(aiPredictionResult: String): List<LocalDate>? {
        try {
            Log.d(TAG, "开始解析AI预测结果: $aiPredictionResult")
            
            // 从AI预测结果中提取所有日期
            val dateRegex = Regex("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})[-/日]?")
            val matches = dateRegex.findAll(aiPredictionResult)
            val dates = matches.map { matchResult ->
                val year = matchResult.groupValues[1].toInt()
                val month = matchResult.groupValues[2].toInt()
                val day = matchResult.groupValues[3].toInt()
                LocalDate.of(year, month, day)
            }.toList()
            
            Log.d(TAG, "解析到${dates.size}个日期: $dates")
            
            // 如果有5个或以上日期，按预期顺序返回
            if (dates.size >= 5) {
                // 按时间顺序排序
                val sortedDates = dates.sorted()
                Log.d(TAG, "排序后的日期: $sortedDates")
                
                // 确保返回的顺序符合预期：(下次经期开始, 下次经期结束, 排卵期, 易孕期开始, 易孕期结束)
                // 易孕期通常在排卵期前后，排卵期通常在下次经期前14天
                // 我们需要根据日期的逻辑关系来确定每个日期的类型
                
                // 查找排卵期（通常是第3个日期或最接近中间的日期）
                val ovulationDate = sortedDates[2] // 假设第3个日期是排卵期
                
                // 易孕期开始通常在排卵期前5天
                val fertileStart = ovulationDate.minusDays(5)
                
                // 易孕期结束通常在排卵期后1天
                val fertileEnd = ovulationDate.plusDays(1)
                
                // 下次经期开始通常在排卵期后14天
                val periodStart = ovulationDate.plusDays(14)
                
                // 下次经期结束通常在经期开始后4-5天
                val periodEnd = periodStart.plusDays(4)
                
                val result = listOf(periodStart, periodEnd, ovulationDate, fertileStart, fertileEnd)
                Log.d(TAG, "最终AI预测结果顺序: $result")
                return result
            }
            
            // 如果日期不足5个，使用传统预测作为备选
            Log.w(TAG, "AI返回日期不足5个，使用传统预测作为备选")
            return predictNextPeriod()
        } catch (e: Exception) {
            Log.e(TAG, "解析AI预测结果失败: ${e.message}", e)
            // 解析失败时使用传统预测作为备选
            return predictNextPeriod()
        }
    }
    
    /**
     * 获取AI健康建议
     */
    suspend fun getAIHealthAdvice(context: Context, cyclePhase: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val deepSeekManager = DeepSeekManager(context)
                val aiPredictionData = generateAIPredictionData()
                deepSeekManager.getHealthAdvice(aiPredictionData, cyclePhase)
            } catch (e: Exception) {
                Log.e(TAG, "AI健康建议获取失败: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * 获取AI症状分析
     */
    suspend fun getAISymptomAnalysis(context: Context, symptoms: String, cyclePhase: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val deepSeekManager = DeepSeekManager(context)
                deepSeekManager.analyzeSymptoms(symptoms, cyclePhase)
            } catch (e: Exception) {
                Log.e(TAG, "AI症状分析失败: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * 获取AI统计分析
     */
    suspend fun getAIStatsAnalysis(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                val deepSeekManager = DeepSeekManager(context)
                val aiPredictionData = generateAIPredictionData()
                deepSeekManager.getStatsAnalysis(aiPredictionData)
            } catch (e: Exception) {
                Log.e(TAG, "AI统计分析失败: ${e.message}", e)
                null
            }
        }
    }
}
