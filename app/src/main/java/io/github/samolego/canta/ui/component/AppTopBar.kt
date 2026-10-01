package io.github.samolego.canta.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.menu.*
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.util.apps.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CantaTopBar(
    navigateToPage: (route: String) -> Unit,
    appListViewModel: AppListViewModel,
    selectedAppsType: AppsType,
    showProfilesMenu: Boolean,
    onProfilesClick: () -> Unit,
    onProfileSelected: (UserProfile) -> Unit,
    onDismissProfilesMenu: () -> Unit,
    containerColor: Color,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var more by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val closeSearch = { searchActive = false; appListViewModel.searchQuery = ""; keyboard?.hide(); Unit }
    BackHandler(searchActive, closeSearch)
    Column(Modifier.background(containerColor)) {
        TopAppBar(title = {
            Row(Modifier.widthIn(max = 216.dp), verticalAlignment = Alignment.CenterVertically) {
                CanaWordmark(style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    Surface(
                        onClick = onProfilesClick,
                        modifier = Modifier.fillMaxWidth().testTag("profile-chip"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(appListViewModel.selectedProfile?.icon() ?: Icons.Default.Person,
                                contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(profileName(appListViewModel.selectedUserId),
                                modifier = Modifier.weight(1f), maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    ProfilesMenu(showProfilesMenu, appListViewModel.users, appListViewModel.selectedUserId, onProfileSelected, onDismissProfilesMenu)
                }
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
            scrollBehavior = scrollBehavior,
            actions = {
                IconClickButton(onClick = { searchActive = !searchActive }, icon = Icons.Default.Search, contentDescription = stringResource(R.string.search_action))
                IconClickButton(onClick = { filters = true }, icon = Icons.Default.FilterAlt, contentDescription = stringResource(R.string.filters_action))
                Box {
                    IconClickButton(onClick = { more = true }, icon = Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options_action))
                    MoreOptionsMenu(more, navigateToPage, { startTrackerScan(context, appListViewModel) }) { more = false }
                }
            })
        if (searchActive || appListViewModel.searchQuery.isNotEmpty()) {
            OutlinedTextField(value = appListViewModel.searchQuery, onValueChange = { appListViewModel.searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp).focusRequester(focus),
                label = { Text(stringResource(R.string.search_apps)) }, singleLine = true,
                leadingIcon = { IconClickButton(onClick = closeSearch, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                trailingIcon = { if (appListViewModel.searchQuery.isNotEmpty()) IconClickButton(onClick = { appListViewModel.searchQuery = "" },
                    icon = Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search_action)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
            LaunchedEffect(searchActive) { if (searchActive) { focus.requestFocus(); keyboard?.show() } }
        }
        FiltersMenu(filters, { filters = false }, appListViewModel, selectedAppsType)
    }
}
