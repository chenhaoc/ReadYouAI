package me.ash.reader.domain.model.article

import java.util.Date

data class ArticleDateBucketRow(
    val date: Date,
    val articleCount: Int,
)

data class ArticleDateJumpItem(
    val date: Date,
    val articleCount: Int,
    val articleOffset: Int,
)
