package com.ziaee.frenchreader.ui.statistics

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.text.NumberFormat
import java.util.Locale

internal fun chartNumber(value: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(value)

internal fun chartDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("d/M", locale).withDecimalStyle(DecimalStyle.of(locale)))

internal fun chartNumber(value: Float, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value)
