package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.efah.nextbox.bg.Box
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.GlassScreen
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.Segmented
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType

/** The "selected apps" scope: only these apps use the VPN, or every app except these. */
@Composable
fun PerAppScreen(onClose: () -> Unit) {
    val c = Nb.c
    val routeScope by Prefs.routeScope.collectAsState()
    val mode by Prefs.perAppMode.collectAsState()
    val selected by Prefs.perAppList.collectAsState()
    // the VPN only picks up the new app list when the core reloads
    DisposableEffect(Unit) { onDispose { Box.reloadIfRunning() } }

    GlassScreen(header = {
        TopBar(T("指定应用"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) {
            Text(T("已选 %d 个", selected.size), style = NbType.small, color = c.muted, modifier = Modifier.padding(end = 12.dp))
        }
    }) { pad ->
        AppChooser(
            selected = selected,
            onChange = { Prefs.setPerAppList(it) },
            topPadding = pad.calculateTopPadding() + 4.dp,
        ) {
            item(key = "mode") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    Segmented(
                        listOf(T("只代理选中的应用"), T("排除选中的应用")),
                        if (mode == Prefs.APPS_EXCLUDE) 1 else 0,
                        onSelect = { Prefs.setPerAppMode(if (it == 1) Prefs.APPS_EXCLUDE else Prefs.APPS_INCLUDE) },
                    )
                    Text(
                        buildString {
                            append(
                                if (mode == Prefs.APPS_EXCLUDE) {
                                    T("选中的应用直接联网，其余应用都走代理；以后新装的应用也走代理。适合排除银行、支付类应用。")
                                } else {
                                    T("只有选中的应用经过 VPN，其他应用直接联网。")
                                },
                            )
                            if (routeScope != Prefs.SCOPE_APPS) append(T("在首页选「指定应用」后生效。"))
                        },
                        style = NbType.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
