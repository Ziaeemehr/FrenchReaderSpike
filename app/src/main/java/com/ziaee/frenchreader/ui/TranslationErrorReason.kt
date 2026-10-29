package com.ziaee.frenchreader.ui

import androidx.annotation.StringRes
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.translate.llm.LlmError

@StringRes
internal fun translationErrorReason(kind: LlmError.Kind?): Int = when (kind) {
    LlmError.Kind.MISSING_KEY -> R.string.translation_error_missing_key
    LlmError.Kind.UNAUTHORIZED -> R.string.translation_error_key
    LlmError.Kind.PAYMENT -> R.string.translation_error_payment
    LlmError.Kind.FORBIDDEN -> R.string.translation_error_forbidden
    LlmError.Kind.NOT_FOUND -> R.string.translation_error_model
    LlmError.Kind.RATE_LIMIT -> R.string.translation_error_rate
    LlmError.Kind.UNREACHABLE -> R.string.translation_error_unreachable
    else -> R.string.translation_error_response
}
