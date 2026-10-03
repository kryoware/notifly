package ph.notifly.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import notifly.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import ph.notifly.domain.model.Account
import ph.notifly.domain.model.AccountType

/** Licensed Material Symbols fallback until a brand grants permission for its logo. */
@Composable
internal fun AccountSymbol(account: Account) {
    Icon(painterResource(when (account.type) {
        AccountType.BANK -> Res.drawable.symbol_account_balance
        AccountType.CARD -> Res.drawable.symbol_credit_card
        AccountType.WALLET -> Res.drawable.symbol_account_balance_wallet
    }), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
