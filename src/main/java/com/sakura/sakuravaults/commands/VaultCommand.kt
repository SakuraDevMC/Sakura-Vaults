package com.sakura.sakuravaults.commands

import com.sakura.sakuravaults.SakuraVaults
import com.sakura.sakuravaults.utils.VaultManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class VaultCommand(private val vaultManager: VaultManager, private val plugin: SakuraVaults) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage(formatMessage("Only players can use this command."))
            return true
        }

        val player = sender

        if (args.isNullOrEmpty()) {
            vaultManager.openVault(player, 1)
        } else {
            val vaultNumber = args[0].toIntOrNull()
            if (vaultNumber == null) {
                player.sendMessage(formatMessage("Invalid vault number."))
                return true
            }
            vaultManager.openVault(player, vaultNumber)
        }

        return true
    }

    private fun formatMessage(message: String): Component {
        return LegacyComponentSerializer.legacy('&').deserialize(message)
    }
}
