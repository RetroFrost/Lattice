package com.retrofrost.lattice.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.retrofrost.lattice.model.LatticeSettings

private data class HomeTab(val title: String, val icon: ImageVector)

@Composable
fun LatticeApp(incomingLink: String?) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }

    MaterialTheme(colorScheme = colors) {
        var settings by remember { mutableStateOf(LatticeSettings()) }
        var showSettings by remember { mutableStateOf(false) }

        if (showSettings) {
            SettingsScreen(
                settings = settings,
                onSettingsChange = { settings = it },
                onBack = { showSettings = false }
            )
        } else {
            HomeScreen(
                incomingLink = incomingLink,
                onOpenSettings = { showSettings = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(incomingLink: String?, onOpenSettings: () -> Unit) {
    val tabs = listOf(
        HomeTab("Chats", Icons.Outlined.Message),
        HomeTab("Updates", Icons.Outlined.Update),
        HomeTab("Groups", Icons.Outlined.Groups),
        HomeTab("Calls", Icons.Outlined.Call)
    )
    var selected by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lattice") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        val title = tabs[selected].title
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Maximum Privacy", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Optional Telegram data is disabled. Direct-IP fallback is blocked when privacy routing is enabled.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (!incomingLink.isNullOrBlank()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Telegram link received", style = MaterialTheme.typography.titleMedium)
                            Text(incomingLink, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Text("It will open through TDLib once the Telegram transport is connected.")
                        }
                    }
                }
            }

            item {
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }

            items(placeholderRowsFor(title)) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(row.first, style = MaterialTheme.typography.titleMedium)
                        Text(row.second, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun placeholderRowsFor(tab: String): List<Pair<String, String>> = when (tab) {
    "Chats" -> listOf(
        "Telegram connection not configured" to "TDLib authentication will replace this bootstrap state.",
        "Private chats" to "Cloud chats and Secret Chats will be clearly distinguished."
    )
    "Updates" -> listOf(
        "Channels" to "Joined channels, posts and discussions belong here.",
        "Paid media" to "Stars-locked previews are hidden under Maximum Privacy."
    )
    "Groups" -> listOf(
        "Groups & supergroups" to "Members, replies, reactions, permissions and invite links are in scope."
    )
    else -> listOf(
        "Calls" to "Peer-to-peer calls are disabled when Hide my IP is active."
    )
}
