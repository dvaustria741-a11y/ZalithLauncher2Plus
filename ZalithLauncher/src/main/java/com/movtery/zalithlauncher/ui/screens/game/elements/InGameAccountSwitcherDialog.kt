/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.game.elements

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.ui.screens.content.elements.PlayerFace
import com.movtery.zalithlauncher.ui.theme.cardColor
import com.movtery.zalithlauncher.ui.theme.onCardColor

/**
 * In-game account switcher dialog.
 * Lets the user pick any loaded account as the active one without leaving the game.
 * The switch is persisted immediately via [AccountsManager.setCurrentAccount] and
 * takes effect on the next game launch (the current JVM session keeps its token).
 */
@Composable
fun InGameAccountSwitcherDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val accounts by AccountsManager.accountsFlow.collectAsState()
    val current  by AccountsManager.currentAccountFlow.collectAsState()

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = cardColor(false),
            contentColor = onCardColor(),
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.game_menu_account_switcher_title),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(accounts) { account ->
                        AccountRow(
                            account  = account,
                            isCurrent = account.uniqueUUID == current?.uniqueUUID,
                            onClick  = {
                                if (account.uniqueUUID != current?.uniqueUUID) {
                                    AccountsManager.setCurrentAccount(account)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.game_menu_account_switched, account.username),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerFace(account = account, avatarSize = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.username, style = MaterialTheme.typography.bodyMedium)
            Text(account.accountType.orEmpty(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
        }
        if (isCurrent) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.game_menu_account_current),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
