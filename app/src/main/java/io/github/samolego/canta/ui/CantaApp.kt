package io.github.samolego.canta.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.samolego.canta.R
import io.github.samolego.canta.util.optionalTextArgument
import io.github.samolego.canta.extension.addAll
import io.github.samolego.canta.extension.showFor
import io.github.samolego.canta.ui.component.AppList
import io.github.samolego.canta.ui.component.CantaTopBar
import io.github.samolego.canta.ui.component.fab.PresetEditFAB
import io.github.samolego.canta.ui.dialog.NoWarrantyDialog
import io.github.samolego.canta.ui.dialog.ShizukuRequirementDialog
import io.github.samolego.canta.ui.dialog.PackageActionDialogs
import io.github.samolego.canta.ui.component.fab.ExpandableFAB
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.navigation.Screen
import io.github.samolego.canta.ui.screen.LogsPage
import io.github.samolego.canta.ui.screen.PresetsPage
import io.github.samolego.canta.ui.screen.SettingsScreen
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PresetsViewModel
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import io.github.samolego.canta.ui.viewmodel.SettingsViewModelFactory
import io.github.samolego.canta.util.apps.Filter
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.ui.theme.LocalCanaColors
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.shizuku.AuthorizationState
import kotlinx.coroutines.launch


@Composable
fun CantaApp(
    closeApp: () -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val appListViewModel = viewModel<AppListViewModel>()
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory())
    val presetViewModel = viewModel<PresetsViewModel>()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiMessages = remember { io.github.samolego.canta.ops.CanaServices.getInstance().uiMessages }
    CanaMessageCollector(uiMessages, snackbarHostState)
    PackageActionDialogs(appListViewModel)

    CompositionLocalProvider(LocalCanaSnackbar provides snackbarHostState) {
    Column(Modifier.fillMaxSize()) {
    io.github.samolego.canta.ui.component.BatchProgressBanner { navController.navigate(Screen.History.route) { launchSingleTop = true } }
    NavHost(navController = navController, startDestination = Screen.Main.route, modifier = Modifier.weight(1f)) {
        composable(Screen.AppDetail.route) { entry ->
            val target = io.github.samolego.canta.ops.AppTarget(entry.arguments!!.getString("packageName")!!,
                entry.arguments!!.getString("userId")!!.toInt())
            io.github.samolego.canta.ui.screen.AppDetailPage(target, { navController.navigateUp() }) { action ->
                appListViewModel.requestAction(action, listOf(target.packageName), target.userId)
                navController.popBackStack(Screen.Main.route, inclusive = false)
            }
        }
        composable(Screen.Privacy.route) {
            io.github.samolego.canta.ui.screen.PrivacyDashboardPage { navController.navigateUp() }
        }
        composable(Screen.Comparison.route) {
            io.github.samolego.canta.ui.screen.ProfileComparisonPage { navController.navigateUp() }
        }
        composable(Screen.Main.route) {
            val presetSaveError = stringResource(R.string.preset_save_error)
            MainContent(
                navigateToPage = { navController.navigate(it) },
                closeApp = closeApp,
                presetEditMode = presetViewModel.editingPreset != null,
                onPresetEditFinish = {
                    // Save all the selected apps to the preset
                    presetViewModel.editingPreset?.let { preset ->
                        presetViewModel.setPresetApps(
                            preset = preset,
                            newApps = appListViewModel.selectedApps.keys,
                            onSuccess = {
                                appListViewModel.selectedApps.clear()
                                presetViewModel.editingPreset = null
                                navController.navigate(Screen.Presets.route)
                            },
                            onError = {
                                uiMessages.post(presetSaveError)
                            }
                        )
                    }
                },
                enableSelectAll = true,
                appListViewModel = appListViewModel,
                settingsViewModel = settingsViewModel,
            )
        }
        composable(route = Screen.Logs.route) {
            LogsPage(onNavigateBack = { navController.navigateUp() })
        }
        composable(route = Screen.History.route) {
            io.github.samolego.canta.ui.screen.HistoryPage(onNavigateBack = { navController.navigateUp() })
        }
        composable(route = Screen.System.route) {
            io.github.samolego.canta.ui.screen.SystemPage(onNavigateBack = { navController.navigateUp() })
        }
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.navigateUp() },
                settingsViewModel = settingsViewModel,
                onVersionTap = {},
                onOpenLogs = { navController.navigate(Screen.Logs.route) },
            )
        }

        composable(route = Screen.Presets.route) {
            PresetsPage(
                presetViewModel = presetViewModel,
                onNavigateBack = { preset ->
                    preset?.let {
                        appListViewModel.selectedApps.clear()
                        // Select apps in appListViewModel according to preset
                        appListViewModel.selectedApps.addAll(it.apps)
                    }

                    navController.navigateUp()
                },
                appListViewModel = appListViewModel,
            )
        }
    }
}
}
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    onPresetEditFinish: () -> Unit,
    navigateToPage: (route: String) -> Unit,
    closeApp: () -> Unit,
    enableSelectAll: Boolean,
    presetEditMode: Boolean,
    appListViewModel: AppListViewModel,
    settingsViewModel: SettingsViewModel,
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val profileLoadFailed = stringResource(R.string.profiles_load_failed)
    val profileKind = appListViewModel.selectedProfile?.kind
        ?: if (appListViewModel.selectedUserId == 0 && UserProfile.currentUserId == 0) UserProfile.Kind.PERSONAL else UserProfile.Kind.OTHER
    val headerColor = LocalCanaColors.current.profileTint(profileKind)
    val headerContentColor = when (profileKind) {
        UserProfile.Kind.PERSONAL -> MaterialTheme.colorScheme.onPrimaryContainer
        UserProfile.Kind.WORK -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val installedCount = appListViewModel.allApps.count { !it.isUninstalled }
    val removedCount = appListViewModel.allApps.count { it.isUninstalled }

    // Selected tab
    var selectedAppsType by remember { mutableStateOf(AppsType.INSTALLED) }

    val disableRiskDialog by settingsViewModel.disableRiskDialog.collectAsStateWithLifecycle()

    var showShizukuDialog by rememberSaveable { mutableStateOf(false) }

    if (!disableRiskDialog) {
        NoWarrantyDialog(
            onProceed = { neverShowAgain ->
                settingsViewModel.saveDisableRiskDialog(neverShowAgain)
            },
            onCancel = { closeApp() }
        )
    }


    val pagerState = rememberPagerState(pageCount = { AppsType.entries.size })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedAppsType = AppsType.entries[page]
        }
    }


    var showProfilesMenu by remember { mutableStateOf(false) }

    LaunchedEffect(appListViewModel) {
        ShizukuPermission.refresh()
        ShizukuPermission.state.collect { authorization ->
            if (authorization == AuthorizationState.GRANTED) appListViewModel.loadUsers()
        }
    }

    val openProfilesMenu = {
        coroutineScope.launch {
            if (appListViewModel.loadUsers()) {
                showProfilesMenu = true
            } else {
                io.github.samolego.canta.ops.CanaServices.getInstance().uiMessages.post(profileLoadFailed)
            }
        }
    }

    if (showShizukuDialog) ShizukuRequirementDialog { proceed ->
        showShizukuDialog = false
        if (proceed) openProfilesMenu()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(LocalCanaSnackbar.current) },
        topBar = {
            CantaTopBar(
                navigateToPage = navigateToPage,
                appListViewModel = appListViewModel,
                selectedAppsType = selectedAppsType,
                showProfilesMenu = showProfilesMenu,
                onProfilesClick = {
                    // Other profiles can only be seen through Shizuku
                    if (ShizukuPermission.isCantaAuthorized()) {
                        openProfilesMenu()
                    } else {
                        showShizukuDialog = true
                    }
                },
                onProfileSelected = { profile ->
                    coroutineScope.launch {
                        appListViewModel.selectUser(profile, context.packageManager, context)
                    }
                },
                onDismissProfilesMenu = { showProfilesMenu = false },
                containerColor = headerColor,
                scrollBehavior = scrollBehavior,
            )

        },
        bottomBar = {
            if (!presetEditMode) io.github.samolego.canta.ui.component.SelectionActionBar(appListViewModel, selectedAppsType)
        },
        floatingActionButton = {
            if (presetEditMode && appListViewModel.selectedApps.isNotEmpty()) PresetEditFAB(onPresetEditFinish = onPresetEditFinish)
        },
        floatingActionButtonPosition = FabPosition.End,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            io.github.samolego.canta.ui.component.OtaBanner()
            PrimaryTabRow(
                selectedTabIndex = selectedAppsType.ordinal,
                contentColor = headerContentColor,
                containerColor = headerColor,
            ) {
                AppsType.entries.forEach { currentTab ->
                    Tab(
                        selected = selectedAppsType == currentTab,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(currentTab.ordinal)
                                                }
                            selectedAppsType = currentTab
                        },
                        text = {
                            val count = if (currentTab == AppsType.INSTALLED) installedCount else removedCount
                            Text(pluralStringResource(
                                if (currentTab == AppsType.INSTALLED) R.plurals.tab_installed_count else R.plurals.tab_removed_count,
                                count, count,
                            ), maxLines = 1)
                        },
                    )
                }
            }
            io.github.samolego.canta.ui.component.ActiveFilterChips(appListViewModel)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->

                var isRefreshing by remember { mutableStateOf(false) }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            appListViewModel.loadInstalled(
                                packageManager = context.packageManager,
                                context = context,
                                forceRefresh = true,
                            )
                            isRefreshing = false
                        }
                    },
                ) {
                    AppList(
                        appType = AppsType.entries[page],
                        appListModel = appListViewModel,
                        settingsViewModel = settingsViewModel,
                        enableSelectAll = enableSelectAll,
                        onOpenDetails = { pkg, user -> navigateToPage(Screen.AppDetail.path(pkg, user)) },
                    )
                }
            }
        }
    }
}

enum class AppsType { INSTALLED, UNINSTALLED }
