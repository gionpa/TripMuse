package com.tripmuse.ui.recommendation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tripmuse.data.model.RecommendationItem
import com.tripmuse.data.model.RecommendationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    onCreateAlbumClick: () -> Unit
) {
    // Placeholder state - will be connected to ViewModel later
    val recommendations = remember { emptyList<RecommendationItem>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("스마트 추천") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (recommendations.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "새로운 추천이 없습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "사진을 더 찍으면 여행을 자동으로 감지해드려요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recommendations) { recommendation ->
                        RecommendationCard(
                            recommendation = recommendation,
                            onAcceptClick = { /* TODO */ },
                            onDismissClick = { /* TODO */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(
    recommendation: RecommendationItem,
    onAcceptClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (recommendation.type) {
                    RecommendationType.NEW_TRIP -> {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "새로운 여행을 발견했어요!",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    RecommendationType.ADD_TO_EXISTING -> {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "\"${recommendation.targetAlbumTitle}\" 앨범에 추가할 사진을 발견했어요!",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Location and date info
            if (recommendation.location != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = recommendation.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (recommendation.startDate != null) {
                Text(
                    text = buildString {
                        append(recommendation.startDate)
                        recommendation.endDate?.let { append(" ~ $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preview thumbnails
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(recommendation.previewFilenames.take(3)) { filename ->
                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.small),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        // Placeholder for preview images
                    }
                }
                if (recommendation.mediaCount > 3) {
                    item {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+${recommendation.mediaCount - 3}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismissClick) {
                    Text("무시하기")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onAcceptClick) {
                    Text(
                        when (recommendation.type) {
                            RecommendationType.NEW_TRIP -> "앨범 만들기"
                            RecommendationType.ADD_TO_EXISTING -> "추가하기"
                        }
                    )
                }
            }
        }
    }
}
