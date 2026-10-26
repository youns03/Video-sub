package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.SherpaWhisperBackend
import com.example.service.ArabicTranslator
import com.example.ui.MainViewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PipelineScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryBlue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SherpaWhisperBackend.install()
        ArabicTranslator.initialize(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SubVidMainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubVidMainApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val currentProject by viewModel.currentProject.collectAsState()
    val savedProjects by viewModel.savedProjects.collectAsState()
    val inputUrl by viewModel.inputUrl.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isMergingHardsub by viewModel.isMergingHardsub.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()

    // Handle notices
    LaunchedEffect(statusNotice) {
        statusNotice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }
    }

    // BackHandler: Navigate back to Home tab if on sub-screens
    BackHandler(enabled = selectedTab != 0) {
        viewModel.setSelectedTab(0)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "مترجم الفيديوهات (FR ➔ AR)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("app_top_bar")
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        icon = {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "المعالجة",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("المعالجة", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("tab_pipeline")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        icon = {
                            Icon(
                                Icons.Default.Subtitles,
                                contentDescription = "المحرر والمعاينة",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("المحرر والمعاينة", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldGreen,
                            selectedTextColor = EmeraldGreen,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("tab_editor")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.setSelectedTab(2) },
                        icon = {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "السجل",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("السجل", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("tab_history")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { viewModel.setSelectedTab(3) },
                        icon = {
                            Icon(
                                Icons.Default.Article,
                                contentDescription = "الإعدادات",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("الإعدادات", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("tab_report")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    label = "tab_animation"
                ) { tab ->
                    when (tab) {
                        0 -> PipelineScreen(
                            currentProject = currentProject,
                            inputUrl = inputUrl,
                            onUrlChange = { viewModel.setInputUrl(it) },
                            onStartPipeline = { url, preset ->
                                viewModel.startPipeline(url, preset)
                            },
                            onRetryPipeline = { viewModel.retryPipeline() },
                            onRetryTranslation = { viewModel.retryTranslation() },
                            onNavigateToEditor = { viewModel.setSelectedTab(1) },
                            onDownloadSrt = { viewModel.downloadSrtFile() },
                            onDownload360pVideo = { viewModel.download360pVideo() },
                            onMergeHardsub = { viewModel.mergeHardsubAndExport() },
                            onShareVideo = { viewModel.shareVideoFile() },
                            onShareSrt = { viewModel.shareSrtFile() },
                            isMergingHardsub = isMergingHardsub
                        )

                        1 -> EditorScreen(
                            project = currentProject,
                            playbackPositionMs = playbackPositionMs,
                            isPlaying = isPlaying,
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onSeekTo = { viewModel.seekTo(it) },
                            onPlaybackPositionChanged = { viewModel.updatePlaybackPosition(it) },
                            onPlaybackEnded = { viewModel.onPlaybackEnded() },
                            onUpdateSegment = { viewModel.updateSegment(it) },
                            onDeleteSegment = { viewModel.deleteSegment(it) },
                            onAddNewSegment = { fr, ar, start, end ->
                                viewModel.addNewSegment(fr, ar, start, end)
                            },
                            onRetranslateAll = { viewModel.retranslateAllSegments() },
                            onDownload360pVideo = { viewModel.download360pVideo() },
                            onMergeHardsub = { viewModel.mergeHardsubAndExport() }
                        )

                        2 -> HistoryScreen(
                            projects = savedProjects,
                            onOpenProject = { viewModel.loadProjectIntoEditor(it) },
                            onDeleteProject = { viewModel.deleteProject(it) }
                        )

                        3 -> SettingsScreen()
                    }
                }
            }
        }
    }
}
