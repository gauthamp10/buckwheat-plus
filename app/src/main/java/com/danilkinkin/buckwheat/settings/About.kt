package com.danilkinkin.buckwheat.settings

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import com.danilkinkin.buckwheat.base.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.DescriptionButton
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.PathState
import com.danilkinkin.buckwheat.ui.BuckwheatTheme
import com.danilkinkin.buckwheat.util.openInBrowser

const val PROJECT_URL = "https://github.com/gauthamp10/buckwheat-plus"

@Composable
private fun ProfileLink(
    label: String,
    handle: String,
    url: String,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current

    val annotatedString = buildAnnotatedString {
        withStyle(style = SpanStyle(color = contentColor)) {
            append("$label ")
        }

        pushStringAnnotation(tag = "profile", annotation = url)
        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
            append("$handle ")
        }

        appendInlineContent("openInBrowser")

        pop()
    }

    ClickableText(
        text = annotatedString,
        inlineContent = mapOf(
            "openInBrowser" to InlineTextContent(
                Placeholder(
                    MaterialTheme.typography.bodyLarge.fontSize,
                    MaterialTheme.typography.bodyLarge.fontSize,
                    PlaceholderVerticalAlign.TextCenter,
                )
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.primary,
                    painter = painterResource(R.drawable.ic_open_in_browser_small),
                    contentDescription = null,
                )
            }
        ),
        style = MaterialTheme.typography.bodyLarge,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "profile", start = offset, end = offset)
                .firstOrNull()
                ?.let { openInBrowser(context, it.item) }
        },
    )
}

@Composable
fun About(
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        val contentColor = LocalContentColor.current

        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.fork_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileLink(
                label = stringResource(R.string.developer),
                handle = "@danilkinkin",
                url = "https://danilkinkin.com",
                contentColor = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ProfileLink(
                label = stringResource(R.string.fork_developer),
                handle = "@gauthamp10",
                url = PROJECT_URL,
                contentColor = contentColor,
            )
            Spacer(modifier = Modifier.height(24.dp))
            DescriptionButton(
                title = { Text(stringResource(R.string.contribute)) },
                icon = painterResource(R.drawable.ic_contribute),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                    end = 12.dp,
                ),
                onClick = {
                    val currentLocale = configuration.locales[0].language

                    openInBrowser(
                        context,
                        if (currentLocale === "ru") {
                            "https://buckwheat.app/ru/contribute"
                        } else {
                            "https://buckwheat.app/contribute"
                        },
                    )
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            DescriptionButton(
                title = { Text(stringResource(R.string.report_bug)) },
                icon = painterResource(R.drawable.ic_bug_report),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                    end = 12.dp,
                ),
                onClick = {
                    appViewModel.openSheet(PathState(BUG_REPORTER_SHEET))
                },
            )
        }
    }
}

@Preview(name = "Default")
@Composable
private fun PreviewDefault() {
    BuckwheatTheme {
        About()
    }
}

@Preview(name = "Night mode", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNightMode() {
    BuckwheatTheme {
        About()
    }
}