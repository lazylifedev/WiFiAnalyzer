package com.lazyapps.wifianalyzer.ui.operation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lazyapps.wifianalyzer.R
import java.text.DateFormat
import java.util.Date

@Composable
fun InlineProgress(running: OperationState.Running, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(running.titleRes), style = MaterialTheme.typography.titleMedium)
        running.messageRes?.let { Text(stringResource(it)) }
        when (val progress = running.progress) {
            OperationProgress.Indeterminate -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is OperationProgress.Count -> {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.operation_count_progress, progress.current, progress.total))
            }
            is OperationProgress.Percent ->
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            is OperationProgress.Stage -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(progress.messageRes))
            }
        }
        Text(stringResource(if (running.cancellable) R.string.operation_cancellable else R.string.operation_not_cancellable))
    }
}

@Composable
fun OperationProgressDialog(
    running: OperationState.Running,
    onCancelRequest: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(running.titleRes)) },
        text = { InlineProgress(running) },
        confirmButton = {
            if (running.cancellable && onCancelRequest != null) {
                TextButton(onClick = onCancelRequest, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
fun OperationErrorState(failure: OperationState.Failure, onRetry: (() -> Unit)? = null, onDetails: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(stringResource(failure.error.category.titleRes), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(failure.error.category.messageRes))
        Row {
            if (failure.error.retryable && onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            if (onDetails != null) TextButton(onClick = onDetails) { Text(stringResource(R.string.error_details)) }
        }
    }
}

@Composable
fun ErrorDetailDialog(error: OperationError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(error.category.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(error.category.messageRes))
                Text(stringResource(R.string.error_detail_code, error.detailCode))
                Text(stringResource(R.string.error_detail_operation, stringResource(error.operationRes)))
                Text(stringResource(R.string.error_detail_time, DateFormat.getDateTimeInstance().format(Date(error.occurredAtMillis))))
                Text(stringResource(if (error.retryable) R.string.error_detail_retryable else R.string.error_detail_not_retryable))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun OperationResultSnackbar(state: OperationState, hostState: SnackbarHostState, onConsumed: (Long) -> Unit) {
    val eventId = when (state) {
        is OperationState.Success -> state.eventId
        is OperationState.Failure -> state.eventId
        else -> null
    }
    val message = when (state) {
        is OperationState.Success -> stringResource(state.messageRes)
        is OperationState.Failure -> stringResource(state.error.category.messageRes)
        else -> ""
    }
    LaunchedEffect(eventId) {
        if (eventId == null) return@LaunchedEffect
        onConsumed(eventId)
        hostState.showSnackbar(message)
    }
}

@Composable
fun DestructiveConfirmationDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun CancelConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cancel_operation_title)) },
        text = { Text(stringResource(R.string.cancel_operation_message)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.cancel_operation_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.continue_operation)) } },
    )
}
