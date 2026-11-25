package de.lagebild.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.DisruptionKind
import de.lagebild.app.domain.model.StoryGroup
import de.lagebild.app.domain.model.TransitDisruption
import de.lagebild.app.domain.model.Warning
import de.lagebild.app.ui.theme.onWarnColor
import de.lagebild.app.ui.theme.parseHexColor
import de.lagebild.app.ui.theme.warnColor
import de.lagebild.app.ui.util.formatArticleTime
import de.lagebild.app.ui.util.formatDateTime

data class ArticleActions(
    val open: (Article) -> Unit,
    val bookmark: (Article) -> Unit,
    val share: (Article) -> Unit,
)

/** Quelle (farbiger Punkt + Name) und Uhrzeit – bei jedem Artikel gut sichtbar. */
@Composable
fun SourceLine(article: Article, modifier: Modifier = Modifier) {
    val color = parseHexColor(article.sourceColor, MaterialTheme.colorScheme.primary)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            article.sourceName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "  ·  ${formatArticleTime(article.publishedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (article.isBreaking) {
            Spacer(Modifier.width(8.dp))
            Text(
                "EILMELDUNG",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (article.paywall) {
            Text("  ·  Bezahlinhalt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StoryCard(
    group: StoryGroup,
    actions: ArticleActions,
    large: Boolean = false,
) {
    val article = group.lead
    var showOriginal by rememberSaveable(article.id) { mutableStateOf(false) }
    var expanded by rememberSaveable(article.id + "_x") { mutableStateOf(false) }
    val title = if (showOriginal) article.title else article.displayTitle
    val teaser = if (showOriginal) article.teaser else article.displayTeaser
    val dim = if (article.isRead) 0.62f else 1f

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { actions.open(article) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (large && article.imageUrl != null) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(10.dp))
        }
        SourceLine(article)
        Spacer(Modifier.height(6.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!teaser.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        teaser,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                        maxLines = if (large) 4 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!large && article.imageUrl != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(86.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (article.isTranslated) {
                TextButton(onClick = { showOriginal = !showOriginal }) {
                    Icon(Icons.Outlined.Translate, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showOriginal) "Übersetzung" else "Original", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (group.related.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text("+${group.related.size} weitere Quelle${if (group.related.size > 1) "n" else ""}", style = MaterialTheme.typography.labelMedium)
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { actions.share(article) }) {
                Icon(Icons.Outlined.Share, "Teilen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { actions.bookmark(article) }) {
                Icon(
                    if (article.isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (article.isBookmarked) "Lesezeichen entfernen" else "Lesezeichen setzen",
                    tint = if (article.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                group.related.forEach { rel ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { actions.open(rel) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        SourceLine(rel)
                        Text(rel.displayTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun OfflineBanner(visible: Boolean, lastSync: Long) {
    AnimatedVisibility(visible) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Offline – du siehst gespeicherte Meldungen" + if (lastSync > 0) " (Stand ${formatDateTime(lastSync)})" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun WarningCard(warning: Warning) {
    var expanded by rememberSaveable(warning.id) { mutableStateOf(warning.level.isHighPriority) }
    val bg = warnColor(warning.level)
    val fg = onWarnColor(warning.level)
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${warning.level.label} · ${warning.provider}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(warning.title, style = MaterialTheme.typography.titleMedium)
            val period = listOfNotNull(warning.start?.let { "ab ${formatDateTime(it)}" }, warning.end?.let { "bis ${formatDateTime(it)}" })
            if (period.isNotEmpty()) Text(period.joinToString(" "), style = MaterialTheme.typography.bodySmall)
            AnimatedVisibility(expanded) {
                Column {
                    warning.description?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
                    warning.instruction?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("Was tun?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DisruptionRow(d: TransitDisruption) {
    var expanded by rememberSaveable(d.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = d.text != null) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            d.lines.take(4).forEach { LineBadge(it) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                when (d.kind) {
                    DisruptionKind.WARNING -> "Störungsmeldung"
                    DisruptionKind.CANCELLATION -> "Ausfall"
                    DisruptionKind.DELAY -> "Verspätung"
                } + " · " + d.stationName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(d.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (d.text != null) {
                Text(
                    d.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun LineBadge(line: String) {
    val color = when {
        line.startsWith("S") -> Color(0xFF008D4F)
        line.startsWith("U") -> Color(0xFF115D91)
        line.startsWith("M") || line.matches(Regex("\\d{2}")) -> Color(0xFFBE1414)
        line.startsWith("RE") || line.startsWith("RB") -> Color(0xFFE2001A)
        else -> Color(0xFF993399)
    }
    Text(
        line,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
