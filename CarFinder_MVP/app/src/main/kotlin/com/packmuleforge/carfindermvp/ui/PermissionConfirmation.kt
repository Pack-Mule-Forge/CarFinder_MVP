package com.packmuleforge.carfindermvp.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.packmuleforge.carfindermvp.R
import com.packmuleforge.carfindermvp.shared.platform.Capability

/**
 * The FR-056 confirmation after a required permission is denied. "Close" confirms; "Allow", back and a tap outside
 * all dismiss, which asks for the permission again.
 *
 * @requirement FR-056, QR-012
 */
@Composable
fun PermissionConfirmation(capability: Capability, onConfirmed: () -> Unit, onDismissed: () -> Unit) {
    val message = when (capability) {
        Capability.NOTIFICATIONS -> R.string.permission_required_notifications
        else -> R.string.permission_required_location
    }
    AlertDialog(
        onDismissRequest = onDismissed,
        text = { Text(stringResource(message), modifier = Modifier.testTag(UiTags.PERMISSION_REQUIRED)) },
        confirmButton = {
            TextButton(onClick = onConfirmed, modifier = Modifier.testTag(UiTags.PERMISSION_REQUIRED_CLOSE)) {
                Text(stringResource(R.string.permission_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissed, modifier = Modifier.testTag(UiTags.PERMISSION_REQUIRED_ALLOW)) {
                Text(stringResource(R.string.permission_allow))
            }
        },
    )
}
