package com.sakura.sakuravaults

import com.sakura.sakuravaults.utils.SakuraVaults
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

class VaultManager(private val plugin: SakuraVaults) : Listener {

    private val vaults: MutableMap<UUID, MutableList<Inventory>> = mutableMapOf()
    private val unlockedVaults: MutableMap<UUID, MutableSet<Int>> = mutableMapOf()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun openVault(player: Player, vaultNumber: Int, targetPlayer: Player? = null) {
        val actualPlayer = targetPlayer ?: player
        val unlocked = unlockedVaults.getOrPut(actualPlayer.uniqueId) { loadUnlockedVaults(actualPlayer.uniqueId) }
        if (vaultNumber !in unlocked) {
            plugin.config.getString("messages.vault_not_unlocked")?.let { player.sendMessage(it) }
            return
        }
        val vault = getVault(actualPlayer.uniqueId, vaultNumber)
        player.openInventory(vault)
    }

    private fun getVault(playerUUID: UUID, vaultNumber: Int): Inventory {
        val playerVaults = vaults.getOrPut(playerUUID) { mutableListOf() }
        if (vaultNumber > playerVaults.size) {
            for (i in playerVaults.size until vaultNumber) {
                val newVault = Bukkit.createInventory(null, 54, plugin.config.getString("gui.vault_name")!!.replace("%vault%", (i + 1).toString()))
                initializeVault(newVault, i + 1)
                loadVaultContents(playerUUID, i + 1, newVault)
                updateVaultIndicators(newVault, i + 1, playerUUID)
                playerVaults.add(newVault)
            }
        }
        return playerVaults[vaultNumber - 1]
    }

    private fun initializeVault(vault: Inventory, vaultNumber: Int) {
        for (i in 9 until 54) {
            vault.setItem(i, createLockedSlotItem(vaultNumber, i))
        }
    }

    private fun createLockedSlotItem(vaultNumber: Int, slotIndex: Int): ItemStack {
        val item = ItemStack(Material.valueOf(plugin.config.getString("glass_item.material", "BLACK_STAINED_GLASS_PANE")!!))
        val meta = item.itemMeta
        val price = calculateUnlockCost(vaultNumber, slotIndex)
        meta?.setDisplayName(plugin.config.getString("glass_item.name")?.replace("%price%", price.toString())?.replace("%slot%", slotIndex.toString()))
        meta?.lore = plugin.config.getStringList("glass_item.lore").map { it.replace("%price%", price.toString()).replace("%slot%", slotIndex.toString()) }
        if (plugin.config.contains("glass_item.model_data")) {
            meta?.setCustomModelData(plugin.config.getInt("glass_item.model_data"))
        }
        item.itemMeta = meta
        return item
    }

    private fun updateVaultIndicators(vault: Inventory, currentVault: Int, playerUUID: UUID) {
        val unlocked = unlockedVaults.getOrPut(playerUUID) { loadUnlockedVaults(playerUUID) }
        for (i in 0 until 9) {
            val item = if (i + 1 in unlocked) {
                createShulkerItem("unlocked", i + 1)
            } else {
                createShulkerItem("locked", i + 1)
            }
            vault.setItem(45 + i, item)
        }
    }

    private fun createShulkerItem(type: String, vaultNumber: Int): ItemStack {
        val material = Material.valueOf(plugin.config.getString("shulker_item.$type.material")!!)
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.setDisplayName(plugin.config.getString("shulker_item.$type.name")?.replace("%vault%", vaultNumber.toString()))
        if (plugin.config.contains("shulker_item.$type.model_data")) {
            meta?.setCustomModelData(plugin.config.getInt("shulker_item.$type.model_data"))
        }
        item.itemMeta = meta
        return item
    }

    fun unlockSlot(player: Player, vaultNumber: Int, slotIndex: Int) {
        val vault = getVault(player.uniqueId, vaultNumber)
        val cost = calculateUnlockCost(vaultNumber, slotIndex)
        val previousSlotIndex = slotIndex - 1
        if (slotIndex > 9 && vault.getItem(previousSlotIndex)?.type == Material.valueOf(plugin.config.getString("glass_item.material", "BLACK_STAINED_GLASS_PANE")!!)) {
            player.sendMessage(plugin.config.getString("messages.slot_not_unlocked")!!)
            return
        }
        if (SakuraVaults.economy.getBalance(player) >= cost) {
            SakuraVaults.economy.withdrawPlayer(player, cost)
            vault.setItem(slotIndex, ItemStack(Material.AIR))
            plugin.config.getString("messages.slot_unlocked")?.let { player.sendMessage(it) }
            if (isVaultFullyUnlocked(vault)) {
                unlockNextVault(player, vaultNumber)
            }
            updateVaultIndicators(vault, vaultNumber, player.uniqueId)
        } else {
            plugin.config.getString("messages.not_enough_money")?.let { player.sendMessage(it) }
        }
    }

    private fun calculateUnlockCost(vaultNumber: Int, slotIndex: Int): Double {
        val exponentialRate = plugin.config.getDouble("exponential_rate", 2.0)
        return 5022.0 * Math.pow(exponentialRate, (vaultNumber - 1).toDouble()) * (slotIndex + 1)
    }

