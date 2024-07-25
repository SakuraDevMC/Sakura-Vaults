package com.sakura.sakuravaults.commands

import com.sakura.sakuravaults.SakuraVaults
import com.sakura.sakuravaults.utils.VaultManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class AdminCommand(private val vaultManager: VaultManager, private val plugin: SakuraVaults) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("sakuravaults.admin")) {
            sender.sendMessage(plugin.config.getString("messages.no_permission") ?: "You do not have permission to use this command.")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.config.getString("messages.usage") ?: "Usage: /pvadmin <open/view> <player> [number]")
            return true
        }

        val action = args[0]
        val targetPlayer = Bukkit.getPlayer(args[1]) ?: run {
            sender.sendMessage(plugin.config.getString("messages.player_not_found") ?: "Player not found.")
            return true
        }
        val vaultNumber = if (args.size > 2) args[2].toIntOrNull() ?: 1 else 1

        when (action.toLowerCase()) {
            "open" -> {
                if (sender is Player) {
                    vaultManager.openVault(sender, vaultNumber, targetPlayer)
                } else {
                    sender.sendMessage(plugin.config.getString("messages.player_only_command") ?: "Only players can open vaults.")
                }
            }
            "view" -> {
                // Implementation for viewing vaults (can be the same as open or different depending on requirements)
                if (sender is Player) {
                    vaultManager.openVault(sender, vaultNumber, targetPlayer)
                } else {
                    sender.sendMessage(plugin.config.getString("messages.player_only_command") ?: "Only players can view vaults.")
                }
            }
            else -> {
                sender.sendMessage(plugin.config.getString("messages.invalid_action") ?: "Invalid action. Use 'open' or 'view'.")
            }
        }

        return true
    }
}
