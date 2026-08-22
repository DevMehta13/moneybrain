package com.rajnikant.moneybrain.buckets

import java.time.YearMonth

/** Compatibility overload used by the architect's zero-line test; production supplies month explicitly. */
suspend fun BucketSplitter.splitSalary(
    sourceTransactionId: Long,
    salaryPaise: Long,
    plan: List<PlanEntry>,
    nowMillis: Long,
): SplitOutcome = splitSalary(sourceTransactionId, salaryPaise, YearMonth.now().toString(), plan, nowMillis)