    private fun isVaultFullyUnlocked(vault: Inventory): Boolean {
        for (i in 9 until 54) {
            if (vault.getItem(i)?.type == Material.valueOf(plugin.config.getString("glass_item.material", "BLACK_STAINED_GLASS_PANE")!!)) {
                return false
            }
        }
        return true
    }

    private fun unlockNextVault(player: Player, currentVault: Int) {
        val unlocked = unlockedVaults.getOrPut(player.uniqueId) { loadUnlockedVaults(player.uniqueId) }
        unlocked.add(currentVault + 1)
        saveUnlockedVault(player.uniqueId, currentVault + 1)
        player.sendMessage(plugin.config.getString("messages.vault_unlocked")!!.replace("%vault%", (currentVault + 1).toString()))
    }

    @org.bukkit.event.EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val vaultNumber = getVaultNumberFromTitle(event.view.title) ?: return
        val slotIndex = event.rawSlot

        if (slotIndex in 45..53) {
            // Handle clicks on shulker boxes
            val shulkerItem = event.currentItem ?: return
            val targetVault = slotIndex - 44
            if (shulkerItem.type == Material.valueOf(plugin.config.getString("shulker_item.unlocked.material")!!)) {
                openVault(player, targetVault)
            } else if (shulkerItem.type == Material.valueOf(plugin.config.getString("shulker_item.locked.material")!!)) {
                player.sendMessage(plugin.config.getString("messages.vault_not_unlocked")!!)
            }
            event.isCancelled = true
            return
        }

        if (slotIndex in 9 until 54) {
            event.isCancelled = true
            if (event.currentItem?.type == Material.valueOf(plugin.config.getString("glass_item.material", "BLACK_STAINED_GLASS_PANE")!!)) {
                unlockSlot(player, vaultNumber, slotIndex)
            }
        }
    }

    @org.bukkit.event.EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val vaultNumber = getVaultNumberFromTitle(event.view.title) ?: return
        saveVaultContents(player.uniqueId, vaultNumber, event.inventory)
    }

    private fun getVaultNumberFromTitle(title: String): Int? {
        return Regex(plugin.config.getString("gui.vault_name")!!.replace("%vault%", "(\\d+)")).find(title)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun saveVaultContents(playerUUID: UUID, vaultNumber: Int, inventory: Inventory) {
        val sql = "REPLACE INTO vaults (player_uuid, vault_number, slot_index, item_data) VALUES (?, ?, ?, ?)"
        try {
            val pstmt: PreparedStatement = SakuraVaults.connection.prepareStatement(sql)
            for (i in 9 until 54) {
                val item = inventory.getItem(i)
                if (item != null && item.type != Material.AIR) {
                    pstmt.setString(1, playerUUID.toString())
                    pstmt.setInt(2, vaultNumber)
                    pstmt.setInt(3, i)
                    val itemData = YamlConfiguration().apply { set("item", item.serialize()) }.saveToString()
                    pstmt.setString(4, itemData)
                    pstmt.addBatch()
                }
            }
            pstmt.executeBatch()
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to save vault contents: ${e.message}")
        }
    }

    private fun loadVaultContents(playerUUID: UUID, vaultNumber: Int, inventory: Inventory) {
        val sql = "SELECT slot_index, item_data FROM vaults WHERE player_uuid = ? AND vault_number = ?"
        try {
            val pstmt: PreparedStatement = SakuraVaults.connection.prepareStatement(sql)
            pstmt.setString(1, playerUUID.toString())
            pstmt.setInt(2, vaultNumber)
            val rs: ResultSet = pstmt.executeQuery()
            while (rs.next()) {
                val slotIndex = rs.getInt("slot_index")
                val itemData = rs.getString("item_data")
                val yaml = YamlConfiguration().apply { loadFromString(itemData) }
                val itemMap = yaml.getConfigurationSection("item")?.getValues(false) ?: continue
                val item = ItemStack.deserialize(itemMap as Map<String, Any>)
                inventory.setItem(slotIndex, item)
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to load vault contents: ${e.message}")
        }
    }

    private fun saveUnlockedVault(playerUUID: UUID, vaultNumber: Int) {
        val sql = "REPLACE INTO unlocked_vaults (player_uuid, vault_number) VALUES (?, ?)"
        try {
            val pstmt: PreparedStatement = SakuraVaults.connection.prepareStatement(sql)
            pstmt.setString(1, playerUUID.toString())
            pstmt.setInt(2, vaultNumber)
            pstmt.executeUpdate()
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to save unlocked vault: ${e.message}")
        }
    }

    private fun loadUnlockedVaults(playerUUID: UUID): MutableSet<Int> {
        val sql = "SELECT vault_number FROM unlocked_vaults WHERE player_uuid = ?"
        val unlockedVaults = mutableSetOf(1) // By default, vault 1 is unlocked
        try {
            val pstmt: PreparedStatement = SakuraVaults.connection.prepareStatement(sql)
            pstmt.setString(1, playerUUID.toString())
            val rs: ResultSet = pstmt.executeQuery()
            while (rs.next()) {
                unlockedVaults.add(rs.getInt("vault_number"))
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to load unlocked vaults: ${e.message}")
        }
        return unlockedVaults
    }
}
