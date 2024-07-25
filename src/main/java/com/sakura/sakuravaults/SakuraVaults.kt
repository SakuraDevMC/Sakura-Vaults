package com.sakura.sakuravaults

import com.sakura.sakuravaults.commands.AdminCommand
import com.sakura.sakuravaults.commands.VaultCommand
import com.sakura.sakuravaults.utils.VaultManager
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class SakuraVaults : JavaPlugin() {

    companion object {
        lateinit var economy: Economy
        lateinit var connection: Connection
    }

    override fun onEnable() {
        if (!setupEconomy()) {
            logger.severe("Vault dependency not found! Disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Load config
        saveDefaultConfig()

        // Initialize SQLite
        initializeDatabase()

        // Register the VaultManager and Command Executors
        val vaultManager = VaultManager(this)
        getCommand("pv")?.setExecutor(VaultCommand(vaultManager, this))
        getCommand("pvadmin")?.setExecutor(AdminCommand(vaultManager, this))

        logger.info("SakuraVaults has been enabled")
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("SakuraVaults has been disabled")
        connection.close()
    }

    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) {
            logger.severe("Vault plugin not found!")
            return false
        }
        val rsp = server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            logger.severe("No economy provider found in Vault!")
            return false
        }
        economy = rsp.provider
        logger.info("Economy provider found: ${rsp.provider.name}")
        return true
    }

    private fun initializeDatabase() {
        try {
            // Ensure the directory exists
            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }

            connection = DriverManager.getConnection("jdbc:sqlite:${dataFolder}/vaults.db")
            connection.createStatement().executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS vaults (
                    player_uuid TEXT NOT NULL,
                    vault_number INTEGER NOT NULL,
                    slot_index INTEGER NOT NULL,
                    item_data TEXT NOT NULL,
                    PRIMARY KEY (player_uuid, vault_number, slot_index)
                )
                """
            )
            connection.createStatement().executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS unlocked_vaults (
                    player_uuid TEXT NOT NULL,
                    vault_number INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, vault_number)
                )
                """
            )
            logger.info("Database initialized successfully")
        } catch (e: SQLException) {
            logger.severe("Failed to initialize database: ${e.message}")
        }
    }
}
