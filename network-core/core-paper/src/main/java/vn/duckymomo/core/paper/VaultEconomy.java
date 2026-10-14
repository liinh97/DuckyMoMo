package vn.duckymomo.core.paper;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Thu/trả tiền qua Vault (VaultUnlocked + EssentialsX Economy ở banghoi). Tách lớp riêng: chỉ gọi khi available()
 * để server không có Vault không bị lỗi thiếu lớp.
 */
final class VaultEconomy {

    private VaultEconomy() {
    }

    static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("Vault") && economy() != null;
    }

    private static Economy economy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    static boolean has(OfflinePlayer p, double amount) {
        return economy().has(p, amount);
    }

    static boolean withdraw(OfflinePlayer p, double amount) {
        return economy().has(p, amount) && economy().withdrawPlayer(p, amount).transactionSuccess();
    }

    static void deposit(OfflinePlayer p, double amount) {
        economy().depositPlayer(p, amount);
    }

    static String format(double amount) {
        return economy().format(amount);
    }
}
