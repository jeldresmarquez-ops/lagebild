package de.lagebild.app.data.local

import de.lagebild.app.domain.model.Article
import de.lagebild.app.domain.model.Category
import de.lagebild.app.domain.model.WarnLevel
import de.lagebild.app.domain.model.Warning

fun ArticleEntity.toDomain(isRead: Boolean = false, isBookmarked: Boolean = false) = Article(
    id = id,
    sourceId = sourceId,
    sourceName = sourceName,
    sourceColor = sourceColor,
    url = url,
    title = title,
    teaser = teaser,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
    category = Category.fromKey(category),
    isTop = isTop,
    isBreaking = isBreaking,
    language = language,
    titleDe = titleDe,
    teaserDe = teaserDe,
    isRead = isRead,
    isBookmarked = isBookmarked,
    paywall = paywall,
)

fun ArticleWithState.toDomain() = article.toDomain(isRead, isBookmarked)

fun Article.toEntity(now: Long) = ArticleEntity(
    id = id,
    sourceId = sourceId,
    sourceName = sourceName,
    sourceColor = sourceColor,
    url = url,
    title = title,
    teaser = teaser,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
    category = category?.name,
    isTop = isTop,
    isBreaking = isBreaking,
    language = language,
    paywall = paywall,
    titleDe = titleDe,
    teaserDe = teaserDe,
    firstSeenAt = now,
    fetchedAt = now,
)

fun WarningEntity.toDomain() = Warning(
    id = id,
    provider = provider,
    title = title,
    description = description,
    instruction = instruction,
    level = runCatching { WarnLevel.valueOf(level) }.getOrDefault(WarnLevel.MINOR),
    start = start,
    end = end,
    sentAt = sentAt,
)

fun Warning.toEntity() = WarningEntity(
    id = id,
    provider = provider,
    title = title,
    description = description,
    instruction = instruction,
    level = level.name,
    start = start,
    end = end,
    sentAt = sentAt,
)
