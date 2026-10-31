package com.danilkinkin.buckwheat.cards

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.LocalBottomSheetScrollState
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.util.numberFormat
import com.danilkinkin.buckwheat.util.prettyDate

const val CARD_SPENDS_SHEET = "cardSpends"

@Composable
fun CardSpends(
    cardSpendsViewModel: CardSpendsViewModel = hiltViewModel(),
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
) {
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val groups by cardSpendsViewModel.groups.observeAsState(emptyList())
    val currency by spendsViewModel.currency.observeAsState(ExtendCurrency.none())
    val navigationBarHeight = LocalWindowInsets.current.calculateBottomPadding().coerceAtLeast(16.dp)

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onClose() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.weight(1F))
                Text(
                    text = stringResource(R.string.card_spends_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1F))
                Spacer(Modifier.width(48.dp))
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = navigationBarHeight),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (groups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.card_spends_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.card_spends_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                groups.forEach { group ->
                    CardGroupItem(group = group, currency = currency)
                }
            }
        }
    }
}

@Composable
private fun CardGroupItem(group: CardGroup, currency: ExtendCurrency) {
    val context = LocalContext.current
    var expanded by rememberSaveable(group.cardLast4) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "cardArrowRotation")

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (group.cardLast4 != null) R.drawable.ic_credit_card else R.drawable.ic_balance_wallet
                    ),
                    contentDescription = null,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.cardLast4
                        ?.let { stringResource(R.string.card_spends_card, it) }
                        ?: stringResource(R.string.card_spends_no_card),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = listOfNotNull(
                        group.sources.joinToString(", ").ifEmpty {
                            stringResource(R.string.card_spends_source_manual)
                        },
                        pluralStringResource(R.plurals.card_spends_count, group.spends.size, group.spends.size),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = numberFormat(context, group.total, currency = currency),
                style = MaterialTheme.typography.titleMedium,
                softWrap = false,
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .rotate(arrowRotation),
            )
        }

        if (expanded) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = LocalContentColor.current.copy(alpha = 0.1f),
            )
            Column(Modifier.padding(vertical = 8.dp)) {
                group.spends.forEach { spend ->
                    CardSpendRow(spend = spend, currency = currency)
                }
            }
        }
    }
}

@Composable
private fun CardSpendRow(spend: Transaction, currency: ExtendCurrency) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = LocalContentColor.current.copy(alpha = 0.3f),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .size(6.dp),
        ) {}
        Column(Modifier.weight(1f)) {
            Text(
                text = spend.comment.ifEmpty { stringResource(R.string.card_spends_no_comment) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = prettyDate(spend.date, shortMonth = true),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = numberFormat(context, spend.value, currency = currency),
            style = MaterialTheme.typography.bodyMedium,
            softWrap = false,
        )
    }
}
