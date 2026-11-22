package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import dev.efah.nextbox.ui.TopBar

/**
 * 代理范围: which apps use the VPN — every app, only the selected ones, or
 * every app except the selected ones. Routing (global / rule / direct)
 * applies to whatever goes through the VPN.
 */
@Composable
fun PerAppScreen(onClose: () -> Unit) {
    val c = Nb.c
    val routeScope by Prefs.routeScope.collectAsState()
    val mode by Prefs.perAppMode.collectAsState()
    val selected by Prefs.perAppList.collectAsState()
    // the VPN only picks up the new app list when the core reloads
    DisposableEffect(Unit) { onDispose { Box.reloadIfRunning() } }
    val choice = when {
        routeScope != Prefs.SCOPE_APPS -> 0
        mode == Prefs.APPS_EXCLUDE -> 2
        else -> 1
    }

    GlassScreen(header = {
        TopBar(T("代理范围"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) }) {
            if (choice != 0) Text(T("已选 %d 个", selected.size), style = NbType.small, color = c.muted, modifier = Modifier.padding(end = 12.dp))
        }
    }) { pad ->
        AppChooser(
            selected = selected,
            onChange = { Prefs.setPerAppList(it) },
            topPadding = pad.calculateTopPadding() + 4.dp,
            enabled = choice != 0,
        ) {
            item(key = "mode") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    Segmented(
                        listOf(T("所有应用"), T("只代理选中的"), T("排除选中的")),
                        choice,
                        onSelect = {
                            when (it) {
                                0 -> Prefs.setRouteScope(Prefs.SCOPE_GLOBAL)
                                1 -> { Prefs.setRouteScope(Prefs.SCOPE_APPS); Prefs.setPerAppMode(Prefs.APPS_INCLUDE) }
                                else -> { Prefs.setRouteScope(Prefs.SCOPE_APPS); Prefs.setPerAppMode(Prefs.APPS_EXCLUDE) }
                            }
                        },
                    )
                    Text(
                        when (choice) {
                            0 -> T("手机上所有应用都经过 VPN，按路由模式和分流规则决定走代理还是直连。下面选好的应用会保留，切换范围后生效。")
                            2 -> T("选中的应用直接联网，其余应用都走 VPN；以后新装的应用也走 VPN。适合排除银行、支付类应用。")
                            else -> T("只有选中的应用经过 VPN，其他应用直接联网。")
                        },
                        style = NbType.small, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
